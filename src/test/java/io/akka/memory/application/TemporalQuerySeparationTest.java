package io.akka.memory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.akka.memory.application.FlureeStore.StoredFact;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The two timelines answer different questions, and this proves they do.
 *
 * <p>The interesting case is a fact learned late: recorded in March, but describing January. A
 * valid-time query at February finds it, because it was true then. A transaction-time query at
 * February does not, because nobody knew it yet. An implementation that answers one axis with the
 * other's data passes every test where the two agree and fails silently everywhere they do not —
 * which is exactly the case anyone asks a bi-temporal system about.
 */
class TemporalQuerySeparationTest {

  private static final Instant JANUARY = Instant.parse("2024-01-01T00:00:00Z");
  private static final Instant FEBRUARY = Instant.parse("2024-02-01T00:00:00Z");
  private static final Instant MARCH = Instant.parse("2024-03-01T00:00:00Z");
  private static final Instant APRIL = Instant.parse("2024-04-01T00:00:00Z");

  private static FlureeStore store;

  @BeforeAll
  static void connect() {
    store = FlureeStore.localhost();
    assumeTrue(store.isHealthy(), "no store reachable on 127.0.0.1:8090 — skipping");
  }

  private static String freshPartition() {
    return "test-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private static StoredFact fact(
      String partition,
      String statement,
      Instant validFrom,
      Instant validUntil,
      Instant recordedAt,
      Instant supersededAt) {
    return new StoredFact(
        UUID.randomUUID().toString(),
        partition,
        "ana",
        "acme",
        "worksAt",
        statement,
        validFrom,
        validUntil,
        recordedAt,
        supersededAt,
        List.of("ep1"),
        null);
  }

  @Test
  @DisplayName("a fact learned late is visible on the valid-time axis before the other")
  void lateKnowledgeSeparatesTheAxes() {
    String partition = freshPartition();
    // True from January. Recorded in March.
    store.putFacts(List.of(fact(partition, "Ana works at Acme", JANUARY, null, MARCH, null)));

    assertThat(store.validAt(partition, FEBRUARY))
        .as("in February it was already true in the world")
        .containsExactly("Ana works at Acme");

    assertThat(store.believedAt(partition, FEBRUARY))
        .as("but in February the system had not been told")
        .isEmpty();

    assertThat(store.believedAt(partition, APRIL))
        .as("by April it had")
        .containsExactly("Ana works at Acme");
  }

  @Test
  @DisplayName("a superseded fact leaves the belief timeline without leaving the world timeline")
  void supersedingSeparatesTheAxes() {
    String partition = freshPartition();
    // Believed from January, superseded in March. Its validity was never bounded.
    store.putFacts(List.of(fact(partition, "Ana works at Acme", JANUARY, null, JANUARY, MARCH)));

    assertThat(store.believedAt(partition, FEBRUARY))
        .as("the system believed it in February")
        .containsExactly("Ana works at Acme");

    assertThat(store.believedAt(partition, APRIL))
        .as("and stopped believing it in March")
        .isEmpty();

    assertThat(store.validAt(partition, APRIL))
        .as("nothing was ever said about when it stopped being true, so on that axis it is open")
        .containsExactly("Ana works at Acme");
  }

  @Test
  @DisplayName("the axes agree when nothing distinguishes them")
  void theAxesAgreeInTheOrdinaryCase() {
    String partition = freshPartition();
    store.putFacts(List.of(fact(partition, "Ana works at Acme", JANUARY, null, JANUARY, null)));

    assertThat(store.validAt(partition, MARCH)).containsExactly("Ana works at Acme");
    assertThat(store.believedAt(partition, MARCH)).containsExactly("Ana works at Acme");
  }

  /**
   * The reason both timelines are explicit fields rather than reads off the ledger.
   *
   * <p>The store accepts a query-at-an-earlier-commit parameter. It ignores it: asking at commit 1
   * for a record written far later still returns the record, with a 200 and nothing to say the
   * request was dropped. A design that leaned on it would answer every historical question with
   * today's data and look correct doing it.
   *
   * <p>If a later store version starts honouring the parameter this test fails, which is the
   * intent — that is the signal that the explicit transaction-time fields could be reconsidered.
   */
  @Test
  @DisplayName("the store ignores query-at-an-earlier-commit instead of refusing it")
  void commitHistoryCannotAnswerHistoricalQuestions() throws Exception {
    String partition = freshPartition();
    var written = fact(partition, "Ana works at Acme", JANUARY, null, JANUARY, null);
    store.putFacts(List.of(written));

    String body =
        """
        {"@context":{"ex":"http://example.org/"},"from":"memory",\
        "select":{"ex:%s":["*"]},"t":{"at":1}}"""
            .formatted(written.factId());

    var request =
        java.net.http.HttpRequest.newBuilder(
                java.net.URI.create("http://127.0.0.1:8090/v1/fluree/query"))
            .header("Content-Type", "application/json")
            .timeout(java.time.Duration.ofSeconds(20))
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
            .build();
    var response =
        java.net.http.HttpClient.newHttpClient()
            .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).as("no error is raised").isEqualTo(200);
    assertThat(response.body())
        .as("and the answer is today's, not commit 1's — the parameter was dropped silently")
        .contains("Ana works at Acme");
  }
}
