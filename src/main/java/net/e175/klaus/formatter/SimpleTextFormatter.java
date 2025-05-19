package net.e175.klaus.formatter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/** Streaming, aligned text output formatter. */
public class SimpleTextFormatter<T> implements StreamingFormatter<T> {
  private final SerializerRegistry registry;
  private final String lineSeparator;
  private static final String FORMAT_SEPARATOR = ": ";

  /**
   * Creates a text formatter with the given registry and system line separator.
   *
   * @param registry The serializer registry to use (must not be null)
   */
  public SimpleTextFormatter(SerializerRegistry registry) {
    this(registry, System.lineSeparator());
  }

  /**
   * Creates a text formatter with the given registry and line separator.
   *
   * @param registry The serializer registry to use (must not be null)
   * @param lineSeparator The line separator to use between items
   */
  public SimpleTextFormatter(SerializerRegistry registry, String lineSeparator) {
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

    // Find the maximum field name length for alignment - only using field names, not streaming data
    int maxNameLength = calculateMaxFieldNameLength(fields);
    String formatPattern = "%-" + maxNameLength + "s" + FORMAT_SEPARATOR + "%s%n";

    // Process each item
    formatItems(items, fields, formatPattern, out);
  }

  /**
   * Calculates the maximum length of field names for alignment.
   *
   * @param fields The field descriptors to check
   * @return The maximum length of field names
   */
  private int calculateMaxFieldNameLength(List<FieldDescriptor<T>> fields) {
    return fields.stream()
        .mapToInt(fd -> fd.name().length())
        .max()
        .orElse(0); // Shouldn't happen as we checked isEmpty() above
  }

  /**
   * Formats multiple items from a stream.
   *
   * @param items The stream of items to format
   * @param fields The field descriptors for formatting
   * @param formatPattern The format pattern to use
   * @param out The output destination
   * @throws IOException If an I/O error occurs
   */
  private void formatItems(
      Stream<T> items, List<FieldDescriptor<T>> fields, String formatPattern, Appendable out)
      throws IOException {
    var iterator = items.iterator();
    while (iterator.hasNext()) {
      var item = iterator.next();
      formatSingleItem(item, fields, formatPattern, out);

      if (iterator.hasNext()) {
        out.append(lineSeparator);
      }
    }
  }

  /**
   * Formats a single item with all its fields.
   *
   * @param item The item to format
   * @param fields The field descriptors for formatting
   * @param formatPattern The format pattern to use
   * @param out The output destination
   * @throws IOException If an I/O error occurs
   */
  private void formatSingleItem(
      T item, List<FieldDescriptor<T>> fields, String formatPattern, Appendable out)
      throws IOException {
    for (var field : fields) {
      var value = field.extractor().apply(item);
      out.append(
          String.format(Locale.US, formatPattern, field.name(), registry.serialize(value, field)));
    }
  }
}
