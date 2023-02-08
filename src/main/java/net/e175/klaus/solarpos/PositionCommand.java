package net.e175.klaus.solarpos;

import net.e175.klaus.solarpositioning.AzimuthZenithAngle;
import net.e175.klaus.solarpositioning.Grena3;
import net.e175.klaus.solarpositioning.SPA;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static net.e175.klaus.solarpos.Main.Format.HUMAN;

@CommandLine.Command(name = "position", description = "calculates topocentric solar coordinates")
final class PositionCommand implements Callable<Integer> {

    enum Algorithm {SPA, GRENA3}

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

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

    @CommandLine.Option(names = {"--step"}, description = "step interval for time series, in seconds", defaultValue = "3600")
    int step;

    @Override
    public Integer call() {
        parent.validate();
        if (step < 1 || step > 24 * 60 * 60) {
            throw new CommandLine.ParameterException(spec.commandLine(), "invalid step value");
        }

        final Stream<ZonedDateTime> dateTimes = getDatetimes(parent.dateTime, parent.timezone, step);
        parent.printAnyHeaders(HEADERS);

        final PrintWriter out = parent.spec.commandLine().getOut();
        dateTimes.forEach(dateTime -> {
            final double deltaT = parent.getBestGuessDeltaT(dateTime);
            AzimuthZenithAngle position = switch (this.algorithm) {
                case SPA ->
                        SPA.calculateSolarPosition(dateTime, parent.latitude, parent.longitude, elevation, deltaT, pressure, temperature);
                case GRENA3 ->
                        Grena3.calculateSolarPosition(dateTime, parent.latitude, parent.longitude, deltaT, pressure, temperature);
            };

            out.print(buildOutput(parent.format, dateTime, deltaT, position, parent.showInput));
        });
        out.flush();

        return 0;
    }

    static Stream<ZonedDateTime> getDatetimes(TemporalAccessor dateTime, Optional<ZoneId> zoneId, int step) {
        final ZoneId overrideTz = zoneId.orElse(ZoneId.systemDefault());

        if (dateTime instanceof Year y) {
            return Stream.iterate(ZonedDateTime.of(LocalDate.of(y.getValue(), 1, 1),
                            LocalTime.of(0, 0),
                            overrideTz),
                    i -> i.getYear() == y.getValue(),
                    i -> i.plusSeconds(step));
        } else if (dateTime instanceof YearMonth ym) {
            return Stream.iterate(ZonedDateTime.of(LocalDate.of(ym.getYear(), ym.getMonth(), 1),
                            LocalTime.of(0, 0),
                            overrideTz),
                    i -> i.getMonth() == ym.getMonth(),
                    i -> i.plusSeconds(step));
        } else if (dateTime instanceof LocalDate ld) {
            return Stream.iterate(ZonedDateTime.of(ld, LocalTime.of(0, 0), overrideTz),
                    i -> i.getDayOfMonth() == ld.getDayOfMonth(),
                    i -> i.plusSeconds(step));
        } else if (dateTime instanceof LocalDateTime ldt) {
            return Stream.of(ZonedDateTime.of(ldt, overrideTz));
        } else if (dateTime instanceof ZonedDateTime zdt) {
            return Stream.of(zoneId.isPresent() ?
                    ZonedDateTime.of(zdt.toLocalDate(), zdt.toLocalTime(), overrideTz) :
                    zdt);
        } else {
            throw new IllegalStateException("unexpected date/time type");
        }
    }

    private static final Map<Boolean, String> JSON_FORMATS = Map.of(
            true, """
                    {"latitude":%.5f,"longitude":%5f,"elevation":%.3f,"pressure":%.3f,"temperature":%.3f,"dateTime":"%s","deltaT":%.3f,"azimuth":%.5f,"zenith":%.5f}
                    """,
            false, """
                    {"dateTime":"%6$s","azimuth":%8$.5f,"zenith":%9$.5f}
                    """);

    private static final Map<Boolean, String> CSV_HEADERS = Map.of(
            true, "latitude,longitude,elevation,pressure,temperature,dateTime,deltaT,azimuth,zenith",
            false, "dateTime,azimuth,zenith");

    private static final Map<Boolean, String> CSV_FORMATS = Map.of(
            true, "%.5f,%.5f,%.3f,%.3f,%.3f,%s,%.3f,%.5f,%.5f%n",
            false, "%6$s,%8$.5f,%9$.5f%n");

    private static final Map<Boolean, String> HUMAN_FORMATS = Map.of(
            true, """
                    latitude:    %24.4f
                    longitude:   %24.4f
                    elevation:   %22.2f
                    pressure:    %22.2f
                    temperature: %22.2f
                    date/time:  %s
                    delta T:     %22.2f
                    azimuth:     %24.4f
                    zenith:      %24.4f
                    """,
            false, """
                    date/time:  %6$s
                    azimuth:     %8$24.4f
                    zenith:      %9$24.4f
                    """);

    private static final Map<Main.Format, Map<Boolean, String>> HEADERS =
            Map.of(Main.Format.CSV, CSV_HEADERS);

    private static final Map<Main.Format, Map<Boolean, String>> TEMPLATES =
            Map.of(Main.Format.CSV, CSV_FORMATS, Main.Format.JSON, JSON_FORMATS, HUMAN, HUMAN_FORMATS);

    private String buildOutput(Main.Format format, ZonedDateTime dateTime, double deltaT, AzimuthZenithAngle result, boolean showInput) {
        String template = TEMPLATES.get(format).get(showInput);
        DateTimeFormatter dtf = (format == HUMAN) ? Main.ISO_HUMAN_LOCAL_DATE_TIME_REDUCED : Main.ISO_LOCAL_DATE_TIME_REDUCED;
        return template.formatted(parent.latitude,
                parent.longitude,
                elevation,
                pressure,
                temperature,
                dtf.format(dateTime),
                deltaT,
                result.getAzimuth(),
                result.getZenithAngle());
    }

}
