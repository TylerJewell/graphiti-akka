package io.akka.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The extractor's timestamps, put under a hostile corpus.
 *
 * <p>The contract is that <b>nothing escapes as an exception</b> — a malformed value degrades to
 * absent, which by the invalidation rule makes the fact inert. And under-specified values are
 * <b>accepted, not rejected</b>, which shifts a fact's start by up to a day and therefore changes
 * which facts close which.
 */
class TimestampDegradationTest {

  private static final List<String> HOSTILE =
      List.of(
          "unknown", "N/A", "null", "None", "present", "not specified", "circa 2024",
          "January 15, 2024", "15/01/2024", "2024-13-45T99:99:99Z", "2024-02-30",
          "ZZZZ", "Z", "--", "2024-01-15T10:30:00Z; DROP TABLE", "{\"date\": \"2024-01-15\"}",
          "", "   ", "\n2024-01-15\n", "2024-01-15T10:30:00+25:00");

  @Test
  @DisplayName("no hostile input escapes as an exception")
  void nothingEscapes() {
    for (String raw : HOSTILE) {
      assertThat(Fact.parseTimestamp(raw))
          .as("input %s must degrade, not raise", raw)
          .isNotNull();
    }
    assertThat(Fact.parseTimestamp(null)).isEmpty();
  }

  @Test
  @DisplayName("refusal text and malformed dates degrade to absent")
  void refusalsDegradeToAbsent() {
    for (String raw : List.of("unknown", "N/A", "not specified", "2024-02-30", "ZZZZ")) {
      assertThat(Fact.parseTimestamp(raw)).as(raw).isEmpty();
    }
  }

  @Test
  @DisplayName("a well-formed instant parses exactly")
  void wellFormedParses() {
    assertThat(Fact.parseTimestamp("2024-01-15T10:30:00Z"))
        .contains(Instant.parse("2024-01-15T10:30:00Z"));
  }

  @Test
  @DisplayName("a date with no time is ACCEPTED and read at midnight UTC")
  void dateOnlyBecomesMidnightUtc() {
    assertThat(Fact.parseTimestamp("2024-01-15"))
        .as("not a parse failure — it silently acquires a time")
        .contains(Instant.parse("2024-01-15T00:00:00Z"));
  }

  @Test
  @DisplayName("a time with no zone is ACCEPTED and assumed UTC")
  void zonelessIsAssumedUtc() {
    assertThat(Fact.parseTimestamp("2024-01-15T10:30:00"))
        .contains(Instant.parse("2024-01-15T10:30:00Z"));
  }

  @Test
  @DisplayName("an unparseable start makes the fact inert for invalidation")
  void degradationDisablesTemporalReasoning() {
    var undated =
        new Fact("u", "p", "s", "o", "r", "u", Fact.parseTimestamp("unknown"),
            java.util.Optional.empty(), Instant.EPOCH, java.util.Optional.empty(), List.of());
    var dated =
        new Fact("d", "p", "s", "o", "r", "d", Fact.parseTimestamp("2024-06-01T00:00:00Z"),
            java.util.Optional.empty(), Instant.EPOCH, java.util.Optional.empty(), List.of());

    assertThat(dated.closes(undated)).isFalse();
    assertThat(undated.closes(dated)).isFalse();
  }
}
