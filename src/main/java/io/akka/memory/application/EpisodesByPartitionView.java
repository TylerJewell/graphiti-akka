package io.akka.memory.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import io.akka.memory.domain.PartitionEvent;
import java.time.Instant;
import java.util.List;

/** The read side for episodes. */
@Component(id = "episodes-by-partition")
public class EpisodesByPartitionView extends View {

  public record EpisodeRow(
      String episodeId,
      String partition,
      String content,
      String kind,
      Instant referenceTime,
      String sourceDescription,
      Instant recordedAt) {}

  public record EpisodeRows(List<EpisodeRow> items) {}

  @Consume.FromEventSourcedEntity(PartitionEntity.class)
  public static class EpisodesUpdater extends TableUpdater<EpisodeRow> {

    public Effect<EpisodeRow> onEvent(PartitionEvent event) {
      return switch (event) {
        case PartitionEvent.EpisodeRecorded e ->
            effects()
                .updateRow(
                    new EpisodeRow(
                        e.episode().id(),
                        e.episode().partition(),
                        e.episode().content(),
                        e.episode().kind().name().toLowerCase(java.util.Locale.ROOT),
                        e.episode().referenceTime(),
                        e.episode().sourceDescription(),
                        e.episode().recordedAt()));
        case PartitionEvent.EpisodeRemoved ignored -> effects().deleteRow();
        default -> effects().ignore();
      };
    }
  }

  @Query("SELECT * AS items FROM episodes_by_partition WHERE partition = :partition")
  public QueryEffect<EpisodeRows> byPartition(String partition) {
    return queryResult();
  }
}
