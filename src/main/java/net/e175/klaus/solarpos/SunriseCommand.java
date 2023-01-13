package net.e175.klaus.solarpos;

import net.e175.klaus.solarpositioning.SPA;
import net.e175.klaus.solarpositioning.SunriseTransitSet;
import picocli.CommandLine;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Formatter;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

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

    private String buildOutput(Main.Format format, ZonedDateTime dateTime, double deltaT, SunriseTransitSet result, boolean showInput) {
        try (Formatter fmt = new Formatter(new StringBuilder(100))) {
            final DateTimeFormatter dateTimeFormatter = Main.ISO_LOCAL_DATE_TIME_REDUCED;
            switch (format) {
                case JSON -> {
                    fmt.format("{");
                    if (showInput) {
                        fmt.format("\"latitude\":%.5f, ", parent.latitude);
                        fmt.format("\"longitude\":%.5f, ", parent.longitude);
                    }
                    fmt.format("\"dateTime\":\"%s\", ", dateTimeFormatter.format(dateTime));
                    if (showInput) {
                        fmt.format("\"deltaT\":%.4f, ", deltaT);
                    }
                    fmt.format("\"sunrise\":\"%s\", ", dateTimeFormatter.format(result.getSunrise()));
                    fmt.format("\"transit\":\"%s\", ", dateTimeFormatter.format(result.getTransit()));
                    fmt.format("\"sunset\":\"%s\"", dateTimeFormatter.format(result.getSunset()));
                    fmt.format("}");
                }

                case CSV -> {
                    if (showInput) {
                        fmt.format("%.5f,", parent.latitude);
                        fmt.format("%.5f,", parent.longitude);
                    }
                    fmt.format("%s,", dateTimeFormatter.format(dateTime));
                    if (showInput) {
                        fmt.format("%.2f,", deltaT);
                    }
                    fmt.format("%s,", dateTimeFormatter.format(result.getSunrise()));
                    fmt.format("%s,", dateTimeFormatter.format(result.getTransit()));
                    fmt.format("%s", dateTimeFormatter.format(result.getSunset()));
                }

                case HUMAN -> {
                    if (showInput) {
                        fmt.format("latitude:    %24.4f%n", parent.latitude);
                        fmt.format("longitude:   %24.4f%n", parent.longitude);
                    }
                    String[] splitDateTime = dateTimeFormatter.format(dateTime).split("T");
                    fmt.format("date:                      %s%n", splitDateTime[0]);
                    if (showInput) {
                        fmt.format("delta T:     %22.2f%n", deltaT);
                    }
                    fmt.format("sunrise:    %25s%n", Main.ISO_LOCAL_TIME_REDUCED.format(result.getSunrise()));
                    fmt.format("transit:    %25s%n", Main.ISO_LOCAL_TIME_REDUCED.format(result.getTransit()));
                    fmt.format("sunset:     %25s%n", Main.ISO_LOCAL_TIME_REDUCED.format(result.getSunset()));
                }
            }
            return fmt.toString();
        }
    }
}
