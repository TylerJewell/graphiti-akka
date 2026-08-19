package io.akka.memory.application;

import static java.time.Duration.ofSeconds;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import io.akka.memory.domain.Entity;
import io.akka.memory.domain.Episode;
import io.akka.memory.domain.Fact;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The seven-stage ingest pipeline.
 *
 * <p>Stages 1–6 build state in memory; stage 7 is the only one that writes. A failure before stage
 * 7 therefore persists nothing and the episode is lost cleanly.
 *
 * <p><b>Automatic step recovery is deliberately not configured.</b> The runtime offers durable
 * retry and failover for free, and enabling it would let the port recover from failures the source
 * system cannot — reaching states the specification says are unreachable, and breaking equivalence
 * in the one place the source is weakest. Suppressing a capability that costs nothing to have is
 * easy to forget, which is why it is called out here and asserted by a test rather than left as an
 * omission. Phase 2 turns it on behind a flag.
 */
@Component(id = "episode-ingest")
public class EpisodeIngestWorkflow extends Workflow<EpisodeIngestWorkflow.State> {

  public record State(
      Episode episode,
      List<Entity> entities,
      List<Fact> facts,
      boolean storeRawContent,
      String status) {

    State withEntities(List<Entity> e) {
      return new State(episode, e, facts, storeRawContent, "entities-resolved");
    }

    State withFacts(List<Fact> f) {
      return new State(episode, entities, f, storeRawContent, "facts-resolved");
    }

    State withStatus(String s) {
      return new State(episode, entities, facts, storeRawContent, s);
    }
  }

  public record Start(Episode episode, boolean storeRawContent) {}

  private final ComponentClient componentClient;

  public EpisodeIngestWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public WorkflowSettings settings() {
    // Long step timeouts because model calls are slow. No defaultStepRecovery: a failed step
    // fails the episode, which is the specified behaviour. See the class comment.
    return WorkflowSettings.builder().defaultStepTimeout(ofSeconds(90)).build();
  }

  public Effect<Done> start(Start command) {
    return effects()
        .updateState(
            new State(command.episode(), List.of(), List.of(), command.storeRawContent(), "started"))
        .transitionTo(EpisodeIngestWorkflow::extractEntitiesStep)
        .thenReply(Done.getInstance());
  }

  public ReadOnlyEffect<String> status() {
    return effects().reply(currentState() == null ? "unknown" : currentState().status());
  }

  /** Stages 3–4: extract entities, then resolve them against the partition. */
  @StepName("extract-entities")
  private StepEffect extractEntitiesStep() {
    var episode = currentState().episode();
    var extracted =
        componentClient
            .forAgent()
            .inSession(sessionId())
            .method(EntityExtractionAgent::extract)
            .invoke(
                new EntityExtractionAgent.Request(
                    episode.content(), episode.kind(), List.of(Entity.BASE_TYPE)));

    var entities = new ArrayList<Entity>();
    for (var e : extracted.extractedEntities()) {
      if (e.name() == null || e.name().isBlank()) {
        continue; // empty names are dropped before anything else sees them
      }
      // An out-of-range type index degrades to the base type rather than raising or dropping.
      String type = Entity.BASE_TYPE;
      entities.add(
          Entity.create(UUID.randomUUID().toString(), episode.partition(), e.name(), type));
    }

    componentClient
        .forEventSourcedEntity(episode.partition())
        .method(PartitionEntity::recordEntities)
        .invoke(new PartitionEntity.RecordEntities(entities));

    return stepEffects()
        .updateState(currentState().withEntities(entities))
        .thenTransitionTo(EpisodeIngestWorkflow::extractFactsStep);
  }

  /** Stage 5: extract facts and let the partition decide which existing ones they close. */
  @StepName("extract-facts")
  private StepEffect extractFactsStep() {
    var episode = currentState().episode();
    var names = currentState().entities().stream().map(Entity::name).toList();

    var extracted =
        componentClient
            .forAgent()
            .inSession(sessionId())
            .method(FactExtractionAgent::extract)
            .invoke(
                new FactExtractionAgent.Request(
                    episode.content(), names, episode.referenceTime().toString()));

    var byName = new java.util.HashMap<String, String>();
    currentState().entities().forEach(e -> byName.put(e.name(), e.id()));

    var facts = new ArrayList<Fact>();
    for (var f : extracted.edges()) {
      String subject = byName.get(f.sourceEntityName());
      String object = byName.get(f.targetEntityName());
      if (subject == null || object == null || f.fact() == null || f.fact().isBlank()) {
        continue;
      }
      facts.add(
          new Fact(
              UUID.randomUUID().toString(),
              episode.partition(),
              subject,
              object,
              f.relationType(),
              f.fact(),
              Fact.parseTimestamp(f.validAt()),
              Fact.parseTimestamp(f.invalidAt()),
              Instant.now(),
              java.util.Optional.empty(),
              List.of(episode.id())));
    }

    componentClient
        .forEventSourcedEntity(episode.partition())
        .method(PartitionEntity::recordFacts)
        .invoke(new PartitionEntity.RecordFacts(facts, Instant.now()));

    return stepEffects()
        .updateState(currentState().withFacts(facts))
        .thenTransitionTo(EpisodeIngestWorkflow::hydrateStep);
  }

  /** Stage 6: summaries, shown only the facts new in this episode. */
  @StepName("hydrate")
  private StepEffect hydrateStep() {
    var newFactStatements = currentState().facts().stream().map(Fact::statement).toList();

    for (Entity entity : currentState().entities()) {
      var summary =
          componentClient
              .forAgent()
              .inSession(sessionId())
              .method(AttributeHydrationAgent::hydrate)
              .invoke(
                  new AttributeHydrationAgent.Request(
                      entity.name(), entity.summary(), newFactStatements));

      Map<String, Object> capped =
          AttributeCap.apply(Map.of("summary", summary.summary()), java.util.Set.of());

      componentClient
          .forEventSourcedEntity(currentState().episode().partition())
          .method(PartitionEntity::hydrateEntity)
          .invoke(
              new PartitionEntity.HydrateEntity(
                  entity.id(),
                  capped.containsKey("summary") ? summary.summary() : "",
                  Map.of()));
    }

    return stepEffects()
        .updateState(currentState().withStatus("hydrated"))
        .thenTransitionTo(EpisodeIngestWorkflow::persistStep);
  }

  /** Stage 7: the only stage that writes the episode itself. */
  @StepName("persist")
  private StepEffect persistStep() {
    var episode = currentState().episode();
    // Retention blanking happens immediately before the write, so derived entities and facts
    // persist while the source text does not.
    var toStore = currentState().storeRawContent() ? episode : episode.withoutRawContent();

    componentClient
        .forEventSourcedEntity(episode.partition())
        .method(PartitionEntity::recordEpisode)
        .invoke(new PartitionEntity.RecordEpisode(toStore));

    return stepEffects().updateState(currentState().withStatus("complete")).thenEnd();
  }

  private String sessionId() {
    return commandContext().workflowId();
  }
}
