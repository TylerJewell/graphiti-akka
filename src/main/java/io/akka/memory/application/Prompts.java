package io.akka.memory.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The extraction, resolution and summarisation instructions, loaded verbatim from resources.
 *
 * <p>These are <b>contract, not configuration</b>. The instructions and response schemas condition
 * what the model produces, so a paraphrase is a behavioural change and invalidates every recorded
 * model interaction. They are extracted from the source system rather than transcribed, for the
 * same reason the surface inventory is generated rather than hand-written.
 */
public final class Prompts {

  private static final Map<String, String[]> CACHE = new HashMap<>();

  private Prompts() {}

  /** @return {@code [systemMessage, userTemplate]} for the named prompt. */
  public static synchronized String[] load(String name) {
    return CACHE.computeIfAbsent(name, Prompts::read);
  }

  public static String system(String name) {
    return load(name)[0];
  }

  public static String userTemplate(String name) {
    return load(name)[1];
  }

  private static String[] read(String name) {
    String path = "/prompts/" + name + ".txt";
    try (InputStream in = Prompts.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("prompt resource missing: " + path);
      }
      String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      // The file is "--- system ---\n<text>\n\n--- user ---\n<text>".
      String[] parts = raw.split("--- user ---", 2);
      String system = parts[0].replace("--- system ---", "").trim();
      String user = parts.length > 1 ? parts[1].trim() : "";
      return new String[] {system, user};
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
