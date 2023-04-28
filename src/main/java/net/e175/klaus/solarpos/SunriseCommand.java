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
import net.e175.klaus.solarpositioning.SunriseTransitSet;
import picocli.CommandLine;

@CommandLine.Command(name = "sunrise", description = "calculates sunrise, transit, sunset and (optionally) twilight times")
final class SunriseCommand implements Callable<Integer> {
  @CommandLine.ParentCommand Main parent;

  @CommandLine.Option(
      names = {"--twilight"},
      description = "show twilight times")
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
          Map<SPA.Horizon, SunriseTransitSet> result =
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

    if (dateTime instanceof Year y) {
      return Stream.iterate(
          ZonedDateTime.of(
              LocalDate.of(y.getValue(), Month.JANUARY, 1), LocalTime.of(0, 0), overrideTz),
          i -> i.getYear() == y.getValue(),
          i -> i.plusDays(1));
    } else if (dateTime instanceof YearMonth ym) {
      return Stream.iterate(
          ZonedDateTime.of(
              LocalDate.of(ym.getYear(), ym.getMonth(), 1), LocalTime.of(0, 0), overrideTz),
          i -> i.getMonth() == ym.getMonth(),
          i -> i.plusDays(1));
    } else if (dateTime instanceof LocalDate ld) {
      return Stream.of(ZonedDateTime.of(ld, LocalTime.of(0, 0), overrideTz));
    } else if (dateTime instanceof LocalDateTime ldt) {
      return Stream.of(ZonedDateTime.of(ldt, overrideTz));
    } else if (dateTime instanceof LocalTime lt) {
      return Stream.of(ZonedDateTime.of(LocalDate.now(), lt, overrideTz));
    } else if (dateTime instanceof OffsetTime ot) {
      return Stream.of(
          ZonedDateTime.of(
              LocalDate.now(), ot.toLocalTime(), zoneId.isPresent() ? overrideTz : ot.getOffset()));
    } else if (dateTime instanceof ZonedDateTime zdt) {
      return Stream.of(
          zoneId.isPresent()
              ? ZonedDateTime.of(zdt.toLocalDate(), zdt.toLocalTime(), overrideTz)
              : zdt);
    } else {
      throw new IllegalStateException("unexpected date/time type " + dateTime);
    }
  }

  private static final Map<Main.Format, Map<Boolean, String>> HEADERS =
      Map.of(
          Main.Format.CSV,
          Map.of(
              true, "latitude,longitude,dateTime,deltaT,type,sunrise,transit,sunset",
              false, "type,sunrise,transit,sunset"));

  private static final Map<Main.Format, Map<Boolean, String>> TWILIGHT_HEADERS =
      Map.of(
          Main.Format.CSV,
          Map.of(
              true,
                  "latitude,longitude,dateTime,deltaT,type,sunrise,transit,sunset,civil_start,civil_end,nautical_start,nautical_end,astronomical_start,astronomical_end",
              false,
                  "type,sunrise,transit,sunset,civil_start,civil_end,nautical_start,nautical_end,astronomical_start,astronomical_end"));

  private static final Map<Main.Format, String> NULL_DATE =
      Map.of(Main.Format.CSV, "", JSON, "null", HUMAN, "none");

  private static String buildOutput(
      Main.Format format,
      double latitude,
      double longitude,
      ZonedDateTime dateTime,
      double deltaT,
      Map<SPA.Horizon, SunriseTransitSet> result,
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
      Main.Format format, Map<SPA.Horizon, SunriseTransitSet> result, String pattern) {
    final var civil = result.get(SPA.Horizon.CIVIL_TWILIGHT);
    final var nautical = result.get(SPA.Horizon.NAUTICAL_TWILIGHT);
    final var astronomical = result.get(SPA.Horizon.ASTRONOMICAL_TWILIGHT);
    return pattern.formatted(
        formatDate(format, civil.getSunrise()),
        formatDate(format, civil.getSunset()),
        formatDate(format, nautical.getSunrise()),
        formatDate(format, nautical.getSunset()),
        formatDate(format, astronomical.getSunrise()),
        formatDate(format, astronomical.getSunset()));
  }

  private static String sunriseSunsetFragment(
      Main.Format format, SunriseTransitSet sunriseSunset, String pattern) {
    return pattern.formatted(
        formatType(format, sunriseSunset.getType()),
        formatDate(format, sunriseSunset.getSunrise()),
        formatDate(format, sunriseSunset.getTransit()),
        formatDate(format, sunriseSunset.getSunset()));
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

  private static Object formatType(Main.Format format, SunriseTransitSet.Type type) {
    return format == HUMAN ? type.toString().toLowerCase().replace('_', ' ') : type;
  }
}
