package io.akka.memory.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import java.util.List;

/**
 * Turns episode prose into candidate facts, each with the interval over which it is true.
 *
 * <p>Validity is inferred from the <em>content</em>, not supplied by the caller and not taken from
 * ingestion time. A value the model formats badly degrades to absent, which makes the fact inert.
 */
@Component(id = "fact-extraction-agent")
public class FactExtractionAgent extends Agent {

  public record ExtractedFact(
      String sourceEntityName,
      String targetEntityName,
      String relationType,
      String fact,
      String validAt,
      String invalidAt) {}

  public record ExtractedFacts(List<ExtractedFact> edges) {}

  public record Request(String content, List<String> entityNames, String referenceTime) {}

  public Effect<ExtractedFacts> extract(Request request) {
    return effects()
        .systemMessage(Prompts.system("extract_edges.edge"))
        .userMessage(
            Prompts.userTemplate("extract_edges.edge")
                + "\n\n<ENTITIES>\n"
                + String.join("\n", request.entityNames())
                + "\n</ENTITIES>\n\n<REFERENCE_TIME>\n"
                + request.referenceTime()
                + "\n</REFERENCE_TIME>\n\n<EPISODE>\n"
                + request.content()
                + "\n</EPISODE>")
        .responseConformsTo(ExtractedFacts.class)
        .onFailure(error -> new ExtractedFacts(List.of()))
        .thenReply();
  }
}
