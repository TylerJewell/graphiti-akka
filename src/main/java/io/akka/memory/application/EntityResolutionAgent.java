package io.akka.memory.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import java.util.List;

/**
 * The escalation stage of entity recognition — reached only when the deterministic stages decline.
 *
 * <p>The model selects a candidate <b>by index into a list it was shown</b>. It never sees or emits
 * an identifier, so it cannot name an entity that was not offered: the worst it can do is an
 * out-of-range integer, which the caller absorbs into "no duplicate".
 */
@Component(id = "entity-resolution-agent")
public class EntityResolutionAgent extends Agent {

  /**
   * @param name required by the response schema and <b>never read</b>. It conditions what the model
   *     generates, so removing it changes the verdicts. A tool that strips unread fields will
   *     delete it and silently alter resolution quality.
   */
  public record Resolution(int id, String name, int duplicateCandidateId) {}

  public record Resolutions(List<Resolution> entityResolutions) {}

  public record Request(List<String> extractedNames, List<String> candidateNames) {}

  public Effect<Resolutions> resolve(Request request) {
    return effects()
        .systemMessage(Prompts.system("dedupe_nodes.nodes"))
        .userMessage(
            Prompts.userTemplate("dedupe_nodes.nodes")
                + "\n\n<EXTRACTED_ENTITIES>\n"
                + indexed(request.extractedNames())
                + "\n</EXTRACTED_ENTITIES>\n\n<EXISTING_ENTITIES>\n"
                + indexed(request.candidateNames())
                + "\n</EXISTING_ENTITIES>")
        .responseConformsTo(Resolutions.class)
        .onFailure(error -> new Resolutions(List.of()))
        .thenReply();
  }

  private static String indexed(List<String> names) {
    var sb = new StringBuilder();
    for (int i = 0; i < names.size(); i++) {
      sb.append(i).append(": ").append(names.get(i)).append('\n');
    }
    return sb.toString().trim();
  }
}
