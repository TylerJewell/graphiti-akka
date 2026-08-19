package io.akka.memory.application;

import io.akka.memory.application.FlureeStore.StoredFact;
import io.akka.memory.domain.Bm25;
import io.akka.memory.domain.RankFusion;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hybrid retrieval over the read projection.
 *
 * <p>Two candidate lists are produced independently — lexical, then vector similarity, in that
 * order, because rank fusion resolves ties by first-seen order and the order the lists are built in
 * is therefore part of the answer. Each list is drawn at twice the requested limit before fusion,
 * and the fused list is truncated to the limit at the very end.
 *
 * <p>Naming a centre entity does not filter the results; it replaces the ranking. Fusion still runs
 * to produce the candidate order, and the result is then regrouped by how far each fact's subject
 * sits from the centre. That substitution is silent in the source system and is reproduced here.
 *
 * <p>This reads the projection only. The write path is never queried to serve a read (RENDER-001
 * §3.4).
 */
public final class RetrievalService {

  /** Results returned per search. */
  public static final int DEFAULT_SEARCH_LIMIT = 10;

  /** Cosine floor on vector search. In {@code [-1, 1]}. */
  public static final double DEFAULT_MIN_SCORE = 0.6;

  /**
   * Fusion-score floor. In {@code (0, n]} — a different quantity on a different scale from {@link
   * #DEFAULT_MIN_SCORE}, and zero by default, so no fusion filtering happens unless asked for.
   */
  public static final double RERANKER_MIN_SCORE = 0;

  private final FlureeStore store;
  private final Embedder embedder;

  public RetrievalService(FlureeStore store, Embedder embedder) {
    this.store = store;
    this.embedder = embedder;
  }

  /** What a search returned, and on how much evidence. */
  public record Results(List<StoredFact> facts, boolean vectorListIncluded) {}

  public Results search(String partition, String query, int limit, String centreEntityId) {
    int effectiveLimit = limit > 0 ? limit : DEFAULT_SEARCH_LIMIT;
    int candidateLimit = 2 * effectiveLimit;

    List<StoredFact> corpus = store.factsInPartition(partition);
    if (corpus.isEmpty()) {
      return new Results(List.of(), false);
    }

    var byId = new LinkedHashMap<String, StoredFact>();
    corpus.forEach(fact -> byId.put(fact.factId(), fact));

    List<String> lexical = lexicalList(query, corpus, candidateLimit);
    float[] queryVector = embedder.embed(query);
    List<String> similar = similarityList(queryVector, corpus, candidateLimit);

    var lists = new ArrayList<List<String>>();
    lists.add(lexical);
    if (queryVector != null) {
      lists.add(similar);
    }

    List<String> fused =
        RankFusion.fuse(lists, RERANKER_MIN_SCORE).stream().map(RankFusion.Scored::id).toList();

    List<String> ranked =
        centreEntityId == null || centreEntityId.isBlank()
            ? fused
            : byDistanceFromCentre(fused, byId, corpus, centreEntityId);

    List<StoredFact> out =
        ranked.stream().map(byId::get).filter(java.util.Objects::nonNull).limit(effectiveLimit)
            .toList();
    return new Results(out, queryVector != null);
  }

  private static List<String> lexicalList(String query, List<StoredFact> corpus, int limit) {
    var documents = new LinkedHashMap<String, String>();
    corpus.forEach(fact -> documents.put(fact.factId(), fact.statement()));
    return Bm25.rank(query, documents).stream().limit(limit).toList();
  }

  private static List<String> similarityList(
      float[] queryVector, List<StoredFact> corpus, int limit) {
    if (queryVector == null) {
      return List.of();
    }
    record Scored(String id, double similarity) {}
    var scored = new ArrayList<Scored>();
    for (StoredFact fact : corpus) {
      double similarity = Embedder.cosine(queryVector, fact.embedding());
      if (similarity >= DEFAULT_MIN_SCORE) {
        scored.add(new Scored(fact.factId(), similarity));
      }
    }
    scored.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));
    return scored.stream().map(Scored::id).limit(limit).toList();
  }

  /**
   * Regroups an already-fused order by graph distance from a centre entity.
   *
   * <p>Distance is binary in the source system, not a true shortest path: the centre first, then
   * everything directly connected to it, then everything else — with the fused order preserved
   * inside each group. Reproducing that means reproducing a coarser ordering than the name
   * "distance" suggests.
   */
  private static List<String> byDistanceFromCentre(
      List<String> fused,
      Map<String, StoredFact> byId,
      List<StoredFact> corpus,
      String centreEntityId) {

    var factsBySubject = new LinkedHashMap<String, List<String>>();
    for (String factId : fused) {
      StoredFact fact = byId.get(factId);
      if (fact != null) {
        factsBySubject.computeIfAbsent(fact.subjectId(), key -> new ArrayList<>()).add(factId);
      }
    }

    Set<String> neighbours = neighboursOf(centreEntityId, corpus);

    var centre = new ArrayList<String>();
    var adjacent = new ArrayList<String>();
    var rest = new ArrayList<String>();
    for (var entry : factsBySubject.entrySet()) {
      if (entry.getKey().equals(centreEntityId)) {
        centre.addAll(entry.getValue());
      } else if (neighbours.contains(entry.getKey())) {
        adjacent.addAll(entry.getValue());
      } else {
        rest.addAll(entry.getValue());
      }
    }

    var out = new LinkedHashSet<String>();
    out.addAll(centre);
    out.addAll(adjacent);
    out.addAll(rest);
    return List.copyOf(out);
  }

  /** Entities one relation away from the centre, in either direction. */
  private static Set<String> neighboursOf(String centreEntityId, List<StoredFact> corpus) {
    var out = new HashSet<String>();
    for (StoredFact fact : corpus) {
      if (centreEntityId.equals(fact.subjectId())) {
        out.add(fact.objectId());
      } else if (centreEntityId.equals(fact.objectId())) {
        out.add(fact.subjectId());
      }
    }
    return out;
  }
}
