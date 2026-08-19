package io.akka.memory.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.akka.memory.domain.Fact;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The graph, vector and full-text store, reached over its HTTP API.
 *
 * <p>No client library is needed — the store speaks JSON over HTTP and the JDK's own client is
 * sufficient. That is one fewer dependency to justify.
 *
 * <p><b>The two timelines are stored differently, and deliberately so.</b> Transaction time is the
 * store's own commit history and needs no modelling. Valid time has no store-level support and is
 * written as explicit typed metadata on each fact — which was probed rather than assumed, and
 * confirmed to be natively range-queryable in a single query (research R-005).
 */
public final class FlureeStore {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String EX = "http://example.org/";

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final String baseUrl;
  private final String ledger;

  public FlureeStore(String baseUrl, String ledger) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.ledger = ledger;
  }

  public static FlureeStore localhost() {
    return new FlureeStore("http://127.0.0.1:8090", "memory");
  }

  public boolean isHealthy() {
    try {
      var response = send(HttpRequest.newBuilder(URI.create(baseUrl + "/health")).GET());
      return response.statusCode() == 200;
    } catch (Exception e) {
      return false;
    }
  }

  /** Writes facts as JSON-LD, with valid time as typed metadata. */
  public void insert(List<Fact> facts) {
    ObjectNode context = MAPPER.createObjectNode();
    context.put("ex", EX);
    context.put("xsd", "http://www.w3.org/2001/XMLSchema#");

    ArrayNode graph = MAPPER.createArrayNode();
    for (Fact fact : facts) {
      ObjectNode node = MAPPER.createObjectNode();
      node.put("@id", "ex:" + fact.id());
      node.put("@type", "ex:Fact");
      node.put("ex:partition", fact.partition());
      node.put("ex:subject", fact.subjectId());
      node.put("ex:object", fact.objectId());
      node.put("ex:relation", fact.relation());
      node.put("ex:statement", fact.statement());
      fact.validFrom().ifPresent(v -> node.set("ex:validFrom", dateTime(v)));
      fact.validUntil().ifPresent(v -> node.set("ex:validUntil", dateTime(v)));
      graph.add(node);
    }

    ObjectNode body = MAPPER.createObjectNode();
    body.set("@context", context);
    body.put("ledger", ledger);
    body.set("insert", graph);
    post("/v1/fluree/update", body);
  }

  /**
   * Facts true in the world at {@code when} — the <b>valid-time</b> axis.
   *
   * <p>The predicate is {@code validFrom <= when AND (validUntil unbound OR validUntil > when)},
   * expressed as a single store-side filter. Note the vocabulary: {@code or}/{@code not}/{@code
   * bound}. Writing {@code ||} is a parse error, and a {@code coalesce} formulation silently
   * returns zero rows — the more dangerous of the two mistakes, since it looks like "no data".
   */
  public List<String> validAt(String partition, Instant when) {
    String t = when.toString();
    ObjectNode context = MAPPER.createObjectNode();
    context.put("ex", EX);

    ArrayNode where = MAPPER.createArrayNode();
    ObjectNode pattern = MAPPER.createObjectNode();
    pattern.put("@id", "?f");
    pattern.put("ex:validFrom", "?from");
    pattern.put("ex:partition", partition);
    where.add(pattern);

    ArrayNode optional = MAPPER.createArrayNode();
    optional.add("optional");
    ObjectNode until = MAPPER.createObjectNode();
    until.put("@id", "?f");
    until.put("ex:validUntil", "?until");
    optional.add(until);
    where.add(optional);

    ArrayNode filter = MAPPER.createArrayNode();
    filter.add("filter");
    filter.add("(<= ?from \"" + t + "\")");
    filter.add("(or (not (bound ?until)) (> ?until \"" + t + "\"))");
    where.add(filter);

    ObjectNode select = MAPPER.createObjectNode();
    ArrayNode fields = MAPPER.createArrayNode();
    fields.add("ex:statement");
    select.set("?f", fields);

    ObjectNode body = MAPPER.createObjectNode();
    body.set("@context", context);
    body.put("from", ledger);
    body.set("select", select);
    body.set("where", where);

    JsonNode result = post("/v1/fluree/query", body);
    var out = new ArrayList<String>();
    if (result != null && result.isArray()) {
      result.forEach(row -> {
        var statement = row.get("ex:statement");
        if (statement != null) {
          out.add(statement.asText());
        }
      });
    }
    return out;
  }

  private ObjectNode dateTime(Instant instant) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("@value", instant.toString());
    node.put("@type", "xsd:dateTime");
    return node;
  }

  private JsonNode post(String path, ObjectNode body) {
    try {
      var request =
          HttpRequest.newBuilder(URI.create(baseUrl + path))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
      var response = send(request);
      if (response.statusCode() >= 300) {
        throw new IllegalStateException(
            "store returned " + response.statusCode() + ": " + response.body());
      }
      return response.body().isBlank() ? null : MAPPER.readTree(response.body());
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("store request failed: " + path, e);
    }
  }

  private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
    return http.send(
        builder.timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofString());
  }
}
