package io.akka.memory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the one obligation in this port that is easiest to lose by accident.
 *
 * <p>The runtime offers durable step retry and failover for free. Enabling it would let this
 * service recover from failures the source system cannot — reaching states the specification
 * enumerates as unreachable, and breaking equivalence in the one place the source is weakest.
 *
 * <p>So the port must <b>suppress a capability that costs nothing to have</b>. Nothing in the code
 * marks its absence; someone adding a sensible-looking {@code defaultStepRecovery} later would be
 * making the system better and the port wrong, and no other test would notice. This one does.
 *
 * <p>It reads the source because the absence of a configuration call cannot be observed at runtime.
 * That is a weaker check than a behavioural one and is used deliberately here — the alternative is
 * no check at all.
 */
class RetrySuppressionTest {

  private static final Path WORKFLOW =
      Path.of("src/main/java/io/akka/memory/application/EpisodeIngestWorkflow.java");

  private static String source() throws Exception {
    assertThat(Files.exists(WORKFLOW)).as("workflow source must be readable").isTrue();
    return Files.readString(WORKFLOW);
  }

  @Test
  @DisplayName("no automatic step recovery is configured — OD-19")
  void noStepRecoveryIsConfigured() throws Exception {
    var src = source();

    // Matches the invocation, not the word: the comment explaining the absence necessarily
    // mentions it, and an earlier version of this test failed on its own documentation.
    assertThat(src)
        .as("defaultStepRecovery(...) would let a failed stage retry, which the source cannot do")
        .doesNotContain(".defaultStepRecovery(");
    assertThat(src)
        .as("a per-step recovery strategy has the same effect for that step")
        .doesNotContain(".stepRecovery(");
    assertThat(src)
        .as("RecoverStrategy.maxRetries(...) is how either is configured")
        .doesNotContain("RecoverStrategy.");
  }

  @Test
  @DisplayName("the suppression is documented where someone would undo it")
  void suppressionIsExplained() throws Exception {
    var src = source();
    // A bare absence reads as an omission. The reason has to sit next to the settings method,
    // or the next person restores retry believing they are fixing a gap.
    assertThat(src)
        .as("the reason must be next to the code, not only in the specification")
        .contains("No defaultStepRecovery");
    assertThat(src).contains("OD-19");
  }

  @Test
  @DisplayName("step timeouts are long, because model calls are slow")
  void stepTimeoutsAccommodateModelCalls() throws Exception {
    var src = source();
    assertThat(src).contains("defaultStepTimeout");
    // Suppressing retry makes an over-tight timeout expensive: there is no second attempt.
    assertThat(src).containsPattern("defaultStepTimeout\\(ofSeconds\\((6[0-9]|[7-9][0-9]|[1-9][0-9]{2,})\\)\\)");
  }
}
