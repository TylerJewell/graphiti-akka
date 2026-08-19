package io.akka.memory.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import io.akka.memory.domain.Episode;
import java.util.List;

/** Turns episode prose into candidate entities. */
@Component(id = "entity-extraction-agent")
public class EntityExtractionAgent extends Agent {

  /** What the model returns. Types are chosen by <b>index</b>, never by name. */
  public record ExtractedEntity(String name, int entityTypeId, List<Integer> episodeIndices) {}

  public record ExtractedEntities(List<ExtractedEntity> extractedEntities) {}

  public record Request(String content, Episode.Kind kind, List<String> offeredTypes) {}

  public Effect<ExtractedEntities> extract(Request request) {
    String promptName =
        switch (request.kind()) {
          case MESSAGE -> "extract_nodes.extract_message";
          case JSON -> "extract_nodes.extract_json";
          case TEXT -> "extract_nodes.extract_text";
        };
    String types = String.join("\n", numbered(request.offeredTypes()));
    return effects()
        .systemMessage(Prompts.system(promptName))
        .userMessage(
            Prompts.userTemplate(promptName)
                + "\n\n<ENTITY_TYPES>\n"
                + types
                + "\n</ENTITY_TYPES>\n\n<EPISODE>\n"
                + request.content()
                + "\n</EPISODE>")
        .responseConformsTo(ExtractedEntities.class)
        .onFailure(error -> new ExtractedEntities(List.of()))
        .thenReply();
  }

  private static List<String> numbered(List<String> types) {
    var out = new java.util.ArrayList<String>();
    for (int i = 0; i < types.size(); i++) {
      out.add(i + ": " + types.get(i));
    }
    return out;
  }
}
