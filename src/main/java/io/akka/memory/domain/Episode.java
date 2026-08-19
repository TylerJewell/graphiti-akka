package io.akka.memory.domain;

import java.time.Instant;

/** A unit of remembered input. Facts and entities trace back to the episodes they came from. */
public record Episode(
    String id,
    String partition,
    String content,
    Kind kind,
    Instant referenceTime,
    String sourceDescription,
    Instant recordedAt) {

  /** Selects the extraction instruction. An unrecognised kind falls back to prose. */
  public enum Kind {
    MESSAGE,
    TEXT,
    JSON;

    public static Kind fromWire(String raw) {
      if (raw == null) {
        return TEXT;
      }
      return switch (raw.toLowerCase(java.util.Locale.ROOT)) {
        case "message" -> MESSAGE;
        case "json" -> JSON;
        default -> TEXT; // silent fallback, matching the source
      };
    }
  }

  /**
   * Blanks the content while keeping everything derived from it.
   *
   * <p>The system's only data-minimisation control. Note the asymmetry: this removes the source
   * text and leaves every entity name, fact and summary the model derived from it. It reduces
   * retention; it does not anonymise.
   */
  public Episode withoutRawContent() {
    return new Episode(id, partition, "", kind, referenceTime, sourceDescription, recordedAt);
  }
}
