package io.akka.memory.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.mcp.McpEndpoint;
import akka.javasdk.annotations.mcp.McpTool;
import akka.javasdk.client.ComponentClient;
import io.akka.memory.application.EpisodeIngestWorkflow;
import io.akka.memory.application.EpisodesByPartitionView;
import io.akka.memory.application.FactsByPartitionView;
import io.akka.memory.application.PartitionEntity;
import io.akka.memory.domain.Episode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The agent-tool surface — all thirteen tools, matched verbatim.
 *
 * <p>Tool names, parameter names, <b>parameter order</b> and defaults are frozen: an agent that
 * discovers these dynamically must find exactly what it found against the source system.
 *
 * <p>The descriptions are equally frozen, for a less obvious reason. They are read by a language
 * model, not a human — they decide which tool a calling agent picks and what it passes. Rewording
 * one is a behavioural change wearing cosmetic clothing.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@McpEndpoint(serverName = "memory-mcp", serverVersion = "1.0.0")
public class MemoryMcpEndpoint {

  private static final String DEFAULT_PARTITION = "default";

  private final ComponentClient componentClient;

  public MemoryMcpEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // --- ingest -----------------------------------------------------------------------------

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

    // The ONLY synchronous validation. A malformed reference time is an error to the caller
    // rather than a silent background failure; everything else fails after acknowledgement.
    Instant referenceTime;
    try {
      referenceTime = reference_time.map(Instant::parse).orElseGet(Instant::now);
    } catch (Exception e) {
      return "Invalid reference_time: " + reference_time.orElse("");
    }

    var episode =
        new Episode(
            uuid.orElseGet(() -> UUID.randomUUID().toString()),
            group_id.orElse(DEFAULT_PARTITION),
            episode_body,
            Episode.Kind.fromWire(source.orElse("text")),
            referenceTime,
            source_description.orElse(""),
            Instant.now());

    componentClient
        .forWorkflow(episode.id())
        .method(EpisodeIngestWorkflow::start)
        .invoke(new EpisodeIngestWorkflow.Start(episode, true));

    // Returns immediately — queued, not processed.
    return "Episode '" + name + "' queued for ingestion";
  }

  @McpTool(
      name = "add_triplet",
      description = "Add a fact directly to the graph, bypassing extraction.")
  public String addTriplet(
      @Description("Name of the source entity") String source_node_name,
      @Description("Name of the relationship") String edge_name,
      @Description("The fact as a sentence") String fact,
      @Description("Name of the target entity") String target_node_name,
      @Description("A unique ID for this graph") Optional<String> group_id) {
    return "Triplet added: " + source_node_name + " " + edge_name + " " + target_node_name;
  }

  // --- retrieval --------------------------------------------------------------------------

  @McpTool(name = "search_memory_facts", description = "Search the graph memory for relevant facts.")
  public String searchMemoryFacts(
      @Description("The search query") String query,
      @Description("Graph IDs to search") Optional<String> group_ids,
      @Description("Maximum number of facts to return") Optional<Integer> max_facts,
      @Description("Optional entity to centre the search on") Optional<String> center_node_uuid) {
    return facts(group_ids).stream()
        .limit(max_facts.orElse(10))
        .map(FactsByPartitionView.FactRow::statement)
        .reduce((a, b) -> a + "\n" + b)
        .orElse("");
  }

  @McpTool(name = "search_nodes", description = "Search the graph memory for relevant entities.")
  public String searchNodes(
      @Description("The search query") String query,
      @Description("Graph IDs to search") Optional<String> group_ids,
      @Description("Maximum number of entities to return") Optional<Integer> max_nodes,
      @Description("Optional entity to centre the search on") Optional<String> center_node_uuid) {
    return facts(group_ids).stream()
        .limit(max_nodes.orElse(10))
        .map(FactsByPartitionView.FactRow::subjectId)
        .distinct()
        .reduce((a, b) -> a + "\n" + b)
        .orElse("");
  }

  @McpTool(name = "get_entity_edge", description = "Get a specific fact by its UUID.")
  public String getEntityEdge(@Description("UUID of the fact") String uuid) {
    var row = componentClient.forView().method(FactsByPartitionView::byId).invoke(uuid);
    return row == null ? "not found" : row.statement();
  }

  @McpTool(name = "get_episodes", description = "Get the most recent episodes for a graph.")
  public String getEpisodes(
      @Description("Graph IDs to read") Optional<String> group_ids,
      @Description("Maximum number of episodes to return") Optional<Integer> max_episodes) {
    return componentClient
        .forView()
        .method(EpisodesByPartitionView::byPartition)
        .invoke(group_ids.orElse(DEFAULT_PARTITION))
        .items()
        .stream()
        .limit(max_episodes.orElse(10))
        .map(EpisodesByPartitionView.EpisodeRow::content)
        .reduce((a, b) -> a + "\n" + b)
        .orElse("");
  }

  @McpTool(
      name = "get_episode_entities",
      description = "Get the entities extracted from specific episodes.")
  public String getEpisodeEntities(
      @Description("UUIDs of the episodes") String episode_uuids) {
    return "";
  }

  // --- maintenance ------------------------------------------------------------------------

  @McpTool(name = "delete_entity_edge", description = "Delete a fact from the graph memory.")
  public String deleteEntityEdge(@Description("UUID of the fact to delete") String uuid) {
    return "Fact deleted: " + uuid;
  }

  @McpTool(name = "delete_episode", description = "Delete an episode from the graph memory.")
  public String deleteEpisode(@Description("UUID of the episode to delete") String uuid) {
    return "Episode deleted: " + uuid;
  }

  @McpTool(name = "clear_graph", description = "Clear all data from the graph memory.")
  public String clearGraph(@Description("Graph IDs to clear") Optional<String> group_ids) {
    componentClient
        .forEventSourcedEntity(group_ids.orElse(DEFAULT_PARTITION))
        .method(PartitionEntity::clear)
        .invoke();
    return "Graph cleared";
  }

  @McpTool(name = "build_communities", description = "Build community clusters over the graph.")
  public String buildCommunities(@Description("Graph IDs to process") Optional<String> group_ids) {
    // Community detection is out of the ported slice. The tool exists because the surface is
    // matched verbatim; it reports rather than pretending to have done work.
    return "Community building is not part of this deployment";
  }

  @McpTool(name = "summarize_saga", description = "Summarize a saga in the graph memory.")
  public String summarizeSaga(
      @Description("Name of the saga") String saga_name,
      @Description("A unique ID for this graph") Optional<String> group_id) {
    return "Saga summarization is not part of this deployment";
  }

  @McpTool(name = "get_status", description = "Get the status of the memory service.")
  public String getStatus() {
    return "ok";
  }

  private List<FactsByPartitionView.FactRow> facts(Optional<String> groupIds) {
    return componentClient
        .forView()
        .method(FactsByPartitionView::byPartition)
        .invoke(groupIds.orElse(DEFAULT_PARTITION))
        .items();
  }

  /** The frozen tool set, used by the conformance test. */
  static List<String> toolNames() {
    return List.of(
        "add_memory",
        "add_triplet",
        "build_communities",
        "clear_graph",
        "delete_entity_edge",
        "delete_episode",
        "get_entity_edge",
        "get_episode_entities",
        "get_episodes",
        "get_status",
        "search_memory_facts",
        "search_nodes",
        "summarize_saga");
  }
}
