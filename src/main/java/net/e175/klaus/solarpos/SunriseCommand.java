package net.e175.klaus.solarpos;

import static net.e175.klaus.solarpos.Main.Format.HUMAN;
import static net.e175.klaus.solarpos.Main.Format.JSON;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.*;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import net.e175.klaus.formatter.*;
import net.e175.klaus.solarpos.util.TimeFormatUtil;
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

    final Stream<ZonedDateTime> dateTimes = getDatetimes(parent.dateTime, parent.timezone);

    final SPA.Horizon[] horizons = twilight ? TWILIGHT_HORIZONS : BASIC_HORIZONS;

    final PrintWriter out = parent.spec.commandLine().getOut();

    try {
      List<FieldDescriptor<SunriseData>> fields = createFields();
      List<String> fieldNames = getFieldNames(parent.showInput, twilight);
      StreamingFormatter<SunriseData> formatter = createFormatter(parent.format);
      formatter.format(
          fields,
          fieldNames,
          dateTimes.map(dateTime -> calculateSunriseData(dateTime, horizons)),
          out);
    } catch (IOException e) {
      throw new RuntimeException("Failed to format output", e);
    }

    out.flush();
    return 0;
  }

  private List<FieldDescriptor<SunriseData>> createFields() {
    List<FieldDescriptor<SunriseData>> fields = new ArrayList<>();

    fields.add(FieldDescriptor.numeric("latitude", SunriseData::latitude, 5));
    fields.add(FieldDescriptor.numeric("longitude", SunriseData::longitude, 5));
    fields.add(
        FieldDescriptor.dateTime(
            "dateTime",
            SunriseData::dateTime,
            parent.format == HUMAN ? "yyyy-MM-dd HH:mm:ssXXX" : "yyyy-MM-dd'T'HH:mm:ssXXX"));
    fields.add(FieldDescriptor.numeric("deltaT", SunriseData::deltaT, 3));

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
    SerializerRegistry registry = createRegistry(format);

    return switch (format) {
      case HUMAN -> {
        var displayNames = Map.of("dateTime", "date/time", "deltaT", "delta T");
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

    if (format == HUMAN) {
      registry.register(
          Double.class,
          (d, hints) -> {
            int precision = (int) hints.getOrDefault("precision", 4);
            String result = String.format("%." + precision + "f", d);

            String fieldName = (String) hints.getOrDefault("fieldName", "");
            return switch (fieldName) {
              case "latitude", "longitude" -> String.format("%28s°", result);
              case "deltaT" -> String.format("%28s s", result);
              default -> result;
            };
          });
    }

    return registry;
  }

  private SunriseData calculateSunriseData(ZonedDateTime dateTime, SPA.Horizon[] horizons) {
    final double deltaT = parent.getBestGuessDeltaT(dateTime);
    Map<SPA.Horizon, SunriseResult> result =
        SPA.calculateSunriseTransitSet(
            dateTime, parent.latitude, parent.longitude, deltaT, horizons);

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
        parent.latitude,
        parent.longitude,
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

  private static record Times(ZonedDateTime start, ZonedDateTime end) {}

  private static Times extractTimes(SunriseResult result) {
    return result instanceof SunriseResult.RegularDay regularDay
        ? new Times(regularDay.sunrise(), regularDay.sunset())
        : new Times(null, null);
  }

  static Stream<ZonedDateTime> getDatetimes(TemporalAccessor dateTime, Optional<ZoneId> zoneId) {
    final ZoneId overrideTz = zoneId.orElse(ZoneId.systemDefault());

    return switch (dateTime) {
      case Year y ->
          Stream.iterate(
              ZonedDateTime.of(
                  LocalDate.of(y.getValue(), Month.JANUARY, 1), LocalTime.of(0, 0), overrideTz),
              i -> i.getYear() == y.getValue(),
              i -> i.plusDays(1));
      case YearMonth ym ->
          Stream.iterate(
              ZonedDateTime.of(
                  LocalDate.of(ym.getYear(), ym.getMonth(), 1), LocalTime.of(0, 0), overrideTz),
              i -> i.getMonth() == ym.getMonth(),
              i -> i.plusDays(1));
      case LocalDate ld -> Stream.of(ZonedDateTime.of(ld, LocalTime.of(0, 0), overrideTz));
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
