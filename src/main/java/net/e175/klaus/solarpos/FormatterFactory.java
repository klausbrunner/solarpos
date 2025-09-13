package net.e175.klaus.solarpos;

import java.util.Map;
import net.e175.klaus.formatter.CsvFormatter;
import net.e175.klaus.formatter.JsonFormatter;
import net.e175.klaus.formatter.SerializerRegistry;
import net.e175.klaus.formatter.SimpleTextFormatter;
import net.e175.klaus.formatter.StreamingFormatter;

final class FormatterFactory {
  private FormatterFactory() {}

  static <T> StreamingFormatter<T> create(Main.Format format, boolean headers) {
    SerializerRegistry registry =
        switch (format) {
          case HUMAN -> SerializerRegistry.forText();
          case JSON -> SerializerRegistry.forJson();
          case CSV -> SerializerRegistry.forCsv();
        };

    return switch (format) {
      case HUMAN -> {
        var displayNames = Map.of("dateTime", "date/time", "deltaT", "delta T");
        yield new SimpleTextFormatter<>(registry, displayNames);
      }
      case JSON -> new JsonFormatter<>(registry, "\n");
      case CSV -> new CsvFormatter<>(registry, headers);
    };
  }
}
