package net.e175.klaus.formatter;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/** Registry for type-to-string serializers with sensible defaults. */
public final class SerializerRegistry {
  private final Map<Class<?>, BiFunction<Object, Map<String, Object>, String>> serializers =
      new HashMap<>();
  private String nullValue = "";

  private SerializerRegistry() {}

  /** Creates a registry with default serializers for common types. */
  public static SerializerRegistry defaults() {
    // Ensure US locale is used for all formatters
    return new SerializerRegistry()
        .register(String.class, (s, hints) -> s)
        .register(Number.class, SerializerRegistry::formatNumber)
        .register(Boolean.class, (b, hints) -> b.toString())
        .register(ZonedDateTime.class, SerializerRegistry::formatDateTime);
  }

  /**
   * Formats a date/time object using the pattern from hints or a default pattern.
   *
   * @param dt The date/time object to format
   * @param hints The format hints map
   * @return The formatted date/time string
   */
  private static String formatDateTime(Object dt, Map<String, Object> hints) {
    // Use a default pattern if none is provided
    var pattern = (String) hints.getOrDefault("pattern", "yyyy-MM-dd HH:mm:ssXXX");
    // Create a formatter for the pattern
    var formatter = DateTimeFormatter.ofPattern(pattern);
    return ((ZonedDateTime) dt).format(formatter);
  }

  /**
   * Creates a formatter for floating point numbers with the specified default precision.
   *
   * @param defaultPrecision The default precision to use if not specified in hints
   * @return A function that formats floating point numbers
   */
  private static <T extends Number> BiFunction<T, Map<String, Object>, String> createFloatFormatter(
      int defaultPrecision) {
    return (num, hints) -> {
      var precision = (int) hints.getOrDefault("precision", defaultPrecision);
      return String.format(Locale.US, "%." + precision + "f", num);
    };
  }

  /** Creates text-optimized serializer registry with 2 decimal places for floats. */
  public static SerializerRegistry forText() {
    return defaults()
        .register(Double.class, createFloatFormatter(2))
        .register(Float.class, createFloatFormatter(2));
  }

  /** Creates JSON-optimized serializer registry with 6 decimal places for floats. */
  public static SerializerRegistry forJson() {
    return defaults()
        .register(Double.class, createFloatFormatter(6))
        .register(Float.class, createFloatFormatter(6))
        .register(
            ZonedDateTime.class, (dt, hints) -> dt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
  }

  /** Creates CSV-optimized serializer registry with 5 decimal places for floats. */
  public static SerializerRegistry forCsv() {
    return defaults()
        .register(Double.class, createFloatFormatter(5))
        .register(Float.class, createFloatFormatter(5));
  }

  /**
   * Formats a number with the specified precision from hints.
   *
   * @param n The number to format
   * @param hints The format hints map
   * @return The formatted number string
   */
  private static String formatNumber(Object n, Map<String, Object> hints) {
    var precision = (int) hints.getOrDefault("precision", 6);

    // Use pattern matching with switch for cleaner code
    return switch (n) {
      case Double d -> String.format(Locale.US, "%." + precision + "f", d);
      case Float f -> String.format(Locale.US, "%." + precision + "f", f);
      case Number num -> num.toString();
      default -> n.toString();
    };
  }

  /** Register a serializer for a given type. */
  public <U> SerializerRegistry register(
      Class<U> type, BiFunction<U, Map<String, Object>, String> fn) {
    serializers.put(type, (obj, hints) -> fn.apply(type.cast(obj), hints));
    return this;
  }

  /** Set a custom null representation. */
  public SerializerRegistry withNullValue(String nullValue) {
    this.nullValue = Objects.requireNonNull(nullValue);
    return this;
  }

  /**
   * Serialize an object using field hints if available.
   *
   * @param obj The object to serialize (may be null)
   * @param field The field descriptor containing format hints
   * @return The serialized string representation
   */
  public String serialize(Object obj, FieldDescriptor<?> field) {
    Objects.requireNonNull(field, "Field descriptor must not be null");

    if (obj == null) return nullValue;

    // Look for exact type match first
    var serializer = serializers.get(obj.getClass());
    if (serializer != null) {
      return serializer.apply(obj, field.formatHints());
    }

    // Try to find assignable type match (using a more efficient approach)
    for (var entry : serializers.entrySet()) {
      if (entry.getKey().isAssignableFrom(obj.getClass())) {
        return entry.getValue().apply(obj, field.formatHints());
      }
    }

    // No matching serializer found, use toString
    return obj.toString();
  }

  /**
   * Serialize an object without field hints.
   *
   * @param obj The object to serialize (may be null)
   * @return The serialized string representation
   */
  public String serialize(Object obj) {
    return serialize(obj, new FieldDescriptor<>("", x -> x));
  }
}
