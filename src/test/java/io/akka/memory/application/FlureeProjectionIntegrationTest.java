package io.akka.memory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.akka.memory.application.FlureeStore.StoredEpisode;
import io.akka.memory.application.FlureeStore.StoredFact;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The projection's own behaviour, against a real store.
 *
 * <p>These are the operations the read side depends on and that no unit test can stand in for: what
 * happens to a fact when it is closed rather than deleted, what a deleted episode takes with it, and
 * whether clearing one partition reaches into another. Each was wrong in a plausible way before it
 * was checked here.
 *
 * <p>Skips rather than fails when no store is reachable, so the suite stays green on a machine
 * without one. A suite that goes red for an absent dependency trains people to ignore red.
 */
class FlureeProjectionIntegrationTest {

  private static final Instant JAN = Instant.parse("2024-01-01T00:00:00Z");
  private static final Instant JUL = Instant.parse("2024-07-01T00:00:00Z");

  private static FlureeStore store;

  @BeforeAll
  static void connect() {
    store = FlureeStore.localhost();
    assumeTrue(store.isHealthy(), "no store reachable on 127.0.0.1:8090 — skipping");
  }

  private static String freshPartition() {
    return "test-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private static StoredFact fact(String partition, String object, List<String> episodeIds) {
    return new StoredFact(
        UUID.randomUUID().toString(),
        partition,
        "ana",
        object,
        "worksAt",
        "Ana works at " + object,
        JAN,
        null,
        JAN,
        null,
        episodeIds,
        null);
  }

  private static StoredEpisode episode(String partition, String id) {
    return new StoredEpisode(id, partition, "Ana joined " + id, "message", JAN, "test", JAN);
  }

  @Test
  @DisplayName("a fact read back carries every field it was written with")
  void factsRoundTrip() {
    String partition = freshPartition();
    var written = fact(partition, "Acme", List.of("ep1"));
    store.putFacts(List.of(written));

    var read = store.factById(written.factId());
    assertThat(read).isPresent();
    assertThat(read.get().statement()).isEqualTo("Ana works at Acme");
    assertThat(read.get().subjectId()).isEqualTo("ana");
    assertThat(read.get().objectId()).isEqualTo("Acme");
    assertThat(read.get().relation()).isEqualTo("worksAt");
    assertThat(read.get().validFrom()).isEqualTo(JAN);
    assertThat(read.get().validUntil()).as("an open fact has no end").isNull();
    assertThat(read.get().episodeIds()).containsExactly("ep1");
  }

  @Test
  @DisplayName("writing the same fact twice leaves one fact, not one with two statements")
  void repeatedProjectionIsIdempotent() {
    String partition = freshPartition();
    var written = fact(partition, "Acme", List.of("ep1"));
    store.putFacts(List.of(written));
    store.putFacts(List.of(written));

    // Delivery is at-least-once. Without the retraction before each write this store would hold
    // both values for every property, and the fact would read back with two statements.
    var all = store.factsInPartition(partition);
    assertThat(all).hasSize(1);
    assertThat(all.get(0).statement()).isEqualTo("Ana works at Acme");
  }

  @Test
  @DisplayName("closing a fact ends it on both timelines and keeps it readable")
  void closingIsNotDeletion() {
    String partition = freshPartition();
    var written = fact(partition, "Acme", List.of("ep1"));
    store.putFacts(List.of(written));

    Instant supersededAt = Instant.parse("2024-07-02T00:00:00Z");
    store.closeFact(written.factId(), JUL, supersededAt);

    var read = store.factById(written.factId());
    assertThat(read).as("the record survives being closed").isPresent();
    assertThat(read.get().validUntil()).isEqualTo(JUL);
    assertThat(read.get().supersededAt()).isEqualTo(supersededAt);
    assertThat(read.get().statement()).isEqualTo("Ana works at Acme");
  }

  @Test
  @DisplayName("closing twice leaves one end, not two")
  void closingIsIdempotent() {
    String partition = freshPartition();
    var written = fact(partition, "Acme", List.of("ep1"));
    store.putFacts(List.of(written));

    store.closeFact(written.factId(), JUL, JUL);
    store.closeFact(written.factId(), JUL, JUL);

    assertThat(store.factById(written.factId()).orElseThrow().validUntil()).isEqualTo(JUL);
  }

  @Test
  @DisplayName("deleting an episode takes the facts that had no other source, and leaves the rest")
  void episodeDeletionFollowsProvenance() {
    String partition = freshPartition();
    store.putEpisode(episode(partition, "ep-only"));

    var solelyDerived = fact(partition, "Acme", List.of("ep-only"));
    var alsoElsewhere = fact(partition, "Globex", List.of("ep-only", "ep-other"));
    store.putFacts(List.of(solelyDerived, alsoElsewhere));

    store.deleteEpisode("ep-only");

    assertThat(store.factById(solelyDerived.factId()))
        .as("its only source is gone, so it goes too")
        .isEmpty();
    var survivor = store.factById(alsoElsewhere.factId());
    assertThat(survivor).as("another episode still says this").isPresent();
    assertThat(survivor.get().episodeIds())
        .as("the deleted attribution is dropped, the other kept")
        .containsExactly("ep-other");
    assertThat(store.episodesInPartition(partition)).isEmpty();
  }

  @Test
  @DisplayName("clearing one partition leaves the others untouched")
  void partitionsClearIndependently() {
    String kept = freshPartition();
    String cleared = freshPartition();
    store.putEpisode(episode(kept, "ep-kept"));
    store.putEpisode(episode(cleared, "ep-cleared"));
    store.putFacts(List.of(fact(kept, "Acme", List.of("ep-kept"))));
    store.putFacts(List.of(fact(cleared, "Globex", List.of("ep-cleared"))));

    store.clearPartition(cleared);

    assertThat(store.factsInPartition(cleared)).isEmpty();
    assertThat(store.episodesInPartition(cleared)).isEmpty();
    assertThat(store.factsInPartition(kept)).hasSize(1);
    assertThat(store.episodesInPartition(kept)).hasSize(1);
  }

  @Test
  @DisplayName("the projection can enumerate the partitions it has seen")
  void partitionsAreEnumerable() {
    String partition = freshPartition();
    store.putEpisode(episode(partition, "ep-listed"));

    // The write path is keyed by partition and cannot list its own keys, so /clear depends on
    // this being the place that can.
    assertThat(store.partitions()).contains(partition);
    assertThat(store.episodePartition("ep-listed")).contains(partition);
  }

  @Test
  @DisplayName("a partition holds every fact written to it, not just the last one")
  void manyFactsCoexistInOnePartition() {
    String partition = freshPartition();
    store.putFacts(
        List.of(
            fact(partition, "Acme", List.of("ep1")),
            fact(partition, "Globex", List.of("ep1")),
            fact(partition, "Initech", List.of("ep1"))));

    // The projection this replaced was keyed by partition rather than by fact, so a second fact
    // overwrote the first and search could never return more than one result.
    assertThat(store.factsInPartition(partition)).hasSize(3);
  }
}
