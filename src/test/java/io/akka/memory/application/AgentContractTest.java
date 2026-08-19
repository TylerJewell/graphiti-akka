package io.akka.memory.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.JsonSupport;
import io.akka.memory.TestBase;
import io.akka.memory.domain.Episode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Each agent's contract with the model: what it accepts back, and what it does when it gets
 * something it cannot use.
 *
 * <p>The failure cases are the point. Every one of these agents declares a fallback, and a fallback
 * that has never been exercised is a guess. A model returning prose instead of the requested shape
 * is the ordinary case, not the exotic one, and each agent has a different right answer for it —
 * extraction yields nothing, hydration keeps what it already had.
 */
class AgentContractTest extends TestBase {

  private static String session() {
    return "test-" + java.util.UUID.randomUUID();
  }

  @Test
  @DisplayName("entity extraction returns what the model named")
  void entityExtractionParsesAWellFormedResponse() {
    entityExtraction.fixedResponse(
        JsonSupport.encodeToString(
            new EntityExtractionAgent.ExtractedEntities(
                List.of(
                    new EntityExtractionAgent.ExtractedEntity("Ana", 0, List.of(0)),
                    new EntityExtractionAgent.ExtractedEntity("Acme", 0, List.of(0))))));

    var result =
        componentClient
            .forAgent()
            .inSession(session())
            .method(EntityExtractionAgent::extract)
            .invoke(
                new EntityExtractionAgent.Request(
                    "Ana works at Acme", Episode.Kind.MESSAGE, List.of("Entity")));

    assertThat(result.extractedEntities())
        .extracting(EntityExtractionAgent.ExtractedEntity::name)
        .containsExactly("Ana", "Acme");
  }

  @Test
  @DisplayName("entity extraction yields nothing rather than failing the episode")
  void entityExtractionDegradesToEmpty() {
    entityExtraction.fixedResponse("I'm afraid I can't help with that.");

    var result =
        componentClient
            .forAgent()
            .inSession(session())
            .method(EntityExtractionAgent::extract)
            .invoke(
                new EntityExtractionAgent.Request(
                    "Ana works at Acme", Episode.Kind.MESSAGE, List.of("Entity")));

    assertThat(result.extractedEntities()).isEmpty();
  }

  @Test
  @DisplayName("fact extraction returns the interval the model inferred, as text")
  void factExtractionParsesAWellFormedResponse() {
    factExtraction.fixedResponse(
        JsonSupport.encodeToString(
            new FactExtractionAgent.ExtractedFacts(
                List.of(
                    new FactExtractionAgent.ExtractedFact(
                        "Ana",
                        "Acme",
                        "worksAt",
                        "Ana works at Acme",
                        "2024-01-01T00:00:00Z",
                        null)))));

    var result =
        componentClient
            .forAgent()
            .inSession(session())
            .method(FactExtractionAgent::extract)
            .invoke(
                new FactExtractionAgent.Request(
                    "Ana works at Acme", List.of("Ana", "Acme"), "2024-06-01T00:00:00Z"));

    assertThat(result.edges()).hasSize(1);
    assertThat(result.edges().get(0).fact()).isEqualTo("Ana works at Acme");
    assertThat(result.edges().get(0).validAt()).isEqualTo("2024-01-01T00:00:00Z");
    assertThat(result.edges().get(0).invalidAt())
        .as("an open interval is absent, not a sentinel")
        .isNull();
  }

  @Test
  @DisplayName("fact extraction yields nothing rather than failing the episode")
  void factExtractionDegradesToEmpty() {
    factExtraction.fixedResponse("{ this is not the shape you asked for");

    var result =
        componentClient
            .forAgent()
            .inSession(session())
            .method(FactExtractionAgent::extract)
            .invoke(new FactExtractionAgent.Request("Ana works at Acme", List.of("Ana"), "now"));

    assertThat(result.edges()).isEmpty();
  }

  @Test
  @DisplayName("hydration returns the summary the model wrote")
  void hydrationParsesAWellFormedResponse() {
    attributeHydration.fixedResponse(
        JsonSupport.encodeToString(new AttributeHydrationAgent.Summary("Ana works at Acme.")));

    var result =
        componentClient
            .forAgent()
            .inSession(session())
            .method(AttributeHydrationAgent::hydrate)
            .invoke(
                new AttributeHydrationAgent.Request("Ana", "", List.of("Ana works at Acme")));

    assertThat(result.summary()).isEqualTo("Ana works at Acme.");
  }

  @Test
  @DisplayName("hydration keeps the existing summary rather than blanking it")
  void hydrationDegradesToWhatItAlreadyHad() {
    attributeHydration.fixedResponse("sorry, no");

    var result =
        componentClient
            .forAgent()
            .inSession(session())
            .method(AttributeHydrationAgent::hydrate)
            .invoke(
                new AttributeHydrationAgent.Request(
                    "Ana", "Ana is a person we already knew about.", List.of("something new")));

    // Degrading to empty here would silently erase a summary built over many episodes. The
    // fallback is the previous value precisely because the failure is transient and the loss
    // would not be.
    assertThat(result.summary()).isEqualTo("Ana is a person we already knew about.");
  }
}
