package io.akka.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.akka.memory.domain.EntityResolution.Candidate;
import io.akka.memory.domain.EntityResolution.Outcome;
import io.akka.memory.domain.EntityResolution.Stage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The recognition cascade, stage by stage, including the stages that decline.
 *
 * <p>Where it stops matters as much as what it decides. An implementation that resolves the same
 * pairs but reaches the answer at a different stage sends different work to the model, and the
 * model's answers are where the graphs diverge.
 */
class EntityResolutionCascadeTest {

  private static final Candidate ANA = new Candidate("id-ana", "Ana Kowalski");
  private static final Candidate ACME = new Candidate("id-acme", "Acme Corporation");

  @Test
  @DisplayName("no candidates means new, and nothing later runs")
  void anEmptyCandidateSetEndsTheCascade() {
    assertThat(EntityResolution.resolve("Ana Kowalski", List.of()))
        .isInstanceOf(Outcome.New.class);
    assertThat(EntityResolution.resolve("Ana Kowalski", null)).isInstanceOf(Outcome.New.class);
  }

  @Test
  @DisplayName("one exact match among the candidates resolves to it")
  void oneExactMatchResolves() {
    var outcome = EntityResolution.resolve("Ana Kowalski", List.of(ANA, ACME));
    assertThat(outcome).isInstanceOf(Outcome.Resolved.class);
    var resolved = (Outcome.Resolved) outcome;
    assertThat(resolved.entityId()).isEqualTo("id-ana");
    assertThat(resolved.decidedAt()).isEqualTo(Stage.EXACT_NAME);
  }

  @Test
  @DisplayName("exact matching uses the exact normal form, so case and spacing do not matter")
  void exactMatchingNormalisesFirst() {
    var outcome = EntityResolution.resolve("  ANA   kowalski ", List.of(ANA));
    assertThat(outcome).isInstanceOf(Outcome.Resolved.class);
  }

  @Test
  @DisplayName("two candidates with the same name is a question, not an answer")
  void ambiguousExactMatchesEscalate() {
    var duplicate = new Candidate("id-other", "Ana Kowalski");
    var outcome = EntityResolution.resolve("Ana Kowalski", List.of(ANA, duplicate));
    assertThat(outcome).isInstanceOf(Outcome.Escalate.class);
    assertThat(((Outcome.Escalate) outcome).failedAt()).isEqualTo(Stage.EXACT_NAME);
  }

  @Test
  @DisplayName("a short single-word name never reaches fuzzy matching")
  void shortNamesFailTheEntropyGateOnLength() {
    // "IBM" clears the entropy bar and fails on length, which the gate's name does not suggest.
    var outcome = EntityResolution.resolve("IBM", List.of(new Candidate("id-ibm", "IBN")));
    assertThat(outcome).isInstanceOf(Outcome.Escalate.class);
    assertThat(((Outcome.Escalate) outcome).failedAt()).isEqualTo(Stage.ENTROPY_GATE);
  }

  @Test
  @DisplayName("a near-identical long name resolves deterministically, without the model")
  void closeEnoughNamesResolveOnSimilarity() {
    var outcome =
        EntityResolution.resolve("Acme Corporationn", List.of(ACME)); // one duplicated letter
    assertThat(outcome).isInstanceOf(Outcome.Resolved.class);
    assertThat(((Outcome.Resolved) outcome).decidedAt()).isEqualTo(Stage.FUZZY_SIMILARITY);
  }

  @Test
  @DisplayName("a merely similar name escalates rather than guessing")
  void insufficientSimilarityEscalates() {
    var outcome = EntityResolution.resolve("Acme Industries", List.of(ACME));
    assertThat(outcome).isInstanceOf(Outcome.Escalate.class);
    assertThat(((Outcome.Escalate) outcome).failedAt()).isEqualTo(Stage.FUZZY_SIMILARITY);
  }

  @Test
  @DisplayName("an identical name that was never offered as a candidate is not recognised")
  void identicalNamesAreNotMergedWithoutACandidateSearch() {
    // The stage-2 comparison is against the candidates only. A global name index would merge
    // these two and pass every behavioural test while building a different graph.
    assertThat(EntityResolution.resolve("Ana Kowalski", List.of(ACME)))
        .isNotInstanceOf(Outcome.Resolved.class);
  }
}
