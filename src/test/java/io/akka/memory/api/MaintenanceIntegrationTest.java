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
 * Deletion and clearing, driven through the surface a caller actually uses.
 *
 * <p>The interesting property is what a delete leaves behind. Removing an episode is not the same
 * as removing what it taught, and clearing one partition must not reach into another — both are
 * easy to get right in the projection and wrong through the endpoint, because the endpoint has to
 * find the right partition first.
 */
class MaintenanceIntegrationTest extends TestBase {

  private static final String JANUARY = "2024-01-01T00:00:00Z";

  private FlureeStore store;
  private String partition;

  @BeforeEach
  void setUp() {
    store = FlureeStore.localhost();
    assumeTrue(store.isHealthy(), "no store reachable on 127.0.0.1:8090 — skipping");
    partition = "test-" + UUID.randomUUID().toString().substring(0, 8);

    entityExtraction.fixedResponse(
        JsonSupport.encodeToString(
            new EntityExtractionAgent.ExtractedEntities(
                List.of(
                    new EntityExtractionAgent.ExtractedEntity("Ana", 0, List.of(0)),
                    new EntityExtractionAgent.ExtractedEntity("Acme", 0, List.of(0))))));
    factExtraction.fixedResponse(
        JsonSupport.encodeToString(
            new FactExtractionAgent.ExtractedFacts(
                List.of(
                    new FactExtractionAgent.ExtractedFact(
                        "Ana", "Acme", "worksAt", "Ana works at Acme", JANUARY, null)))));
    attributeHydration.fixedResponse(
        JsonSupport.encodeToString(new AttributeHydrationAgent.Summary("")));
    entityResolution.fixedResponse(
        JsonSupport.encodeToString(new EntityResolutionAgent.Resolutions(List.of())));
  }

  private String ingestInto(String targetPartition, String suffix) {
    String episodeId = targetPartition + "-" + suffix;
    var message =
        new MemoryEndpoint.Message(
            "Ana joined Acme", episodeId, episodeId, "user", "ana", JANUARY, "test");
    var response =
        httpClient
            .POST("/messages")
            .withRequestBody(
                new MemoryEndpoint.AddMessagesRequest(targetPartition, List.of(message)))
            .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.ACCEPTED);

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(
            () -> {
              assertThat(store.factsInPartition(targetPartition)).hasSize(1);
              assertThat(store.episodesInPartition(targetPartition)).hasSize(1);
            });
    return episodeId;
  }

  private void awaitGone(String targetPartition) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(
            () -> {
              assertThat(store.factsInPartition(targetPartition)).isEmpty();
              assertThat(store.episodesInPartition(targetPartition)).isEmpty();
            });
  }

  @Test
  @DisplayName("a deleted episode disappears, and takes the facts it alone taught")
  void deletingAnEpisodeTakesWhatOnlyItSaid() {
    String episodeId = ingestInto(partition, "ep");

    var response = httpClient.DELETE("/episode/" + episodeId).invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);

    awaitGone(partition);
  }

  @Test
  @DisplayName("clearing one partition leaves the others untouched")
  void clearingOnePartitionIsNotClearingAll() {
    String other = "test-" + UUID.randomUUID().toString().substring(0, 8);
    ingestInto(partition, "ep");
    ingestInto(other, "ep");

    var response = httpClient.DELETE("/group/" + partition).invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);

    awaitGone(partition);
    assertThat(store.factsInPartition(other))
        .as("a neighbouring partition is none of this operation's business")
        .hasSize(1);
    assertThat(store.episodesInPartition(other)).hasSize(1);
  }

  @Test
  @DisplayName("a deleted fact is gone, while the episode that taught it remains")
  void deletingAFactIsNotDeletingItsSource() {
    ingestInto(partition, "ep");
    String factId = store.factsInPartition(partition).get(0).factId();

    var response = httpClient.DELETE("/entity-edge/" + factId).invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(() -> assertThat(store.factsInPartition(partition)).isEmpty());

    assertThat(store.episodesInPartition(partition))
        .as("deleting what was concluded does not delete what it was concluded from")
        .hasSize(1);
  }

  @Test
  @DisplayName("episode listing returns the partition's episodes, capped at what was asked for")
  void episodeListingRespectsItsCap() {
    ingestInto(partition, "ep");

    var listed =
        httpClient
            .GET("/episodes/" + partition + "?last_n=5")
            .responseBodyAsListOf(MemoryEndpoint.EpisodeResult.class)
            .invoke()
            .body();
    assertThat(listed).hasSize(1);
    assertThat(listed.get(0).group_id()).isEqualTo(partition);

    var none =
        httpClient
            .GET("/episodes/" + partition + "?last_n=0")
            .responseBodyAsListOf(MemoryEndpoint.EpisodeResult.class)
            .invoke()
            .body();
    assertThat(none).as("a cap of zero is a cap, not an absent one").isEmpty();
  }
}
