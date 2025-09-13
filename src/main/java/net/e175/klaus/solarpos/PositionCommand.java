package net.e175.klaus.solarpos;

import static net.e175.klaus.solarpos.Main.Format.HUMAN;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import net.e175.klaus.formatter.FieldDescriptor;
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
    final PerformanceTracker tracker = PerformanceTracker.create(parent.showPerformance);

    try {
      List<FieldDescriptor<PositionData>> fields = createFields();
      List<String> fieldNames = getFieldNames(parent.shouldShowInputs());
      StreamingFormatter<PositionData> formatter = createFormatter(parent.format);

      Stream<PositionData> resultStream;
      if (parent.isPairedData()) {
        // Use paired processing for 1:1 coordinate-time correspondence
        var stream = parent.getPairedDataStream(DateTimeIterator.TimePrecision.TIME_REQUIRED);
        resultStream =
            (parent.parallel ? stream.parallel() : stream)
                .map(pair -> calculatePositionData(pair.dateTime(), pair.coordinates()));
      } else {
        // Use Cartesian product for separate coordinate/time inputs
        // Flatten the entire Cartesian product first, then parallelize across all combinations
        var cartesianStream =
            parent
                .getDateTimesStream(step, DateTimeIterator.TimePrecision.TIME_REQUIRED)
                .flatMap(
                    dt ->
                        parent
                            .getCoordinatesStream()
                            .map(coord -> new DateTimeIterator.CoordinateTimePair(coord, dt)));
        resultStream =
            (parent.parallel ? cartesianStream.parallel() : cartesianStream)
                .map(pair -> calculatePositionData(pair.dateTime(), pair.coordinates()));
      }

      resultStream = PerformanceTracker.wrapIfNeeded(tracker, resultStream);
      formatter.format(fields, fieldNames, resultStream, out);
    } catch (IOException e) {
      throw new RuntimeException("Failed to format output", e);
    }

    PerformanceTracker.reportIfNeeded(tracker);
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
    return FormatterFactory.create(format, parent.headers);
  }

  private PositionData calculatePositionData(ZonedDateTime dateTime, CoordinatePair coord) {
    final double deltaT = parent.getBestGuessDeltaT(dateTime);

    SolarPosition position =
        refraction
            ? calculateSolarPositionWithRefraction(dateTime, coord, deltaT)
            : calculateSolarPositionWithoutRefraction(dateTime, coord, deltaT);

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

  private SolarPosition calculateSolarPositionWithRefraction(
      ZonedDateTime dateTime, CoordinatePair coord, double deltaT) {
    return switch (algorithm) {
      case SPA ->
          SPA.calculateSolarPosition(
              dateTime,
              coord.latitude(),
              coord.longitude(),
              elevation,
              deltaT,
              pressure,
              temperature);
      case GRENA3 ->
          Grena3.calculateSolarPosition(
              dateTime, coord.latitude(), coord.longitude(), deltaT, pressure, temperature);
    };
  }

  private SolarPosition calculateSolarPositionWithoutRefraction(
      ZonedDateTime dateTime, CoordinatePair coord, double deltaT) {
    return switch (algorithm) {
      case SPA ->
          SPA.calculateSolarPosition(
              dateTime, coord.latitude(), coord.longitude(), elevation, deltaT);
      case GRENA3 ->
          Grena3.calculateSolarPosition(dateTime, coord.latitude(), coord.longitude(), deltaT);
    };
  }

  private void validate() {
    validateRange("pressure", pressure, 0.1, 2000.0, "hPa");
    validateRange("temperature", temperature, -100.0, 100.0, "°C");
  }

  private void validateRange(String paramName, double value, double min, double max, String unit) {
    if (value < min || value > max) {
      throw new CommandLine.ParameterException(
          spec.commandLine(),
          "%s must be between %.1f and %.1f %s, got %.1f"
              .formatted(paramName, min, max, unit, value));
    }
  }
}
