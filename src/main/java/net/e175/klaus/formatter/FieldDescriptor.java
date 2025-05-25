package net.e175.klaus.formatter;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Descriptor for a field in a data record, including name, extractor function, and format hints.
 *
 * @param <T> The type of data record
 */
public class FieldDescriptor<T> {
  private final String name;
  private final Function<T, ?> extractor;
  private final Map<String, Object> hints;

  /**
   * Creates a new field descriptor with the given name and extractor function.
   *
   * @param name The field name
   * @param extractor The function to extract the field value from a data record
   */
  public FieldDescriptor(String name, Function<T, ?> extractor) {
    this(name, extractor, Map.of());
  }

  /**
   * Creates a new field descriptor with the given name, extractor function, and format hints.
   *
   * @param name The field name
   * @param extractor The function to extract the field value from a data record
   * @param hints Format hints for serialization
   */
  public FieldDescriptor(String name, Function<T, ?> extractor, Map<String, Object> hints) {
    this.name = Objects.requireNonNull(name, "Field name must not be null");
    this.extractor = Objects.requireNonNull(extractor, "Field extractor must not be null");
    this.hints = new HashMap<>(hints);
  }

  /**
   * Factory method for numeric fields with a specified precision.
   *
   * @param name The field name
   * @param extractor The function to extract the field value from a data record
   * @param precision The number of decimal places to display
   * @param <T> The type of data record
   * @return A new field descriptor for the numeric field
   */
  public static <T> FieldDescriptor<T> numeric(
      String name, Function<T, ? extends Number> extractor, int precision) {
    return new FieldDescriptor<>(name, extractor, Map.of("precision", precision));
  }

  /**
   * Factory method for date/time fields with a specified format pattern.
   *
   * @param name The field name
   * @param extractor The function to extract the field value from a data record
   * @param pattern The date/time format pattern
   * @param <T> The type of data record
   * @return A new field descriptor for the date/time field
   */
  public static <T> FieldDescriptor<T> dateTime(
      String name, Function<T, ZonedDateTime> extractor, String pattern) {
    return new FieldDescriptor<>(name, extractor, Map.of("pattern", pattern));
  }

  /**
   * Adds a format hint to this field descriptor.
   *
   * @param key The hint key
   * @param value The hint value
   * @return A new field descriptor with the added hint
   */
  public FieldDescriptor<T> withHint(String key, Object value) {
    var newHints = new HashMap<>(this.hints);
    newHints.put(key, value);
    return new FieldDescriptor<>(this.name, this.extractor, newHints);
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

  /** Returns the field name. */
  public String name() {
    return name;
  }

  /** Returns the field extractor function. */
  public Function<T, ?> extractor() {
    return extractor;
  }

  /** Returns the format hints map. */
  public Map<String, Object> formatHints() {
    return Map.copyOf(hints);
  }
}
