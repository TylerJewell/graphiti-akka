package io.akka.memory.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.akka.memory.domain.Entity;
import io.akka.memory.domain.EntityResolution;
import io.akka.memory.domain.Episode;
import io.akka.memory.domain.Fact;
import io.akka.memory.domain.PartitionEvent;
import io.akka.memory.domain.PartitionState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One partition of the memory.
 *
 * <p>The partition — not the episode — is the unit of concurrency. Keying the write path here gives
 * both halves of the requirement at once and without a lock: episodes within a partition are
 * serialised, and partitions do not block each other.
 */
@Component(id = "partition")
public class PartitionEntity extends EventSourcedEntity<PartitionState, PartitionEvent> {

  private final String partitionId;

  public PartitionEntity(EventSourcedEntityContext context) {
    this.partitionId = context.entityId();
  }

  @Override
  public PartitionState emptyState() {
    return PartitionState.empty(partitionId);
  }

  public record RecordEpisode(Episode episode) {}

  public record RecordFacts(List<Fact> facts, Instant now) {}

  public record RecordEntities(List<Entity> entities) {}

  public record HydrateEntity(String entityId, String summary, Map<String, Object> attributes) {}

  public Effect<Done> recordEpisode(RecordEpisode command) {
    return effects()
        .persist(new PartitionEvent.EpisodeRecorded(command.episode()))
        .thenReply(s -> Done.getInstance());
  }

  /**
   * Records entities that have <b>already been resolved</b> by the caller.
   *
   * <p>The recognition cascade is not run here, and deliberately so: when its deterministic stages
   * decline it asks the model, which cannot happen inside a command handler. The candidates it
   * compares against do come from here, via {@link #candidates()}.
   *
   * <p>What the cascade must not become is a global name lookup: two entities
   * with byte-identical names are only ever merged when a candidate search first surfaced one for
   * the other, so matching on name across the whole partition would merge entities the source system
   * keeps apart (SPEC-003 §7).
   *
   * <p>This handler therefore asks one question — does this identifier already exist here — and the
   * answer is safe because episodes in a partition are serialised, so the caller's decision cannot
   * have gone stale between deciding and recording.
   */
  public Effect<Done> recordEntities(RecordEntities command) {
    var events = new ArrayList<PartitionEvent>();
    for (Entity entity : command.entities()) {
      if (currentState().entities().containsKey(entity.id())) {
        events.add(new PartitionEvent.EntityRecognised(entity.name(), entity.id()));
      } else {
        events.add(new PartitionEvent.EntityCreated(entity));
      }
    }
    if (events.isEmpty()) {
      return effects().reply(Done.getInstance());
    }
    return effects().persistAll(events).thenReply(s -> Done.getInstance());
  }

  /**
   * Records new facts and closes any they contradict.
   *
   * <p>The decision is the domain predicate's; this handler only turns its verdict into events.
   * Note that a fact is compared against the state as it stands when the command runs — which is
   * exactly why the partition must be the serialisation boundary.
   */
  public Effect<Done> recordFacts(RecordFacts command) {
    var events = new ArrayList<PartitionEvent>();
    var working = currentState();

    for (Fact incoming : command.facts()) {
      for (Fact candidate : working.contradictionCandidates(incoming)) {
        if (incoming.closes(candidate)) {
          var closed = candidate.closedBy(incoming, command.now());
          events.add(
              new PartitionEvent.FactClosed(
                  candidate.id(),
                  closed.validUntil().orElseThrow(),
                  closed.supersededAt().orElseThrow()));
          working = working.apply(events.get(events.size() - 1));
        }
      }
      events.add(new PartitionEvent.FactRecorded(incoming));
      working = working.apply(events.get(events.size() - 1));
    }

    if (events.isEmpty()) {
      return effects().reply(Done.getInstance());
    }
    return effects().persistAll(events).thenReply(s -> Done.getInstance());
  }

  public Effect<Done> hydrateEntity(HydrateEntity command) {
    return effects()
        .persist(
            new PartitionEvent.EntityHydrated(
                command.entityId(), command.summary(), command.attributes()))
        .thenReply(s -> Done.getInstance());
  }

  public Effect<Done> removeEpisode(String episodeId) {
    return effects()
        .persist(new PartitionEvent.EpisodeRemoved(episodeId))
        .thenReply(s -> Done.getInstance());
  }

  /** Deletes a fact outright — distinct from closing it, which keeps the record readable. */
  public Effect<Done> removeFact(String factId) {
    return effects()
        .persist(new PartitionEvent.FactRemoved(factId))
        .thenReply(s -> Done.getInstance());
  }

  public Effect<Done> clear() {
    return effects().persist(new PartitionEvent.PartitionCleared()).thenReply(s -> Done.getInstance());
  }

  /**
   * The entities this partition already holds, for the recognition cascade to compare against.
   *
   * <p>Served from the write model rather than the projection, and that is not a CQRS violation:
   * this is part of deciding a write, not answering a query. It has to be — the projection is
   * eventually consistent, so a second episode could run its cascade before the first episode's
   * entities had landed, fail to recognise them, and create duplicates that nothing would report.
   */
  public ReadOnlyEffect<List<EntityResolution.Candidate>> candidates() {
    return effects()
        .reply(
            currentState().entities().values().stream()
                .map(entity -> new EntityResolution.Candidate(entity.id(), entity.name()))
                .toList());
  }

  public ReadOnlyEffect<PartitionState> get() {
    return effects().reply(currentState());
  }

  @Override
  public PartitionState applyEvent(PartitionEvent event) {
    return currentState().apply(event);
  }
}
