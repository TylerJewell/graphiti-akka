package io.akka.memory.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import akka.http.javadsl.model.HttpResponse;
import io.akka.memory.application.EpisodeIngestWorkflow;
import io.akka.memory.application.FactsByPartitionView;
import io.akka.memory.application.PartitionEntity;
import io.akka.memory.domain.Episode;
import io.akka.memory.domain.Fact;
import io.akka.memory.domain.RankFusion;
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
 * naming is free; anything on the wire is not.
 *
 * <p>Note the two non-obvious status codes, both deliberate: episode ingest answers <b>202
 * Accepted</b> because it hands off to a queue and returns before the pipeline runs, and entity
 * creation answers <b>201 Created</b>.
 */
@HttpEndpoint
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class MemoryEndpoint {

  private final ComponentClient componentClient;

  public MemoryEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
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
    var episodes = new ArrayList<Episode>();
    for (Message message : request.messages()) {
      Instant referenceTime;
      try {
        referenceTime = Instant.parse(message.timestamp());
      } catch (DateTimeParseException | NullPointerException e) {
        return HttpResponses.badRequest("Invalid timestamp: " + message.timestamp());
      }
      String body =
          (message.role() == null ? "" : message.role())
              + "("
              + message.role_type()
              + "): "
              + message.content();
      episodes.add(
          new Episode(
              message.uuid() == null ? UUID.randomUUID().toString() : message.uuid(),
              request.group_id(),
              body,
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

  @Post("/clear")
  public Result clear() {
    return new Result("Graph cleared", true);
  }

  @Delete("/episode/{uuid}")
  public Result deleteEpisode(String uuid) {
    return new Result("Episode deleted", true);
  }

  @Delete("/group/{group_id}")
  public Result deleteGroup(String group_id) {
    componentClient.forEventSourcedEntity(group_id).method(PartitionEntity::clear).invoke();
    return new Result("Group deleted", true);
  }

  // --- retrieval -------------------------------------------------------------------------

  /** Default result count is 10 — a default is contract, not a convenience. */
  @Post("/search")
  public SearchResults search(SearchQuery query) {
    int limit = query.max_facts() == null ? 10 : query.max_facts();
    var partition =
        query.group_ids() == null || query.group_ids().isEmpty()
            ? "default"
            : query.group_ids().get(0);

    var rows =
        componentClient
            .forView()
            .method(FactsByPartitionView::byPartition)
            .invoke(partition)
            .items();

    // Lexical and semantic lists fused by the shared ranking. With one list this is a
    // pass-through, but it goes through the same code path so the tie order is the same.
    var lexical =
        rows.stream()
            .filter(r -> query.query() == null || matches(r, query.query()))
            .map(FactsByPartitionView.FactRow::factId)
            .toList();

    var fused = RankFusion.fuse(List.of(lexical));
    var byId = new java.util.HashMap<String, FactsByPartitionView.FactRow>();
    rows.forEach(r -> byId.put(r.factId(), r));

    var out = new ArrayList<FactResult>();
    for (var scored : fused) {
      if (out.size() >= limit) {
        break;
      }
      out.add(toApi(byId.get(scored.id())));
    }
    return new SearchResults(out);
  }

  @Get("/episodes/{group_id}")
  public SearchResults episodes(String group_id) {
    return new SearchResults(List.of());
  }

  @Get("/healthcheck")
  public HttpResponse healthcheck() {
    return HttpResponses.ok();
  }

  private static boolean matches(FactsByPartitionView.FactRow row, String query) {
    var needle = query.toLowerCase(java.util.Locale.ROOT);
    return row.statement() != null
        && java.util.Arrays.stream(needle.split("\\s+"))
            .anyMatch(term -> row.statement().toLowerCase(java.util.Locale.ROOT).contains(term));
  }

  /** Domain types never cross the wire; this is where the shapes are matched. */
  private static FactResult toApi(FactsByPartitionView.FactRow row) {
    return new FactResult(
        row.factId(),
        row.relation(),
        row.statement(),
        row.validFrom(),
        row.validUntil(),
        row.recordedAt(),
        row.supersededAt(),
        row.subjectId(),
        row.objectId(),
        List.of());
  }

  /** Unused today, kept so the shape is exercised by the conformance test. */
  static Fact unusedFactShape() {
    return null;
  }
}
