package io.akka.memory.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.mcp.McpEndpoint;
import akka.javasdk.annotations.mcp.McpTool;
import akka.javasdk.client.ComponentClient;
import io.akka.memory.application.EpisodeIngestWorkflow;
import io.akka.memory.application.FlureeStore;
import io.akka.memory.application.PartitionEntity;
import io.akka.memory.application.RetrievalService;
import io.akka.memory.domain.Entity;
import io.akka.memory.domain.Episode;
import io.akka.memory.domain.Fact;
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
  private final FlureeStore store;
  private final RetrievalService retrieval;

  public MemoryMcpEndpoint(
      ComponentClient componentClient, FlureeStore store, RetrievalService retrieval) {
    this.componentClient = componentClient;
    this.store = store;
    this.retrieval = retrieval;
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

  /**
   * Writes a fact with no extraction and no model call.
   *
   * <p>The two named entities are created if they are not already present. The fact carries no
   * validity interval, which makes it permanently open and unable to close anything — the same
   * outcome an extracted fact reaches when its dates cannot be determined.
   */
  @McpTool(
      name = "add_triplet",
      description = "Add a fact directly to the graph, bypassing extraction.")
  public String addTriplet(
      @Description("Name of the source entity") String source_node_name,
      @Description("Name of the relationship") String edge_name,
      @Description("The fact as a sentence") String fact,
      @Description("Name of the target entity") String target_node_name,
      @Description("A unique ID for this graph") Optional<String> group_id) {

    String partition = group_id.orElse(DEFAULT_PARTITION);
    var source = Entity.create(UUID.randomUUID().toString(), partition, source_node_name, Entity.BASE_TYPE);
    var target = Entity.create(UUID.randomUUID().toString(), partition, target_node_name, Entity.BASE_TYPE);

    componentClient
        .forEventSourcedEntity(partition)
        .method(PartitionEntity::recordEntities)
        .invoke(new PartitionEntity.RecordEntities(List.of(source, target)));

    var now = Instant.now();
    var triplet =
        new Fact(
            UUID.randomUUID().toString(),
            partition,
            source.id(),
            target.id(),
            edge_name,
            fact,
            Optional.empty(),
            Optional.empty(),
            now,
            Optional.empty(),
            List.of());

    componentClient
        .forEventSourcedEntity(partition)
        .method(PartitionEntity::recordFacts)
        .invoke(new PartitionEntity.RecordFacts(List.of(triplet), now));

    return "Triplet added: " + source_node_name + " " + edge_name + " " + target_node_name;
  }

  // --- retrieval --------------------------------------------------------------------------

  @McpTool(name = "search_memory_facts", description = "Search the graph memory for relevant facts.")
  public String searchMemoryFacts(
      @Description("The search query") String query,
      @Description("Graph IDs to search") Optional<String> group_ids,
      @Description("Maximum number of facts to return") Optional<Integer> max_facts,
      @Description("Optional entity to centre the search on") Optional<String> center_node_uuid) {
    return search(query, group_ids, max_facts, center_node_uuid).stream()
        .map(FlureeStore.StoredFact::statement)
        .reduce((a, b) -> a + "\n" + b)
        .orElse("");
  }

  @McpTool(name = "search_nodes", description = "Search the graph memory for relevant entities.")
  public String searchNodes(
      @Description("The search query") String query,
      @Description("Graph IDs to search") Optional<String> group_ids,
      @Description("Maximum number of entities to return") Optional<Integer> max_nodes,
      @Description("Optional entity to centre the search on") Optional<String> center_node_uuid) {
    var partition = group_ids.orElse(DEFAULT_PARTITION);
    var names = new java.util.HashMap<String, String>();
    store.entitiesInPartition(partition).forEach(e -> names.put(e.entityId(), e.name()));

    // Entities are reached through the facts that mention them, so the fact ranking decides the
    // entity order. An entity named by a better-ranked fact comes first.
    return search(query, group_ids, max_nodes, center_node_uuid).stream()
        .flatMap(fact -> java.util.stream.Stream.of(fact.subjectId(), fact.objectId()))
        .distinct()
        .map(id -> names.getOrDefault(id, id))
        .limit(max_nodes.orElse(RetrievalService.DEFAULT_SEARCH_LIMIT))
        .reduce((a, b) -> a + "\n" + b)
        .orElse("");
  }

  @McpTool(name = "get_entity_edge", description = "Get a specific fact by its UUID.")
  public String getEntityEdge(@Description("UUID of the fact") String uuid) {
    return store.factById(uuid).map(FlureeStore.StoredFact::statement).orElse("not found");
  }

  @McpTool(name = "get_episodes", description = "Get the most recent episodes for a graph.")
  public String getEpisodes(
      @Description("Graph IDs to read") Optional<String> group_ids,
      @Description("Maximum number of episodes to return") Optional<Integer> max_episodes) {
    return store.episodesInPartition(group_ids.orElse(DEFAULT_PARTITION)).stream()
        .sorted(
            java.util.Comparator.comparing(
                FlureeStore.StoredEpisode::referenceTime,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
        .limit(max_episodes.orElse(RetrievalService.DEFAULT_SEARCH_LIMIT))
        .map(FlureeStore.StoredEpisode::content)
        .reduce((a, b) -> a + "\n" + b)
        .orElse("");
  }

  /** The entities named by facts attributed to the given episodes. */
  @McpTool(
      name = "get_episode_entities",
      description = "Get the entities extracted from specific episodes.")
  public String getEpisodeEntities(@Description("UUIDs of the episodes") String episode_uuids) {
    var wanted = List.of(episode_uuids.split("[,\\s]+"));
    var names = new java.util.LinkedHashSet<String>();
    for (String episodeId : wanted) {
      store
          .episodePartition(episodeId)
          .ifPresent(
              partition -> {
                var byId = new java.util.HashMap<String, String>();
                store.entitiesInPartition(partition).forEach(e -> byId.put(e.entityId(), e.name()));
                store.factsInPartition(partition).stream()
                    .filter(fact -> fact.episodeIds().contains(episodeId))
                    .forEach(
                        fact -> {
                          names.add(byId.getOrDefault(fact.subjectId(), fact.subjectId()));
                          names.add(byId.getOrDefault(fact.objectId(), fact.objectId()));
                        });
              });
    }
    return String.join("\n", names);
  }

  // --- maintenance ------------------------------------------------------------------------

  @McpTool(name = "delete_entity_edge", description = "Delete a fact from the graph memory.")
  public String deleteEntityEdge(@Description("UUID of the fact to delete") String uuid) {
    store
        .factById(uuid)
        .ifPresent(
            fact ->
                componentClient
                    .forEventSourcedEntity(fact.partition())
                    .method(PartitionEntity::removeFact)
                    .invoke(uuid));
    return "Fact deleted: " + uuid;
  }

  @McpTool(name = "delete_episode", description = "Delete an episode from the graph memory.")
  public String deleteEpisode(@Description("UUID of the episode to delete") String uuid) {
    store
        .episodePartition(uuid)
        .ifPresent(
            partition ->
                componentClient
                    .forEventSourcedEntity(partition)
                    .method(PartitionEntity::removeEpisode)
                    .invoke(uuid));
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

  private List<FlureeStore.StoredFact> search(
      String query,
      Optional<String> groupIds,
      Optional<Integer> limit,
      Optional<String> centreNodeUuid) {
    return retrieval
        .search(
            groupIds.orElse(DEFAULT_PARTITION),
            query,
            limit.orElse(RetrievalService.DEFAULT_SEARCH_LIMIT),
            centreNodeUuid.orElse(null))
        .facts();
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
