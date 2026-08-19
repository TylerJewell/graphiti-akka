package io.akka.memory.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal rank fusion, as the source system performs it.
 *
 * <p>{@code score(id) = SUM over result lists of 1 / (zeroBasedRank + RANK_CONST)}.
 *
 * <p><b>{@code RANK_CONST} is 1, where the literature standard is 60.</b> The difference is
 * behavioural, not cosmetic: at 1 a single top hit outranks several mid-list hits; at 60 it loses to
 * them. A port that reaches for a stock implementation gets 60 and reranks differently on every
 * asymmetric query.
 *
 * <p><b>Ties resolve to first-seen order</b> across the input lists. That is not incidental — the
 * source accumulates into an insertion-ordered map and sorts stably. An implementation backed by an
 * unordered map, or an unstable sort, silently reorders every tie.
 */
public final class RankFusion {

  public static final int RANK_CONST = 1;
  public static final double DEFAULT_MIN_SCORE = 0.0;

  private RankFusion() {}

  public record Scored(String id, double score) {}

  public static List<Scored> fuse(List<List<String>> resultLists) {
    return fuse(resultLists, DEFAULT_MIN_SCORE);
  }

  /**
   * @param minScore the floor on the <em>fused</em> score. Defaults to zero, so nothing is dropped
   *     unless a caller asks — this is a different field, on a different scale, from the cosine
   *     floor applied to vector search, and the two are easy to conflate.
   */
  public static List<Scored> fuse(List<List<String>> resultLists, double minScore) {
    // LinkedHashMap is load-bearing: it preserves first-seen order, which is the tie-break.
    Map<String, Double> scores = new LinkedHashMap<>();
    for (List<String> list : resultLists) {
      for (int rank = 0; rank < list.size(); rank++) {
        scores.merge(list.get(rank), 1.0 / (rank + RANK_CONST), Double::sum);
      }
    }
    List<Scored> out = new ArrayList<>(scores.size());
    scores.forEach((id, score) -> out.add(new Scored(id, score)));
    // List.sort is stable, so equal scores keep insertion order.
    out.sort((a, b) -> Double.compare(b.score(), a.score()));
    out.removeIf(s -> s.score() < minScore);
    return out;
  }
}
