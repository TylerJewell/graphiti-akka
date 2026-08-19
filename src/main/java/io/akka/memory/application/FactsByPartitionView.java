package io.akka.memory.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import io.akka.memory.domain.PartitionEvent;
import java.time.Instant;
import java.util.List;

/**
 * The read side for facts.
 *
 * <p>Reads never load write state — retrieval resolves here, against a projection, so read latency
 * is not coupled to the per-partition serialisation of ingest.
 */
@Component(id = "facts-by-partition")
public class FactsByPartitionView extends View {

  /**
   * A fact as the read side sees it.
   *
   * <p>Both timelines are carried separately and deliberately: {@code validFrom}/{@code validUntil}
   * answer what was true in the world, {@code recordedAt}/{@code supersededAt} answer what the
   * system believed. Answering one from the other's data is the failure this shape prevents.
   */
  public record FactRow(
      String factId,
      String partition,
      String subjectId,
      String objectId,
      String relation,
      String statement,
      Instant validFrom,
      Instant validUntil,
      Instant recordedAt,
      Instant supersededAt) {}

  public record FactRows(List<FactRow> items) {}

  @Consume.FromEventSourcedEntity(PartitionEntity.class)
  public static class FactsUpdater extends TableUpdater<FactRow> {

    public Effect<FactRow> onEvent(PartitionEvent event) {
      return switch (event) {
        case PartitionEvent.FactRecorded e ->
            effects()
                .updateRow(
                    new FactRow(
                        e.fact().id(),
                        e.fact().partition(),
                        e.fact().subjectId(),
                        e.fact().objectId(),
                        e.fact().relation(),
                        e.fact().statement(),
                        e.fact().validFrom().orElse(null),
                        e.fact().validUntil().orElse(null),
                        e.fact().recordedAt(),
                        e.fact().supersededAt().orElse(null)));
        // Closing is a state transition, never a deletion: the row stays and gains an end.
        case PartitionEvent.FactClosed e ->
            rowState() == null
                ? effects().ignore()
                : effects()
                    .updateRow(
                        new FactRow(
                            rowState().factId(),
                            rowState().partition(),
                            rowState().subjectId(),
                            rowState().objectId(),
                            rowState().relation(),
                            rowState().statement(),
                            rowState().validFrom(),
                            e.validUntil(),
                            rowState().recordedAt(),
                            rowState().supersededAt() == null
                                ? e.supersededAt()
                                : rowState().supersededAt()));
        default -> effects().ignore();
      };
    }
  }

  @Query("SELECT * AS items FROM facts_by_partition WHERE partition = :partition")
  public QueryEffect<FactRows> byPartition(String partition) {
    return queryResult();
  }

  @Query("SELECT * FROM facts_by_partition WHERE factId = :factId")
  public QueryEffect<FactRow> byId(String factId) {
    return queryResult();
  }
}
