package io.akka.memory.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The entities, facts and episodes of one partition.
 *
 * <p>The partition is the unit of serialisation: episodes within it are processed one at a time,
 * which is what prevents a contradiction being lost when two arrive together.
 */
public record PartitionState(
    String partition,
    Map<String, Entity> entities,
    Map<String, Fact> facts,
    Map<String, Episode> episodes) {

  public static PartitionState empty(String partition) {
    return new PartitionState(partition, Map.of(), Map.of(), Map.of());
  }

  public boolean isEmpty() {
    return entities.isEmpty() && facts.isEmpty() && episodes.isEmpty();
  }

  /**
   * Facts that could be closed by {@code incoming} — same subject and relation, and not the fact
   * itself. Whether any of them actually close is decided by {@link Fact#closes}.
   */
  public List<Fact> contradictionCandidates(Fact incoming) {
    List<Fact> out = new ArrayList<>();
    for (Fact existing : facts.values()) {
      if (!existing.id().equals(incoming.id())
          && existing.subjectId().equals(incoming.subjectId())
          && existing.relation().equals(incoming.relation())) {
        out.add(existing);
      }
    }
    return out;
  }

  /** Facts open on the <b>valid-time</b> axis at the given instant. */
  public List<Fact> validAt(Instant when) {
    return facts.values().stream().filter(f -> f.isValidAt(when)).toList();
  }

  public Optional<Entity> entityByExactName(String name) {
    String target = EntityIdentity.normalizeExact(name);
    return entities.values().stream()
        .filter(e -> EntityIdentity.normalizeExact(e.name()).equals(target))
        .findFirst();
  }

  // --- event application ---------------------------------------------------------------

  public PartitionState apply(PartitionEvent event) {
    return switch (event) {
      case PartitionEvent.EpisodeRecorded e -> withEpisode(e.episode());
      case PartitionEvent.EntityCreated e -> withEntity(e.entity());
      case PartitionEvent.EntityRecognised ignored -> this; // provenance only; no state change
      case PartitionEvent.EntityHydrated e -> hydrate(e);
      case PartitionEvent.FactRecorded e -> withFact(e.fact());
      case PartitionEvent.FactClosed e -> close(e);
      case PartitionEvent.EpisodeRemoved e -> withoutEpisode(e.episodeId());
      case PartitionEvent.FactRemoved e -> withoutFact(e.factId());
      case PartitionEvent.PartitionCleared ignored -> empty(partition);
    };
  }

  private PartitionState withEpisode(Episode episode) {
    var next = new LinkedHashMap<>(episodes);
    next.put(episode.id(), episode);
    return new PartitionState(partition, entities, facts, Map.copyOf(next));
  }

  private PartitionState withoutEpisode(String episodeId) {
    var nextEpisodes = new LinkedHashMap<>(episodes);
    nextEpisodes.remove(episodeId);
    // Facts derived solely from this episode go with it; facts with other provenance stay.
    var nextFacts = new LinkedHashMap<String, Fact>();
    facts.forEach(
        (id, fact) -> {
          var remaining = fact.episodeIds().stream().filter(e -> !e.equals(episodeId)).toList();
          if (!remaining.isEmpty()) {
            nextFacts.put(id, fact);
          }
        });
    return new PartitionState(partition, entities, Map.copyOf(nextFacts), Map.copyOf(nextEpisodes));
  }

  private PartitionState withoutFact(String factId) {
    if (!facts.containsKey(factId)) {
      return this;
    }
    var next = new LinkedHashMap<>(facts);
    next.remove(factId);
    return new PartitionState(partition, entities, Map.copyOf(next), episodes);
  }

  private PartitionState withEntity(Entity entity) {
    var next = new LinkedHashMap<>(entities);
    next.put(entity.id(), entity);
    return new PartitionState(partition, Map.copyOf(next), facts, episodes);
  }

  private PartitionState hydrate(PartitionEvent.EntityHydrated event) {
    var existing = entities.get(event.entityId());
    if (existing == null) {
      return this;
    }
    var next = new LinkedHashMap<>(entities);
    next.put(
        event.entityId(), existing.withSummary(event.summary()).withAttributes(event.attributes()));
    return new PartitionState(partition, Map.copyOf(next), facts, episodes);
  }

  private PartitionState withFact(Fact fact) {
    var next = new LinkedHashMap<>(facts);
    next.put(fact.id(), fact);
    return new PartitionState(partition, entities, Map.copyOf(next), episodes);
  }

  private PartitionState close(PartitionEvent.FactClosed event) {
    var existing = facts.get(event.factId());
    if (existing == null) {
      return this;
    }
    var next = new LinkedHashMap<>(facts);
    next.put(
        event.factId(),
        new Fact(
            existing.id(),
            existing.partition(),
            existing.subjectId(),
            existing.objectId(),
            existing.relation(),
            existing.statement(),
            existing.validFrom(),
            Optional.of(event.validUntil()),
            existing.recordedAt(),
            existing.supersededAt().isPresent()
                ? existing.supersededAt()
                : Optional.of(event.supersededAt()),
            existing.episodeIds()));
    return new PartitionState(partition, entities, Map.copyOf(next), episodes);
  }
}
