package net.e175.klaus.solarpos;

import static net.e175.klaus.solarpos.Main.Format.HUMAN;
import static net.e175.klaus.solarpos.Main.Format.JSON;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.*;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import net.e175.klaus.formatter.CsvFormatter;
import net.e175.klaus.formatter.FieldDescriptor;
import net.e175.klaus.formatter.JsonFormatter;
import net.e175.klaus.formatter.SerializerRegistry;
import net.e175.klaus.formatter.SimpleTextFormatter;
import net.e175.klaus.formatter.StreamingFormatter;
import net.e175.klaus.solarpos.util.TimeFormatUtil;
import net.e175.klaus.solarpositioning.Grena3;
import net.e175.klaus.solarpositioning.SPA;
import net.e175.klaus.solarpositioning.SolarPosition;
import picocli.CommandLine;

@CommandLine.Command(name = "position", description = "Calculates topocentric solar coordinates.")
final class PositionCommand implements Callable<Integer> {

  enum Algorithm {
    SPA,
    GRENA3
  }

  @CommandLine.Spec CommandLine.Model.CommandSpec spec;

  @CommandLine.ParentCommand Main parent;

  @CommandLine.Option(
      names = {"-a", "--algorithm"},
      description = "One of ${COMPLETION-CANDIDATES}. Default: ${DEFAULT-VALUE}.",
      defaultValue = "spa")
  Algorithm algorithm;

  @CommandLine.Option(
      names = {"--elevation"},
      description = "Elevation above sea level, in meters. Default: ${DEFAULT-VALUE}.",
      defaultValue = "0")
  double elevation;

  @CommandLine.Option(
      names = {"--pressure"},
      description =
          "Avg. air pressure in millibars/hectopascals. Used for refraction correction. Default: ${DEFAULT-VALUE}.",
      defaultValue = "1013")
  double pressure;

  @CommandLine.Option(
      names = {"--temperature"},
      description =
          "Avg. air temperature in degrees Celsius. Used for refraction correction. Default: ${DEFAULT-VALUE}.",
      defaultValue = "15")
  double temperature;

  @CommandLine.Option(
      names = {"--step"},
      description = "Step interval for time series, in seconds. Default: ${DEFAULT-VALUE}.",
      defaultValue = "3600")
  int step;

  @CommandLine.Option(
      names = "--refraction",
      negatable = true,
      defaultValue = "true",
      fallbackValue = "true",
      description = "Apply refraction correction. Default: ${DEFAULT-VALUE}.")
  boolean refraction;

  /** Record to hold position data for formatting. */
  record PositionData(
      double latitude,
      double longitude,
      double elevation,
      double pressure,
      double temperature,
      ZonedDateTime dateTime,
      double deltaT,
      double azimuth,
      double zenith) {}

  @Override
  public Integer call() {
    parent.validate();
    validate();

    final Stream<ZonedDateTime> dateTimes = getDatetimes(parent.dateTime, parent.timezone, step);

    final PrintWriter out = parent.spec.commandLine().getOut();

    try {
      List<FieldDescriptor<PositionData>> fields = createFields();
      List<String> fieldNames = getFieldNames(parent.showInput);
      StreamingFormatter<PositionData> formatter = createFormatter(parent.format);
      formatter.format(fields, fieldNames, dateTimes.map(this::calculatePositionData), out);
    } catch (IOException e) {
      throw new RuntimeException("Failed to format output", e);
    }

    out.flush();
    return 0;
  }

  private List<FieldDescriptor<PositionData>> createFields() {
    List<FieldDescriptor<PositionData>> fields = new ArrayList<>();

    // Input fields
    fields.add(FieldDescriptor.numeric("latitude", PositionData::latitude, 5));
    fields.add(FieldDescriptor.numeric("longitude", PositionData::longitude, 5));
    fields.add(FieldDescriptor.numeric("elevation", PositionData::elevation, 3));

    // Only add refraction-related fields if refraction correction is enabled
    if (refraction) {
      fields.add(FieldDescriptor.numeric("pressure", PositionData::pressure, 3));
      fields.add(FieldDescriptor.numeric("temperature", PositionData::temperature, 3));
    }

    fields.add(
        FieldDescriptor.dateTime(
            "dateTime",
            PositionData::dateTime,
            parent.format == HUMAN ? "yyyy-MM-dd HH:mm:ssXXX" : "yyyy-MM-dd'T'HH:mm:ssXXX"));
    fields.add(FieldDescriptor.numeric("deltaT", PositionData::deltaT, 3));

    // Result fields
    fields.add(FieldDescriptor.numeric("azimuth", PositionData::azimuth, 5));
    fields.add(FieldDescriptor.numeric("zenith", PositionData::zenith, 5));

    return fields;
  }

  private List<String> getFieldNames(boolean showInput) {
    List<String> names = new ArrayList<>();

    // Input fields if showInput is true
    if (showInput) {
      // Always include these base inputs
      names.addAll(List.of("latitude", "longitude", "elevation"));

      // Only include refraction parameters if refraction correction is enabled
      if (refraction) {
        names.addAll(List.of("pressure", "temperature"));
      }

      // Always include these remaining inputs
      names.addAll(List.of("dateTime", "deltaT"));
    } else {
      // Include dateTime even when not showing all inputs
      names.add("dateTime");
    }

    // Always include result fields
    names.addAll(List.of("azimuth", "zenith"));

    return names;
  }

  private StreamingFormatter<PositionData> createFormatter(Main.Format format) {
    SerializerRegistry registry = createRegistry(format);

    return switch (format) {
      case HUMAN -> {
        // Create display name mapping for human-readable output
        var displayNames =
            Map.of(
                "dateTime", "date/time",
                "deltaT", "delta T");
        yield new SimpleTextFormatter<>(registry, displayNames);
      }
      case JSON -> new JsonFormatter<>(registry, "\n");
      case CSV -> new CsvFormatter<>(registry, parent.headers);
    };
  }

  private SerializerRegistry createRegistry(Main.Format format) {
    SerializerRegistry registry =
        switch (format) {
          case HUMAN -> SerializerRegistry.forText();
          case JSON -> SerializerRegistry.forJson();
          case CSV -> SerializerRegistry.forCsv();
        };

    // Custom serializer for temporal accessors
    registry.register(
        ZonedDateTime.class,
        (dt, hints) -> {
          if (dt == null) {
            return switch (format) {
              case HUMAN -> "none";
              case JSON -> "null";
              case CSV -> "";
            };
          }

          String formatted =
              dt.format(
                  format == HUMAN
                      ? TimeFormatUtil.ISO_HUMAN_LOCAL_DATE_TIME_REDUCED
                      : TimeFormatUtil.ISO_LOCAL_DATE_TIME_REDUCED);

          return format == JSON ? '"' + formatted + '"' : formatted;
        });

    // Add degree symbols and units for human format
    if (format == HUMAN) {
      registry.register(
          Double.class,
          (d, hints) -> {
            int precision = (int) hints.getOrDefault("precision", 4);
            String result = String.format("%." + precision + "f", d);

            String fieldName = (String) hints.getOrDefault("fieldName", "");
            return switch (fieldName) {
              case "latitude", "longitude", "azimuth", "zenith" -> String.format("%28s°", result);
              case "elevation" -> String.format("%28s m", result);
              case "pressure" -> String.format("%28s hPa", result);
              case "temperature" -> String.format("%28s °C", result);
              case "deltaT" -> String.format("%28s s", result);
              default -> result;
            };
          });
    }

    return registry;
  }

  private PositionData calculatePositionData(ZonedDateTime dateTime) {
    final double deltaT = parent.getBestGuessDeltaT(dateTime);
    SolarPosition position =
        switch (this.algorithm) {
          case SPA ->
              this.refraction
                  ? SPA.calculateSolarPosition(
                      dateTime,
                      parent.latitude,
                      parent.longitude,
                      elevation,
                      deltaT,
                      pressure,
                      temperature)
                  : SPA.calculateSolarPosition(
                      dateTime, parent.latitude, parent.longitude, elevation, deltaT);
          case GRENA3 ->
              this.refraction
                  ? Grena3.calculateSolarPosition(
                      dateTime, parent.latitude, parent.longitude, deltaT, pressure, temperature)
                  : Grena3.calculateSolarPosition(
                      dateTime, parent.latitude, parent.longitude, deltaT);
        };

    return new PositionData(
        parent.latitude,
        parent.longitude,
        elevation,
        pressure,
        temperature,
        dateTime,
        deltaT,
        position.azimuth(),
        position.zenithAngle());
  }

  private void validate() {
    if (step < 1 || step > 24 * 60 * 60) {
      throw new CommandLine.ParameterException(spec.commandLine(), "invalid step value");
    }

    if (pressure <= 0 || pressure > 2000) {
      throw new CommandLine.ParameterException(spec.commandLine(), "invalid pressure value");
    }

    if (temperature < -100 || temperature > 100) {
      throw new CommandLine.ParameterException(spec.commandLine(), "invalid temperature value");
    }
  }

  static Stream<ZonedDateTime> getDatetimes(
      TemporalAccessor dateTime, Optional<ZoneId> zoneId, int step) {
    final ZoneId overrideTz = zoneId.orElse(ZoneId.systemDefault());

    return switch (dateTime) {
      case Year y ->
          Stream.iterate(
              ZonedDateTime.of(LocalDate.of(y.getValue(), 1, 1), LocalTime.of(0, 0), overrideTz),
              i -> i.getYear() == y.getValue(),
              i -> i.plusSeconds(step));
      case YearMonth ym ->
          Stream.iterate(
              ZonedDateTime.of(
                  LocalDate.of(ym.getYear(), ym.getMonth(), 1), LocalTime.of(0, 0), overrideTz),
              i -> i.getMonth() == ym.getMonth(),
              i -> i.plusSeconds(step));
      case LocalDate ld ->
          Stream.iterate(
              ZonedDateTime.of(ld, LocalTime.of(0, 0), overrideTz),
              i -> i.getDayOfMonth() == ld.getDayOfMonth(),
              i -> i.plusSeconds(step));
      case LocalDateTime ldt -> Stream.of(ZonedDateTime.of(ldt, overrideTz));
      case LocalTime lt -> Stream.of(ZonedDateTime.of(LocalDate.now(), lt, overrideTz));
      case OffsetTime ot ->
          Stream.of(
              ZonedDateTime.of(
                  LocalDate.now(),
                  ot.toLocalTime(),
                  zoneId.isPresent() ? overrideTz : ot.getOffset()));
      case ZonedDateTime zdt ->
          Stream.of(
              zoneId.isPresent()
                  ? ZonedDateTime.of(zdt.toLocalDate(), zdt.toLocalTime(), overrideTz)
                  : zdt);
      default -> throw new IllegalStateException("unexpected date/time type " + dateTime);
    };
  }
}
