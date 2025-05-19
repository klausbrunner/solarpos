package net.e175.klaus.formatter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Describes a field: its name, how to extract it from a model object, and formatting hints.
 *
 * @param <T> The type of the model object from which to extract field values
 */
public record FieldDescriptor<T>(
    String name, Function<T, ?> extractor, Map<String, Object> formatHints) {

  /** Creates a field descriptor with defensive validation and immutable formatHints. */
  public FieldDescriptor {
    Objects.requireNonNull(name, "Field name must not be null");
    Objects.requireNonNull(extractor, "Field extractor must not be null");
    Objects.requireNonNull(formatHints, "Format hints must not be null");

    // Ensure formatHints is immutable to prevent exposing internal representation
    formatHints = Map.copyOf(formatHints);
  }

  /** Creates a field descriptor with name and extractor function, but no format hints. */
  public FieldDescriptor(String name, Function<T, ?> extractor) {
    this(name, extractor, Map.of());
  }

  /** Creates a new field descriptor with an additional format hint. */
  public FieldDescriptor<T> withHint(String key, Object value) {
    Objects.requireNonNull(key, "Hint key must not be null");

    var newHints = new HashMap<>(formatHints);
    newHints.put(key, value);
    return new FieldDescriptor<>(name, extractor, newHints);
  }

  /**
   * Creates a new field descriptor with a precision hint for numeric values.
   *
   * @param precision The precision value for formatting
   * @return A new field descriptor with the precision hint
   */
  public FieldDescriptor<T> withPrecision(int precision) {
    return withHint("precision", precision);
  }

  /**
   * Creates a new field descriptor with a date/time pattern hint.
   *
   * @param pattern The date/time pattern for formatting
   * @return A new field descriptor with the pattern hint
   */
  public FieldDescriptor<T> withPattern(String pattern) {
    Objects.requireNonNull(pattern, "Pattern must not be null");
    return withHint("pattern", pattern);
  }

  /**
   * Factory method to create a numeric field descriptor with precision.
   *
   * @param name The field name
   * @param extractor Function to extract the field value
   * @param precision The precision value for formatting
   * @return A new field descriptor with precision hint
   * @param <T> The type of the model object
   */
  public static <T> FieldDescriptor<T> numeric(
      String name, Function<T, ? extends Number> extractor, int precision) {
    return new FieldDescriptor<>(name, extractor, Map.of("precision", precision));
  }

  /**
   * Factory method to create a date/time field descriptor with pattern.
   *
   * @param name The field name
   * @param extractor Function to extract the field value
   * @param pattern The date/time pattern for formatting
   * @return A new field descriptor with pattern hint
   * @param <T> The type of the model object
   */
  public static <T> FieldDescriptor<T> dateTime(
      String name, Function<T, ?> extractor, String pattern) {
    return new FieldDescriptor<>(name, extractor, Map.of("pattern", pattern));
  }
}
