package io.akka.memory.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * A person, organisation or thing the memory knows about.
 *
 * <p>Identity is not a field here. There is no natural key — it is the outcome of the recognition
 * cascade, and the same two names can resolve differently depending on what else exists and what
 * the candidate search returned.
 */
public record Entity(
    String id,
    String partition,
    String name,
    List<String> types,
    String summary,
    Map<String, Object> attributes) {

  /** The base type every entity carries. */
  public static final String BASE_TYPE = "Entity";

  public Entity {
    // Sorted, because the source builds this from an unordered set whose iteration order varies
    // between processes. There is no order to match, so the port picks a reproducible one.
    types = List.copyOf(new TreeSet<>(types));
    attributes = Map.copyOf(attributes);
  }

  public static Entity create(String id, String partition, String name, String resolvedType) {
    var types =
        BASE_TYPE.equals(resolvedType) ? List.of(BASE_TYPE) : List.of(BASE_TYPE, resolvedType);
    return new Entity(id, partition, name, types, "", Map.of());
  }

  /** The more specific of two entities that resolved to the same normalised name. */
  public Entity mergeWith(Entity other) {
    Entity moreSpecific = other.types().size() > this.types().size() ? other : this;
    return new Entity(
        id,
        partition,
        moreSpecific.name(),
        moreSpecific.types(),
        summary.isEmpty() ? other.summary() : summary,
        attributes.isEmpty() ? other.attributes() : attributes);
  }

  public Entity withSummary(String newSummary) {
    return new Entity(id, partition, name, types, newSummary, attributes);
  }

  public Entity withAttributes(Map<String, Object> newAttributes) {
    return new Entity(id, partition, name, types, summary, newAttributes);
  }

  public Optional<String> specificType() {
    return types.stream().filter(t -> !BASE_TYPE.equals(t)).findFirst();
  }
}
