package net.e175.klaus.formatter;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public record FieldDescriptor<T>(String name, Function<T, ?> extractor, Map<String, Object> hints) {
  public FieldDescriptor(String name, Function<T, ?> extractor) {
    this(name, extractor, Map.of());
  }

  public FieldDescriptor(String name, Function<T, ?> extractor, Map<String, Object> hints) {
    this.name = Objects.requireNonNull(name, "Field name must not be null");
    this.extractor = Objects.requireNonNull(extractor, "Field extractor must not be null");
    this.hints = Map.copyOf(hints);
  }

  public static <T> FieldDescriptor<T> numeric(
      String name, Function<T, ? extends Number> extractor, int precision) {
    return new FieldDescriptor<>(name, extractor, Map.of("precision", precision));
  }

  public static <T> FieldDescriptor<T> dateTime(
      String name, Function<T, ZonedDateTime> extractor, String pattern) {
    return new FieldDescriptor<>(name, extractor, Map.of("pattern", pattern));
  }

  public FieldDescriptor<T> withHint(String key, Object value) {
    var newHints = new HashMap<>(this.hints);
    newHints.put(key, value);
    return new FieldDescriptor<>(this.name, this.extractor, newHints);
  }

  public FieldDescriptor<T> withPrecision(int precision) {
    return withHint("precision", precision);
  }

  public FieldDescriptor<T> withPattern(String pattern) {
    Objects.requireNonNull(pattern, "Pattern must not be null");
    return withHint("pattern", pattern);
  }

  public FieldDescriptor<T> withUnit(String unit) {
    Objects.requireNonNull(unit, "Unit must not be null");
    return withHint("unit", unit);
  }
}
