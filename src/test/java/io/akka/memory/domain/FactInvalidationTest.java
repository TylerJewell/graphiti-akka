package io.akka.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Exhaustive verification of the invalidation rule.
 *
 * <p>Not a sample of interesting cases — every combination of {@code absent / t0 / t1 / t2} across
 * four nullable instants, which is 256 configurations. A rule this small and this load-bearing can
 * be checked completely, so it is.
 */
class FactInvalidationTest {

  private static final Instant T0 = Instant.parse("2024-01-01T00:00:00Z");
  private static final Instant T1 = Instant.parse("2024-01-02T00:00:00Z");
  private static final Instant T2 = Instant.parse("2024-01-03T00:00:00Z");
  private static final List<Optional<Instant>> DOMAIN =
      List.of(Optional.empty(), Optional.of(T0), Optional.of(T1), Optional.of(T2));

  private static Fact fact(String id, Optional<Instant> from, Optional<Instant> until) {
    return new Fact(
        id, "p", "s", "o", "worksAt", id + " statement",
        from, until, T0, Optional.empty(), List.of("e1"));
  }

  @Test
  @DisplayName("a strictly earlier overlapping fact is closed at the new fact's start")
  void closesStrictlyEarlierOverlappingFact() {
    Fact candidate = fact("old", Optional.of(T0), Optional.empty());
    Fact incoming = fact("new", Optional.of(T1), Optional.empty());

    assertThat(incoming.closes(candidate)).isTrue();

    Fact closed = candidate.closedBy(incoming, T2);
    assertThat(closed.validUntil()).contains(T1);
    assertThat(closed.supersededAt()).contains(T2);
  }

  @Test
  @DisplayName("an exact tie closes nothing — both facts stay open")
  void exactTieClosesNothing() {
    Fact candidate = fact("a", Optional.of(T0), Optional.empty());
    Fact incoming = fact("b", Optional.of(T0), Optional.empty());

    assertThat(incoming.closes(candidate)).isFalse();
    assertThat(candidate.closes(incoming)).isFalse();
  }

  @Test
  @DisplayName("a fact arriving out of chronological order closes nothing in either direction")
  void outOfOrderArrivalClosesNothing() {
    Fact existing = fact("later", Optional.of(T1), Optional.empty());
    Fact arriving = fact("earlier", Optional.of(T0), Optional.empty());

    assertThat(arriving.closes(existing)).isFalse();
    assertThat(existing.closes(arriving)).isTrue(); // only the newer direction closes
  }

  @Test
  @DisplayName("a fact with no start is inert — it closes nothing and is closed by nothing")
  void absentStartIsInert() {
    Fact undated = fact("undated", Optional.empty(), Optional.empty());
    Fact dated = fact("dated", Optional.of(T1), Optional.empty());

    assertThat(dated.closes(undated)).isFalse();
    assertThat(undated.closes(dated)).isFalse();
  }

  @Test
  @DisplayName("non-overlapping intervals never close each other")
  void nonOverlappingIntervalsAreLeftAlone() {
    Fact closedAlready = fact("old", Optional.of(T0), Optional.of(T1));
    Fact starting = fact("new", Optional.of(T2), Optional.empty());

    assertThat(starting.closes(closedAlready)).isFalse();
  }

  @Test
  @DisplayName("an existing supersededAt is preserved, not overwritten")
  void preservesExistingSupersededAt() {
    Fact candidate =
        new Fact("old", "p", "s", "o", "worksAt", "old", Optional.of(T0), Optional.empty(),
            T0, Optional.of(T1), List.of("e1"));
    Fact incoming = fact("new", Optional.of(T2), Optional.empty());

    assertThat(candidate.closedBy(incoming, T2).supersededAt()).contains(T1);
  }

  @Test
  @DisplayName("the rule is exhaustively consistent across all 256 temporal configurations")
  void exhaustiveLatticeIsSelfConsistent() {
    int total = 0;
    int closing = 0;
    for (Optional<Instant> cFrom : DOMAIN) {
      for (Optional<Instant> cUntil : DOMAIN) {
        for (Optional<Instant> nFrom : DOMAIN) {
          for (Optional<Instant> nUntil : DOMAIN) {
            Fact candidate = fact("c", cFrom, cUntil);
            Fact incoming = fact("n", nFrom, nUntil);
            boolean closes = incoming.closes(candidate);
            total++;
            if (closes) {
              closing++;
              // Whenever the rule fires, these must hold — they are the rule's meaning.
              assertThat(cFrom).isPresent();
              assertThat(nFrom).isPresent();
              assertThat(cFrom.get()).isBefore(nFrom.get());
              assertThat(candidate.closedBy(incoming, T2).validUntil()).isEqualTo(nFrom);
            }
          }
        }
      }
    }
    assertThat(total).isEqualTo(256);
    // Not trivially constant: it fires sometimes, and not always.
    assertThat(closing).isGreaterThan(0).isLessThan(total);
  }
}
