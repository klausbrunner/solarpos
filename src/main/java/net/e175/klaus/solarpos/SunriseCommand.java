package net.e175.klaus.solarpos;

import static net.e175.klaus.solarpos.Main.Format.HUMAN;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import net.e175.klaus.formatter.*;
import net.e175.klaus.solarpos.util.TimeFormats;
import net.e175.klaus.solarpositioning.SPA;
import net.e175.klaus.solarpositioning.SunriseResult;
import picocli.CommandLine;

@CommandLine.Command(
    name = "sunrise",
    description = "Calculates sunrise, transit, sunset and (optionally) twilight times.")
final class SunriseCommand implements Callable<Integer> {

  private static final SPA.Horizon[] BASIC_HORIZONS = {SPA.Horizon.SUNRISE_SUNSET};
  private static final SPA.Horizon[] TWILIGHT_HORIZONS = {
    SPA.Horizon.SUNRISE_SUNSET,
    SPA.Horizon.CIVIL_TWILIGHT,
    SPA.Horizon.NAUTICAL_TWILIGHT,
    SPA.Horizon.ASTRONOMICAL_TWILIGHT
  };

  record SunriseData(
      double latitude,
      double longitude,
      ZonedDateTime dateTime,
      double deltaT,
      String type,
      ZonedDateTime sunrise,
      ZonedDateTime transit,
      ZonedDateTime sunset,
      ZonedDateTime civilStart,
      ZonedDateTime civilEnd,
      ZonedDateTime nauticalStart,
      ZonedDateTime nauticalEnd,
      ZonedDateTime astronomicalStart,
      ZonedDateTime astronomicalEnd) {}

  private record TwilightTimes(
      ZonedDateTime civilStart,
      ZonedDateTime civilEnd,
      ZonedDateTime nauticalStart,
      ZonedDateTime nauticalEnd,
      ZonedDateTime astronomicalStart,
      ZonedDateTime astronomicalEnd) {}

  @CommandLine.ParentCommand Main parent;

  @CommandLine.Option(
      names = {"--twilight"},
      description = "Show twilight times.")
  boolean twilight;

  @Override
  public Integer call() {
    parent.validate();

    final SPA.Horizon[] horizons = twilight ? TWILIGHT_HORIZONS : BASIC_HORIZONS;
    final PrintWriter out = parent.spec.commandLine().getOut();
    final PerformanceTracker tracker = PerformanceTracker.create(parent.showPerformance);

    try {
      List<FieldDescriptor<SunriseData>> fields = createFields();
      List<String> fieldNames = getFieldNames(parent.shouldShowInputs(), twilight);
      StreamingFormatter<SunriseData> formatter = createFormatter(parent.format);

      Stream<SunriseData> resultStream;
      if (parent.isPairedData()) {
        // Use paired processing for 1:1 coordinate-time correspondence
        var stream = parent.getPairedDataStream();
        resultStream =
            (parent.parallel ? stream.parallel() : stream)
                .map(pair -> calculateSunriseData(pair.dateTime(), pair.coordinates(), horizons));
      } else {
        // Use Cartesian product for separate coordinate/time inputs
        var timeStream = parent.getDateTimesStream(Duration.ofDays(1));
        resultStream =
            (parent.parallel ? timeStream.parallel() : timeStream)
                .flatMap(
                    dt ->
                        parent
                            .getCoordinatesStream()
                            .map(coord -> calculateSunriseData(dt, coord, horizons)));
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

  private List<FieldDescriptor<SunriseData>> createFields() {
    List<FieldDescriptor<SunriseData>> fields = new ArrayList<>();

    fields.add(FieldDescriptor.numeric("latitude", SunriseData::latitude, 5).withUnit("°"));
    fields.add(FieldDescriptor.numeric("longitude", SunriseData::longitude, 5).withUnit("°"));
    fields.add(
        FieldDescriptor.dateTime(
            "dateTime",
            SunriseData::dateTime,
            parent.format == HUMAN
                ? TimeFormats.OUTPUT_DATE_TIME_HUMAN_PATTERN
                : TimeFormats.OUTPUT_DATE_TIME_ISO_PATTERN));
    fields.add(FieldDescriptor.numeric("deltaT", SunriseData::deltaT, 3).withUnit(" s"));

    fields.add(new FieldDescriptor<>("type", SunriseData::type));
    fields.add(new FieldDescriptor<>("sunrise", SunriseData::sunrise));
    fields.add(new FieldDescriptor<>("transit", SunriseData::transit));
    fields.add(new FieldDescriptor<>("sunset", SunriseData::sunset));

    fields.add(new FieldDescriptor<>("civil_start", SunriseData::civilStart));
    fields.add(new FieldDescriptor<>("civil_end", SunriseData::civilEnd));
    fields.add(new FieldDescriptor<>("nautical_start", SunriseData::nauticalStart));
    fields.add(new FieldDescriptor<>("nautical_end", SunriseData::nauticalEnd));
    fields.add(new FieldDescriptor<>("astronomical_start", SunriseData::astronomicalStart));
    fields.add(new FieldDescriptor<>("astronomical_end", SunriseData::astronomicalEnd));

    return fields;
  }

  private List<String> getFieldNames(boolean showInput, boolean twilight) {
    List<String> names = new ArrayList<>();

    if (showInput) {
      names.addAll(List.of("latitude", "longitude", "dateTime", "deltaT"));
    }

    names.add("type");

    if (twilight) {
      names.addAll(
          List.of(
              "astronomical_start",
              "nautical_start",
              "civil_start",
              "sunrise",
              "transit",
              "sunset",
              "civil_end",
              "nautical_end",
              "astronomical_end"));
    } else {
      names.addAll(List.of("sunrise", "transit", "sunset"));
    }

    return names;
  }

  private StreamingFormatter<SunriseData> createFormatter(Main.Format format) {
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

  private SunriseData calculateSunriseData(
      ZonedDateTime dateTime, CoordinatePair coord, SPA.Horizon[] horizons) {
    final double deltaT = parent.getBestGuessDeltaT(dateTime);
    Map<SPA.Horizon, SunriseResult> result =
        SPA.calculateSunriseTransitSet(
            dateTime, coord.latitude(), coord.longitude(), deltaT, horizons);

    SunriseResult sunriseSunset = result.get(SPA.Horizon.SUNRISE_SUNSET);

    String type;
    ZonedDateTime sunrise = null;
    ZonedDateTime transit;
    ZonedDateTime sunset = null;

    switch (sunriseSunset) {
      case SunriseResult.RegularDay(var sr, var tr, var ss) -> {
        type = parent.format == HUMAN ? "normal" : "NORMAL";
        sunrise = sr;
        transit = tr;
        sunset = ss;
      }
      case SunriseResult.AllDay(var tr) -> {
        type = parent.format == HUMAN ? "all day" : "ALL_DAY";
        transit = tr;
      }
      case SunriseResult.AllNight(var tr) -> {
        type = parent.format == HUMAN ? "all night" : "ALL_NIGHT";
        transit = tr;
      }
      default -> throw new IllegalStateException("Unexpected result type: " + sunriseSunset);
    }

    var twilightTimes = extractTwilightTimes(result, horizons.length > 1);

    return new SunriseData(
        coord.latitude(),
        coord.longitude(),
        dateTime,
        deltaT,
        type,
        sunrise,
        transit,
        sunset,
        twilightTimes.civilStart(),
        twilightTimes.civilEnd(),
        twilightTimes.nauticalStart(),
        twilightTimes.nauticalEnd(),
        twilightTimes.astronomicalStart(),
        twilightTimes.astronomicalEnd());
  }

  private static TwilightTimes extractTwilightTimes(
      Map<SPA.Horizon, SunriseResult> result, boolean includeTwilight) {
    if (!includeTwilight) {
      return new TwilightTimes(null, null, null, null, null, null);
    }

    var civil = extractTimes(result.get(SPA.Horizon.CIVIL_TWILIGHT));
    var nautical = extractTimes(result.get(SPA.Horizon.NAUTICAL_TWILIGHT));
    var astronomical = extractTimes(result.get(SPA.Horizon.ASTRONOMICAL_TWILIGHT));

    return new TwilightTimes(
        civil.start(), civil.end(),
        nautical.start(), nautical.end(),
        astronomical.start(), astronomical.end());
  }

  private record Times(ZonedDateTime start, ZonedDateTime end) {}

  private static Times extractTimes(SunriseResult result) {
    return result instanceof SunriseResult.RegularDay regularDay
        ? new Times(regularDay.sunrise(), regularDay.sunset())
        : new Times(null, null);
  }

  static Stream<CoordinatePair> getCoordinates(CoordinateRange latRange, CoordinateRange lngRange) {
    return PositionCommand.getCoordinates(latRange, lngRange);
  }
}
