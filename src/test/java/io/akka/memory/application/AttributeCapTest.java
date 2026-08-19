package io.akka.memory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The attribute length guard.
 *
 * <p>"Cap" implies truncation. It is not truncation — an over-long value is <b>dropped entirely</b>,
 * so the entity ends up without the attribute rather than with a shortened one. And the guard has an
 * exemption that makes its protection conditional on the caller's schema.
 */
class AttributeCapTest {

  private static final int CAP = AttributeCap.DEFAULT_MAX_LENGTH;

  private static String of(int length) {
    return "a".repeat(length);
  }

  @Test
  @DisplayName("a value at the cap is kept — the boundary is inclusive")
  void boundaryIsInclusive() {
    assertThat(AttributeCap.apply(Map.of("bio", of(CAP - 1)), Set.of())).containsKey("bio");
    assertThat(AttributeCap.apply(Map.of("bio", of(CAP)), Set.of())).containsKey("bio");
  }

  @Test
  @DisplayName("one character over the cap drops the attribute entirely — it is not shortened")
  void oneOverDropsRatherThanTruncates() {
    var kept = AttributeCap.apply(Map.of("bio", of(CAP + 1)), Set.of());
    assertThat(kept).doesNotContainKey("bio");
    assertThat(kept).isEmpty();
  }

  @Test
  @DisplayName("a single over-cap item drops the whole list")
  void perItemAxis() {
    var kept = AttributeCap.apply(Map.of("tags", List.of("ok", of(CAP + 1))), Set.of());
    assertThat(kept).doesNotContainKey("tags");
  }

  @Test
  @DisplayName("many just-under-cap items breach the aggregate axis")
  void aggregateAxis() {
    var items = new java.util.ArrayList<String>();
    for (int i = 0; i < AttributeCap.LIST_TOTAL_MULTIPLIER + 2; i++) {
      items.add(of(CAP - 1));
    }
    assertThat(AttributeCap.apply(Map.of("tags", items), Set.of())).doesNotContainKey("tags");

    // A small list survives both axes.
    assertThat(AttributeCap.apply(Map.of("tags", List.of("a", "b", "c")), Set.of()))
        .containsKey("tags");
  }

  @Test
  @DisplayName("a REQUIRED field is exempt and kept at full length")
  void requiredFieldsAreExempt() {
    var huge = of(CAP * 40);
    var kept = AttributeCap.apply(Map.of("bio", huge), Set.of("bio"));

    assertThat(kept).containsKey("bio");
    assertThat((String) kept.get("bio")).hasSize(CAP * 40);
  }

  @Test
  @DisplayName("the exemption means a caller's schema silently disables the limit")
  void exemptionIsConditionalOnCallerSchema() {
    var attributes = Map.<String, Object>of("bio", of(CAP + 1));

    // Same value, same cap — the only difference is what the caller declared required.
    assertThat(AttributeCap.apply(attributes, Set.of())).isEmpty();
    assertThat(AttributeCap.apply(attributes, Set.of("bio"))).containsKey("bio");
  }
}
