package io.akka.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RankFusionTest {

  private static List<String> ids(List<RankFusion.Scored> scored) {
    return scored.stream().map(RankFusion.Scored::id).toList();
  }

  @Test
  @DisplayName("the fusion constant is 1, not the literature's 60")
  void constantIsOne() {
    assertThat(RankFusion.RANK_CONST).isEqualTo(1);
    // A single rank-0 hit therefore scores 1.0, not 1/61.
    assertThat(RankFusion.fuse(List.of(List.of("a"))).get(0).score()).isCloseTo(1.0, within(1e-9));
  }

  @Test
  @DisplayName("the constant is behavioural: one top hit beats two mid-list hits at k=1")
  void constantChangesTheWinner() {
    // "a" appears once at rank 0. "b" appears twice at rank 5.
    List<List<String>> lists =
        List.of(
            List.of("a", "x1", "x2", "x3", "x4", "x5"),
            List.of("p", "q", "r", "s", "t", "b"),
            List.of("u", "v", "w", "y", "z", "b"));

    var fused = RankFusion.fuse(lists);
    assertThat(fused.get(0).id()).as("at k=1 the single top hit wins").isEqualTo("a");

    // Score check: a = 1/1 = 1.0; b = 1/6 + 1/6 = 0.333.
    double aScore = fused.stream().filter(s -> s.id().equals("a")).findFirst().orElseThrow().score();
    double bScore = fused.stream().filter(s -> s.id().equals("b")).findFirst().orElseThrow().score();
    assertThat(aScore).isCloseTo(1.0, within(1e-9));
    assertThat(bScore).isCloseTo(1.0 / 6 + 1.0 / 6, within(1e-9));
    assertThat(aScore).isGreaterThan(bScore);
  }

  @Test
  @DisplayName("ties resolve to first-seen order across the input lists")
  void tiesResolveByFirstSeenOrder() {
    // Identical scores; only input order differs.
    assertThat(ids(RankFusion.fuse(List.of(List.of("x"), List.of("y")))))
        .containsExactly("x", "y");
    assertThat(ids(RankFusion.fuse(List.of(List.of("y"), List.of("x")))))
        .containsExactly("y", "x");
  }

  @Test
  @DisplayName("results are ordered by descending score")
  void ordersByScore() {
    // Pinned against the source implementation:
    //   a = 1/1 + 1/2 + 1/3 = 1.833
    //   b = 1/2 + 1/1 + 1/2 = 2.000  <- wins
    //   c = 1/3 + 1/3 + 1/1 = 1.667
    var fused =
        RankFusion.fuse(
            List.of(List.of("a", "b", "c"), List.of("b", "a", "c"), List.of("c", "b", "a")));
    assertThat(ids(fused)).containsExactly("b", "a", "c");
    for (int i = 1; i < fused.size(); i++) {
      assertThat(fused.get(i - 1).score()).isGreaterThanOrEqualTo(fused.get(i).score());
    }
  }

  @Test
  @DisplayName("the score floor defaults to zero, so nothing is dropped unless asked")
  void floorIsInertByDefault() {
    var lists = List.of(List.of("a", "b", "c"));
    assertThat(RankFusion.fuse(lists)).hasSize(3);
    assertThat(RankFusion.fuse(lists, 0.6)).hasSize(1); // only the rank-0 hit clears 0.6
  }
}
