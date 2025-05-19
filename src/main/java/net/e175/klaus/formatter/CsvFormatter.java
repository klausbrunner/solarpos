package net.e175.klaus.formatter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** CSV output formatter. */
public class CsvFormatter<T> implements StreamingFormatter<T> {
  private final SerializerRegistry registry;
  private final boolean withHeaders;
  private final String delimiter;
  private final String lineSeparator;
  private static final char QUOTE = '"';
  private static final String ESCAPED_QUOTE = "\"\"";

  /**
   * Creates a CSV formatter with default comma delimiter and CRLF line separator.
   *
   * @param registry The serializer registry to use (must not be null)
   * @param withHeaders Whether to include headers in the output
   */
  public CsvFormatter(SerializerRegistry registry, boolean withHeaders) {
    this(registry, withHeaders, ",", "\r\n");
  }

  /**
   * Creates a CSV formatter with custom delimiter and line separator.
   *
   * @param registry The serializer registry to use (must not be null)
   * @param withHeaders Whether to include headers in the output
   * @param delimiter The field delimiter character/string
   * @param lineSeparator The line separator to use between rows
   */
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

    // Write headers if requested
    if (withHeaders) {
      writeHeaders(fields, out);
    }

    // Process each item
    formatItems(items, fields, out);
  }

  /**
   * Writes the CSV header row with field names.
   *
   * @param fields The field descriptors containing field names
   * @param out The output destination
   * @throws IOException If an I/O error occurs
   */
  private void writeHeaders(List<FieldDescriptor<T>> fields, Appendable out) throws IOException {
    var firstField = true;
    for (var field : fields) {
      if (!firstField) out.append(delimiter);
      firstField = false;

      // Escape field names if needed
      String fieldName = field.name();
      if (needsEscaping(fieldName)) {
        out.append(QUOTE).append(fieldName.replace("\"", ESCAPED_QUOTE)).append(QUOTE);
      } else {
        out.append(fieldName);
      }
    }
    out.append(lineSeparator);
  }

  /**
   * Formats multiple items from a stream.
   *
   * @param items The stream of items to format
   * @param fields The field descriptors for formatting
   * @param out The output destination
   * @throws IOException If an I/O error occurs
   */
  private void formatItems(Stream<T> items, List<FieldDescriptor<T>> fields, Appendable out)
      throws IOException {
    var iterator = items.iterator();
    while (iterator.hasNext()) {
      formatRow(iterator.next(), fields, out);

      if (iterator.hasNext()) {
        out.append(lineSeparator);
      }
    }
  }

  /**
   * Formats a single item as a CSV row.
   *
   * @param item The item to format
   * @param fields The field descriptors for formatting
   * @param out The output destination
   * @throws IOException If an I/O error occurs
   */
  private void formatRow(T item, List<FieldDescriptor<T>> fields, Appendable out)
      throws IOException {
    var firstField = true;
    for (var field : fields) {
      if (!firstField) out.append(delimiter);
      firstField = false;

      var value = field.extractor().apply(item);
      var serialized = registry.serialize(value, field);

      // Escape CSV values if needed
      appendEscapedIfNecessary(serialized, out);
    }
  }

  /**
   * Appends a value to the output, escaping it if necessary according to CSV rules.
   *
   * @param value The value to append
   * @param out The output destination
   * @throws IOException If an I/O error occurs
   */
  private void appendEscapedIfNecessary(String value, Appendable out) throws IOException {
    if (needsEscaping(value)) {
      out.append(QUOTE).append(value.replace("\"", ESCAPED_QUOTE)).append(QUOTE);
    } else {
      out.append(value);
    }
  }

  /**
   * Checks if a value needs to be escaped according to CSV rules.
   *
   * @param value The value to check
   * @return True if the value needs escaping, false otherwise
   */
  private boolean needsEscaping(String value) {
    return value.contains(delimiter)
        || value.indexOf(QUOTE) >= 0
        || value.indexOf('\n') >= 0
        || value.indexOf('\r') >= 0;
  }
}
