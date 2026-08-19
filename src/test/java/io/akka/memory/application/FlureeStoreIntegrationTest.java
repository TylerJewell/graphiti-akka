package io.akka.memory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.akka.memory.domain.Fact;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The store round-trip, including the query that decides whether the whole read side works.
 *
 * <p>Skips rather than fails when no store is reachable, so the suite stays green on a machine
 * without one. That is a deliberate trade: a test that cannot run is less useful than one that can,
 * but a suite that goes red for an absent dependency trains people to ignore red.
 */
class FlureeStoreIntegrationTest {

  private static final Instant JAN = Instant.parse("2024-01-01T00:00:00Z");
  private static final Instant JUL = Instant.parse("2024-07-01T00:00:00Z");
  private static final Instant MARCH = Instant.parse("2024-03-01T00:00:00Z");
  private static final Instant SEPT = Instant.parse("2024-09-01T00:00:00Z");

  private static FlureeStore store;

  @BeforeAll
  static void connect() {
    store = FlureeStore.localhost();
    assumeTrue(store.isHealthy(), "no store reachable on 127.0.0.1:8090 — skipping");
  }

  private static Fact fact(String partition, String object, Instant from, Instant until) {
    return new Fact(
        UUID.randomUUID().toString(),
        partition,
        "ana",
        object,
        "worksAt",
        "Ana works at " + object,
        Optional.of(from),
        Optional.ofNullable(until),
        Instant.now(),
        Optional.empty(),
        List.of("ep1"));
  }

  @Test
  @DisplayName("valid-time queries distinguish what was true then from what is true now")
  void validTimeQueryIsAnsweredByTheStore() {
    // A unique partition per run, so repeated runs do not interfere.
    String partition = "test-" + UUID.randomUUID().toString().substring(0, 8);

    store.insert(
        List.of(
            fact(partition, "Acme", JAN, JUL), // true Jan–Jul, then closed
            fact(partition, "Globex", JUL, null))); // true from Jul, still open

    assertThat(store.validAt(partition, MARCH))
        .as("in March, Ana worked at Acme")
        .containsExactly("Ana works at Acme");

    assertThat(store.validAt(partition, SEPT))
        .as("by September the Acme fact had been closed and Globex was open")
        .containsExactly("Ana works at Globex");
  }

  @Test
  @DisplayName("a closed fact is still stored — closing is not deletion")
  void closedFactsRemainQueryable() {
    String partition = "test-" + UUID.randomUUID().toString().substring(0, 8);
    store.insert(List.of(fact(partition, "Acme", JAN, JUL)));

    // Absent at a time it was not valid...
    assertThat(store.validAt(partition, SEPT)).isEmpty();
    // ...but present at a time it was. The record survived being closed.
    assertThat(store.validAt(partition, MARCH)).containsExactly("Ana works at Acme");
  }

  @Test
  @DisplayName("partitions do not leak into one another")
  void partitionsAreIsolatedInQueries() {
    String a = "test-" + UUID.randomUUID().toString().substring(0, 8);
    String b = "test-" + UUID.randomUUID().toString().substring(0, 8);

    store.insert(List.of(fact(a, "Acme", JAN, null)));
    store.insert(List.of(fact(b, "Globex", JAN, null)));

    assertThat(store.validAt(a, MARCH)).containsExactly("Ana works at Acme");
    assertThat(store.validAt(b, MARCH)).containsExactly("Ana works at Globex");
  }
}
