package net.e175.klaus.formatter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public record CsvFormatter<T>(
    SerializerRegistry registry, boolean withHeaders, String delimiter, String lineSeparator)
    implements StreamingFormatter<T> {
  private static final char QUOTE = '"';
  private static final String ESCAPED_QUOTE = "\"\"";

  public CsvFormatter(SerializerRegistry registry, boolean withHeaders) {
    this(registry, withHeaders, ",", "\r\n");
  }

  public CsvFormatter(
      SerializerRegistry registry, boolean withHeaders, String delimiter, String lineSeparator) {
    this.registry = Objects.requireNonNull(registry, "Registry must not be null");
    this.withHeaders = withHeaders;
    this.delimiter = Objects.requireNonNull(delimiter, "Delimiter must not be null");
    this.lineSeparator = Objects.requireNonNull(lineSeparator, "Line separator must not be null");
  }

  @Override
  public void format(
      List<FieldDescriptor<T>> allFields, List<String> subset, Stream<T> items, Appendable out)
      throws IOException {
    validateInputs(allFields, subset, items, out);

    var fields = filterFields(allFields, subset);
    if (fields.isEmpty()) return;

    if (withHeaders) {
      writeHeaders(fields, out);
    }

    formatItems(items, fields, out, withHeaders);
  }

  private void writeHeaders(List<FieldDescriptor<T>> fields, Appendable out) throws IOException {
    var firstField = true;
    for (var field : fields) {
      if (!firstField) out.append(delimiter);
      firstField = false;

      String fieldName = field.name();
      if (needsEscaping(fieldName)) {
        out.append(QUOTE);
        if (fieldName.indexOf(QUOTE) >= 0) {
          out.append(fieldName.replace("\"", ESCAPED_QUOTE));
        } else {
          out.append(fieldName);
        }
        out.append(QUOTE);
      } else {
        out.append(fieldName);
      }
    }
    out.append(lineSeparator);
  }

  private void formatItems(
      Stream<T> items, List<FieldDescriptor<T>> fields, Appendable out, boolean hasHeaders) {

    items.forEachOrdered(
        item -> {
          try {
            formatRow(item, fields, out);
            out.append(lineSeparator);
          } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
          }
        });
  }

  private void formatRow(T item, List<FieldDescriptor<T>> fields, Appendable out)
      throws IOException {
    var firstField = true;
    for (var field : fields) {
      if (!firstField) out.append(delimiter);
      firstField = false;

      var value = field.extractor().apply(item);
      var serialized = registry.serialize(value, field);

      appendEscapedIfNecessary(serialized, out);
    }
  }

  private void appendEscapedIfNecessary(String value, Appendable out) throws IOException {
    if (needsEscaping(value)) {
      out.append(QUOTE);
      if (value.indexOf(QUOTE) >= 0) {
        out.append(value.replace("\"", ESCAPED_QUOTE));
      } else {
        out.append(value);
      }
      out.append(QUOTE);
    } else {
      out.append(value);
    }
  }

  private boolean needsEscaping(String value) {
    return value.contains(delimiter)
        || value.indexOf(QUOTE) >= 0
        || value.indexOf('\n') >= 0
        || value.indexOf('\r') >= 0;
  }
}
