package io.akka.memory.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What happens to an extracted entity before it becomes one.
 *
 * <p>Three rules, each of which decides an outcome rather than reporting a problem. The model
 * chooses types by <b>index</b>, and an index it invents is not an error — it degrades to the base
 * type. An excluded type is dropped silently. Unusable episode attribution widens to <em>every</em>
 * episode rather than none, which is the surprising direction: an entity the model could not place
 * ends up attributed everywhere rather than nowhere.
 *
 * <p>Kept out of the agent and free of any framework type, because these are the rules most likely
 * to be got subtly wrong and they should be checkable without starting anything.
 */
public final class ExtractionGuardrails {

  private ExtractionGuardrails() {}

  /** One entity as the model proposed it. */
  public record Candidate(String name, int entityTypeId, List<Integer> episodeIndices) {}

  /** One entity after the rules have been applied. */
  public record Accepted(String name, String type, List<Integer> episodeIndices) {}

  /**
   * Applies the three rules in order.
   *
   * @param offeredTypes the types the model was shown, in the order it was shown them — the index
   *     it returns is a position in this list
   * @param excludedTypes type names the caller does not want, matched after the index is resolved,
   *     so an out-of-range index that degrades to the base type can still be excluded by it
   * @param episodeCount how many episodes were in scope, which bounds the valid indices
   */
  public static List<Accepted> apply(
      List<Candidate> candidates,
      List<String> offeredTypes,
      Set<String> excludedTypes,
      int episodeCount) {

    var out = new ArrayList<Accepted>();
    for (Candidate candidate : candidates) {
      if (candidate.name() == null || candidate.name().isBlank()) {
        continue;
      }

      // An index outside the offered range degrades to the base type. It does not raise, and it
      // does not drop the entity — the entity survives, less specifically typed than intended.
      int id = candidate.entityTypeId();
      String type =
          id >= 0 && id < offeredTypes.size() ? offeredTypes.get(id) : Entity.BASE_TYPE;

      if (excludedTypes != null && excludedTypes.contains(type)) {
        continue;
      }

      var valid = new ArrayList<Integer>();
      for (Integer index : candidate.episodeIndices() == null ? List.<Integer>of() : candidate.episodeIndices()) {
        if (index != null && index >= 0 && index < episodeCount) {
          valid.add(index);
        }
      }
      // Nothing usable means every episode, not no episode. The entity becomes attributed more
      // widely than the model asked for, which is the opposite of what "invalid input" suggests.
      if (valid.isEmpty()) {
        for (int i = 0; i < episodeCount; i++) {
          valid.add(i);
        }
      }

      out.add(new Accepted(candidate.name(), type, List.copyOf(valid)));
    }
    return out;
  }
}
