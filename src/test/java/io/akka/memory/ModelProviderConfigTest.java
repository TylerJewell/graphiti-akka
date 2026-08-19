package io.akka.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.akka.memory.application.AttributeHydrationAgent;
import io.akka.memory.application.Embedder;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The provider sections are configuration rather than code, so nothing else would catch a typo in
 * them until an agent call failed at runtime — halfway through ingesting somebody's messages, with
 * the caller already told the work was accepted.
 */
class ModelProviderConfigTest {

  private static final List<String> SUPPORTED =
      List.of(
          "openai", "anthropic", "googleai-gemini", "mistral-ai",
          "vertex-ai", "azure-openai", "bedrock", "hugging-face",
          "ollama", "local-ai");

  private static Config selecting(String provider) {
    // Stands in for MODEL_PROVIDER, which cannot be set inside a running JVM.
    return ConfigFactory.parseString("akka.javasdk.agent.model-provider = " + provider)
        .withFallback(ConfigFactory.load())
        .resolve();
  }

  @Test
  @DisplayName("every supported provider has a section that declares its own name")
  void everySupportedProviderResolves() {
    var config = ConfigFactory.load();

    for (var name : SUPPORTED) {
      var path = "akka.javasdk.agent." + name;
      assertThat(config.hasPath(path)).as("section %s exists", path).isTrue();
      assertThat(config.getString(path + ".provider"))
          .as("%s declares its own provider name", name)
          .isEqualTo(name);
    }
  }

  @Test
  @DisplayName("every supported provider has a reduced twin on the same provider")
  void everyProviderHasASmallTwin() {
    var config = ConfigFactory.load();

    for (var name : SUPPORTED) {
      var path = "akka.javasdk.agent." + name + "-small";
      assertThat(config.hasPath(path)).as("section %s exists", path).isTrue();
      // The twin must stay on the same provider. A copy that drifted onto another one would
      // send half the pipeline to an account the operator never configured.
      assertThat(config.getString(path + ".provider"))
          .as("%s-small stays on %s", name, name)
          .isEqualTo(name);
    }
  }

  @Test
  @DisplayName("choosing a provider chooses its reduced twin with it")
  void selectingAProviderSelectsItsTwin() {
    for (var name : SUPPORTED) {
      assertThat(AttributeHydrationAgent.smallModelPath(selecting(name)))
          .as("the hydration agent follows the selected provider")
          .isEqualTo("akka.javasdk.agent." + name + "-small");
    }
  }

  @Test
  @DisplayName("an unknown provider falls back to its own section rather than a missing twin")
  void anUnknownProviderDoesNotLookForATwin() {
    var config =
        ConfigFactory.parseString(
                "akka.javasdk.agent.model-provider = custom\n"
                    + "akka.javasdk.agent.custom.provider = custom")
            .withFallback(ConfigFactory.load())
            .resolve();

    assertThat(AttributeHydrationAgent.smallModelPath(config))
        .isEqualTo("akka.javasdk.agent.custom");
  }

  @Test
  @DisplayName("the providers that can carry a default model do")
  void defaultModelsArePresentWhereTheyCanBe() {
    var config = ConfigFactory.load();

    for (var name : List.of("openai", "anthropic", "googleai-gemini")) {
      assertThat(config.getString("akka.javasdk.agent." + name + ".model-name"))
          .as("%s has a default model", name)
          .isNotBlank();
      assertThat(config.getString("akka.javasdk.agent." + name + "-small.model-name"))
          .as("%s has a default reduced model", name)
          .isNotBlank();
    }
  }

  @Test
  @DisplayName("the full-size and reduced models are different models")
  void theTwinIsActuallySmaller() {
    var config = ConfigFactory.load();

    for (var name : List.of("openai", "anthropic", "googleai-gemini")) {
      // Identical names would mean the reduced tier silently stopped existing, and the only
      // visible symptom would be a larger bill.
      assertThat(config.getString("akka.javasdk.agent." + name + "-small.model-name"))
          .as("%s-small differs from %s", name, name)
          .isNotEqualTo(config.getString("akka.javasdk.agent." + name + ".model-name"));
    }
  }

  @Test
  @DisplayName("openai is the default, matching the source system")
  void defaultsToOpenAi() {
    // MODEL_PROVIDER is unset in the test environment, so the default stands.
    assertThat(ConfigFactory.load().getString("akka.javasdk.agent.model-provider"))
        .isEqualTo("openai");
    assertThat(ConfigFactory.load().getString("akka.javasdk.agent.openai.model-name"))
        .isEqualTo("gpt-5.5");
    assertThat(ConfigFactory.load().getString("akka.javasdk.agent.openai-small.model-name"))
        .isEqualTo("gpt-4.1-nano");
  }

  @Test
  @DisplayName("embedding is configured separately from the agents")
  void embeddingIsItsOwnAccount() {
    var embedder = Embedder.fromConfig(ConfigFactory.load());

    // Choosing a chat provider must not decide whether search works. Several providers offer no
    // embeddings at all, so the two are configured apart and default to the source's model.
    assertThat(embedder.model()).isEqualTo("text-embedding-3-small");
    assertThat(embedder.dimensions()).isEqualTo(1024);

    var withAnthropicSelected = selecting("anthropic");
    assertThat(Embedder.fromConfig(withAnthropicSelected).model())
        .as("switching chat provider leaves embedding alone")
        .isEqualTo("text-embedding-3-small");
  }
}
