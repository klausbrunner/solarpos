package net.e175.klaus.solarpos;

import static net.e175.klaus.solarpos.Main.Format.HUMAN;
import static net.e175.klaus.solarpos.Main.Format.JSON;

import java.io.PrintWriter;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
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
    parent.printAnyHeaders(twilight ? TWILIGHT_HEADERS : HEADERS);

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
    dateTimes.forEach(
        dateTime -> {
          final double deltaT = parent.getBestGuessDeltaT(dateTime);
          Map<SPA.Horizon, SunriseResult> result =
              SPA.calculateSunriseTransitSet(
                  dateTime, parent.latitude, parent.longitude, deltaT, horizons);
          out.print(
              buildOutput(
                  parent.format,
                  parent.latitude,
                  parent.longitude,
                  dateTime,
                  deltaT,
                  result,
                  parent.showInput,
                  twilight));
        });
    out.flush();

    return 0;
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

  private static final Map<Main.Format, Map<Boolean, String>> HEADERS =
      Map.of(
          Main.Format.CSV,
          Map.of(
              true, "latitude,longitude,dateTime,deltaT,type,sunrise,transit,sunset\r\n",
              false, "type,sunrise,transit,sunset\r\n"));

  private static final Map<Main.Format, Map<Boolean, String>> TWILIGHT_HEADERS =
      Map.of(
          Main.Format.CSV,
          Map.of(
              true,
                  "latitude,longitude,dateTime,deltaT,type,sunrise,transit,sunset,civil_start,civil_end,nautical_start,nautical_end,astronomical_start,astronomical_end\r\n",
              false,
                  "type,sunrise,transit,sunset,civil_start,civil_end,nautical_start,nautical_end,astronomical_start,astronomical_end\r\n"));

  private static final Map<Main.Format, String> NULL_DATE =
      Map.of(Main.Format.CSV, "", JSON, "null", HUMAN, "none");

  private static String buildOutput(
      Main.Format format,
      double latitude,
      double longitude,
      ZonedDateTime dateTime,
      double deltaT,
      Map<SPA.Horizon, SunriseResult> result,
      boolean showInput,
      boolean twilight) {

    final var sb = new StringBuilder(100);
    final var sunriseSunset = result.get(SPA.Horizon.SUNRISE_SUNSET);

    switch (format) {
      case HUMAN -> {
        if (showInput) {
          sb.append(
                  """
                  latitude:            %24.4f
                  longitude:           %24.4f
                  date/time:          %s
                  delta T:             %22.2f
                  """
                  .formatted(latitude, longitude, formatDate(format, dateTime), deltaT));
        }
        sb.append(
            sunriseSunsetFragment(
                format,
                sunriseSunset,
                """
                        type:               %s
                        sunrise:            %s
                        transit:            %s
                        sunset:             %s
                        """));
        if (twilight) {
          sb.append(
              twilightFragment(
                  format,
                  result,
                  """
                          civil start:        %s
                          civil end:          %s
                          nautical start:     %s
                          nautical end:       %s
                          astronomical start: %s
                          astronomical end:   %s
                          """));
        }
      }
      case JSON -> {
        sb.append("{");
        if (showInput) {
          sb.append(
                  """
                  "latitude":%.5f,"longitude":%5f,"dateTime":%s,"deltaT":%.3f,"""
                  .formatted(latitude, longitude, formatDate(format, dateTime), deltaT));
        }
        sb.append(
            sunriseSunsetFragment(
                format,
                sunriseSunset,
                """
                    "type":"%s","sunrise":%s,"transit":%s,"sunset":%s"""));
        if (twilight) {
          sb.append(
              twilightFragment(
                  format,
                  result,
                  """
                    ,"civil_start":%s,"civil_end":%s,"nautical_start":%s,"nautical_end":%s,"astronomical_start":%s,"astronomical_end":%s"""));
        }
        sb.append("}\n");
      }
      case CSV -> {
        if (showInput) {
          sb.append(
              "%.5f,%.5f,%s,%.3f,"
                  .formatted(latitude, longitude, formatDate(format, dateTime), deltaT));
        }
        sb.append(sunriseSunsetFragment(format, sunriseSunset, "%s,%s,%s,%s"));
        if (twilight) {
          sb.append(twilightFragment(format, result, ",%s,%s,%s,%s,%s,%s"));
        }
        sb.append("\r\n"); // according to https://www.rfc-editor.org/rfc/rfc4180#section-2
      }
    }
    return sb.toString();
  }

  private static String twilightFragment(
      Main.Format format, Map<SPA.Horizon, SunriseResult> result, String pattern) {
    final var civil = FormattedSunriseResult.of(format, result.get(SPA.Horizon.CIVIL_TWILIGHT));
    final var nautical =
        FormattedSunriseResult.of(format, result.get(SPA.Horizon.NAUTICAL_TWILIGHT));
    final var astronomical =
        FormattedSunriseResult.of(format, result.get(SPA.Horizon.ASTRONOMICAL_TWILIGHT));
    return pattern.formatted(
        civil.sunrise(),
        civil.sunset(),
        nautical.sunrise(),
        nautical.sunset(),
        astronomical.sunrise(),
        astronomical.sunset());
  }

  private static String sunriseSunsetFragment(
      Main.Format format, SunriseResult sunriseSunset, String pattern) {
    var formatted = FormattedSunriseResult.of(format, sunriseSunset);

    return pattern.formatted(
        formatted.type(), formatted.sunrise(), formatted.transit(), formatted.sunset());
  }

  record FormattedSunriseResult(String sunrise, String transit, String sunset, String type) {
    private FormattedSunriseResult(
        Main.Format format,
        TemporalAccessor sunrise,
        TemporalAccessor transit,
        TemporalAccessor sunset,
        String type) {
      this(
          formatDate(format, sunrise),
          formatDate(format, transit),
          formatDate(format, sunset),
          type);
    }

    public static FormattedSunriseResult of(Main.Format format, SunriseResult result) {
      return switch (result) {
        case SunriseResult.AllDay ad ->
            new FormattedSunriseResult(
                format, null, ad.transit(), null, format == HUMAN ? "all day" : "ALL_DAY");
        case SunriseResult.AllNight an ->
            new FormattedSunriseResult(
                format, null, an.transit(), null, format == HUMAN ? "all night" : "ALL_NIGHT");
        case SunriseResult.RegularDay rd ->
            new FormattedSunriseResult(
                format,
                rd.sunrise(),
                rd.transit(),
                rd.sunset(),
                format == HUMAN ? "normal" : "NORMAL");
      };
    }
  }

  private static String formatDate(Main.Format format, TemporalAccessor temporal) {
    if (temporal == null) {
      return NULL_DATE.get(format);
    }

    DateTimeFormatter dtf =
        (format == HUMAN)
            ? Main.ISO_HUMAN_LOCAL_DATE_TIME_REDUCED
            : Main.ISO_LOCAL_DATE_TIME_REDUCED;

    return format == JSON ? '"' + dtf.format(temporal) + '"' : dtf.format(temporal);
  }
}
