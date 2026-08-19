package io.akka.memory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.JsonSupport;
import io.akka.memory.TestBase;
import io.akka.memory.application.AttributeHydrationAgent;
import io.akka.memory.application.EntityExtractionAgent;
import io.akka.memory.application.EntityResolutionAgent;
import io.akka.memory.application.FactExtractionAgent;
import io.akka.memory.application.FlureeStore;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ingest end to end: request in, projection out.
 *
 * <p>The model is stubbed, so what is under test is everything around it — the handoff, the
 * cascade, the invalidation decision and the projection. Those are the parts a port gets wrong, and
 * none of them is visible from a unit test of any single component.
 *
 * <p>Each test uses a fresh partition, so they neither see nor disturb one another.
 */
class IngestIntegrationTest extends TestBase {

  private static final String JANUARY = "2024-01-01T00:00:00Z";
  private static final String JULY = "2024-07-01T00:00:00Z";

  private FlureeStore store;
  private String partition;

  @BeforeEach
  void setUp() {
    store = FlureeStore.localhost();
    assumeTrue(store.isHealthy(), "no store reachable on 127.0.0.1:8090 — skipping");
    partition = "test-" + UUID.randomUUID().toString().substring(0, 8);

    // Nothing to hydrate about, and nothing to escalate to — each test overrides what it needs.
    attributeHydration.fixedResponse(
        JsonSupport.encodeToString(new AttributeHydrationAgent.Summary("")));
    entityResolution.fixedResponse(
        JsonSupport.encodeToString(new EntityResolutionAgent.Resolutions(List.of())));
  }

  // --- helpers ---------------------------------------------------------------------------

  private void extractsEntities(String... names) {
    var entities =
        java.util.Arrays.stream(names)
            .map(name -> new EntityExtractionAgent.ExtractedEntity(name, 0, List.of(0)))
            .toList();
    entityExtraction.fixedResponse(
        JsonSupport.encodeToString(new EntityExtractionAgent.ExtractedEntities(entities)));
  }

  private void extractsFact(
      String whenContentContains,
      String source,
      String target,
      String relation,
      String statement,
      String validAt,
      String invalidAt) {
    factExtraction
        .whenMessage(message -> message.contains(whenContentContains))
        .reply(
            JsonSupport.encodeToString(
                new FactExtractionAgent.ExtractedFacts(
                    List.of(
                        new FactExtractionAgent.ExtractedFact(
                            source, target, relation, statement, validAt, invalidAt)))));
  }

  /**
   * Ingests one message.
   *
   * <p>The episode identifier is scoped to the partition, because it becomes the orchestration's
   * identifier too and an orchestration that has already run cannot be started again — reusing a
   * name across tests fails the request rather than the assertion, which is a confusing way to
   * learn it.
   */
  private void ingest(String episodeId, String content) {
    var scopedId = partition + "-" + episodeId;
    var message =
        new MemoryEndpoint.Message(content, scopedId, scopedId, "user", "ana", JANUARY, "test");
    var response =
        httpClient
            .POST("/messages")
            .withRequestBody(new MemoryEndpoint.AddMessagesRequest(partition, List.of(message)))
            .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.ACCEPTED);
  }

  private List<FlureeStore.StoredFact> awaitFacts(int count) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(() -> assertThat(store.factsInPartition(partition)).hasSize(count));
    return store.factsInPartition(partition);
  }

  private FlureeStore.StoredFact factSaying(List<FlureeStore.StoredFact> facts, String statement) {
    return facts.stream()
        .filter(fact -> statement.equals(fact.statement()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no fact saying: " + statement));
  }

  // --- the contract ----------------------------------------------------------------------

  @Test
  @DisplayName("ingest is acknowledged before the pipeline runs, and a bad timestamp is not")
  void handoffIsImmediateAndValidationIsOneFieldWide() {
    extractsEntities("Ana");
    var good =
        new MemoryEndpoint.Message(
            "Ana works at Acme", partition + "-ok", "ok", "user", "ana", JANUARY, "");
    var accepted =
        httpClient
            .POST("/messages")
            .withRequestBody(new MemoryEndpoint.AddMessagesRequest(partition, List.of(good)))
            .invoke();
    assertThat(accepted.status())
        .as("the work is queued, not done — 202, not 200")
        .isEqualTo(StatusCodes.ACCEPTED);

    var bad =
        new MemoryEndpoint.Message(
            "Ana works at Acme", partition + "-bad", "bad", "user", "ana", "the fourth of July", "");
    var rejected =
        httpClient
            .POST("/messages")
            .withRequestBody(new MemoryEndpoint.AddMessagesRequest(partition, List.of(bad)))
            .invoke();

    // Exactly one field is checked before the acknowledgement. Everything else fails later, in
    // the background, with no channel back to the caller.
    assertThat(rejected.status()).isEqualTo(StatusCodes.BAD_REQUEST);
  }

  @Test
  @DisplayName("a later contradicting fact closes the earlier one, and both stay readable")
  void contradictionClosesRatherThanDeletes() {
    extractsEntities("Ana", "Acme", "Globex");
    extractsFact("joined Acme", "Ana", "Acme", "worksAt", "Ana works at Acme", JANUARY, null);
    ingest("ep-1", "Ana joined Acme");
    awaitFacts(1);

    extractsFact("moved to Globex", "Ana", "Globex", "worksAt", "Ana works at Globex", JULY, null);
    ingest("ep-2", "Ana moved to Globex");
    var facts = awaitFacts(2);

    var earlier = factSaying(facts, "Ana works at Acme");
    var later = factSaying(facts, "Ana works at Globex");

    assertThat(earlier.validUntil())
        .as("the earlier fact ends where the later one begins")
        .isEqualTo(java.time.Instant.parse(JULY));
    assertThat(earlier.supersededAt()).as("and the system records when it learned that").isNotNull();
    assertThat(later.validUntil()).as("the later fact is still open").isNull();
    assertThat(store.factById(earlier.factId()))
        .as("closing is not deletion — the earlier fact is still there")
        .isPresent();
  }

  @Test
  @DisplayName("a fact with no start stays open and closes nothing")
  void anUndatedFactIsInert() {
    extractsEntities("Ana", "Acme", "Globex");
    extractsFact("joined Acme", "Ana", "Acme", "worksAt", "Ana works at Acme", JANUARY, null);
    ingest("ep-1", "Ana joined Acme");
    awaitFacts(1);

    // The model could not determine when this became true, so it says nothing about the interval.
    extractsFact("somewhere else", "Ana", "Globex", "worksAt", "Ana works at Globex", null, null);
    ingest("ep-2", "Ana works somewhere else now");
    var facts = awaitFacts(2);

    assertThat(factSaying(facts, "Ana works at Acme").validUntil())
        .as("an undated fact cannot close anything — there is no instant to close it at")
        .isNull();
    assertThat(factSaying(facts, "Ana works at Globex").validFrom()).isNull();
  }

  @Test
  @DisplayName("two facts starting at the same instant both stay open")
  void anExactTieClosesNothing() {
    extractsEntities("Ana", "Acme", "Globex");
    extractsFact("joined Acme", "Ana", "Acme", "worksAt", "Ana works at Acme", JANUARY, null);
    ingest("ep-1", "Ana joined Acme");
    awaitFacts(1);

    extractsFact("joined Globex", "Ana", "Globex", "worksAt", "Ana works at Globex", JANUARY, null);
    ingest("ep-2", "Ana joined Globex");
    var facts = awaitFacts(2);

    // Strictly later, not later-or-equal. A tie is not a contradiction, and an implementation
    // using >= would close the first fact at the instant it began.
    assertThat(factSaying(facts, "Ana works at Acme").validUntil()).isNull();
    assertThat(factSaying(facts, "Ana works at Globex").validUntil()).isNull();
  }

  @Test
  @DisplayName("a second mention of a known entity attaches to it rather than creating another")
  void knownEntitiesAreRecognised() {
    extractsEntities("Ana Kowalski", "Acme Corporation");
    extractsFact(
        "joined Acme",
        "Ana Kowalski",
        "Acme Corporation",
        "worksAt",
        "Ana works at Acme",
        JANUARY,
        null);
    ingest("ep-1", "Ana Kowalski joined Acme Corporation");
    awaitFacts(1);

    var afterFirst = store.entitiesInPartition(partition);
    assertThat(afterFirst).hasSize(2);
    String anaId =
        afterFirst.stream()
            .filter(entity -> "Ana Kowalski".equals(entity.name()))
            .findFirst()
            .orElseThrow()
            .entityId();

    extractsFact(
        "was promoted",
        "Ana Kowalski",
        "Acme Corporation",
        "leads",
        "Ana leads Acme",
        JULY,
        null);
    ingest("ep-2", "Ana Kowalski was promoted at Acme Corporation");
    var facts = awaitFacts(2);

    assertThat(store.entitiesInPartition(partition))
        .as("the same two people and companies, not four")
        .hasSize(2);
    assertThat(factSaying(facts, "Ana leads Acme").subjectId())
        .as("and the new fact attaches to the entity that was already there")
        .isEqualTo(anaId);
  }
}
