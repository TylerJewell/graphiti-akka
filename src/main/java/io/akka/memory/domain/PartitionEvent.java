package io.akka.memory.domain;

import akka.javasdk.annotations.TypeName;

/** Everything that can happen to one partition of the memory. */
public sealed interface PartitionEvent {

  @TypeName("episode-recorded")
  record EpisodeRecorded(Episode episode) implements PartitionEvent {}

  @TypeName("entity-created")
  record EntityCreated(Entity entity) implements PartitionEvent {}

  @TypeName("entity-recognised")
  record EntityRecognised(String extractedName, String resolvedEntityId) implements PartitionEvent {}

  @TypeName("entity-hydrated")
  record EntityHydrated(String entityId, String summary, java.util.Map<String, Object> attributes)
      implements PartitionEvent {}

  @TypeName("fact-recorded")
  record FactRecorded(Fact fact) implements PartitionEvent {}

  /**
   * A fact was closed by a newer contradicting one.
   *
   * <p>Deliberately distinct from any deletion event: closing is a state transition and the record
   * survives it.
   */
  @TypeName("fact-closed")
  record FactClosed(String factId, java.time.Instant validUntil, java.time.Instant supersededAt)
      implements PartitionEvent {}

  @TypeName("episode-removed")
  record EpisodeRemoved(String episodeId) implements PartitionEvent {}

  @TypeName("partition-cleared")
  record PartitionCleared() implements PartitionEvent {}
}
