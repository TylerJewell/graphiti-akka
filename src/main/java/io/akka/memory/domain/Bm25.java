package io.akka.memory.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lexical ranking over a set of statements — the full-text half of hybrid retrieval.
 *
 * <p>Okapi BM25 with the parameters the source system inherits from its search index: {@code k1 =
 * 1.2}, {@code b = 0.75}. Those are the Lucene defaults, which is what the source's index uses; the
 * source never sets them itself, so matching the index's defaults is what matching the source
 * means.
 *
 * <p>Only the resulting <em>order</em> reaches the fused ranking — rank fusion consumes positions,
 * not scores — so a scoring difference matters here only where it changes which document sorts
 * above which.
 */
public final class Bm25 {

  private static final double K1 = 1.2;
  private static final double B = 0.75;

  private Bm25() {}

  /** Document ids ordered by descending score; documents scoring zero are omitted. */
  public static List<String> rank(String query, Map<String, String> documents) {
    List<String> queryTerms = tokenize(query);
    if (queryTerms.isEmpty() || documents.isEmpty()) {
      return List.of();
    }

    // Insertion-ordered, so the stable sort below leaves equally-scoring documents in the order the
    // caller supplied. A HashMap here would make ties depend on hash order, which is not an order.
    var tokenized = new LinkedHashMap<String, List<String>>();
    double totalLength = 0;
    for (var entry : documents.entrySet()) {
      var terms = tokenize(entry.getValue());
      tokenized.put(entry.getKey(), terms);
      totalLength += terms.size();
    }
    double averageLength = totalLength / documents.size();

    var documentFrequency = new HashMap<String, Integer>();
    for (String term : queryTerms) {
      int count = 0;
      for (List<String> terms : tokenized.values()) {
        if (terms.contains(term)) {
          count++;
        }
      }
      documentFrequency.put(term, count);
    }

    int total = documents.size();
    var scored = new ArrayList<Map.Entry<String, Double>>();
    for (var entry : tokenized.entrySet()) {
      double score = 0;
      List<String> terms = entry.getValue();
      for (String term : queryTerms) {
        int frequency = (int) terms.stream().filter(term::equals).count();
        if (frequency == 0) {
          continue;
        }
        int containing = documentFrequency.getOrDefault(term, 0);
        double idf = Math.log(1 + (total - containing + 0.5) / (containing + 0.5));
        double denominator = frequency + K1 * (1 - B + B * terms.size() / averageLength);
        score += idf * (frequency * (K1 + 1)) / denominator;
      }
      if (score > 0) {
        scored.add(Map.entry(entry.getKey(), score));
      }
    }

    // Stable sort: documents scoring equally keep the order the caller supplied them in, which is
    // the same tie discipline rank fusion applies downstream.
    scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
    return scored.stream().map(Map.Entry::getKey).toList();
  }

  private static List<String> tokenize(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    var out = new ArrayList<String>();
    for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
      if (!token.isEmpty()) {
        out.add(token);
      }
    }
    return out;
  }
}
