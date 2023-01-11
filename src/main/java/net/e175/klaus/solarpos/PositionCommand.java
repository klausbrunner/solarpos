package net.e175.klaus.solarpos;

import net.e175.klaus.solarpositioning.AzimuthZenithAngle;
import net.e175.klaus.solarpositioning.Grena3;
import net.e175.klaus.solarpositioning.SPA;
import picocli.CommandLine;

import java.util.Formatter;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "position", description = "calculates topocentric solar coordinates")
class PositionCommand implements Callable<Integer> {

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

        AzimuthZenithAngle position = switch (this.algorithm) {
            case SPA ->
                    SPA.calculateSolarPosition(parent.dateTime, parent.latitude, parent.longitude, elevation, parent.deltaT, pressure, temperature);
            case GRENA3 ->
                    Grena3.calculateSolarPosition(parent.dateTime, parent.latitude, parent.longitude, parent.deltaT, pressure, temperature);
        };

        String output = buildOutput(parent.format, position, parent.showInput);
        parent.spec.commandLine().getOut().println(output);

        return 0;
    }

    private String buildOutput(Main.Format format, AzimuthZenithAngle position, boolean allInput) {
        try (Formatter fmt = new Formatter(new StringBuilder(100))) {
            switch (format) {
                case JSON -> {
                    fmt.format("{");
                    if (allInput) {
                        fmt.format("\"latitude\":%.5f, ", parent.latitude);
                        fmt.format("\"longitude\":%.5f, ", parent.longitude);
                        fmt.format("\"elevation\":%.4f, ", elevation);
                        fmt.format("\"pressure\":%.4f, ", pressure);
                        fmt.format("\"temperature\":%.4f, ", temperature);
                    }
                    fmt.format("\"dateTime\":\"%s\", ", Main.ISO_LOCAL_DATE_TIME_REDUCED.format(parent.dateTime));
                    if (allInput) {
                        fmt.format("\"deltaT\":%.4f, ", parent.deltaT);
                    }
                    fmt.format("\"azimuth\":%.5f, ", position.getAzimuth());
                    fmt.format("\"zenith\":%.5f", position.getZenithAngle());
                    fmt.format("}%n");
                }

                case CSV -> {
                    if (allInput) {
                        fmt.format("%.5f,", parent.latitude);
                        fmt.format("%.5f,", parent.longitude);
                        fmt.format("%.2f,", elevation);
                        fmt.format("%.2f,", pressure);
                        fmt.format("%.2f,", temperature);
                    }
                    fmt.format("%s,", Main.ISO_LOCAL_DATE_TIME_REDUCED.format(parent.dateTime));
                    if (allInput) {
                        fmt.format("%.2f,", parent.deltaT);
                    }
                    fmt.format("%.5f,", position.getAzimuth());
                    fmt.format("%.5f%n", position.getZenithAngle());
                }

                case HUMAN -> {
                    if (allInput) {
                        fmt.format("latitude:    %24.4f%n", parent.latitude);
                        fmt.format("longitude:   %24.4f%n", parent.longitude);
                        fmt.format("elevation:   %22.2f%n", elevation);
                        fmt.format("pressure:    %22.2f%n", pressure);
                        fmt.format("temperature: %22.2f%n", temperature);
                    }
                    String[] splitDateTime = Main.ISO_LOCAL_DATE_TIME_REDUCED.format(parent.dateTime).split("T");
                    fmt.format("date:                      %s%n", splitDateTime[0]);
                    fmt.format("time:               %s%n", splitDateTime[1]);
                    if (allInput) {
                        fmt.format("delta T:     %22.2f%n", parent.deltaT);
                    }
                    fmt.format("azimuth:     %24.4f%n", position.getAzimuth());
                    fmt.format("zenith:      %24.4f%n", position.getZenithAngle());
                }
            }
            return fmt.toString();
        }
    }

}
