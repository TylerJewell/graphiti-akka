package io.akka.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link FactInvalidationTest}'s lattice <em>can fail</em>.
 *
 * <p>A test that cannot fail proves nothing, and the invalidation predicate is the one place in
 * this service where a silently-wrong implementation would pass everything else. So the predicate
 * is mutated — a comparison flipped, a guard dropped — and every mutant must be caught by at least
 * one configuration in the same 256-case lattice.
 *
 * <p>A surviving mutant means the lattice does not constrain that clause, and the clause is
 * therefore unverified regardless of how green the suite looks.
 */
class FactInvalidationMutationTest {

  private static final Instant T0 = Instant.parse("2024-01-01T00:00:00Z");
  private static final Instant T1 = Instant.parse("2024-01-02T00:00:00Z");
  private static final Instant T2 = Instant.parse("2024-01-03T00:00:00Z");
  private static final List<Optional<Instant>> DOMAIN =
      List.of(Optional.empty(), Optional.of(T0), Optional.of(T1), Optional.of(T2));

  private record Config(
      Optional<Instant> cFrom,
      Optional<Instant> cUntil,
      Optional<Instant> nFrom,
      Optional<Instant> nUntil) {}

  private static List<Config> lattice() {
    List<Config> out = new ArrayList<>();
    for (var a : DOMAIN) {
      for (var b : DOMAIN) {
        for (var c : DOMAIN) {
          for (var d : DOMAIN) {
            out.add(new Config(a, b, c, d));
          }
        }
      }
    }
    return out;
  }

  private static Fact fact(String id, Optional<Instant> from, Optional<Instant> until) {
    return new Fact(id, "p", "s", "o", "r", id, from, until, T0, Optional.empty(), List.of());
  }

  // --- mutants: each flips exactly one decision the real predicate makes -------------------

  /** M1 — ties close. */
  private static boolean tiesClose(Config k) {
    if (k.cFrom().isEmpty() || k.nFrom().isEmpty()) return false;
    if (k.cUntil().isPresent() && !k.cUntil().get().isAfter(k.nFrom().get())) return false;
    if (k.nUntil().isPresent() && !k.nUntil().get().isAfter(k.cFrom().get())) return false;
    return !k.cFrom().get().isAfter(k.nFrom().get()); // <= instead of <
  }

  /** M2 — direction reversed. */
  private static boolean reversed(Config k) {
    if (k.cFrom().isEmpty() || k.nFrom().isEmpty()) return false;
    if (k.cUntil().isPresent() && !k.cUntil().get().isAfter(k.nFrom().get())) return false;
    if (k.nUntil().isPresent() && !k.nUntil().get().isAfter(k.cFrom().get())) return false;
    return k.cFrom().get().isAfter(k.nFrom().get());
  }

  /** M3 — an absent candidate start no longer blocks. */
  private static boolean noCandidateGuard(Config k) {
    if (k.nFrom().isEmpty()) return false;
    if (k.cUntil().isPresent() && !k.cUntil().get().isAfter(k.nFrom().get())) return false;
    if (k.cFrom().isEmpty()) return true;
    if (k.nUntil().isPresent() && !k.nUntil().get().isAfter(k.cFrom().get())) return false;
    return k.cFrom().get().isBefore(k.nFrom().get());
  }

  /** M4 — an absent incoming start no longer blocks. */
  private static boolean noIncomingGuard(Config k) {
    if (k.cFrom().isEmpty()) return false;
    if (k.nFrom().isEmpty()) return true;
    if (k.cUntil().isPresent() && !k.cUntil().get().isAfter(k.nFrom().get())) return false;
    if (k.nUntil().isPresent() && !k.nUntil().get().isAfter(k.cFrom().get())) return false;
    return k.cFrom().get().isBefore(k.nFrom().get());
  }

  /** M5 — first non-overlap check dropped. */
  private static boolean noFirstOverlapCheck(Config k) {
    if (k.cFrom().isEmpty() || k.nFrom().isEmpty()) return false;
    if (k.nUntil().isPresent() && !k.nUntil().get().isAfter(k.cFrom().get())) return false;
    return k.cFrom().get().isBefore(k.nFrom().get());
  }

  /** M6 — second non-overlap check dropped. */
  private static boolean noSecondOverlapCheck(Config k) {
    if (k.cFrom().isEmpty() || k.nFrom().isEmpty()) return false;
    if (k.cUntil().isPresent() && !k.cUntil().get().isAfter(k.nFrom().get())) return false;
    return k.cFrom().get().isBefore(k.nFrom().get());
  }

  /** M7 — boundary of the non-overlap check loosened from {@code <=} to {@code <}. */
  private static boolean looseOverlapBoundary(Config k) {
    if (k.cFrom().isEmpty() || k.nFrom().isEmpty()) return false;
    if (k.cUntil().isPresent() && k.cUntil().get().isBefore(k.nFrom().get())) return false;
    if (k.nUntil().isPresent() && k.nUntil().get().isBefore(k.cFrom().get())) return false;
    return k.cFrom().get().isBefore(k.nFrom().get());
  }

  @Test
  @DisplayName("every mutant of the invalidation rule is killed by the lattice")
  void everyMutantIsKilled() {
    record Mutant(String name, java.util.function.Predicate<Config> apply) {}
    List<Mutant> mutants =
        List.of(
            new Mutant("M1 ties close", FactInvalidationMutationTest::tiesClose),
            new Mutant("M2 direction reversed", FactInvalidationMutationTest::reversed),
            new Mutant("M3 no candidate-start guard", FactInvalidationMutationTest::noCandidateGuard),
            new Mutant("M4 no incoming-start guard", FactInvalidationMutationTest::noIncomingGuard),
            new Mutant("M5 first overlap check dropped", FactInvalidationMutationTest::noFirstOverlapCheck),
            new Mutant("M6 second overlap check dropped", FactInvalidationMutationTest::noSecondOverlapCheck),
            new Mutant("M7 overlap boundary loosened", FactInvalidationMutationTest::looseOverlapBoundary));

    BiPredicate<Config, java.util.function.Predicate<Config>> disagrees =
        (k, mutant) -> {
          boolean actual =
              fact("n", k.nFrom(), k.nUntil()).closes(fact("c", k.cFrom(), k.cUntil()));
          return mutant.test(k) != actual;
        };

    List<String> survivors = new ArrayList<>();
    for (Mutant mutant : mutants) {
      long killedBy = lattice().stream().filter(k -> disagrees.test(k, mutant.apply())).count();
      if (killedBy == 0) {
        survivors.add(mutant.name());
      } else {
        assertThat(killedBy).as(mutant.name() + " kill count").isPositive();
      }
    }

    assertThat(survivors)
        .as("surviving mutants mean the lattice does not constrain those clauses")
        .isEmpty();
  }
}
