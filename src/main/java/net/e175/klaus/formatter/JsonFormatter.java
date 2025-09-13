package net.e175.klaus.formatter;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

public record JsonFormatter<T>(SerializerRegistry registry, String lineSeparator)
    implements StreamingFormatter<T> {
  private static final int INITIAL_BUFFER_SIZE = 256;
  private static final DateTimeFormatter ISO_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

  public JsonFormatter(SerializerRegistry registry) {
    this(registry, "\n");
  }

  public JsonFormatter(SerializerRegistry registry, String lineSeparator) {
    this.registry = Objects.requireNonNull(registry, "Registry must not be null");
    this.lineSeparator = Objects.requireNonNull(lineSeparator, "Line separator must not be null");
  }

  @Override
  public void format(
      List<FieldDescriptor<T>> allFields, List<String> subset, Stream<T> items, Appendable out)
      throws IOException {
    validateInputs(allFields, subset, items, out);

    var fields = filterFields(allFields, subset);
    if (fields.isEmpty()) return;

    var iterator = items.iterator();
    while (iterator.hasNext()) {
      formatItem(iterator.next(), fields, out);

      if (iterator.hasNext()) {
        out.append(lineSeparator);
      }
    }
  }

  private void formatItem(T item, List<FieldDescriptor<T>> fields, Appendable out)
      throws IOException {
    out.append('{');

    var first = true;
    for (var field : fields) {
      if (!first) out.append(',');
      first = false;

      out.append('"').append(field.name()).append('"').append(':');

      var value = field.extractor().apply(item);
      switch (value) {
        case null -> out.append("null");
        case Double d -> appendDouble(d, getPrecision(field), out);
        case Float f -> appendFloat(f, getPrecision(field), out);
        case Number n -> out.append(n.toString());
        case Boolean b -> out.append(b.toString());
        case ZonedDateTime zdt -> out.append('"').append(zdt.format(ISO_FORMAT)).append('"');
        default -> out.append('"').append(escapeJson(registry.serialize(value, field))).append('"');
      }
    }

    out.append('}');
  }

  private static String escapeJson(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }

    int len = text.length();
    StringBuilder sb = new StringBuilder(Math.max(len + 8, INITIAL_BUFFER_SIZE));

    for (int i = 0; i < len; i++) {
      char c = text.charAt(i);
      switch (c) {
        case '\\' -> sb.append("\\\\");
        case '"' -> sb.append("\\\"");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> sb.append(c);
      }
    }

    return sb.toString();
  }

  private int getPrecision(FieldDescriptor<T> field) {
    return (int) field.hints().getOrDefault("precision", 6);
  }

  private void appendDouble(double value, int precision, Appendable out) throws IOException {
    out.append(String.format(Locale.US, SerializerRegistry.FORMAT_SPECS[precision], value));
  }

  private void appendFloat(float value, int precision, Appendable out) throws IOException {
    appendDouble(value, precision, out);
  }
}
