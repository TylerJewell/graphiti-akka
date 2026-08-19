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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The graph, vector and full-text store, reached over its HTTP API.
 *
 * <p>This is the read projection — the only one. Retrieval, fact lookup and episode listing all
 * resolve here, and the write path is never queried to serve a read (RENDER-001 §3.4, D-009).
 *
 * <p>No client library is needed — the store speaks JSON over HTTP and the JDK's own client is
 * sufficient. That is one fewer dependency to justify.
 *
 * <p><b>Both timelines are modelled explicitly, and both had to be.</b> Valid time was never going
 * to be free — it has no store-level support and is written as typed metadata on each fact, which
 * was probed and confirmed natively range-queryable in a single query (research R-005).
 *
 * <p>Transaction time was expected to be free, because the store keeps a commit history and offers
 * a query-at-a-past-commit parameter. It is not. On this store version that parameter is
 * <b>silently ignored</b>: a query asking for an earlier commit returns the ledger's current
 * answer, with no error and nothing in the response to say the request was dropped. Trusting it
 * would have meant answering every historical question with today's data and believing the answer.
 * So transaction time is written as explicit metadata too, exactly like valid time.
 */
public final class FlureeStore {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String EX = "http://example.org/";

  /** Multi-valued fields are joined rather than stored as repeated triples. */
  private static final String JOIN = ",";

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

  // --- projected shapes ----------------------------------------------------------------

  /** A fact as the read side sees it, carrying both timelines separately. */
  public record StoredFact(
      String factId,
      String partition,
      String subjectId,
      String objectId,
      String relation,
      String statement,
      Instant validFrom,
      Instant validUntil,
      Instant recordedAt,
      Instant supersededAt,
      List<String> episodeIds,
      float[] embedding) {}

  public record StoredEpisode(
      String episodeId,
      String partition,
      String content,
      String kind,
      Instant referenceTime,
      String sourceDescription,
      Instant recordedAt) {}

  public record StoredEntity(String entityId, String partition, String name, String summary) {}

  // --- health --------------------------------------------------------------------------

  public boolean isHealthy() {
    try {
      var response = send(HttpRequest.newBuilder(URI.create(baseUrl + "/health")).GET());
      return response.statusCode() == 200;
    } catch (Exception e) {
      return false;
    }
  }

  // --- writes --------------------------------------------------------------------------

  /** Writes facts as JSON-LD, with valid time as typed metadata. */
  public void insert(List<Fact> facts) {
    putFacts(facts.stream().map(f -> project(f, null)).toList());
  }

  /** Projects a domain fact, optionally with the vector used for similarity retrieval. */
  public static StoredFact project(Fact fact, float[] embedding) {
    return new StoredFact(
        fact.id(),
        fact.partition(),
        fact.subjectId(),
        fact.objectId(),
        fact.relation(),
        fact.statement(),
        fact.validFrom().orElse(null),
        fact.validUntil().orElse(null),
        fact.recordedAt(),
        fact.supersededAt().orElse(null),
        fact.episodeIds(),
        embedding);
  }

  /**
   * Writes facts, replacing any already stored under the same id.
   *
   * <p>The retraction is what makes this safe to call twice with the same event. Delivery is
   * at-least-once, and writing a second value for a property in this store <em>adds</em> it beside
   * the first rather than replacing it — so without the retraction a redelivered event would leave
   * a fact with two statements and two start times.
   */
  public void putFacts(List<StoredFact> facts) {
    if (facts.isEmpty()) {
      return;
    }
    facts.forEach(fact -> retractSubject("ex:" + fact.factId()));
    ArrayNode graph = MAPPER.createArrayNode();
    for (StoredFact fact : facts) {
      ObjectNode node = MAPPER.createObjectNode();
      node.put("@id", "ex:" + fact.factId());
      node.put("@type", "ex:Fact");
      node.put("ex:partition", fact.partition());
      node.put("ex:subject", fact.subjectId());
      node.put("ex:object", fact.objectId());
      node.put("ex:relation", fact.relation());
      node.put("ex:statement", fact.statement());
      node.set("ex:recordedAt", dateTime(fact.recordedAt()));
      node.put("ex:episodes", String.join(JOIN, fact.episodeIds()));
      if (fact.validFrom() != null) {
        node.set("ex:validFrom", dateTime(fact.validFrom()));
      }
      if (fact.validUntil() != null) {
        node.set("ex:validUntil", dateTime(fact.validUntil()));
      }
      if (fact.supersededAt() != null) {
        node.set("ex:supersededAt", dateTime(fact.supersededAt()));
      }
      if (fact.embedding() != null && fact.embedding().length > 0) {
        node.put("ex:embedding", encode(fact.embedding()));
      }
      graph.add(node);
    }
    post("/v1/fluree/update", update(graph));
  }

  public void putEpisode(StoredEpisode episode) {
    retractSubject("ex:" + episode.episodeId());
    ObjectNode node = MAPPER.createObjectNode();
    node.put("@id", "ex:" + episode.episodeId());
    node.put("@type", "ex:Episode");
    node.put("ex:partition", episode.partition());
    node.put("ex:content", episode.content());
    node.put("ex:kind", episode.kind());
    node.set("ex:referenceTime", dateTime(episode.referenceTime()));
    node.put("ex:source", episode.sourceDescription());
    node.set("ex:recordedAt", dateTime(episode.recordedAt()));
    post("/v1/fluree/update", update(MAPPER.createArrayNode().add(node)));
  }

  public void putEntity(StoredEntity entity) {
    retractSubject("ex:" + entity.entityId());
    ObjectNode node = MAPPER.createObjectNode();
    node.put("@id", "ex:" + entity.entityId());
    node.put("@type", "ex:Entity");
    node.put("ex:partition", entity.partition());
    node.put("ex:name", entity.name());
    node.put("ex:summary", entity.summary() == null ? "" : entity.summary());
    post("/v1/fluree/update", update(MAPPER.createArrayNode().add(node)));
  }

  /**
   * Records that a fact was closed.
   *
   * <p>Closing is a state transition, not a deletion: the node keeps every other field and gains an
   * end on both timelines. The old values are retracted first, because writing a second value for a
   * property adds it alongside the first rather than replacing it.
   */
  public void closeFact(String factId, Instant validUntil, Instant supersededAt) {
    ObjectNode body = MAPPER.createObjectNode();
    body.set("@context", context());
    body.put("ledger", ledger);

    ArrayNode where = MAPPER.createArrayNode();
    where.add(optionalPattern("ex:" + factId, "ex:validUntil", "?u"));
    where.add(optionalPattern("ex:" + factId, "ex:supersededAt", "?s"));

    ArrayNode delete = MAPPER.createArrayNode();
    delete.add(pattern("ex:" + factId, "ex:validUntil", "?u"));
    delete.add(pattern("ex:" + factId, "ex:supersededAt", "?s"));

    ObjectNode insert = MAPPER.createObjectNode();
    insert.put("@id", "ex:" + factId);
    insert.set("ex:validUntil", dateTime(validUntil));
    insert.set("ex:supersededAt", dateTime(supersededAt));

    body.set("where", where);
    body.set("delete", delete);
    body.set("insert", MAPPER.createArrayNode().add(insert));
    post("/v1/fluree/update", body);
  }

  /**
   * Removes an episode, and with it the facts whose only provenance was that episode.
   *
   * <p>A fact attributed to more than one episode survives with the remaining attribution, matching
   * the write model — deleting a source does not delete what other sources also said.
   */
  public void deleteEpisode(String episodeId) {
    retractSubject("ex:" + episodeId);
    for (StoredFact fact : query("ex:Fact").stream().map(this::toFact).toList()) {
      if (fact.episodeIds().contains(episodeId)) {
        var remaining = fact.episodeIds().stream().filter(e -> !e.equals(episodeId)).toList();
        if (remaining.isEmpty()) {
          retractSubject("ex:" + fact.factId());
        } else {
          replaceEpisodes(fact.factId(), remaining);
        }
      }
    }
  }

  /** Removes everything in one partition, leaving the others untouched. */
  public void clearPartition(String partition) {
    ObjectNode body = MAPPER.createObjectNode();
    body.set("@context", context());
    body.put("ledger", ledger);

    ArrayNode where = MAPPER.createArrayNode();
    ObjectNode inPartition = MAPPER.createObjectNode();
    inPartition.put("@id", "?s");
    inPartition.put("ex:partition", partition);
    where.add(inPartition);
    where.add(pattern("?s", "?p", "?o"));

    body.set("where", where);
    body.set("delete", MAPPER.createArrayNode().add(pattern("?s", "?p", "?o")));
    post("/v1/fluree/update", body);
  }

  // --- reads ---------------------------------------------------------------------------

  /** Replaces an entity's summary, leaving its name and partition alone. */
  public void updateEntitySummary(String entityId, String summary) {
    ObjectNode body = MAPPER.createObjectNode();
    body.set("@context", context());
    body.put("ledger", ledger);
    body.set("where", MAPPER.createArrayNode().add(pattern("ex:" + entityId, "ex:summary", "?s")));
    body.set("delete", MAPPER.createArrayNode().add(pattern("ex:" + entityId, "ex:summary", "?s")));
    ObjectNode insert = MAPPER.createObjectNode();
    insert.put("@id", "ex:" + entityId);
    insert.put("ex:summary", summary == null ? "" : summary);
    body.set("insert", MAPPER.createArrayNode().add(insert));
    post("/v1/fluree/update", body);
  }

  public List<StoredFact> factsInPartition(String partition) {
    return query("ex:Fact", partition).stream().map(this::toFact).toList();
  }

  /**
   * One fact by its identifier, or empty if there is no such fact.
   *
   * <p>The emptiness check is on the content, not on the row count. Asking for an identifier the
   * store has never seen returns a node carrying only that identifier rather than nothing at all,
   * so a caller that trusts the row count concludes every fact exists.
   */
  public Optional<StoredFact> factById(String factId) {
    return queryById("ex:" + factId)
        .filter(node -> text(node, "ex:statement") != null)
        .map(this::toFact);
  }

  public List<StoredEpisode> episodesInPartition(String partition) {
    return query("ex:Episode", partition).stream().map(this::toEpisode).toList();
  }

  public List<StoredEntity> entitiesInPartition(String partition) {
    return query("ex:Entity", partition).stream().map(this::toEntity).toList();
  }

  /**
   * Every partition the projection has seen.
   *
   * <p>The write path is keyed by partition and so has no index across keys; this is the only place
   * that can enumerate them.
   */
  public List<String> partitions() {
    var out = new java.util.LinkedHashSet<String>();
    query("ex:Episode").forEach(node -> add(out, node));
    query("ex:Fact").forEach(node -> add(out, node));
    query("ex:Entity").forEach(node -> add(out, node));
    return List.copyOf(out);
  }

  /** Which partition an episode belongs to, so a delete can be routed to the right key. */
  public Optional<String> episodePartition(String episodeId) {
    return query("ex:Episode").stream()
        .filter(node -> episodeId.equals(stripPrefix(text(node, "@id"))))
        .map(node -> text(node, "ex:partition"))
        .findFirst();
  }

  private static void add(java.util.Set<String> out, JsonNode node) {
    String partition = text(node, "ex:partition");
    if (partition != null && !partition.isEmpty()) {
      out.add(partition);
    }
  }

  /** Removes one fact outright. Distinct from closing it, which keeps the record. */
  public void removeFact(String factId) {
    retractSubject("ex:" + factId);
  }

  /** Facts true in the world at {@code when} — the valid-time axis. */
  public List<String> validAt(String partition, Instant when) {
    return openAt(partition, when, "ex:validFrom", "ex:validUntil");
  }

  /**
   * Facts the system <b>believed</b> at {@code when} — the transaction-time axis.
   *
   * <p>Deliberately a separate query over separate fields. The two axes answer different questions
   * and diverge whenever something is learned late or corrected: a fact recorded in March about
   * January is absent from a transaction-time query at February and present in a valid-time one.
   * Answering either from the other's data is the failure this pair exists to prevent.
   */
  public List<String> believedAt(String partition, Instant when) {
    return openAt(partition, when, "ex:recordedAt", "ex:supersededAt");
  }

  /**
   * Facts open on one timeline at one instant.
   *
   * <p>The predicate is {@code start <= when AND (end unbound OR end > when)}, expressed as a single
   * store-side filter. Note the vocabulary: {@code or}/{@code not}/{@code bound}. Writing {@code ||}
   * is a parse error, and a {@code coalesce} formulation silently returns zero rows — the more
   * dangerous of the two mistakes, since it looks like "no data".
   */
  private List<String> openAt(String partition, Instant when, String startField, String endField) {
    String t = when.toString();
    ArrayNode where = MAPPER.createArrayNode();
    ObjectNode subject = MAPPER.createObjectNode();
    subject.put("@id", "?f");
    subject.put(startField, "?from");
    subject.put("ex:partition", partition);
    where.add(subject);
    where.add(optionalPattern("?f", endField, "?until"));

    ArrayNode filter = MAPPER.createArrayNode();
    filter.add("filter");
    filter.add("(<= ?from \"" + t + "\")");
    filter.add("(or (not (bound ?until)) (> ?until \"" + t + "\"))");
    where.add(filter);

    ObjectNode select = MAPPER.createObjectNode();
    select.set("?f", MAPPER.createArrayNode().add("ex:statement"));

    ObjectNode body = MAPPER.createObjectNode();
    body.set("@context", context());
    body.put("from", ledger);
    body.set("select", select);
    body.set("where", where);

    JsonNode result = post("/v1/fluree/query", body);
    var out = new ArrayList<String>();
    if (result != null && result.isArray()) {
      result.forEach(
          row -> {
            var statement = row.get("ex:statement");
            if (statement != null) {
              out.add(statement.asText());
            }
          });
    }
    return out;
  }

  // --- query plumbing ------------------------------------------------------------------

  private List<JsonNode> query(String type) {
    return query(type, null);
  }

  private List<JsonNode> query(String type, String partition) {
    ArrayNode where = MAPPER.createArrayNode();
    ObjectNode subject = MAPPER.createObjectNode();
    subject.put("@id", "?s");
    subject.put("@type", type);
    if (partition != null) {
      subject.put("ex:partition", partition);
    }
    where.add(subject);

    ObjectNode body = MAPPER.createObjectNode();
    body.set("@context", context());
    body.put("from", ledger);
    ObjectNode select = MAPPER.createObjectNode();
    select.set("?s", MAPPER.createArrayNode().add("*"));
    body.set("select", select);
    body.set("where", where);

    JsonNode result = post("/v1/fluree/query", body);
    var out = new ArrayList<JsonNode>();
    if (result != null && result.isArray()) {
      result.forEach(out::add);
    }
    return out;
  }

  /**
   * A single node by its identifier.
   *
   * <p>The identifier goes in the {@code select} key and there is no {@code where} clause. Binding
   * it in a where pattern instead — the shape that reads more naturally — matches nothing and
   * returns an empty result rather than an error, which looks exactly like "no such fact".
   */
  private Optional<JsonNode> queryById(String id) {
    ObjectNode body = MAPPER.createObjectNode();
    body.set("@context", context());
    body.put("from", ledger);
    ObjectNode select = MAPPER.createObjectNode();
    select.set(id, MAPPER.createArrayNode().add("*"));
    body.set("select", select);

    JsonNode result = post("/v1/fluree/query", body);
    if (result != null && result.isArray() && !result.isEmpty()) {
      return Optional.of(result.get(0));
    }
    return Optional.empty();
  }

  private void retractSubject(String id) {
    ObjectNode body = MAPPER.createObjectNode();
    body.set("@context", context());
    body.put("ledger", ledger);
    body.set("where", MAPPER.createArrayNode().add(pattern(id, "?p", "?o")));
    body.set("delete", MAPPER.createArrayNode().add(pattern(id, "?p", "?o")));
    post("/v1/fluree/update", body);
  }

  private void replaceEpisodes(String factId, List<String> episodeIds) {
    ObjectNode body = MAPPER.createObjectNode();
    body.set("@context", context());
    body.put("ledger", ledger);
    body.set("where", MAPPER.createArrayNode().add(pattern("ex:" + factId, "ex:episodes", "?e")));
    body.set("delete", MAPPER.createArrayNode().add(pattern("ex:" + factId, "ex:episodes", "?e")));
    ObjectNode insert = MAPPER.createObjectNode();
    insert.put("@id", "ex:" + factId);
    insert.put("ex:episodes", String.join(JOIN, episodeIds));
    body.set("insert", MAPPER.createArrayNode().add(insert));
    post("/v1/fluree/update", body);
  }

  // --- mapping -------------------------------------------------------------------------

  private StoredFact toFact(JsonNode node) {
    return new StoredFact(
        stripPrefix(text(node, "@id")),
        text(node, "ex:partition"),
        text(node, "ex:subject"),
        text(node, "ex:object"),
        text(node, "ex:relation"),
        text(node, "ex:statement"),
        instant(node, "ex:validFrom"),
        instant(node, "ex:validUntil"),
        instant(node, "ex:recordedAt"),
        instant(node, "ex:supersededAt"),
        splitEpisodes(text(node, "ex:episodes")),
        decode(text(node, "ex:embedding")));
  }

  private StoredEpisode toEpisode(JsonNode node) {
    return new StoredEpisode(
        stripPrefix(text(node, "@id")),
        text(node, "ex:partition"),
        text(node, "ex:content"),
        text(node, "ex:kind"),
        instant(node, "ex:referenceTime"),
        text(node, "ex:source"),
        instant(node, "ex:recordedAt"));
  }

  private StoredEntity toEntity(JsonNode node) {
    return new StoredEntity(
        stripPrefix(text(node, "@id")),
        text(node, "ex:partition"),
        text(node, "ex:name"),
        text(node, "ex:summary"));
  }

  private static List<String> splitEpisodes(String joined) {
    if (joined == null || joined.isEmpty()) {
      return List.of();
    }
    return Arrays.stream(joined.split(JOIN)).filter(s -> !s.isEmpty()).toList();
  }

  private static String stripPrefix(String id) {
    return id != null && id.startsWith("ex:") ? id.substring(3) : id;
  }

  private static String text(JsonNode node, String field) {
    var value = node.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    return value.isObject() ? value.path("@value").asText(null) : value.asText();
  }

  private static Instant instant(JsonNode node, String field) {
    String raw = text(node, field);
    return raw == null || raw.isEmpty() ? null : Instant.parse(raw);
  }

  private static String encode(float[] vector) {
    var out = new StringBuilder(vector.length * 8);
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) {
        out.append(' ');
      }
      out.append(vector[i]);
    }
    return out.toString();
  }

  private static float[] decode(String encoded) {
    if (encoded == null || encoded.isEmpty()) {
      return null;
    }
    String[] parts = encoded.split(" ");
    var out = new float[parts.length];
    for (int i = 0; i < parts.length; i++) {
      out[i] = Float.parseFloat(parts[i]);
    }
    return out;
  }

  // --- request building ----------------------------------------------------------------

  private ObjectNode context() {
    ObjectNode context = MAPPER.createObjectNode();
    context.put("ex", EX);
    context.put("xsd", "http://www.w3.org/2001/XMLSchema#");
    return context;
  }

  private ObjectNode update(ArrayNode graph) {
    ObjectNode body = MAPPER.createObjectNode();
    body.set("@context", context());
    body.put("ledger", ledger);
    body.set("insert", graph);
    return body;
  }

  private ObjectNode pattern(String subject, String predicate, String object) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("@id", subject);
    node.put(predicate, object);
    return node;
  }

  private ArrayNode optionalPattern(String subject, String predicate, String object) {
    ArrayNode wrapper = MAPPER.createArrayNode();
    wrapper.add("optional");
    wrapper.add(pattern(subject, predicate, object));
    return wrapper;
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
