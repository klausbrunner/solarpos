package net.e175.klaus.formatter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public record SimpleTextFormatter<T>(
    SerializerRegistry registry, String lineSeparator, Map<String, String> displayNames)
    implements StreamingFormatter<T> {
  private static final String FORMAT_SEPARATOR = ": ";

  public SimpleTextFormatter(SerializerRegistry registry) {
    this(registry, System.lineSeparator());
  }

  public SimpleTextFormatter(SerializerRegistry registry, String lineSeparator) {
    this(registry, lineSeparator, Map.of());
  }

  public SimpleTextFormatter(SerializerRegistry registry, Map<String, String> displayNames) {
    this(registry, System.lineSeparator(), displayNames);
  }

  public SimpleTextFormatter(
      SerializerRegistry registry, String lineSeparator, Map<String, String> displayNames) {
    this.registry = Objects.requireNonNull(registry, "Registry must not be null");
    this.lineSeparator = Objects.requireNonNull(lineSeparator, "Line separator must not be null");
    this.displayNames = displayNames != null ? Map.copyOf(displayNames) : Map.of();
  }

  @Override
  public void format(
      List<FieldDescriptor<T>> allFields, List<String> subset, Stream<T> items, Appendable out) {
    validateInputs(allFields, subset, items, out);

    var fields = filterAndOrderFields(allFields, subset);
    if (fields.isEmpty()) return;

    int maxNameLength = calculateMaxFieldNameLength(fields);
    String formatPattern = "%-" + maxNameLength + "s" + FORMAT_SEPARATOR + "%s%n";

    formatItems(items, fields, formatPattern, out);
  }

  private static <T> List<FieldDescriptor<T>> filterAndOrderFields(
      List<FieldDescriptor<T>> allFields, List<String> subset) {

    var fieldMap =
        allFields.stream()
            .collect(java.util.stream.Collectors.toMap(FieldDescriptor::name, f -> f));

    return subset.stream().map(fieldMap::get).filter(Objects::nonNull).toList();
  }

  private int calculateMaxFieldNameLength(List<FieldDescriptor<T>> fields) {
    return fields.stream()
        .mapToInt(
            fd -> {
              String displayName = displayNames.getOrDefault(fd.name(), fd.name());
              return displayName.length();
            })
        .max()
        .orElse(0);
  }

  private void formatItems(
      Stream<T> items, List<FieldDescriptor<T>> fields, String formatPattern, Appendable out) {
    var first = new java.util.concurrent.atomic.AtomicBoolean(true);

    items.forEachOrdered(
        item -> {
          try {
            if (!first.getAndSet(false)) {
              out.append(lineSeparator);
            }
            formatSingleItem(item, fields, formatPattern, out);
          } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
          }
        });
  }

  private void formatSingleItem(
      T item, List<FieldDescriptor<T>> fields, String formatPattern, Appendable out)
      throws IOException {
    for (var field : fields) {
      var value = field.extractor().apply(item);
      String displayName = displayNames.getOrDefault(field.name(), field.name());

      String serialized = registry.serialize(value, field);

      out.append(String.format(Locale.US, formatPattern, displayName, serialized));
    }
  }
}
