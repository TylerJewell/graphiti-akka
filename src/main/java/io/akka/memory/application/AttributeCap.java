package io.akka.memory.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The length guard on hydrated attributes.
 *
 * <p>It <b>drops</b> rather than truncates: an over-long value is removed entirely, so the entity
 * ends up without the attribute rather than with a shortened one. Two axes are enforced — any
 * single item, and a list's total.
 *
 * <p><b>Required fields are exempt</b>, because dropping one would fail validation. The guard
 * therefore protects optional attributes only, and a caller who marks a field required silently
 * disables a system safety limit. Reproduced, not corrected.
 */
public final class AttributeCap {

  public static final int DEFAULT_MAX_LENGTH = 250;
  public static final int LIST_TOTAL_MULTIPLIER = 8;

  private AttributeCap() {}

  public static Map<String, Object> apply(
      Map<String, Object> attributes, Set<String> requiredFields, int maxLength) {
    var kept = new LinkedHashMap<String, Object>();
    attributes.forEach(
        (name, value) -> {
          if (requiredFields.contains(name) || !exceeds(value, maxLength)) {
            kept.put(name, value);
          }
        });
    return Map.copyOf(kept);
  }

  public static Map<String, Object> apply(
      Map<String, Object> attributes, Set<String> requiredFields) {
    return apply(attributes, requiredFields, DEFAULT_MAX_LENGTH);
  }

  static boolean exceeds(Object value, int maxLength) {
    if (value instanceof String s) {
      return s.length() > maxLength;
    }
    if (value instanceof List<?> list) {
      int total = 0;
      for (Object item : list) {
        if (item instanceof String s) {
          if (s.length() > maxLength) {
            return true; // per-item axis
          }
          total += s.length();
        }
      }
      return total > maxLength * LIST_TOTAL_MULTIPLIER; // aggregate axis
    }
    return false;
  }
}
