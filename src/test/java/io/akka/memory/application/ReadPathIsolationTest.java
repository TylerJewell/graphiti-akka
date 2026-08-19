package io.akka.memory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reads never load write state — checked by inspection, because no runtime check can see it.
 *
 * <p>A read that reaches into the write model still returns the right answer. It just couples read
 * latency to the per-partition serialisation of ingest, and does so invisibly, so no functional test
 * will ever notice. The only way this stays true is if something looks.
 *
 * <p>Inspection is on source text, which is coarse — it catches the call shape, not every route to
 * it. That is a deliberate trade: a check that reads the code the way a reviewer would is worth more
 * than no check, and it fails loudly the moment someone reaches for the entity from a read.
 */
class ReadPathIsolationTest {

  private static final Path SOURCE = Path.of("src", "main", "java", "io", "akka", "memory");

  /** Everything that serves a read. */
  private static final List<Path> READ_COMPONENTS =
      List.of(
          SOURCE.resolve("application/RetrievalService.java"),
          SOURCE.resolve("application/FlureeStore.java"),
          SOURCE.resolve("domain/Bm25.java"),
          SOURCE.resolve("domain/RankFusion.java"));

  private static String read(Path path) throws IOException {
    return Files.readString(path);
  }

  @Test
  @DisplayName("no component that serves a read names the write model at all")
  void readComponentsDoNotKnowAboutTheWriteModel() throws IOException {
    for (Path component : READ_COMPONENTS) {
      assertThat(component).as("the component must exist to be checked").exists();
      assertThat(read(component))
          .as("%s serves reads and must not reach into the write path", component)
          .doesNotContain("PartitionEntity")
          .doesNotContain("PartitionState");
    }
  }

  @Test
  @DisplayName("no surface reads state back out of the write model")
  void surfacesDoNotQueryTheWriteModel() throws IOException {
    for (Path surface :
        List.of(SOURCE.resolve("api/MemoryEndpoint.java"), SOURCE.resolve("api/MemoryMcpEndpoint.java"))) {
      // The surfaces legitimately send commands to the write model. What they must never do is
      // read from it — that is what the projection is for.
      assertThat(read(surface))
          .as("%s must not read the write model's state", surface)
          .doesNotContain("PartitionEntity::get")
          .doesNotContain("PartitionState");
    }
  }

  @Test
  @DisplayName("the projection is maintained by exactly one consumer, and it is the only writer")
  void oneProjectionOneWriter() throws IOException {
    var consumer = read(SOURCE.resolve("application/FlureeProjectionConsumer.java"));
    assertThat(consumer).contains("@Consume.FromEventSourcedEntity(PartitionEntity.class)");

    // The retrieval side reads the store and never writes to it. A read path that writes is a
    // read path that can diverge from the events it was supposed to be derived from.
    var retrieval = read(SOURCE.resolve("application/RetrievalService.java"));
    assertThat(retrieval)
        .doesNotContain("putFacts")
        .doesNotContain("putEpisode")
        .doesNotContain("putEntity")
        .doesNotContain("closeFact")
        .doesNotContain("clearPartition");
  }
}
