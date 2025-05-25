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
  @CommandLine.ParentCommand Main parent;

  @CommandLine.Option(
      names = {"--twilight"},
      description = "Show twilight times.")
  boolean twilight;

  @Override
  public Integer call() {
    parent.validate();

    final Stream<ZonedDateTime> dateTimes = getDatetimes(parent.dateTime, parent.timezone);

    final SPA.Horizon[] horizons =
        twilight
            ? new SPA.Horizon[] {
              SPA.Horizon.SUNRISE_SUNSET,
              SPA.Horizon.CIVIL_TWILIGHT,
              SPA.Horizon.NAUTICAL_TWILIGHT,
              SPA.Horizon.ASTRONOMICAL_TWILIGHT
            }
            : new SPA.Horizon[] {SPA.Horizon.SUNRISE_SUNSET};

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

    // Input fields
    fields.add(FieldDescriptor.numeric("latitude", SunriseData::latitude, 5));
    fields.add(FieldDescriptor.numeric("longitude", SunriseData::longitude, 5));
    fields.add(
        FieldDescriptor.dateTime(
            "dateTime",
            SunriseData::dateTime,
            parent.format == HUMAN ? "yyyy-MM-dd HH:mm:ssXXX" : "yyyy-MM-dd'T'HH:mm:ssXXX"));
    fields.add(FieldDescriptor.numeric("deltaT", SunriseData::deltaT, 3));

    // Result fields
    fields.add(new FieldDescriptor<>("type", SunriseData::type));
    fields.add(new FieldDescriptor<>("sunrise", SunriseData::sunrise));
    fields.add(new FieldDescriptor<>("transit", SunriseData::transit));
    fields.add(new FieldDescriptor<>("sunset", SunriseData::sunset));

    // Twilight fields
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

    // Input fields if showInput is true
    if (showInput) {
      names.addAll(List.of("latitude", "longitude", "dateTime", "deltaT"));
    }

    // Add type field always
    names.add("type");

    // Decide whether to use basic fields or detailed twilight sequence
    if (twilight) {
      // Complete twilight sequence including sunrise and sunset in proper order
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
      // Just the basic fields when twilight is not requested
      names.addAll(List.of("sunrise", "transit", "sunset"));
    }

    return names;
  }

  private StreamingFormatter<SunriseData> createFormatter(Main.Format format) {
    SerializerRegistry registry = createRegistry(format);

    return switch (format) {
      case HUMAN -> new SimpleTextFormatter<>(registry);
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

    // Add degree symbols for human format
    if (format == HUMAN) {
      registry.register(
          Double.class,
          (d, hints) -> {
            int precision = (int) hints.getOrDefault("precision", 4);
            String result = String.format("%." + precision + "f", d);

            String fieldName = (String) hints.getOrDefault("fieldName", "");
            if (fieldName.equals("latitude") || fieldName.equals("longitude")) {
              return String.format("%24s°", result);
            } else if (fieldName.equals("deltaT")) {
              return String.format("%22s s", result);
            }
            return result;
          });
    }

    return registry;
  }

  private SunriseData calculateSunriseData(ZonedDateTime dateTime, SPA.Horizon[] horizons) {
    final double deltaT = parent.getBestGuessDeltaT(dateTime);
    Map<SPA.Horizon, SunriseResult> result =
        SPA.calculateSunriseTransitSet(
            dateTime, parent.latitude, parent.longitude, deltaT, horizons);

    // Get the main sunrise/sunset result
    SunriseResult sunriseSunset = result.get(SPA.Horizon.SUNRISE_SUNSET);

    // Extract type and times based on the result type
    String type;
    ZonedDateTime sunrise = null;
    ZonedDateTime transit = null;
    ZonedDateTime sunset = null;

    // Use pattern matching to extract all at once
    switch (sunriseSunset) {
      case SunriseResult.RegularDay(var sr, var tr, var ss) -> {
        type = parent.format == HUMAN ? "normal" : "NORMAL";
        sunrise = convertToZonedDateTime(sr);
        transit = convertToZonedDateTime(tr);
        sunset = convertToZonedDateTime(ss);
      }
      case SunriseResult.AllDay(var tr) -> {
        type = parent.format == HUMAN ? "all day" : "ALL_DAY";
        transit = convertToZonedDateTime(tr);
      }
      case SunriseResult.AllNight(var tr) -> {
        type = parent.format == HUMAN ? "all night" : "ALL_NIGHT";
        transit = convertToZonedDateTime(tr);
      }
      default -> throw new IllegalStateException("Unexpected result type: " + sunriseSunset);
    }

    // Extract twilight times if available
    ZonedDateTime civilStart = null;
    ZonedDateTime civilEnd = null;
    ZonedDateTime nauticalStart = null;
    ZonedDateTime nauticalEnd = null;
    ZonedDateTime astronomicalStart = null;
    ZonedDateTime astronomicalEnd = null;

    if (horizons.length > 1) {
      SunriseResult civil = result.get(SPA.Horizon.CIVIL_TWILIGHT);
      SunriseResult nautical = result.get(SPA.Horizon.NAUTICAL_TWILIGHT);
      SunriseResult astronomical = result.get(SPA.Horizon.ASTRONOMICAL_TWILIGHT);

      if (civil instanceof SunriseResult.RegularDay regularDay) {
        civilStart = convertToZonedDateTime(regularDay.sunrise());
        civilEnd = convertToZonedDateTime(regularDay.sunset());
      }

      if (nautical instanceof SunriseResult.RegularDay regularDay) {
        nauticalStart = convertToZonedDateTime(regularDay.sunrise());
        nauticalEnd = convertToZonedDateTime(regularDay.sunset());
      }

      if (astronomical instanceof SunriseResult.RegularDay regularDay) {
        astronomicalStart = convertToZonedDateTime(regularDay.sunrise());
        astronomicalEnd = convertToZonedDateTime(regularDay.sunset());
      }
    }

    return new SunriseData(
        parent.latitude,
        parent.longitude,
        dateTime,
        deltaT,
        type,
        sunrise,
        transit,
        sunset,
        civilStart,
        civilEnd,
        nauticalStart,
        nauticalEnd,
        astronomicalStart,
        astronomicalEnd);
  }

  private ZonedDateTime convertToZonedDateTime(TemporalAccessor temporal) {
    if (temporal == null) {
      return null;
    }

    if (temporal instanceof ZonedDateTime zdt) {
      return zdt;
    }

    // Handle other temporal types if needed
    return null;
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
