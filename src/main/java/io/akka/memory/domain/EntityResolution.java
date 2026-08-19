package io.akka.memory.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Deciding whether an extracted name is something already known.
 *
 * <p>Identity is not a field and there is no natural key — it is the outcome of this cascade, and
 * the same two names can resolve differently depending on what else exists and what the candidate
 * search returned (SPEC-003 §2).
 *
 * <p>The cascade stops at the first stage that decides:
 *
 * <ol>
 *   <li><b>Candidate gate.</b> No candidates means new, and nothing further runs.
 *   <li><b>Exact name</b>, compared <em>against the candidates only</em>. One match resolves; more
 *       than one is ambiguous and escalates; none continues.
 *   <li><b>Entropy gate.</b> Failing it escalates.
 *   <li><b>Fuzzy similarity.</b> The best candidate at or above the threshold resolves; otherwise
 *       escalate.
 * </ol>
 *
 * <p><b>Stage 2 is not a global index lookup</b>, and implementing it as one is the mistake this
 * class exists to prevent. Two entities with byte-identical names are never merged unless stage 1
 * surfaced one as a candidate for the other — so a global name index silently merges entities the
 * source system keeps apart, and every behavioural test still passes.
 *
 * <p>The entropy gate does not do what its name suggests. Measured on the fuzzy form, it requires
 * six characters <em>or</em> two tokens, so most single-word names fail on length rather than on
 * entropy: {@code Alice}, {@code Acme} and {@code IBM} all clear the entropy bar and are sent to the
 * model anyway. Deterministic fuzzy matching effectively serves only multi-token or long names.
 */
public final class EntityResolution {

  private EntityResolution() {}

  /** An existing entity offered for comparison. */
  public record Candidate(String id, String name) {}

  /** How the cascade ended, and at which stage. */
  public sealed interface Outcome {

    /** The name is the entity with this identifier. */
    record Resolved(String entityId, Stage decidedAt) implements Outcome {}

    /** Nothing to compare against; the entity is new. */
    record New() implements Outcome {}

    /** Deterministic stages could not decide. The model is asked. */
    record Escalate(Stage failedAt) implements Outcome {}
  }

  public enum Stage {
    CANDIDATE_GATE,
    EXACT_NAME,
    ENTROPY_GATE,
    FUZZY_SIMILARITY
  }

  public static Outcome resolve(String extractedName, List<Candidate> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      return new Outcome.New();
    }

    String exact = EntityIdentity.normalizeExact(extractedName);
    var exactMatches = new ArrayList<Candidate>();
    for (Candidate candidate : candidates) {
      if (EntityIdentity.normalizeExact(candidate.name()).equals(exact)) {
        exactMatches.add(candidate);
      }
    }
    if (exactMatches.size() == 1) {
      return new Outcome.Resolved(exactMatches.get(0).id(), Stage.EXACT_NAME);
    }
    if (exactMatches.size() > 1) {
      // Two candidates with the same name is a question about the world, not about strings.
      return new Outcome.Escalate(Stage.EXACT_NAME);
    }

    String fuzzy = EntityIdentity.normalizeFuzzy(extractedName);
    if (!EntityIdentity.passesEntropyGate(fuzzy)) {
      return new Outcome.Escalate(Stage.ENTROPY_GATE);
    }

    var shingles = EntityIdentity.shingles(fuzzy);
    String bestId = null;
    double best = 0;
    for (Candidate candidate : candidates) {
      double similarity =
          EntityIdentity.jaccard(
              shingles, EntityIdentity.shingles(EntityIdentity.normalizeFuzzy(candidate.name())));
      if (similarity > best) {
        best = similarity;
        bestId = candidate.id();
      }
    }
    if (bestId != null && best >= EntityIdentity.JACCARD_THRESHOLD) {
      return new Outcome.Resolved(bestId, Stage.FUZZY_SIMILARITY);
    }
    return new Outcome.Escalate(Stage.FUZZY_SIMILARITY);
  }
}
