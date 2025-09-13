package net.e175.klaus.formatter;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import net.e175.klaus.solarpos.util.TimeFormats;

public final class SerializerRegistry {
  static final String[] FORMAT_SPECS = {
    "%.0f", "%.1f", "%.2f", "%.3f", "%.4f", "%.5f", "%.6f", "%.7f", "%.8f", "%.9f"
  };

  private final Map<Class<?>, BiFunction<Object, Map<String, Object>, String>> serializers =
      new HashMap<>();
  private String nullValue = "";

  private SerializerRegistry() {}

  public static SerializerRegistry defaults() {
    return new SerializerRegistry()
        .register(String.class, (s, hints) -> s)
        .register(Number.class, SerializerRegistry::formatNumber)
        .register(Boolean.class, (b, hints) -> b.toString())
        .register(ZonedDateTime.class, SerializerRegistry::formatDateTime);
  }

  private static String formatDateTime(Object dt, Map<String, Object> hints) {
    var pattern = (String) hints.getOrDefault("pattern", "yyyy-MM-dd HH:mm:ssXXX");
    var formatter = DateTimeFormatter.ofPattern(pattern);
    return ((ZonedDateTime) dt).format(formatter);
  }

  private static <T extends Number> BiFunction<T, Map<String, Object>, String> createFloatFormatter(
      int defaultPrecision) {
    return (num, hints) -> {
      var precision = (int) hints.getOrDefault("precision", defaultPrecision);
      return String.format(Locale.US, FORMAT_SPECS[precision], num);
    };
  }

  private static <T extends Number>
      BiFunction<T, Map<String, Object>, String> createFloatFormatterWithUnits(
          int defaultPrecision) {
    return (num, hints) -> {
      var precision = (int) hints.getOrDefault("precision", defaultPrecision);
      String result = String.format(Locale.US, FORMAT_SPECS[precision], num);

      var unit = (String) hints.get("unit");
      return unit != null ? String.format("%28s%s", result, unit) : result;
    };
  }

  private static final java.util.concurrent.ConcurrentHashMap<String, DateTimeFormatter>
      FORMATTER_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

  private static BiFunction<ZonedDateTime, Map<String, Object>, String> createDateTimeFormatter(
      String nullValue, DateTimeFormatter defaultFormat, boolean quoted) {
    return (dt, hints) -> {
      if (dt == null) return nullValue;

      var pattern = (String) hints.get("pattern");
      DateTimeFormatter formatter =
          pattern != null
              ? FORMATTER_CACHE.computeIfAbsent(pattern, DateTimeFormatter::ofPattern)
              : defaultFormat;

      String formatted = dt.format(formatter);
      return quoted ? '"' + formatted + '"' : formatted;
    };
  }

  public static SerializerRegistry forText() {
    return defaults()
        .register(Double.class, createFloatFormatterWithUnits(2))
        .register(Float.class, createFloatFormatterWithUnits(2))
        .register(
            ZonedDateTime.class,
            createDateTimeFormatter("none", TimeFormats.ISO_HUMAN_LOCAL_DATE_TIME_REDUCED, false));
  }

  public static SerializerRegistry forJson() {
    return defaults()
        .register(Double.class, createFloatFormatter(6))
        .register(Float.class, createFloatFormatter(6))
        .register(
            ZonedDateTime.class,
            createDateTimeFormatter(
                "null", DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"), true));
  }

  public static SerializerRegistry forCsv() {
    return defaults()
        .register(Double.class, createFloatFormatter(5))
        .register(Float.class, createFloatFormatter(5))
        .register(
            ZonedDateTime.class,
            createDateTimeFormatter("", TimeFormats.ISO_LOCAL_DATE_TIME_REDUCED, false));
  }

  private static String formatNumber(Object n, Map<String, Object> hints) {
    var precision = (int) hints.getOrDefault("precision", 6);

    return switch (n) {
      case Double d -> String.format(Locale.US, FORMAT_SPECS[precision], d);
      case Float f -> String.format(Locale.US, FORMAT_SPECS[precision], f);
      case Number num -> num.toString();
      default -> n.toString();
    };
  }

  public <U> SerializerRegistry register(
      Class<U> type, BiFunction<U, Map<String, Object>, String> fn) {
    serializers.put(type, (obj, hints) -> fn.apply(type.cast(obj), hints));
    return this;
  }

  public SerializerRegistry withNullValue(String nullValue) {
    this.nullValue = Objects.requireNonNull(nullValue);
    return this;
  }

  public String serialize(Object obj, FieldDescriptor<?> field) {
    Objects.requireNonNull(field, "Field descriptor must not be null");

    if (obj == null) return nullValue;

    Map<String, Object> hintsWithFieldName;
    if (field.hints().isEmpty()) {
      hintsWithFieldName = Map.of("fieldName", field.name());
    } else {
      hintsWithFieldName = new HashMap<>(field.hints());
      hintsWithFieldName.put("fieldName", field.name());
    }

    var serializer = serializers.get(obj.getClass());
    if (serializer != null) {
      return serializer.apply(obj, hintsWithFieldName);
    }

    return serializers.entrySet().stream()
        .filter(entry -> entry.getKey().isAssignableFrom(obj.getClass()))
        .findFirst()
        .map(entry -> entry.getValue().apply(obj, hintsWithFieldName))
        .orElseGet(obj::toString);
  }

  public String serialize(Object obj) {
    return serialize(obj, new FieldDescriptor<>("", x -> x));
  }

  public <T> int getPrecision(FieldDescriptor<T> field) {
    return (int) field.hints().getOrDefault("precision", 5);
  }

  public void appendDouble(double value, int precision, Appendable out) throws IOException {
    out.append(String.format(Locale.US, FORMAT_SPECS[precision], value));
  }

  public void appendFloat(float value, int precision, Appendable out) throws IOException {
    appendDouble(value, precision, out);
  }
}
