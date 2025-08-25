package net.e175.klaus.solarpos;

import static net.e175.klaus.solarpos.Main.Format.HUMAN;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import net.e175.klaus.formatter.CsvFormatter;
import net.e175.klaus.formatter.FieldDescriptor;
import net.e175.klaus.formatter.JsonFormatter;
import net.e175.klaus.formatter.SerializerRegistry;
import net.e175.klaus.formatter.SimpleTextFormatter;
import net.e175.klaus.formatter.StreamingFormatter;
import net.e175.klaus.solarpos.util.DateTimeIterator;
import net.e175.klaus.solarpos.util.TimeFormats;
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
      description =
          "Step interval for time series. Examples: 30s, 15m, 2h. Default: ${DEFAULT-VALUE}.",
      defaultValue = "1h",
      converter = DurationConverter.class)
  Duration step;

  @CommandLine.Option(
      names = "--refraction",
      negatable = true,
      defaultValue = "true",
      fallbackValue = "true",
      description = "Apply refraction correction. Default: ${DEFAULT-VALUE}.")
  boolean refraction;

  @CommandLine.Option(
      names = "--elevation-angle",
      description = "Output elevation angle instead of zenith angle.")
  boolean elevationOutput;

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
      double zenith,
      double elevationAngle) {}

  @Override
  public Integer call() {
    parent.validate();
    validate();

    final PrintWriter out = parent.spec.commandLine().getOut();

    try {
      List<FieldDescriptor<PositionData>> fields = createFields();
      List<String> fieldNames = getFieldNames(parent.shouldShowInputs());
      StreamingFormatter<PositionData> formatter = createFormatter(parent.format);

      Stream<PositionData> resultStream =
          DateTimeIterator.iterate(parent.dateTime, parent.timezone, step)
              .parallel()
              .flatMap(
                  dt ->
                      getCoordinates(parent.latitude, parent.longitude)
                          .map(coord -> calculatePositionData(dt, coord)));

      formatter.format(fields, fieldNames, resultStream, out);
    } catch (IOException e) {
      throw new RuntimeException("Failed to format output", e);
    }

    out.flush();
    return 0;
  }

  private List<FieldDescriptor<PositionData>> createFields() {
    List<FieldDescriptor<PositionData>> fields = new ArrayList<>();

    fields.add(FieldDescriptor.numeric("latitude", PositionData::latitude, 5).withUnit("°"));
    fields.add(FieldDescriptor.numeric("longitude", PositionData::longitude, 5).withUnit("°"));
    fields.add(FieldDescriptor.numeric("elevation", PositionData::elevation, 3).withUnit(" m"));

    if (refraction) {
      fields.add(FieldDescriptor.numeric("pressure", PositionData::pressure, 3).withUnit(" hPa"));
      fields.add(
          FieldDescriptor.numeric("temperature", PositionData::temperature, 3).withUnit(" °C"));
    }

    fields.add(
        FieldDescriptor.dateTime(
            "dateTime",
            PositionData::dateTime,
            parent.format == HUMAN
                ? TimeFormats.OUTPUT_DATE_TIME_HUMAN_PATTERN
                : TimeFormats.OUTPUT_DATE_TIME_ISO_PATTERN));
    fields.add(FieldDescriptor.numeric("deltaT", PositionData::deltaT, 3).withUnit(" s"));

    fields.add(FieldDescriptor.numeric("azimuth", PositionData::azimuth, 5).withUnit("°"));

    if (elevationOutput) {
      fields.add(
          FieldDescriptor.numeric("elevation-angle", PositionData::elevationAngle, 5)
              .withUnit("°"));
    } else {
      fields.add(FieldDescriptor.numeric("zenith", PositionData::zenith, 5).withUnit("°"));
    }

    return fields;
  }

  private List<String> getFieldNames(boolean showInput) {
    List<String> names = new ArrayList<>();

    if (showInput) {
      names.addAll(List.of("latitude", "longitude", "elevation"));

      if (refraction) {
        names.addAll(List.of("pressure", "temperature"));
      }

      names.addAll(List.of("dateTime", "deltaT"));
    } else {
      names.add("dateTime");
    }

    names.add("azimuth");
    names.add(elevationOutput ? "elevation-angle" : "zenith");

    return names;
  }

  private StreamingFormatter<PositionData> createFormatter(Main.Format format) {
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
      case CSV -> new CsvFormatter<>(registry, parent.headers);
    };
  }

  private PositionData calculatePositionData(ZonedDateTime dateTime, CoordinatePair coord) {
    final double deltaT = parent.getBestGuessDeltaT(dateTime);
    SolarPosition position =
        switch (this.algorithm) {
          case SPA ->
              this.refraction
                  ? SPA.calculateSolarPosition(
                      dateTime,
                      coord.latitude(),
                      coord.longitude(),
                      elevation,
                      deltaT,
                      pressure,
                      temperature)
                  : SPA.calculateSolarPosition(
                      dateTime, coord.latitude(), coord.longitude(), elevation, deltaT);
          case GRENA3 ->
              this.refraction
                  ? Grena3.calculateSolarPosition(
                      dateTime, coord.latitude(), coord.longitude(), deltaT, pressure, temperature)
                  : Grena3.calculateSolarPosition(
                      dateTime, coord.latitude(), coord.longitude(), deltaT);
        };

    return new PositionData(
        coord.latitude(),
        coord.longitude(),
        elevation,
        pressure,
        temperature,
        dateTime,
        deltaT,
        position.azimuth(),
        position.zenithAngle(),
        90.0 - position.zenithAngle());
  }

  private void validate() {
    if (pressure <= 0 || pressure > 2000) {
      throw new CommandLine.ParameterException(spec.commandLine(), "invalid pressure value");
    }

    if (temperature < -100 || temperature > 100) {
      throw new CommandLine.ParameterException(spec.commandLine(), "invalid temperature value");
    }
  }

  static Stream<CoordinatePair> getCoordinates(CoordinateRange latRange, CoordinateRange lngRange) {
    return latRange.stream()
        .boxed()
        .flatMap(lat -> lngRange.stream().mapToObj(lng -> new CoordinatePair(lat, lng)));
  }
}
