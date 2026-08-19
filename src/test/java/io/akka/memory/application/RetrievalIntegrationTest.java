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
 * Retrieval against a populated projection.
 *
 * <p>Runs with whatever candidate lists are available. With no model account configured there is no
 * vector list, so these assert the parts that do not depend on one — that results come back, that
 * they are ordered, that the cap is the cap, that an empty result is empty rather than an error, and
 * that naming a centre entity changes the order rather than the contents. Each result carries
 * whether the vector list took part, so a thinner ranking is reported rather than hidden.
 */
class RetrievalIntegrationTest {

  private static final Instant JAN = Instant.parse("2024-01-01T00:00:00Z");

  private static FlureeStore store;
  private static RetrievalService retrieval;

  @BeforeAll
  static void connect() {
    store = FlureeStore.localhost();
    assumeTrue(store.isHealthy(), "no store reachable on 127.0.0.1:8090 — skipping");
    retrieval = new RetrievalService(store, Embedder.fromEnvironment());
  }

  private static String freshPartition() {
    return "test-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private static StoredFact fact(String partition, String subject, String object, String statement) {
    return new StoredFact(
        UUID.randomUUID().toString(),
        partition,
        subject,
        object,
        "relatesTo",
        statement,
        JAN,
        null,
        JAN,
        null,
        List.of("ep1"),
        null);
  }

  @Test
  @DisplayName("a question against a populated memory returns the facts that mention it")
  void relevantFactsComeBack() {
    String partition = freshPartition();
    store.putFacts(
        List.of(
            fact(partition, "ana", "acme", "Ana works at Acme Corporation"),
            fact(partition, "bob", "globex", "Bob works at Globex"),
            fact(partition, "ana", "sailing", "Ana enjoys sailing at the weekend")));

    var results = retrieval.search(partition, "Acme", 10, null);

    assertThat(results.facts()).hasSize(1);
    assertThat(results.facts().get(0).statement()).isEqualTo("Ana works at Acme Corporation");
  }

  @Test
  @DisplayName("results are capped at the requested count")
  void theCapIsTheCap() {
    String partition = freshPartition();
    for (int i = 0; i < 8; i++) {
      store.putFacts(List.of(fact(partition, "ana", "co" + i, "Ana works at company number " + i)));
    }

    assertThat(retrieval.search(partition, "works", 3, null).facts()).hasSize(3);
    assertThat(retrieval.search(partition, "works", 8, null).facts()).hasSize(8);
  }

  @Test
  @DisplayName("results are ordered — a closer lexical match outranks a weaker one")
  void resultsAreOrdered() {
    String partition = freshPartition();
    store.putFacts(
        List.of(
            fact(partition, "ana", "x", "Ana mentions sailing once"),
            fact(partition, "bob", "y", "Sailing sailing sailing is what Bob does")));

    var ordered = retrieval.search(partition, "sailing", 10, null).facts();

    assertThat(ordered).hasSize(2);
    assertThat(ordered.get(0).statement()).startsWith("Sailing sailing sailing");
  }

  @Test
  @DisplayName("a question with no matches returns empty rather than erroring")
  void emptyIsEmptyNotAnError() {
    String partition = freshPartition();
    store.putFacts(List.of(fact(partition, "ana", "acme", "Ana works at Acme")));

    assertThat(retrieval.search(partition, "photosynthesis", 10, null).facts()).isEmpty();
    assertThat(retrieval.search(freshPartition(), "anything", 10, null).facts())
        .as("a partition that has never been written to is empty, not missing")
        .isEmpty();
  }

  @Test
  @DisplayName("naming a centre entity reorders by closeness to it, and keeps the same facts")
  void centringChangesTheOrderNotTheContents() {
    String partition = freshPartition();
    // ana—acme is a direct relation, so acme is a neighbour of ana. zeta is not connected.
    store.putFacts(
        List.of(
            fact(partition, "zeta", "far", "Zeta reports the quarterly numbers"),
            fact(partition, "acme", "near", "Acme reports the quarterly numbers"),
            fact(partition, "ana", "acme", "Ana reports to Acme")));

    var uncentred = retrieval.search(partition, "reports", 10, null).facts();
    var centred = retrieval.search(partition, "reports", 10, "ana").facts();

    assertThat(centred)
        .as("centring is a reranking, not a filter")
        .hasSameSizeAs(uncentred)
        .extracting(StoredFact::factId)
        .containsExactlyInAnyOrderElementsOf(uncentred.stream().map(StoredFact::factId).toList());

    assertThat(centred.get(0).subjectId())
        .as("the centre's own facts come first")
        .isEqualTo("ana");
    assertThat(centred.get(1).subjectId())
        .as("then what it is directly related to, before anything unconnected")
        .isEqualTo("acme");
  }

  @Test
  @DisplayName("a result reports whether the vector list took part in the ranking")
  void thinnerRankingsAreReportedNotHidden() {
    String partition = freshPartition();
    store.putFacts(List.of(fact(partition, "ana", "acme", "Ana works at Acme")));

    var results = retrieval.search(partition, "Acme", 10, null);

    // Without a model account there is no similarity list, and the fused order comes from the
    // lexical list alone. That is a weaker answer, and it says so rather than looking identical.
    assertThat(results.vectorListIncluded())
        .isEqualTo(Embedder.fromEnvironment().isConfigured());
  }
}
