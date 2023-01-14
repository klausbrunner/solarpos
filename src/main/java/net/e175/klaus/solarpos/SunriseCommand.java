package net.e175.klaus.solarpos;

import net.e175.klaus.solarpositioning.SPA;
import net.e175.klaus.solarpositioning.SunriseTransitSet;
import picocli.CommandLine;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static net.e175.klaus.solarpos.Main.Format.HUMAN;

@CommandLine.Command(name = "sunrise", description = "calculates sunrise, transit, and sunset")
public final class SunriseCommand implements Callable<Integer> {
    @CommandLine.ParentCommand
    Main parent;

    @Override
    public Integer call() {
        parent.validate();

        Stream<ZonedDateTime> dateTimes = getDatetimes(parent.dateTime, parent.timezone);

        dateTimes.forEach(dateTime -> {
            final double deltaT = parent.getBestGuessDeltaT(dateTime);
            SunriseTransitSet result = SPA.calculateSunriseTransitSet(dateTime, parent.latitude, parent.longitude,
                    deltaT);
            String output = buildOutput(parent.format, dateTime, deltaT, result, parent.showInput);
            parent.spec.commandLine().getOut().println(output);
        });

        return 0;
    }

    static Stream<ZonedDateTime> getDatetimes(TemporalAccessor dateTime, Optional<ZoneId> zoneId) {
        Stream<ZonedDateTime> dateTimes;
        ZoneId overrideTz = zoneId.orElse(ZoneId.systemDefault());

        if (dateTime instanceof Year y) {
            dateTimes = Stream.iterate(ZonedDateTime.of(LocalDate.of(y.getValue(), Month.JANUARY, 1), LocalTime.of(0, 0), overrideTz),
                    i -> i.getYear() == y.getValue(),
                    i -> i.plusDays(1));
        } else if (dateTime instanceof YearMonth ym) {
            dateTimes = Stream.iterate(ZonedDateTime.of(LocalDate.of(ym.getYear(), ym.getMonth(), 1), LocalTime.of(0, 0), overrideTz),
                    i -> i.getMonth() == ym.getMonth(),
                    i -> i.plusDays(1));
        } else if (dateTime instanceof LocalDateTime ldt) {
            dateTimes = Stream.of(ZonedDateTime.of(ldt, overrideTz));
        } else if (dateTime instanceof ZonedDateTime zdt) {
            dateTimes = Stream.of(zoneId.isPresent() ?
                    ZonedDateTime.of(zdt.toLocalDate(), zdt.toLocalTime(), overrideTz) :
                    zdt);
        } else {
            throw new IllegalStateException("unexpected date/time type");
        }
        return dateTimes;
    }

    private static final Map<Boolean, String> JSON_FORMATS = Map.of(
            true, """
                    {"latitude":%.5f,"longitude":%5f,"dateTime":"%s","deltaT":%.3f,"sunrise":"%s","transit":"%s","sunset":"%s"}
                    """,
            false, """
                    {"sunrise":"%5$s","transit":"%6$s","sunset":"%7$s"}
                    """);

    private static final Map<Boolean, String> CSV_FORMATS = Map.of(
            true, "%.5f,%.5f,%s,%.3f,%s,%s,%s%n",
            false, "%5$s,%6$s,%7$s%n");

    private static final Map<Boolean, String> HUMAN_FORMATS = Map.of(
            true, """
                    latitude:    %24.4f
                    longitude:   %24.4f
                    date/time:  %s
                    delta T:     %22.2f
                    sunrise:    %s
                    transit:    %s
                    sunset:     %s
                    """,
            false, """
                    sunrise:    %5$s
                    transit:    %6$s
                    sunset:     %7$s
                    """);

    private static final Map<Main.Format, Map<Boolean, String>> TEMPLATES =
            Map.of(Main.Format.CSV, CSV_FORMATS, Main.Format.JSON, JSON_FORMATS, HUMAN, HUMAN_FORMATS);

    private String buildOutput(Main.Format format, ZonedDateTime dateTime, double deltaT, SunriseTransitSet result, boolean showInput) {
        String template = TEMPLATES.get(format).get(showInput);
        DateTimeFormatter dtf = (format == HUMAN) ? Main.ISO_HUMAN_LOCAL_DATE_TIME_REDUCED : Main.ISO_LOCAL_DATE_TIME_REDUCED;
        return template.formatted(parent.latitude,
                parent.longitude,
                dtf.format(dateTime),
                deltaT,
                dtf.format(result.getSunrise()),
                dtf.format(result.getTransit()),
                dtf.format(result.getSunset()));
    }

}
