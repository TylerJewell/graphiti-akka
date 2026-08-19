package io.akka.memory.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Turns text into the vector used for similarity retrieval.
 *
 * <p>Model and width are the source system's: {@code text-embedding-3-small} at 1024 dimensions.
 * Both matter — a different model puts the same sentence somewhere else in the space, and a
 * different width is a different space entirely, so neither can be treated as a free choice.
 *
 * <p><b>Absence is not an error.</b> With no key configured this returns nothing and the caller
 * drops the similarity list from the fusion rather than failing the query. That keeps the service
 * usable without a model account, at the cost of a ranking built from fewer lists — which the
 * caller reports rather than hides.
 */
public final class Embedder {

  public static final String MODEL = "text-embedding-3-small";
  public static final int DIMENSIONS = 1024;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final String apiKey;
  private final String baseUrl;

  public Embedder(String apiKey, String baseUrl) {
    this.apiKey = apiKey;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  public static Embedder fromEnvironment() {
    return new Embedder(System.getenv("OPENAI_API_KEY"), "https://api.openai.com");
  }

  public boolean isConfigured() {
    return apiKey != null && !apiKey.isBlank();
  }

  /** The vector for one piece of text, or {@code null} when no model is configured or reachable. */
  public float[] embed(String text) {
    if (!isConfigured() || text == null || text.isBlank()) {
      return null;
    }
    try {
      ObjectNode body = MAPPER.createObjectNode();
      body.put("model", MODEL);
      body.put("input", text);
      body.put("dimensions", DIMENSIONS);

      var request =
          HttpRequest.newBuilder(URI.create(baseUrl + "/v1/embeddings"))
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + apiKey)
              .timeout(Duration.ofSeconds(30))
              .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
              .build();
      var response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) {
        return null;
      }
      var values = MAPPER.readTree(response.body()).path("data").path(0).path("embedding");
      if (!values.isArray() || values.isEmpty()) {
        return null;
      }
      var out = new float[values.size()];
      for (int i = 0; i < values.size(); i++) {
        out[i] = (float) values.get(i).asDouble();
      }
      return out;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    } catch (Exception e) {
      return null;
    }
  }

  public List<float[]> embedAll(List<String> texts) {
    return texts.stream().map(this::embed).toList();
  }

  /** Cosine similarity, or {@code -1} when either vector is missing or the widths disagree. */
  public static double cosine(float[] a, float[] b) {
    if (a == null || b == null || a.length != b.length || a.length == 0) {
      return -1;
    }
    double dot = 0;
    double normA = 0;
    double normB = 0;
    for (int i = 0; i < a.length; i++) {
      dot += (double) a[i] * b[i];
      normA += (double) a[i] * a[i];
      normB += (double) b[i] * b[i];
    }
    if (normA == 0 || normB == 0) {
      return -1;
    }
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
  }
}
