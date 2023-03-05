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

@CommandLine.Command(name = "sunrise", description = "calculates sunrise, transit, and sunset")
final class SunriseCommand implements Callable<Integer> {
  @CommandLine.ParentCommand Main parent;

  @Override
  public Integer call() {
    parent.validate();

    final Stream<ZonedDateTime> dateTimes = getDatetimes(parent.dateTime, parent.timezone);
    parent.printAnyHeaders(HEADERS);

    final PrintWriter out = parent.spec.commandLine().getOut();
    dateTimes.forEach(
        dateTime -> {
          final double deltaT = parent.getBestGuessDeltaT(dateTime);
          SunriseTransitSet result =
              SPA.calculateSunriseTransitSet(dateTime, parent.latitude, parent.longitude, deltaT);
          out.print(buildOutput(parent.format, dateTime, deltaT, result, parent.showInput));
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

  private static final Map<Boolean, String> JSON_FORMATS =
      Map.of(
          true,
              """
                    {"latitude":%.5f,"longitude":%5f,"dateTime":%s,"deltaT":%.3f,"type":"%s","sunrise":%s,"transit":%s,"sunset":%s}
                    """,
          false,
              """
                    {"type":"%5$s","sunrise":%6$s,"transit":%7$s,"sunset":%8$s}
                    """);

  private static final Map<Boolean, String> CSV_HEADERS =
      Map.of(
          true, "latitude,longitude,dateTime,deltaT,type,sunrise,transit,sunset",
          false, "type,sunrise,transit,sunset");

  private static final Map<Boolean, String> CSV_FORMATS =
      Map.of(
          true, "%.5f,%.5f,%s,%.3f,%s,%s,%s,%s%n",
          false, "%5$s,%6$s,%7$s,%8$s%n");

  private static final Map<Boolean, String> HUMAN_FORMATS =
      Map.of(
          true,
              """
                    latitude:    %24.4f
                    longitude:   %24.4f
                    date/time:  %s
                    delta T:     %22.2f
                    type:       %s
                    sunrise:    %s
                    transit:    %s
                    sunset:     %s
                    """,
          false,
              """
                    type:       %5$s
                    sunrise:    %6$s
                    transit:    %7$s
                    sunset:     %8$s
                    """);

  private static final Map<Main.Format, Map<Boolean, String>> HEADERS =
      Map.of(Main.Format.CSV, CSV_HEADERS);

  private static final Map<Main.Format, Map<Boolean, String>> TEMPLATES =
      Map.of(Main.Format.CSV, CSV_FORMATS, JSON, JSON_FORMATS, HUMAN, HUMAN_FORMATS);

  private static final Map<Main.Format, String> NULL_DATE =
      Map.of(Main.Format.CSV, "", JSON, "null", HUMAN, "none");

  private String buildOutput(
      Main.Format format,
      ZonedDateTime dateTime,
      double deltaT,
      SunriseTransitSet result,
      boolean showInput) {
    String template = TEMPLATES.get(format).get(showInput);

    return template.formatted(
        parent.latitude,
        parent.longitude,
        formatDate(format, dateTime),
        deltaT,
        formatType(format, result.getType()),
        formatDate(format, result.getSunrise()),
        formatDate(format, result.getTransit()),
        formatDate(format, result.getSunset()));
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
