package io.akka.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The deterministic stages of entity recognition, including the constants that are contract.
 *
 * <p>These are the checks that catch a port which implements the right <em>algorithm</em> with the
 * wrong <em>parameters</em> — a failure mode that passes every behavioural test while producing a
 * different graph.
 */
class EntityIdentityTest {

  @Test
  @DisplayName("normalisation: exact form lowercases and collapses whitespace")
  void exactNormalisation() {
    assertThat(EntityIdentity.normalizeExact("  Acme   Corporation ")).isEqualTo("acme corporation");
    assertThat(EntityIdentity.normalizeExact("ACME\tCorp")).isEqualTo("acme corp");
  }

  @Test
  @DisplayName("normalisation: fuzzy form keeps digits and apostrophes, drops other punctuation")
  void fuzzyNormalisation() {
    assertThat(EntityIdentity.normalizeFuzzy("O'Brien & Sons, Ltd.")).isEqualTo("o'brien sons ltd");
    assertThat(EntityIdentity.normalizeFuzzy("Route 66!")).isEqualTo("route 66");
  }

  @Test
  @DisplayName("the entropy gate rejects most single-word names on LENGTH, not entropy")
  void entropyGateRejectsOnLength() {
    // Each of these clears the entropy bar comfortably and is still rejected.
    for (String name : new String[] {"Alice", "Acme", "IBM", "Bob"}) {
      String fuzzy = EntityIdentity.normalizeFuzzy(name);
      assertThat(EntityIdentity.entropy(fuzzy))
          .as(name + " entropy")
          .isGreaterThan(0.9);
      assertThat(EntityIdentity.passesEntropyGate(fuzzy))
          .as(name + " should be rejected by the gate")
          .isFalse();
    }
    // Multi-token or long names pass.
    assertThat(EntityIdentity.passesEntropyGate(EntityIdentity.normalizeFuzzy("Alice Johnson")))
        .isTrue();
    assertThat(EntityIdentity.passesEntropyGate(EntityIdentity.normalizeFuzzy("Northwind")))
        .isTrue();
  }

  @Test
  @DisplayName("short names: one character is matchable, two characters are not")
  void shortNameAsymmetryIsReproduced() {
    assertThat(EntityIdentity.shingles("a")).containsExactly("a");
    assertThat(EntityIdentity.shingles("ab")).isEmpty();
    assertThat(EntityIdentity.minhashSignature(EntityIdentity.shingles("a"))).hasSize(32);
    assertThat(EntityIdentity.minhashSignature(EntityIdentity.shingles("ab"))).isEmpty();
    assertThat(EntityIdentity.bands(EntityIdentity.minhashSignature(EntityIdentity.shingles("a"))))
        .hasSize(8);
  }

  @Test
  @DisplayName("two empty shingle sets score 1.0 — the convention that cancels the quirk above")
  void emptySetsScorePerfect() {
    assertThat(EntityIdentity.jaccard(Set.of(), Set.of())).isEqualTo(1.0);
    assertThat(EntityIdentity.jaccard(Set.of("abc"), Set.of())).isEqualTo(0.0);
  }

  @Test
  @DisplayName("shingling is 3-grams over the space-stripped name")
  void shingling() {
    assertThat(EntityIdentity.shingles("abcd")).containsExactlyInAnyOrder("abc", "bcd");
    assertThat(EntityIdentity.shingles("xi ja")).containsExactlyInAnyOrder("xij", "ija");
  }

  @Test
  @DisplayName("the hash is BLAKE2b over \"{seed}:{shingle}\", big-endian — all of it is contract")
  void hashIsPinned() {
    long h = EntityIdentity.hashShingle("abc", 0);
    // Pinned against the source implementation. If this value moves, the graph moves with it.
    assertThat(Long.toHexString(h)).isEqualTo("6aefad96f5c63487");
  }

  @Test
  @DisplayName("constants match the specification")
  void constantsArePinned() {
    assertThat(EntityIdentity.MINHASH_PERMUTATIONS).isEqualTo(32);
    assertThat(EntityIdentity.MINHASH_BAND_SIZE).isEqualTo(4);
    assertThat(EntityIdentity.JACCARD_THRESHOLD).isCloseTo(0.9, within(1e-9));
    assertThat(EntityIdentity.ENTROPY_THRESHOLD).isCloseTo(1.5, within(1e-9));
    assertThat(EntityIdentity.MIN_NAME_LENGTH).isEqualTo(6);
    assertThat(EntityIdentity.MIN_TOKEN_COUNT).isEqualTo(2);
  }

  @Test
  @DisplayName("a near-duplicate above threshold collides in at least one band")
  void nearDuplicatesCollide() {
    var a = EntityIdentity.shingles(EntityIdentity.normalizeFuzzy("Springfield Nuclear Power Plant"));
    var b = EntityIdentity.shingles(EntityIdentity.normalizeFuzzy("Springfield Nuclear Power Plants"));

    assertThat(EntityIdentity.jaccard(a, b)).isGreaterThanOrEqualTo(EntityIdentity.JACCARD_THRESHOLD);
    assertThat(
            EntityIdentity.bandsCollide(
                EntityIdentity.minhashSignature(a), EntityIdentity.minhashSignature(b)))
        .isTrue();
  }
}
