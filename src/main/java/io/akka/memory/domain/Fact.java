package io.akka.memory.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * A directed, named relationship between two entities, carrying two independent timelines.
 *
 * <p><b>Valid time</b> ({@code validFrom}/{@code validUntil}) is when the fact was true in the
 * world. <b>Transaction time</b> ({@code recordedAt}/{@code supersededAt}) is when the system
 * believed it. The two move independently: a fact can be true long before it is learned, and stay
 * on record long after it stops being true. Answering a question about one from the other's data
 * produces a well-formed, confident, wrong answer.
 *
 * <p>No Akka types appear here. The rules below are the highest-risk logic in the service and are
 * testable with no runtime, no model and no store.
 */
public record Fact(
    String id,
    String partition,
    String subjectId,
    String objectId,
    String relation,
    String statement,
    Optional<Instant> validFrom,
    Optional<Instant> validUntil,
    Instant recordedAt,
    Optional<Instant> supersededAt,
    List<String> episodeIds) {

  public Fact {
    episodeIds = List.copyOf(episodeIds);
  }

  /**
   * Whether {@code candidate} is closed by this (newer) fact.
   *
   * <p>Reproduces the source system exactly, including three behaviours that look like defects and
   * are deliberate under D-006:
   *
   * <ul>
   *   <li><b>Equal starts close nothing.</b> Two contradicting facts both stay open — the source
   *       has no branch for this case, so neither does the port.
   *   <li><b>Out-of-order arrival closes nothing</b>, in either direction. Ingest order therefore
   *       changes the resulting graph, which is why the write path is keyed by partition.
   *   <li><b>An absent start makes a fact inert.</b> It can neither close another nor be closed.
   * </ul>
   */
  public boolean closes(Fact candidate) {
    if (candidate.validFrom.isEmpty() || this.validFrom.isEmpty()) {
      return false;
    }
    Instant candidateFrom = candidate.validFrom.get();
    Instant newFrom = this.validFrom.get();

    // Non-overlapping in either direction: nothing to contradict.
    if (candidate.validUntil.isPresent() && !candidate.validUntil.get().isAfter(newFrom)) {
      return false;
    }
    if (this.validUntil.isPresent() && !this.validUntil.get().isAfter(candidateFrom)) {
      return false;
    }
    return candidateFrom.isBefore(newFrom);
  }

  /**
   * Closes this fact at {@code closingFact}'s start.
   *
   * <p>The closing instant is the <em>new</em> fact's valid start, not the wall clock. {@code
   * supersededAt} is stamped only if currently absent — an existing value is preserved.
   */
  public Fact closedBy(Fact closingFact, Instant now) {
    return new Fact(
        id,
        partition,
        subjectId,
        objectId,
        relation,
        statement,
        validFrom,
        closingFact.validFrom,
        recordedAt,
        supersededAt.isPresent() ? supersededAt : Optional.of(now),
        episodeIds);
  }

  /** Whether this fact is open at the given instant, on the <b>valid-time</b> axis. */
  public boolean isValidAt(Instant when) {
    if (validFrom.isPresent() && validFrom.get().isAfter(when)) {
      return false;
    }
    return validUntil.isEmpty() || validUntil.get().isAfter(when);
  }

  /**
   * Parses a timestamp the extractor produced, reproducing the source's degradation exactly.
   *
   * <p>A value that cannot be parsed becomes {@link Optional#empty()} — it does not raise, does not
   * retry, and does not fall back to the episode's reference time. By {@link #closes} that makes
   * the fact inert, so an extraction formatting error silently disables temporal reasoning for it.
   *
   * <p>Under-specified values are <b>accepted, not rejected</b>: a date with no time is read at
   * midnight UTC, and a time with no zone is assumed UTC. Both shift a fact's start by up to a day
   * and therefore change which facts close which.
   */
  public static Optional<Instant> parseTimestamp(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    String value = raw.trim();
    try {
      return Optional.of(Instant.parse(value));
    } catch (DateTimeParseException ignored) {
      // fall through to the less specific forms
    }
    try {
      return Optional.of(LocalDateTime.parse(value).toInstant(ZoneOffset.UTC));
    } catch (DateTimeParseException ignored) {
      // fall through
    }
    try {
      return Optional.of(LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC));
    } catch (DateTimeParseException ignored) {
      return Optional.empty();
    }
  }
}
