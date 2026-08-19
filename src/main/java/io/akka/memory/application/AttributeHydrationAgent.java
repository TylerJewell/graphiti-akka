package io.akka.memory.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.Component;
import com.typesafe.config.Config;
import java.util.List;

/**
 * Fills an entity's summary from the facts <b>new in this episode</b>.
 *
 * <p>Showing only the new facts is what stops existing ones being restated. The length cap applied
 * to the result is a drop, not a truncation — see {@link AttributeCap}.
 *
 * <p>This is the one agent that runs on the reduced model. The source asks for a smaller model at
 * exactly the call sites that hydrate attributes and infer timestamps, and the full-size model
 * everywhere else; matching that matters because model choice changes what comes back.
 *
 * <p>Which reduced model that is follows whichever provider is selected — each has a {@code -small}
 * twin in the configuration. Resolving it here rather than naming one in the file is what keeps
 * {@code MODEL_PROVIDER} a single decision instead of two that can disagree.
 */
@Component(id = "attribute-hydration-agent")
public class AttributeHydrationAgent extends Agent {

  public record Summary(String summary) {}

  public record Request(String entityName, String existingSummary, List<String> newFacts) {}

  private final String smallModelPath;

  public AttributeHydrationAgent(Config config) {
    this.smallModelPath = smallModelPath(config);
  }

  /** The {@code -small} twin of the selected provider, or the provider itself if it has none. */
  public static String smallModelPath(Config config) {
    String selected = config.getString("akka.javasdk.agent.model-provider");
    String small = "akka.javasdk.agent." + selected + "-small";
    return config.hasPath(small) ? small : "akka.javasdk.agent." + selected;
  }

  public Effect<Summary> hydrate(Request request) {
    return effects()
        .model(ModelProvider.fromConfig(smallModelPath))
        .systemMessage(Prompts.system("extract_nodes.extract_summary"))
        .userMessage(
            Prompts.userTemplate("extract_nodes.extract_summary")
                + "\n\n<ENTITY>\n"
                + request.entityName()
                + "\n</ENTITY>\n\n<EXISTING_SUMMARY>\n"
                + request.existingSummary()
                + "\n</EXISTING_SUMMARY>\n\n<NEW_FACTS>\n"
                + String.join("\n", request.newFacts())
                + "\n</NEW_FACTS>")
        .responseConformsTo(Summary.class)
        .onFailure(error -> new Summary(request.existingSummary()))
        .thenReply();
  }
}
