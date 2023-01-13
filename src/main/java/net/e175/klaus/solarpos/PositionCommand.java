package net.e175.klaus.solarpos;

import net.e175.klaus.solarpositioning.AzimuthZenithAngle;
import net.e175.klaus.solarpositioning.Grena3;
import net.e175.klaus.solarpositioning.SPA;
import picocli.CommandLine;

import java.time.*;
import java.time.temporal.TemporalAccessor;
import java.util.Formatter;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

@CommandLine.Command(name = "position", description = "calculates topocentric solar coordinates")
final class PositionCommand implements Callable<Integer> {

    enum Algorithm {SPA, GRENA3}

    @CommandLine.ParentCommand
    Main parent;

    @CommandLine.Option(names = {"-a", "--algorithm"}, description = "one of ${COMPLETION-CANDIDATES}", defaultValue = "spa")
    Algorithm algorithm;

    @CommandLine.Option(names = {"--elevation"}, description = "elevation above sea level, in meters", defaultValue = "0")
    double elevation;

    @CommandLine.Option(names = {"--pressure"}, description = "avg. air pressure in millibars/hectopascals", defaultValue = "1000")
    double pressure;

    @CommandLine.Option(names = {"--temperature"}, description = "avg. air temperature in degrees Celsius", defaultValue = "0")
    double temperature;

    @Override
    public Integer call() {
        parent.validate();
        Stream<ZonedDateTime> dateTimes = getDatetimes(parent.dateTime, parent.timezone);

        dateTimes.forEach(dateTime -> {
            final double deltaT = parent.getBestGuessDeltaT(dateTime);
            AzimuthZenithAngle position = switch (this.algorithm) {
                case SPA ->
                        SPA.calculateSolarPosition(dateTime, parent.latitude, parent.longitude, elevation, deltaT, pressure, temperature);
                case GRENA3 ->
                        Grena3.calculateSolarPosition(dateTime, parent.latitude, parent.longitude, deltaT, pressure, temperature);
            };

            String output = buildOutput(parent.format, dateTime, deltaT, position, parent.showInput);
            parent.spec.commandLine().getOut().println(output);
        });

        return 0;
    }

    static Stream<ZonedDateTime> getDatetimes(TemporalAccessor dateTime, Optional<ZoneId> zoneId) {
        Stream<ZonedDateTime> dateTimes;
        ZoneId overrideTz = zoneId.orElse(ZoneId.systemDefault());

        if (dateTime instanceof Year) {
            throw new IllegalStateException("this command requires a concrete date and time");
        } else if (dateTime instanceof YearMonth) {
            throw new IllegalStateException("this command requires a concrete date and time");
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

    private String buildOutput(Main.Format format, ZonedDateTime dateTime, double deltaT, AzimuthZenithAngle result, boolean showInput) {
        try (Formatter fmt = new Formatter(new StringBuilder(100))) {
            switch (format) {
                case JSON -> {
                    fmt.format("{");
                    if (showInput) {
                        fmt.format("\"latitude\":%.5f, ", parent.latitude);
                        fmt.format("\"longitude\":%.5f, ", parent.longitude);
                        fmt.format("\"elevation\":%.4f, ", elevation);
                        fmt.format("\"pressure\":%.4f, ", pressure);
                        fmt.format("\"temperature\":%.4f, ", temperature);
                    }
                    fmt.format("\"dateTime\":\"%s\", ", Main.ISO_LOCAL_DATE_TIME_REDUCED.format(dateTime));
                    if (showInput) {
                        fmt.format("\"deltaT\":%.4f, ", deltaT);
                    }
                    fmt.format("\"azimuth\":%.5f, ", result.getAzimuth());
                    fmt.format("\"zenith\":%.5f", result.getZenithAngle());
                    fmt.format("}%n");
                }

                case CSV -> {
                    if (showInput) {
                        fmt.format("%.5f,", parent.latitude);
                        fmt.format("%.5f,", parent.longitude);
                        fmt.format("%.2f,", elevation);
                        fmt.format("%.2f,", pressure);
                        fmt.format("%.2f,", temperature);
                    }
                    fmt.format("%s,", Main.ISO_LOCAL_DATE_TIME_REDUCED.format(dateTime));
                    if (showInput) {
                        fmt.format("%.2f,", deltaT);
                    }
                    fmt.format("%.5f,", result.getAzimuth());
                    fmt.format("%.5f%n", result.getZenithAngle());
                }

                case HUMAN -> {
                    if (showInput) {
                        fmt.format("latitude:    %24.4f%n", parent.latitude);
                        fmt.format("longitude:   %24.4f%n", parent.longitude);
                        fmt.format("elevation:   %22.2f%n", elevation);
                        fmt.format("pressure:    %22.2f%n", pressure);
                        fmt.format("temperature: %22.2f%n", temperature);
                    }
                    String[] splitDateTime = Main.ISO_LOCAL_DATE_TIME_REDUCED.format(dateTime).split("T");
                    fmt.format("date:                      %s%n", splitDateTime[0]);
                    fmt.format("time:               %s%n", splitDateTime[1]);
                    if (showInput) {
                        fmt.format("delta T:     %22.2f%n", deltaT);
                    }
                    fmt.format("azimuth:     %24.4f%n", result.getAzimuth());
                    fmt.format("zenith:      %24.4f%n", result.getZenithAngle());
                }
            }
            return fmt.toString();
        }
    }

}
