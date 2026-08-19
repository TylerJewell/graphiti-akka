package io.akka.memory.api;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.akka.memory.TestBase;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Replays a session recorded against the source system and compares responses field by field.
 *
 * <p>This is the strongest equivalence evidence available, because the expected values are not
 * anyone's reading of the source — they are what it actually sent. Every other test here encodes a
 * belief about the source's behaviour; this one encodes the behaviour.
 *
 * <p><b>It needs a recording, and there is not one yet.</b> Producing it means running the source
 * system, which needs a graph database and a model account, and recording its responses with
 * {@code scripts/record-session.sh}. Until then this skips, loudly, rather than passing on no
 * evidence — a green test with no fixture would be worse than an absent one, because the suite
 * would report parity that nothing checked.
 *
 * <p>Fields that cannot match across systems — generated identifiers, ingestion timestamps — are
 * excluded by name. Everything else must be equal, including field order-insensitive nulls: a
 * field the source sent as {@code null} and the port omits is a difference a caller can see.
 */
class RecordedSessionReplayTest extends TestBase {

  private static final String FIXTURE = "recorded-session.json";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** Values that cannot be equal across two systems and must not be compared. */
  private static final List<String> GENERATED =
      List.of("uuid", "created_at", "source_node_uuid", "target_node_uuid");

  private static JsonNode fixture() throws Exception {
    try (InputStream in =
        RecordedSessionReplayTest.class.getClassLoader().getResourceAsStream(FIXTURE)) {
      return in == null ? null : MAPPER.readTree(in);
    }
  }

  @Test
  @DisplayName("every recorded exchange replays with the same response")
  void recordedResponsesMatch() throws Exception {
    JsonNode session = fixture();
    assumeTrue(
        session != null,
        "no recorded session on the classpath ("
            + FIXTURE
            + "). Record one with scripts/record-session.sh against a running source system —"
            + " it needs a graph database and a model account. Skipping rather than passing:"
            + " parity nothing checked is worse than parity not claimed.");

    var differences = new ArrayList<String>();
    for (JsonNode exchange : session.path("exchanges")) {
      String method = exchange.path("method").asText();
      String path = exchange.path("path").asText();
      JsonNode expected = exchange.path("response");

      var request =
          switch (method) {
            case "GET" -> httpClient.GET(path);
            case "DELETE" -> httpClient.DELETE(path);
            case "POST" -> httpClient.POST(path).withRequestBody(exchange.path("body"));
            default -> throw new IllegalStateException("unrecorded method: " + method);
          };

      var response = request.parseResponseBody(bytes -> new String(bytes, UTF_8)).invoke();
      assertThat(response.status().intValue())
          .as("%s %s status", method, path)
          .isEqualTo(exchange.path("status").asInt());

      String body = response.body();
      JsonNode actual = body == null || body.isBlank() ? MAPPER.nullNode() : MAPPER.readTree(body);
      compare(method + " " + path, expected, actual, differences);
    }

    assertThat(differences).as("field-by-field differences against the recording").isEmpty();
  }

  /** Walks both trees together, so a field present on one side and absent on the other shows up. */
  private static void compare(String where, JsonNode expected, JsonNode actual, List<String> out) {
    if (expected.isObject()) {
      var names = new java.util.TreeSet<String>();
      expected.fieldNames().forEachRemaining(names::add);
      actual.fieldNames().forEachRemaining(names::add);
      for (String name : names) {
        if (GENERATED.contains(name)) {
          continue;
        }
        compare(where + "." + name, expected.path(name), actual.path(name), out);
      }
    } else if (expected.isArray()) {
      if (expected.size() != actual.size()) {
        out.add(where + ": recorded " + expected.size() + " items, got " + actual.size());
        return;
      }
      for (int i = 0; i < expected.size(); i++) {
        compare(where + "[" + i + "]", expected.get(i), actual.get(i), out);
      }
    } else if (!expected.equals(actual)) {
      out.add(where + ": recorded " + expected + ", got " + actual);
    }
  }
}
