package io.akka.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.akka.memory.domain.ExtractionGuardrails.Accepted;
import io.akka.memory.domain.ExtractionGuardrails.Candidate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The guardrail table, one case per rule and one per boundary.
 *
 * <p>Every one of these has an obvious wrong answer that a reimplementation would reach for: raise
 * on a bad index, drop the entity, attribute it to nothing. The source does none of those, and the
 * difference is visible in what ends up stored.
 */
class ExtractionGuardrailTest {

  private static final List<String> TYPES = List.of("Entity", "Person", "Organisation");

  private static List<Accepted> apply(Candidate candidate, Set<String> excluded, int episodes) {
    return ExtractionGuardrails.apply(List.of(candidate), TYPES, excluded, episodes);
  }

  @Test
  @DisplayName("an index inside the offered range selects that type")
  void inRangeIndexSelectsTheType() {
    var out = apply(new Candidate("Ana", 1, List.of(0)), Set.of(), 1);
    assertThat(out).singleElement().extracting(Accepted::type).isEqualTo("Person");
  }

  @Test
  @DisplayName("an index past the end degrades to the base type instead of raising")
  void indexPastTheEndDegrades() {
    var out = apply(new Candidate("Ana", 99, List.of(0)), Set.of(), 1);
    assertThat(out).singleElement().extracting(Accepted::type).isEqualTo(Entity.BASE_TYPE);
  }

  @Test
  @DisplayName("a negative index degrades to the base type too")
  void negativeIndexDegrades() {
    var out = apply(new Candidate("Ana", -1, List.of(0)), Set.of(), 1);
    assertThat(out).singleElement().extracting(Accepted::type).isEqualTo(Entity.BASE_TYPE);
  }

  @Test
  @DisplayName("the last valid index is valid, and the one after it is not")
  void theBoundaryIsWhereItShouldBe() {
    assertThat(apply(new Candidate("Ana", 2, List.of(0)), Set.of(), 1))
        .singleElement()
        .extracting(Accepted::type)
        .isEqualTo("Organisation");
    assertThat(apply(new Candidate("Ana", 3, List.of(0)), Set.of(), 1))
        .singleElement()
        .extracting(Accepted::type)
        .isEqualTo(Entity.BASE_TYPE);
  }

  @Test
  @DisplayName("an excluded type is dropped entirely, with no trace and no error")
  void excludedTypesAreDropped() {
    assertThat(apply(new Candidate("Ana", 1, List.of(0)), Set.of("Person"), 1)).isEmpty();
  }

  @Test
  @DisplayName("exclusion is applied after the index resolves, so a degraded entity can be excluded")
  void exclusionAppliesToTheDegradedType() {
    // The index is nonsense, so the type becomes the base type — and the base type is excluded.
    // Ordering the two rules the other way round would let this entity through.
    assertThat(apply(new Candidate("Ana", 99, List.of(0)), Set.of(Entity.BASE_TYPE), 1)).isEmpty();
  }

  @Test
  @DisplayName("valid episode indices are kept as given")
  void validIndicesSurvive() {
    var out = apply(new Candidate("Ana", 0, List.of(0, 2)), Set.of(), 3);
    assertThat(out).singleElement().extracting(Accepted::episodeIndices).isEqualTo(List.of(0, 2));
  }

  @Test
  @DisplayName("out-of-range indices are dropped, and the valid ones still stand")
  void invalidIndicesAreFilteredNotFatal() {
    var out = apply(new Candidate("Ana", 0, List.of(0, 7, -3)), Set.of(), 3);
    assertThat(out).singleElement().extracting(Accepted::episodeIndices).isEqualTo(List.of(0));
  }

  @Test
  @DisplayName("no usable index means every episode, not none")
  void unusableIndicesWidenAttribution() {
    // The surprising direction. An entity the model could not place ends up attributed to
    // everything rather than nothing, so it appears in more results, not fewer.
    var out = apply(new Candidate("Ana", 0, List.of(9, 9)), Set.of(), 3);
    assertThat(out).singleElement().extracting(Accepted::episodeIndices).isEqualTo(List.of(0, 1, 2));
  }

  @Test
  @DisplayName("an empty index list widens the same way")
  void emptyIndicesWidenToo() {
    var out = apply(new Candidate("Ana", 0, List.of()), Set.of(), 2);
    assertThat(out).singleElement().extracting(Accepted::episodeIndices).isEqualTo(List.of(0, 1));
  }

  @Test
  @DisplayName("a nameless entity never becomes one")
  void namelessCandidatesAreDropped() {
    assertThat(apply(new Candidate("", 0, List.of(0)), Set.of(), 1)).isEmpty();
    assertThat(apply(new Candidate("   ", 0, List.of(0)), Set.of(), 1)).isEmpty();
    assertThat(apply(new Candidate(null, 0, List.of(0)), Set.of(), 1)).isEmpty();
  }

  @Test
  @DisplayName("a missing index list is treated as an unusable one, not as a failure")
  void missingIndicesAreTolerated() {
    var out = apply(new Candidate("Ana", 0, null), Set.of(), 2);
    assertThat(out).singleElement().extracting(Accepted::episodeIndices).isEqualTo(List.of(0, 1));
  }
}
