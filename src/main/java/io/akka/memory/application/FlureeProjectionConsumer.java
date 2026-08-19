package io.akka.memory.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import io.akka.memory.domain.PartitionEvent;

/**
 * Builds the read projection from what the write path recorded.
 *
 * <p>This is the whole of the read side's maintenance. Every read — retrieval, fact lookup, episode
 * listing — resolves against what this consumer wrote, so the write path is never queried to answer
 * a question (RENDER-001 §3.4).
 *
 * <p>Delivery is at-least-once, so every write here is a replace rather than an append. That is not
 * defensive coding for its own sake: in this store a second value for a property is <em>added</em>
 * beside the first, so a redelivered event would leave a fact carrying two statements.
 *
 * <p>Embedding happens here rather than on the write path, because it is a network call to a model
 * and the write path's job is to acknowledge and record. A fact whose vector cannot be computed is
 * still projected; it simply cannot appear in the similarity list.
 */
@Component(id = "fluree-projection")
@Consume.FromEventSourcedEntity(PartitionEntity.class)
public class FlureeProjectionConsumer extends Consumer {

  private final FlureeStore store;
  private final Embedder embedder;

  public FlureeProjectionConsumer(FlureeStore store, Embedder embedder) {
    this.store = store;
    this.embedder = embedder;
  }

  public Effect onEvent(PartitionEvent event) {
    String partition = messageContext().eventSubject().orElse("");
    switch (event) {
      case PartitionEvent.EpisodeRecorded e ->
          store.putEpisode(
              new FlureeStore.StoredEpisode(
                  e.episode().id(),
                  e.episode().partition(),
                  e.episode().content(),
                  e.episode().kind().name().toLowerCase(java.util.Locale.ROOT),
                  e.episode().referenceTime(),
                  e.episode().sourceDescription(),
                  e.episode().recordedAt()));

      case PartitionEvent.EntityCreated e ->
          store.putEntity(
              new FlureeStore.StoredEntity(
                  e.entity().id(), e.entity().partition(), e.entity().name(), e.entity().summary()));

      case PartitionEvent.EntityHydrated e -> store.updateEntitySummary(e.entityId(), e.summary());

      case PartitionEvent.FactRecorded e ->
          store.putFacts(
              java.util.List.of(FlureeStore.project(e.fact(), embedder.embed(e.fact().statement()))));

      // Closing is a state transition, not a deletion: the fact keeps every other field.
      case PartitionEvent.FactClosed e ->
          store.closeFact(e.factId(), e.validUntil(), e.supersededAt());

      case PartitionEvent.EpisodeRemoved e -> store.deleteEpisode(e.episodeId());

      case PartitionEvent.FactRemoved e -> store.removeFact(e.factId());

      case PartitionEvent.PartitionCleared ignored -> store.clearPartition(partition);

      // Provenance only — nothing in the projection changes.
      case PartitionEvent.EntityRecognised ignored -> {
        return effects().ignore();
      }
    }
    return effects().done();
  }
}
