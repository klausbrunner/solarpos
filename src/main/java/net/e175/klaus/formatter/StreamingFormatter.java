package net.e175.klaus.formatter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Streaming formatter interface for converting data objects to formatted text output.
 * Implementations should handle different output formats like text, CSV, JSON, etc.
 */
public interface StreamingFormatter<T> {
  /**
   * Format a stream of items according to the specified field descriptors.
   *
   * @param allFields All available field descriptors for type T
   * @param subset Names of fields to include in the output
   * @param items Stream of items to format
   * @param out Destination for formatted output
   * @throws IOException If an I/O error occurs during writing
   */
  void format(
      List<FieldDescriptor<T>> allFields, List<String> subset, Stream<T> items, Appendable out)
      throws IOException;

  /**
   * Filter fields based on the requested subset names. Keeps only fields whose names are in the
   * subset list.
   *
   * @param allFields All available field descriptors
   * @param subset Names of fields to include
   * @return Filtered list of field descriptors
   */
  default List<FieldDescriptor<T>> filterFields(
      List<FieldDescriptor<T>> allFields, List<String> subset) {
    Objects.requireNonNull(allFields, "Field list must not be null");
    Objects.requireNonNull(subset, "Subset list must not be null");

    return allFields.stream().filter(fd -> subset.contains(fd.name())).toList();
  }

  /**
   * Validates common input parameters for formatters.
   *
   * @param allFields All available field descriptors
   * @param subset Names of fields to include
   * @param items Stream of items to format
   * @param out Destination for formatted output
   * @throws NullPointerException If any parameter is null
   */
  default void validateInputs(
      List<FieldDescriptor<T>> allFields, List<String> subset, Stream<T> items, Appendable out) {
    Objects.requireNonNull(allFields, "Field list must not be null");
    Objects.requireNonNull(subset, "Subset list must not be null");
    Objects.requireNonNull(items, "Items stream must not be null");
    Objects.requireNonNull(out, "Output must not be null");
  }
}
