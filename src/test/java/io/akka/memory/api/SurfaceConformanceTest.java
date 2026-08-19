package io.akka.memory.api;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.mcp.McpTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves the surfaces match the source system.
 *
 * <p>The expected surface is <b>generated</b> from the source by an AST probe, never transcribed —
 * a hand-written version of this map was wrong about two of three capabilities, and a probe that
 * asserts a hand-written answer has automated the assertion rather than the verification.
 *
 * <p>The comparison is against what this service actually declares, read back off its own
 * annotations. So a route renamed in the endpoint fails here even if the code still compiles.
 */
class SurfaceConformanceTest {

  private static JsonNode contract() throws Exception {
    try (InputStream in =
        SurfaceConformanceTest.class.getResourceAsStream("/surface-inventory.json")) {
      assertThat(in).as("the generated contract must be on the test classpath").isNotNull();
      return new ObjectMapper().readTree(in);
    }
  }

  /** What this service declares, read off its own annotations. */
  private static Set<String> declaredRoutes() {
    var out = new TreeSet<String>();
    for (Method m : MemoryEndpoint.class.getDeclaredMethods()) {
      if (m.isAnnotationPresent(Post.class)) {
        out.add("POST " + m.getAnnotation(Post.class).value());
      } else if (m.isAnnotationPresent(Get.class)) {
        out.add("GET " + m.getAnnotation(Get.class).value());
      } else if (m.isAnnotationPresent(Delete.class)) {
        out.add("DELETE " + m.getAnnotation(Delete.class).value());
      }
    }
    return out;
  }

  private static Set<String> declaredTools() {
    var out = new TreeSet<String>();
    for (Method m : MemoryMcpEndpoint.class.getDeclaredMethods()) {
      if (m.isAnnotationPresent(McpTool.class)) {
        out.add(m.getAnnotation(McpTool.class).name());
      }
    }
    return out;
  }

  @Test
  @DisplayName("every route in the source contract is implemented, with the same method and path")
  void routesMatchTheContract() throws Exception {
    var expected = new TreeSet<String>();
    contract().get("routes").forEach(r -> expected.add(r.get("method").asText() + " " + r.get("path").asText()));

    assertThat(expected).as("the contract itself must be non-trivial").hasSize(11);
    assertThat(declaredRoutes())
        .as("declared routes must match the source system exactly — no additions, no omissions")
        .containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  @DisplayName("every agent tool in the source contract is implemented, by name")
  void toolsMatchTheContract() throws Exception {
    var expected = new TreeSet<String>();
    contract().get("mcp_tools").forEach(t -> expected.add(t.get("name").asText()));

    assertThat(expected).hasSize(13);
    assertThat(declaredTools())
        .as("an agent discovering these tools must find exactly what it found upstream")
        .containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  @DisplayName("the non-obvious status codes are preserved")
  void nonObviousStatusCodesArePreserved() throws Exception {
    var codes = new java.util.HashMap<String, String>();
    contract()
        .get("routes")
        .forEach(r -> codes.put(r.get("method").asText() + " " + r.get("path").asText(),
            r.get("status").asText()));

    // These two are the ones a port gets wrong by defaulting to 200.
    assertThat(codes.get("POST /messages")).contains("202");
    assertThat(codes.get("POST /entity-node")).contains("201");
  }

  @Test
  @DisplayName("wire field names are frozen — they are contract, not naming")
  void wireFieldNamesAreFrozen() {
    var searchQueryFields = new LinkedHashSet<String>();
    for (var c : MemoryEndpoint.SearchQuery.class.getRecordComponents()) {
      searchQueryFields.add(c.getName());
    }
    assertThat(searchQueryFields).containsExactly("group_ids", "query", "max_facts");

    var messageFields = new LinkedHashSet<String>();
    for (var c : MemoryEndpoint.Message.class.getRecordComponents()) {
      messageFields.add(c.getName());
    }
    assertThat(messageFields)
        .containsExactly(
            "content", "uuid", "name", "role_type", "role", "timestamp", "source_description");
  }

  @Test
  @DisplayName("the one library-only capability is deliberately absent")
  void bulkIngestIsNotExposed() throws Exception {
    var libraryOnly = new TreeSet<String>();
    contract().get("library_only").forEach(n -> libraryOnly.add(n.asText()));

    assertThat(libraryOnly).containsExactly("add_episode_bulk");
    // It has no external surface upstream, so adding one here would be a divergence.
    assertThat(declaredTools()).doesNotContain("add_episode_bulk");
    assertThat(declaredRoutes()).noneMatch(r -> r.contains("bulk"));
  }
}
