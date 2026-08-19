package io.akka.memory;

import io.akka.memory.domain.EntityIdentity;
import io.akka.memory.domain.Fact;
import io.akka.memory.domain.RankFusion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Measures the ported deterministic core and emits a machine-readable result.
 *
 * <p>Three workloads, chosen because they are the parts that are genuinely comparable: the same
 * inputs run through the same rules in both systems, with no model, no store and no network to
 * blur the measurement. The <b>checksums matter more than the timings</b> — they are what proves
 * the two implementations agree before anyone compares their speed.
 *
 * <p>Corpora are generated from fixed seeds so the source-language runner can reproduce them
 * exactly. A timing comparison over different inputs would be meaningless.
 */
class BenchmarkRunner {

  private static final int WARMUP = 20_000;
  private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");

  @Test
  @DisplayName("benchmark the deterministic core and write results for cross-language comparison")
  void runBenchmark() throws IOException {
    var results = new LinkedHashMap<String, Map<String, Object>>();
    results.put("invalidation", invalidation());
    results.put("identity", identity());
    results.put("fusion", fusion());

    var out = new StringBuilder("{\n");
    out.append("  \"runtime\": \"java\",\n");
    out.append("  \"version\": \"").append(System.getProperty("java.version")).append("\",\n");
    out.append("  \"workloads\": {\n");
    var names = new ArrayList<>(results.keySet());
    for (int i = 0; i < names.size(); i++) {
      var name = names.get(i);
      var r = results.get(name);
      out.append("    \"").append(name).append("\": {");
      out.append("\"ops\": ").append(r.get("ops")).append(", ");
      out.append("\"checksum\": ").append(r.get("checksum")).append(", ");
      out.append("\"nanos_per_op\": ").append(r.get("nanos_per_op"));
      out.append("}").append(i < names.size() - 1 ? "," : "").append("\n");
    }
    out.append("  }\n}\n");

    Path target = Path.of("target", "bench-java.json");
    Files.createDirectories(target.getParent());
    Files.writeString(target, out.toString());
    System.out.println(out);
  }

  /** Every temporal configuration, repeated. Checksum = how many close. */
  private Map<String, Object> invalidation() {
    List<Optional<Instant>> domain =
        List.of(
            Optional.empty(),
            Optional.of(BASE),
            Optional.of(BASE.plusSeconds(86400)),
            Optional.of(BASE.plusSeconds(172800)));

    var pairs = new ArrayList<Fact[]>();
    for (var cf : domain) {
      for (var cu : domain) {
        for (var nf : domain) {
          for (var nu : domain) {
            pairs.add(new Fact[] {fact("c", cf, cu), fact("n", nf, nu)});
          }
        }
      }
    }

    for (int i = 0; i < WARMUP; i++) {
      pairs.get(i % pairs.size())[1].closes(pairs.get(i % pairs.size())[0]);
    }

    int rounds = 400;
    long checksum = 0;
    long start = System.nanoTime();
    for (int r = 0; r < rounds; r++) {
      for (Fact[] pair : pairs) {
        if (pair[1].closes(pair[0])) {
          checksum++;
        }
      }
    }
    long elapsed = System.nanoTime() - start;
    long ops = (long) rounds * pairs.size();
    return metrics(ops, checksum / rounds, elapsed);
  }

  /** Name pairs through shingling, MinHash and Jaccard. Checksum = how many meet the threshold. */
  private Map<String, Object> identity() {
    var names = new ArrayList<String>();
    for (int i = 0; i < 200; i++) {
      names.add("Northwind Traders Division " + i);
      names.add("Northwind Traders Divison " + i); // one transposed character
    }

    for (int i = 0; i < 200; i++) {
      EntityIdentity.minhashSignature(
          EntityIdentity.shingles(EntityIdentity.normalizeFuzzy(names.get(i % names.size()))));
    }

    long checksum = 0;
    long start = System.nanoTime();
    for (int i = 0; i + 1 < names.size(); i += 2) {
      var a = EntityIdentity.shingles(EntityIdentity.normalizeFuzzy(names.get(i)));
      var b = EntityIdentity.shingles(EntityIdentity.normalizeFuzzy(names.get(i + 1)));
      EntityIdentity.minhashSignature(a);
      EntityIdentity.minhashSignature(b);
      if (EntityIdentity.jaccard(a, b) >= EntityIdentity.JACCARD_THRESHOLD) {
        checksum++;
      }
    }
    long elapsed = System.nanoTime() - start;
    return metrics(names.size() / 2L, checksum, elapsed);
  }

  /** Rank fusion over three disagreeing lists. Checksum = a digest of the winning order. */
  private Map<String, Object> fusion() {
    var lists = new ArrayList<List<String>>();
    var a = new ArrayList<String>();
    var b = new ArrayList<String>();
    var c = new ArrayList<String>();
    for (int i = 0; i < 100; i++) {
      a.add("id" + i);
      b.add("id" + (99 - i));
      c.add("id" + ((i * 7) % 100));
    }
    lists.add(a);
    lists.add(b);
    lists.add(c);

    for (int i = 0; i < 2000; i++) {
      RankFusion.fuse(lists);
    }

    int rounds = 20_000;
    long start = System.nanoTime();
    List<RankFusion.Scored> last = null;
    for (int r = 0; r < rounds; r++) {
      last = RankFusion.fuse(lists);
    }
    long elapsed = System.nanoTime() - start;

    // Order-sensitive digest: position matters, so a tie-break difference changes it.
    long checksum = 0;
    for (int i = 0; i < last.size(); i++) {
      checksum = (checksum * 31 + last.get(i).id().hashCode() + i) % 1_000_003;
    }
    return metrics(rounds, Math.abs(checksum), elapsed);
  }

  private static Map<String, Object> metrics(long ops, long checksum, long elapsedNanos) {
    return Map.of("ops", ops, "checksum", checksum, "nanos_per_op", elapsedNanos / Math.max(ops, 1));
  }

  private static Fact fact(String id, Optional<Instant> from, Optional<Instant> until) {
    return new Fact(
        id, "bench", "s", "o", "r", id, from, until, BASE, Optional.empty(), List.of());
  }
}
