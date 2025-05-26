package net.e175.klaus.formatter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/** Streaming, aligned text output formatter. */
public class SimpleTextFormatter<T> implements StreamingFormatter<T> {
  private final SerializerRegistry registry;
  private final String lineSeparator;
  private final Map<String, String> displayNames;
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
    this(registry, lineSeparator, Map.of());
  }

  /**
   * Creates a text formatter with the given registry, line separator, and display name mapping.
   *
   * @param registry The serializer registry to use (must not be null)
   * @param displayNames Map of field names to display names
   */
  public SimpleTextFormatter(SerializerRegistry registry, Map<String, String> displayNames) {
    this(registry, System.lineSeparator(), displayNames);
  }

  /**
   * Creates a text formatter with the given registry, line separator, and display name mapping.
   *
   * @param registry The serializer registry to use (must not be null)
   * @param lineSeparator The line separator to use between items
   * @param displayNames Map of field names to display names
   */
  public SimpleTextFormatter(
      SerializerRegistry registry, String lineSeparator, Map<String, String> displayNames) {
    this.registry = Objects.requireNonNull(registry, "Registry must not be null");
    this.lineSeparator = Objects.requireNonNull(lineSeparator, "Line separator must not be null");
    this.displayNames = displayNames != null ? displayNames : Map.of();
  }

  @Override
  public void format(
      List<FieldDescriptor<T>> allFields, List<String> subset, Stream<T> items, Appendable out) {
    validateInputs(allFields, subset, items, out);

    // For human text output, we need to respect the order of fields in the subset list
    var fields = filterAndOrderFields(allFields, subset);
    if (fields.isEmpty()) return;

    // Find the maximum field name length for alignment - only using field names, not streaming data
    int maxNameLength = calculateMaxFieldNameLength(fields);
    String formatPattern = "%-" + maxNameLength + "s" + FORMAT_SEPARATOR + "%s%n";

    // Process each item
    formatItems(items, fields, formatPattern, out);
  }

  /**
   * Filters and orders field descriptors based on the subset of field names. This ensures the
   * fields appear in the order specified in the subset list.
   *
   * @param allFields All available field descriptors
   * @param subset Names of fields to include, in the desired order
   * @return Ordered list of field descriptors matching the subset
   */
  private static <T> List<FieldDescriptor<T>> filterAndOrderFields(
      List<FieldDescriptor<T>> allFields, List<String> subset) {

    var fieldMap =
        allFields.stream()
            .collect(java.util.stream.Collectors.toMap(FieldDescriptor::name, f -> f));

    return subset.stream().map(fieldMap::get).filter(Objects::nonNull).toList();
  }

  /**
   * Calculates the maximum length of field names for alignment. This method considers display names
   * if available.
   *
   * @param fields The field descriptors to check
   * @return The maximum length of field names
   */
  private int calculateMaxFieldNameLength(List<FieldDescriptor<T>> fields) {
    return fields.stream()
        .mapToInt(
            fd -> {
              String displayName = displayNames.getOrDefault(fd.name(), fd.name());
              return displayName.length();
            })
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
   */
  private void formatItems(
      Stream<T> items, List<FieldDescriptor<T>> fields, String formatPattern, Appendable out) {
    var first = new java.util.concurrent.atomic.AtomicBoolean(true);

    items.forEachOrdered(
        item -> {
          try {
            // Add separator before each item except the first
            if (!first.getAndSet(false)) {
              out.append(lineSeparator);
            }
            formatSingleItem(item, fields, formatPattern, out);
          } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
          }
        });
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
      // Use display name if available, otherwise use the field name
      String displayName = displayNames.getOrDefault(field.name(), field.name());

      // Serialize using the original field descriptor - the registry will add the field name
      String serialized = registry.serialize(value, field);

      out.append(String.format(Locale.US, formatPattern, displayName, serialized));
    }
  }
}
