package io.akka.memory.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import java.util.List;

/**
 * Fills an entity's summary from the facts <b>new in this episode</b>.
 *
 * <p>Showing only the new facts is what stops existing ones being restated. The length cap applied
 * to the result is a drop, not a truncation — see {@link AttributeCap}.
 */
@Component(id = "attribute-hydration-agent")
public class AttributeHydrationAgent extends Agent {

  public record Summary(String summary) {}

  public record Request(String entityName, String existingSummary, List<String> newFacts) {}

  public Effect<Summary> hydrate(Request request) {
    return effects()
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
