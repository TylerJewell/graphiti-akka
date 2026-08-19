package io.akka.memory.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import io.akka.memory.application.EpisodeIngestWorkflow;
import io.akka.memory.application.FlureeStore;
import io.akka.memory.application.PartitionEntity;
import io.akka.memory.application.RetrievalService;
import com.typesafe.config.Config;
import io.akka.memory.domain.Entity;
import io.akka.memory.domain.Episode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The request/response surface, matched verbatim.
 *
 * <p>Paths, methods, status codes, field names and defaults are frozen by the source system's
 * contract: a caller written against it must work here with no change but the address. Internal
 * naming is free; anything on the wire is not — down to the capital E in "Entity Edge deleted",
 * which is inconsistent with its neighbours and is reproduced because a caller may match on it.
 *
 * <p>Note the two non-obvious status codes, both deliberate: episode ingest answers <b>202
 * Accepted</b> because it hands off to a queue and returns before the pipeline runs, and entity
 * creation answers <b>201 Created</b>.
 *
 * <p>Reads resolve against the projection. No route here loads write state (RENDER-001 §3.4).
 */
@HttpEndpoint
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class MemoryEndpoint extends AbstractHttpEndpoint {

  private final ComponentClient componentClient;
  private final FlureeStore store;
  private final RetrievalService retrieval;
  private final BearerAuth auth;

  public MemoryEndpoint(
      ComponentClient componentClient, FlureeStore store, RetrievalService retrieval, Config config) {
    this.componentClient = componentClient;
    this.store = store;
    this.retrieval = retrieval;
    this.auth = new BearerAuth(config.getString("memory.auth.token"));
  }

  /**
   * Refuses the request when authentication is configured and the caller did not satisfy it.
   *
   * <p>Called explicitly at the top of every route but the health check, which stays open so a
   * liveness probe does not need a credential. Explicit at each route rather than hidden in a
   * filter: a route added later that forgets this line is visibly missing it.
   */
  private void authorise() {
    auth.check(
        requestContext().requestHeader("Authorization").map(header -> header.value()));
  }

  // --- wire types. Field names are contract. ------------------------------------------

  public record Message(
      String content,
      String uuid,
      String name,
      String role_type,
      String role,
      String timestamp,
      String source_description) {}

  public record AddMessagesRequest(String group_id, List<Message> messages) {}

  public record Result(String message, boolean success) {}

  public record SearchQuery(List<String> group_ids, String query, Integer max_facts) {}

  public record FactResult(
      String uuid,
      String name,
      String fact,
      Instant valid_at,
      Instant invalid_at,
      Instant created_at,
      Instant expired_at,
      String source_node_uuid,
      String target_node_uuid,
      List<String> episodes) {}

  public record SearchResults(List<FactResult> facts) {}

  public record AddEntityNodeRequest(String uuid, String group_id, String name, String summary) {}

  public record EntityNodeResult(String uuid, String group_id, String name, String summary) {}

  public record EpisodeResult(
      String uuid,
      String group_id,
      String name,
      String content,
      String source,
      String source_description,
      Instant valid_at,
      Instant created_at) {}

  public record GetMemoryRequest(
      String group_id, Integer max_facts, String center_node_uuid, List<Message> messages) {}

  // --- ingest ---------------------------------------------------------------------------

  /**
   * Accepts episodes and returns immediately.
   *
   * <p>Exactly one field is validated before the acknowledgement — the timestamp. Everything else
   * fails later, in the background, with no channel back to the caller. That asymmetry is the
   * contract, not an oversight: success and total failure look identical on the wire.
   */
  @Post("/messages")
  public HttpResponse addMessages(AddMessagesRequest request) {
    authorise();
    var episodes = new ArrayList<Episode>();
    for (Message message : request.messages()) {
      Instant referenceTime;
      try {
        referenceTime = Instant.parse(message.timestamp());
      } catch (DateTimeParseException | NullPointerException e) {
        return HttpResponses.badRequest("Invalid timestamp: " + message.timestamp());
      }
      episodes.add(
          new Episode(
              message.uuid() == null ? UUID.randomUUID().toString() : message.uuid(),
              request.group_id(),
              episodeBody(message),
              Episode.Kind.MESSAGE,
              referenceTime,
              message.source_description() == null ? "" : message.source_description(),
              Instant.now()));
    }

    for (Episode episode : episodes) {
      componentClient
          .forWorkflow(episode.id())
          .method(EpisodeIngestWorkflow::start)
          .invoke(new EpisodeIngestWorkflow.Start(episode, true));
    }

    return HttpResponses.accepted(new Result("Messages added to processing queue", true));
  }

  /**
   * Clears every partition.
   *
   * <p>The set of partitions comes from the projection, which is the only place that knows which
   * ones exist — the write path is keyed by partition and has no index across keys. That is a read
   * of a projection, so §3.4 still holds.
   */
  @Post("/clear")
  public Result clear() {
    authorise();
    for (String partition : store.partitions()) {
      componentClient.forEventSourcedEntity(partition).method(PartitionEntity::clear).invoke();
    }
    return new Result("Graph cleared", true);
  }

  @Delete("/episode/{uuid}")
  public Result deleteEpisode(String uuid) {
    authorise();
    store
        .episodePartition(uuid)
        .ifPresent(
            partition ->
                componentClient
                    .forEventSourcedEntity(partition)
                    .method(PartitionEntity::removeEpisode)
                    .invoke(uuid));
    return new Result("Episode deleted", true);
  }

  @Delete("/group/{group_id}")
  public Result deleteGroup(String group_id) {
    authorise();
    componentClient.forEventSourcedEntity(group_id).method(PartitionEntity::clear).invoke();
    return new Result("Group deleted", true);
  }

  /** Entity creation answers 201 Created — a non-obvious code, and contract. */
  @Post("/entity-node")
  public HttpResponse addEntityNode(AddEntityNodeRequest request) {
    authorise();
    String uuid = request.uuid() == null ? UUID.randomUUID().toString() : request.uuid();
    var entity =
        Entity.create(uuid, request.group_id(), request.name(), Entity.BASE_TYPE)
            .withSummary(request.summary() == null ? "" : request.summary());
    componentClient
        .forEventSourcedEntity(request.group_id())
        .method(PartitionEntity::recordEntities)
        .invoke(new PartitionEntity.RecordEntities(List.of(entity)));
    return HttpResponses.created(
        new EntityNodeResult(uuid, request.group_id(), request.name(), request.summary()),
        "/entity-node/" + uuid);
  }

  @Delete("/entity-edge/{uuid}")
  public Result deleteEntityEdge(String uuid) {
    authorise();
    store
        .factById(uuid)
        .ifPresent(
            fact ->
                componentClient
                    .forEventSourcedEntity(fact.partition())
                    .method(PartitionEntity::removeFact)
                    .invoke(uuid));
    // "Entity Edge" — the capitalisation differs from its neighbours in the source. Contract.
    return new Result("Entity Edge deleted", true);
  }

  // --- retrieval -------------------------------------------------------------------------

  /** Default result count is 10 — a default is contract, not a convenience. */
  @Post("/search")
  public SearchResults search(SearchQuery query) {
    authorise();
    int limit = query.max_facts() == null ? RetrievalService.DEFAULT_SEARCH_LIMIT : query.max_facts();
    var partition =
        query.group_ids() == null || query.group_ids().isEmpty()
            ? "default"
            : query.group_ids().get(0);

    var results = retrieval.search(partition, query.query() == null ? "" : query.query(), limit, null);
    return new SearchResults(results.facts().stream().map(MemoryEndpoint::toApi).toList());
  }

  @Get("/episodes/{group_id}")
  public List<EpisodeResult> episodes(String group_id) {
    authorise();
    int lastN = parseLastN(requestContext().queryParams().getString("last_n").orElse(null));

    // Most recent by reference time, then trimmed — the source orders on the same axis.
    return store.episodesInPartition(group_id).stream()
        .sorted(
            java.util.Comparator.comparing(
                FlureeStore.StoredEpisode::referenceTime,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
        .limit(Math.max(lastN, 0))
        .map(MemoryEndpoint::toApi)
        .toList();
  }

  @Get("/healthcheck")
  public HttpResponse healthcheck() {
    return HttpResponses.ok();
  }

  /**
   * Searches with a query built from <em>every</em> supplied message.
   *
   * <p>The composition here is {@code role_type(role): content}, one line each — which is the
   * reverse field order of the one used when the same messages are ingested. The two are
   * inconsistent in the source and are reproduced separately rather than unified, because unifying
   * them would change what a query matches.
   */
  @Post("/get-memory")
  public SearchResults getMemory(GetMemoryRequest request) {
    authorise();
    var combined = new StringBuilder();
    if (request.messages() != null) {
      for (Message message : request.messages()) {
        combined
            .append(message.role_type() == null ? "" : message.role_type())
            .append('(')
            .append(message.role() == null ? "" : message.role())
            .append("): ")
            .append(message.content())
            .append('\n');
      }
    }
    return search(
        new SearchQuery(List.of(request.group_id()), combined.toString(), request.max_facts()));
  }

  @Get("/entity-edge/{uuid}")
  public HttpResponse getEntityEdge(String uuid) {
    authorise();
    return store
        .factById(uuid)
        .<HttpResponse>map(fact -> HttpResponses.ok(toApi(fact)))
        .orElseGet(() -> HttpResponses.notFound("Fact not found"));
  }

  // --- wire mapping ----------------------------------------------------------------------

  /** {@code last_n} has no default in the source contract; an absent one falls back to the limit. */
  private static int parseLastN(String raw) {
    if (raw == null || raw.isBlank()) {
      return RetrievalService.DEFAULT_SEARCH_LIMIT;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      return RetrievalService.DEFAULT_SEARCH_LIMIT;
    }
  }

  /** The body an ingested message becomes: {@code role(role_type): content}. */
  private static String episodeBody(Message message) {
    return (message.role() == null ? "" : message.role())
        + "("
        + message.role_type()
        + "): "
        + message.content();
  }

  /**
   * Domain types never cross the wire; this is where the shapes are matched.
   *
   * <p>{@code episodes} is always empty. The source builds this response without setting the field
   * and its default is an empty list, so a populated one would be a difference a caller could see.
   */
  private static FactResult toApi(FlureeStore.StoredFact fact) {
    return new FactResult(
        fact.factId(),
        fact.relation(),
        fact.statement(),
        fact.validFrom(),
        fact.validUntil(),
        fact.recordedAt(),
        fact.supersededAt(),
        fact.subjectId(),
        fact.objectId(),
        List.of());
  }

  private static EpisodeResult toApi(FlureeStore.StoredEpisode episode) {
    return new EpisodeResult(
        episode.episodeId(),
        episode.partition(),
        episode.episodeId(),
        episode.content(),
        episode.kind(),
        episode.sourceDescription(),
        episode.referenceTime(),
        episode.recordedAt());
  }
}
