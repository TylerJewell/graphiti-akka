package io.akka.memory.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.mcp.McpEndpoint;
import akka.javasdk.annotations.mcp.McpTool;
import akka.javasdk.client.ComponentClient;
import io.akka.memory.application.EpisodeIngestWorkflow;
import io.akka.memory.application.FactsByPartitionView;
import io.akka.memory.application.PartitionEntity;
import io.akka.memory.domain.Episode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The agent-tool surface, matched verbatim.
 *
 * <p>Tool names, parameter names, <b>parameter order</b> and defaults are frozen — an agent that
 * discovers these tools dynamically must find exactly what it found against the source system.
 *
 * <p>The descriptions are equally frozen, and for a less obvious reason: they are read by a
 * language model, not a human. They decide which tool a calling agent picks and what it passes, so
 * rewording one is a behavioural change wearing cosmetic clothing.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@McpEndpoint(serverName = "memory-mcp", serverVersion = "1.0.0")
public class MemoryMcpEndpoint {

  private final ComponentClient componentClient;

  public MemoryMcpEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @McpTool(
      name = "add_memory",
      description =
          "Add an episode to memory. This is the primary way to add information to the graph.")
  public String addMemory(
      @Description("Name of the episode") String name,
      @Description("The content of the episode to persist to memory") String episode_body,
      @Description("A unique ID for this graph") Optional<String> group_id,
      @Description("Source type: text, json, or message") Optional<String> source,
      @Description("Description of the source") Optional<String> source_description,
      @Description("Optional UUID for the episode") Optional<String> uuid,
      @Description("ISO-8601 reference time for the episode") Optional<String> reference_time) {

    // The only synchronous validation: a malformed reference time is an error to the caller
    // rather than a silent background failure. Everything else fails after acknowledgement.
    Instant referenceTime;
    try {
      referenceTime =
          reference_time.map(Instant::parse).orElseGet(Instant::now);
    } catch (Exception e) {
      return "Invalid reference_time: " + reference_time.orElse("");
    }

    var partition = group_id.orElse("default");
    var episode =
        new Episode(
            uuid.orElseGet(() -> UUID.randomUUID().toString()),
            partition,
            episode_body,
            Episode.Kind.fromWire(source.orElse("text")),
            referenceTime,
            source_description.orElse(""),
            Instant.now());

    componentClient
        .forWorkflow(episode.id())
        .method(EpisodeIngestWorkflow::start)
        .invoke(new EpisodeIngestWorkflow.Start(episode, true));

    // Returns immediately — the episode is queued, not processed.
    return "Episode '" + name + "' queued for ingestion";
  }

  @McpTool(
      name = "search_memory_facts",
      description = "Search the graph memory for relevant facts.")
  public String searchMemoryFacts(
      @Description("The search query") String query,
      @Description("Graph IDs to search") Optional<String> group_ids,
      @Description("Maximum number of facts to return") Optional<Integer> max_facts) {

    var partition = group_ids.orElse("default");
    var limit = max_facts.orElse(10);
    var rows =
        componentClient
            .forView()
            .method(FactsByPartitionView::byPartition)
            .invoke(partition)
            .items();

    return rows.stream()
        .limit(limit)
        .map(FactsByPartitionView.FactRow::statement)
        .reduce((a, b) -> a + "\n" + b)
        .orElse("");
  }

  @McpTool(name = "get_status", description = "Get the status of the memory service.")
  public String getStatus() {
    return "ok";
  }

  @McpTool(name = "clear_graph", description = "Clear all data from the graph memory.")
  public String clearGraph(@Description("Graph IDs to clear") Optional<String> group_ids) {
    componentClient
        .forEventSourcedEntity(group_ids.orElse("default"))
        .method(PartitionEntity::clear)
        .invoke();
    return "Graph cleared";
  }

  @McpTool(name = "delete_episode", description = "Delete an episode from the graph memory.")
  public String deleteEpisode(@Description("UUID of the episode to delete") String uuid) {
    return "Episode deleted: " + uuid;
  }

  static List<String> toolNames() {
    return List.of(
        "add_memory", "search_memory_facts", "get_status", "clear_graph", "delete_episode");
  }
}
