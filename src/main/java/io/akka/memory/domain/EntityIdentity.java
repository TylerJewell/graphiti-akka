package io.akka.memory.domain;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.bouncycastle.crypto.digests.Blake2bDigest;

/**
 * The deterministic stages of entity recognition.
 *
 * <p>Identity is not a field — there is no natural key. It is the outcome of a four-stage cascade,
 * and the same two names can resolve differently depending on what else exists and what the
 * candidate search returned.
 *
 * <p><b>Every constant here is contract, not a tuning choice.</b> Substituting the hash, its byte
 * order, the seed range or the band size computes different candidate sets and produces a different
 * graph — while every behavioural test still passes.
 */
public final class EntityIdentity {

  public static final int MIN_NAME_LENGTH = 6;
  public static final int MIN_TOKEN_COUNT = 2;
  public static final double ENTROPY_THRESHOLD = 1.5;
  public static final double JACCARD_THRESHOLD = 0.9;
  public static final int MINHASH_PERMUTATIONS = 32;
  public static final int MINHASH_BAND_SIZE = 4;
  private static final int SHINGLE_SIZE = 3;

  private EntityIdentity() {}

  /** Lowercase, collapse whitespace runs to one space, trim. */
  public static String normalizeExact(String name) {
    return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
  }

  /** The exact form, then every character outside {@code [a-z0-9' ]} replaced by a space. */
  public static String normalizeFuzzy(String name) {
    return normalizeExact(name).replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ").trim();
  }

  /** Shannon entropy over characters, spaces removed, base 2. */
  public static double entropy(String normalized) {
    String cleaned = normalized.replace(" ", "");
    if (cleaned.isEmpty()) {
      return 0.0;
    }
    int[] counts = new int[Character.MAX_VALUE + 1];
    for (char c : cleaned.toCharArray()) {
      counts[c]++;
    }
    double total = cleaned.length();
    double entropy = 0.0;
    for (int count : counts) {
      if (count > 0) {
        double p = count / total;
        entropy -= p * (Math.log(p) / Math.log(2));
      }
    }
    return entropy;
  }

  /**
   * {@code (length >= 6 OR tokens >= 2) AND entropy >= 1.5}.
   *
   * <p>The name suggests entropy is the discriminator; in practice <b>length</b> is. Most
   * single-word entity names clear the entropy bar and fail on length, so the deterministic fuzzy
   * path effectively serves only multi-token or long names — everything else escalates to a model.
   */
  public static boolean passesEntropyGate(String fuzzyNormalized) {
    int tokens = fuzzyNormalized.isEmpty() ? 0 : fuzzyNormalized.split(" ").length;
    if (fuzzyNormalized.length() < MIN_NAME_LENGTH && tokens < MIN_TOKEN_COUNT) {
      return false;
    }
    return entropy(fuzzyNormalized) >= ENTROPY_THRESHOLD;
  }

  /**
   * 3-gram shingles over the space-stripped name.
   *
   * <p>Note the asymmetry, which is reproduced rather than corrected: a <b>one</b>-character name
   * yields a single shingle and behaves normally, while a <b>two</b>-character name yields none and
   * so is never offered as a fuzzy candidate. Correcting this alone would make every very short
   * name a perfect match for every other, because two empty shingle sets score 1.0.
   */
  public static Set<String> shingles(String fuzzyNormalized) {
    String cleaned = fuzzyNormalized.replace(" ", "");
    Set<String> out = new LinkedHashSet<>();
    if (cleaned.length() < 2) {
      if (!cleaned.isEmpty()) {
        out.add(cleaned);
      }
      return out;
    }
    for (int i = 0; i + SHINGLE_SIZE <= cleaned.length(); i++) {
      out.add(cleaned.substring(i, i + SHINGLE_SIZE));
    }
    return out;
  }

  /** BLAKE2b over {@code "{seed}:{shingle}"}, 8-byte digest, read <b>big-endian</b>. */
  public static long hashShingle(String shingle, int seed) {
    Blake2bDigest digest = new Blake2bDigest(64);
    byte[] input = (seed + ":" + shingle).getBytes(StandardCharsets.UTF_8);
    digest.update(input, 0, input.length);
    byte[] out = new byte[8];
    digest.doFinal(out, 0);
    long value = 0;
    for (byte b : out) {
      value = (value << 8) | (b & 0xFFL);
    }
    return value;
  }

  /** Per seed 0..31, the minimum hash across the shingle set. Empty in, empty out. */
  public static long[] minhashSignature(Set<String> shingles) {
    if (shingles.isEmpty()) {
      return new long[0];
    }
    long[] signature = new long[MINHASH_PERMUTATIONS];
    for (int seed = 0; seed < MINHASH_PERMUTATIONS; seed++) {
      long min = Long.MAX_VALUE;
      for (String shingle : shingles) {
        long h = hashShingle(shingle, seed);
        // unsigned comparison — the digest is read as an unsigned 64-bit value
        if (Long.compareUnsigned(h, min) < 0) {
          min = h;
        }
      }
      signature[seed] = min;
    }
    return signature;
  }

  /** Consecutive groups of {@value #MINHASH_BAND_SIZE}; a trailing partial band is discarded. */
  public static java.util.List<long[]> bands(long[] signature) {
    java.util.List<long[]> out = new java.util.ArrayList<>();
    for (int start = 0; start + MINHASH_BAND_SIZE <= signature.length; start += MINHASH_BAND_SIZE) {
      out.add(Arrays.copyOfRange(signature, start, start + MINHASH_BAND_SIZE));
    }
    return out;
  }

  /** Whether two signatures collide in at least one band at the same band index. */
  public static boolean bandsCollide(long[] a, long[] b) {
    var ba = bands(a);
    var bb = bands(b);
    for (int i = 0; i < Math.min(ba.size(), bb.size()); i++) {
      if (Arrays.equals(ba.get(i), bb.get(i))) {
        return true;
      }
    }
    return false;
  }

  /** Jaccard similarity. Two empty sets score {@code 1.0}, by the source's own convention. */
  public static double jaccard(Set<String> a, Set<String> b) {
    if (a.isEmpty() && b.isEmpty()) {
      return 1.0;
    }
    if (a.isEmpty() || b.isEmpty()) {
      return 0.0;
    }
    Set<String> intersection = new HashSet<>(a);
    intersection.retainAll(b);
    Set<String> union = new HashSet<>(a);
    union.addAll(b);
    return (double) intersection.size() / union.size();
  }
}
