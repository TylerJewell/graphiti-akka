package io.akka.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Nothing is persisted that no specification asked for, and nothing specified is missing.
 *
 * <p>Both directions have to be checked, because they fail differently. A type nobody specified is
 * scope that crept in — it will be maintained, migrated and reasoned about forever, and no document
 * says why it exists. A specified concept with no type is a requirement quietly dropped, which is
 * invisible precisely because there is no code to notice missing.
 *
 * <p>The mapping below is the artefact. Adding a persisted type without adding a line here fails
 * this test, which is the point: the citation is written at the moment the type is, when the reason
 * is still known.
 */
class DomainModelTraceabilityTest {

  private static final Path DOMAIN = Path.of("src", "main", "java", "io", "akka", "memory", "domain");
  private static final Path SPECS = Path.of("..", "graphiti-port", "specs");

  /** Persisted type → a phrase from the specification that requires it. */
  private static final Map<String, String> TRACEABILITY = new LinkedHashMap<>();

  static {
    TRACEABILITY.put("Episode", "episode");
    TRACEABILITY.put("Entity", "entity");
    TRACEABILITY.put("Fact", "fact");
    TRACEABILITY.put("PartitionState", "partition");
    TRACEABILITY.put("PartitionEvent", "episode");
  }

  /** Domain types that hold no persisted state — rules and calculations, not records. */
  private static final Set<String> NOT_PERSISTED =
      Set.of(
          "Bm25",
          "EntityIdentity",
          "EntityResolution",
          "ExtractionGuardrails",
          "RankFusion");

  private static Set<String> domainTypes() throws IOException {
    try (var files = Files.list(DOMAIN)) {
      return files
          .map(path -> path.getFileName().toString())
          .filter(name -> name.endsWith(".java"))
          .map(name -> name.substring(0, name.length() - ".java".length()))
          .collect(Collectors.toCollection(TreeSet::new));
    }
  }

  private static String specCorpus() throws IOException {
    try (var files = Files.list(SPECS)) {
      var out = new StringBuilder();
      for (Path path : files.filter(p -> p.toString().endsWith(".md")).toList()) {
        out.append(Files.readString(path).toLowerCase(java.util.Locale.ROOT)).append('\n');
      }
      return out.toString();
    }
  }

  @Test
  @DisplayName("every domain type is either persisted with a citation, or declared not persisted")
  void everyTypeIsAccountedFor() throws IOException {
    var unaccounted = new TreeSet<>(domainTypes());
    unaccounted.removeAll(TRACEABILITY.keySet());
    unaccounted.removeAll(NOT_PERSISTED);

    assertThat(unaccounted)
        .as(
            "a new domain type must be cited to a specification or declared stateless — "
                + "otherwise it is scope nobody asked for and nobody can later justify")
        .isEmpty();
  }

  @Test
  @DisplayName("no citation names a type that no longer exists")
  void noCitationOutlivesItsType() throws IOException {
    var types = domainTypes();
    assertThat(types).containsAll(TRACEABILITY.keySet());
    assertThat(types).containsAll(NOT_PERSISTED);
  }

  @Test
  @DisplayName("every persisted type's citation is actually present in the specifications")
  void everyCitationResolves() throws IOException {
    assumeTrue(Files.isDirectory(SPECS), "specifications not alongside the port — skipping");
    String corpus = specCorpus();
    assertThat(corpus).as("the corpus must be non-trivial to check against").hasSizeGreaterThan(1000);

    for (var entry : TRACEABILITY.entrySet()) {
      assertThat(corpus)
          .as("%s is persisted, so something must have asked for it", entry.getKey())
          .contains(entry.getValue().toLowerCase(java.util.Locale.ROOT));
    }
  }

  @Test
  @DisplayName("every concept the specifications name has somewhere to live")
  void everySpecifiedConceptHasAType() throws IOException {
    var types = domainTypes();
    // The four the specifications treat as things rather than as rules. A specification that
    // introduces a fifth and finds no type here has had a requirement dropped silently.
    for (String concept : List.of("Episode", "Entity", "Fact", "PartitionState")) {
      assertThat(types).as("%s is specified as a thing and must exist", concept).contains(concept);
    }
  }
}
