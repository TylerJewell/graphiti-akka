package io.akka.memory.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.akka.memory.domain.Entity;
import io.akka.memory.domain.Fact;
import io.akka.memory.domain.PartitionEvent;
import io.akka.memory.domain.PartitionState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The partition's command handlers.
 *
 * <p>This is where the temporal rule stops being a pure function and starts producing events. The
 * decision itself is the domain's; what is checked here is that its verdict becomes the right
 * events, and that closing never becomes deletion.
 */
class PartitionEntityTest {

  private static final Instant T0 = Instant.parse("2024-01-01T00:00:00Z");
  private static final Instant T1 = Instant.parse("2024-01-02T00:00:00Z");
  private static final Instant NOW = Instant.parse("2024-06-01T00:00:00Z");

  private static Fact fact(String id, String object, Optional<Instant> from) {
    return new Fact(
        id, "demo", "ana", object, "worksAt", "Ana works at " + object,
        from, Optional.empty(), T0, Optional.empty(), List.of("ep1"));
  }

  private static EventSourcedTestKit<PartitionState, PartitionEvent, PartitionEntity> kit() {
    return EventSourcedTestKit.of("demo", PartitionEntity::new);
  }

  @Test
  @DisplayName("a later contradicting fact closes the earlier one and both survive")
  void closesEarlierFactAndKeepsBoth() {
    var testKit = kit();

    testKit
        .method(PartitionEntity::recordFacts)
        .invoke(new PartitionEntity.RecordFacts(List.of(fact("f1", "acme", Optional.of(T0))), NOW));

    var result =
        testKit
            .method(PartitionEntity::recordFacts)
            .invoke(
                new PartitionEntity.RecordFacts(
                    List.of(fact("f2", "globex", Optional.of(T1))), NOW));

    assertThat(result.isReply()).isTrue();

    var state = testKit.getState();
    assertThat(state.facts()).containsKeys("f1", "f2");

    // Closed, not deleted — the record survives with an end on the valid-time axis.
    assertThat(state.facts().get("f1").validUntil()).contains(T1);
    assertThat(state.facts().get("f1").supersededAt()).contains(NOW);
    assertThat(state.facts().get("f2").validUntil()).isEmpty();
  }

  @Test
  @DisplayName("two facts starting at the same instant both stay open")
  void exactTieLeavesBothOpen() {
    var testKit = kit();

    testKit
        .method(PartitionEntity::recordFacts)
        .invoke(new PartitionEntity.RecordFacts(List.of(fact("f1", "acme", Optional.of(T0))), NOW));
    testKit
        .method(PartitionEntity::recordFacts)
        .invoke(new PartitionEntity.RecordFacts(List.of(fact("f2", "globex", Optional.of(T0))), NOW));

    var state = testKit.getState();
    assertThat(state.facts().get("f1").validUntil()).isEmpty();
    assertThat(state.facts().get("f2").validUntil()).isEmpty();
  }

  @Test
  @DisplayName("a fact with no start closes nothing")
  void undatedFactClosesNothing() {
    var testKit = kit();

    testKit
        .method(PartitionEntity::recordFacts)
        .invoke(new PartitionEntity.RecordFacts(List.of(fact("f1", "acme", Optional.of(T0))), NOW));
    testKit
        .method(PartitionEntity::recordFacts)
        .invoke(
            new PartitionEntity.RecordFacts(List.of(fact("f2", "globex", Optional.empty())), NOW));

    assertThat(testKit.getState().facts().get("f1").validUntil()).isEmpty();
  }

  @Test
  @DisplayName("a known entity is recognised rather than duplicated")
  void recognisesKnownEntity() {
    var testKit = kit();
    var ana = Entity.create("e1", "demo", "Ana Ruiz", Entity.BASE_TYPE);

    testKit.method(PartitionEntity::recordEntities).invoke(new PartitionEntity.RecordEntities(List.of(ana)));
    assertThat(testKit.getState().entities()).hasSize(1);

    // Same name, different id: must resolve to the existing entity, not create a second.
    var duplicate = Entity.create("e2", "demo", "ana ruiz", Entity.BASE_TYPE);
    var result =
        testKit
            .method(PartitionEntity::recordEntities)
            .invoke(new PartitionEntity.RecordEntities(List.of(duplicate)));

    assertThat(result.isReply()).isTrue();
    assertThat(testKit.getState().entities()).hasSize(1);
    assertThat(result.getAllEvents()).hasSize(1);
    assertThat(result.getNextEventOfType(PartitionEvent.EntityRecognised.class).resolvedEntityId())
        .isEqualTo("e1");
  }

  @Test
  @DisplayName("clearing a partition empties it")
  void clearEmptiesPartition() {
    var testKit = kit();
    testKit
        .method(PartitionEntity::recordFacts)
        .invoke(new PartitionEntity.RecordFacts(List.of(fact("f1", "acme", Optional.of(T0))), NOW));

    testKit.method(PartitionEntity::clear).invoke();

    assertThat(testKit.getState().isEmpty()).isTrue();
  }
}
