package net.e175.klaus.formatter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public interface StreamingFormatter<T> {
  void format(
      List<FieldDescriptor<T>> allFields, List<String> subset, Stream<T> items, Appendable out)
      throws IOException;

  default List<FieldDescriptor<T>> filterFields(
      List<FieldDescriptor<T>> allFields, List<String> subset) {
    Objects.requireNonNull(allFields, "Field list must not be null");
    Objects.requireNonNull(subset, "Subset list must not be null");

    return allFields.stream().filter(fd -> subset.contains(fd.name())).toList();
  }

  default void validateInputs(
      List<FieldDescriptor<T>> allFields, List<String> subset, Stream<T> items, Appendable out) {
    Objects.requireNonNull(allFields, "Field list must not be null");
    Objects.requireNonNull(subset, "Subset list must not be null");
    Objects.requireNonNull(items, "Items stream must not be null");
    Objects.requireNonNull(out, "Output must not be null");
  }
}
