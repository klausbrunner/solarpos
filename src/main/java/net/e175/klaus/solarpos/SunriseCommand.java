package net.e175.klaus.solarpos;

import net.e175.klaus.solarpositioning.SPA;
import net.e175.klaus.solarpositioning.SunriseTransitSet;
import picocli.CommandLine;

import java.time.format.DateTimeFormatter;
import java.util.Formatter;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "sunrise", description = "calculates sunrise, transit, and sunset")
public class SunriseCommand implements Callable<Integer> {
    @CommandLine.ParentCommand
    Main parent;

    @Override
    public Integer call() {
        parent.validate();

        SunriseTransitSet result = SPA.calculateSunriseTransitSet(parent.dateTime, parent.latitude, parent.longitude, parent.deltaT);

        String output = buildOutput(parent.format, result, parent.showInput);
        parent.spec.commandLine().getOut().println(output);

        return 0;
    }

    private String buildOutput(Main.Format format, SunriseTransitSet result, boolean showInput) {
        try (Formatter fmt = new Formatter(new StringBuilder(100))) {
            final DateTimeFormatter dateTimeFormatter = Main.ISO_LOCAL_DATE_TIME_REDUCED;
            switch (format) {
                case JSON -> {
                    fmt.format("{");
                    if (showInput) {
                        fmt.format("\"latitude\":%.5f, ", parent.latitude);
                        fmt.format("\"longitude\":%.5f, ", parent.longitude);
                    }
                    fmt.format("\"dateTime\":\"%s\", ", dateTimeFormatter.format(parent.dateTime));
                    if (showInput) {
                        fmt.format("\"deltaT\":%.4f, ", parent.deltaT);
                    }
                    fmt.format("\"sunrise\":\"%s\", ", dateTimeFormatter.format(result.getSunrise()));
                    fmt.format("\"transit\":\"%s\", ", dateTimeFormatter.format(result.getTransit()));
                    fmt.format("\"sunset\":\"%s\"", dateTimeFormatter.format(result.getSunset()));
                    fmt.format("}%n");
                }

                case CSV -> {
                    if (showInput) {
                        fmt.format("%.5f,", parent.latitude);
                        fmt.format("%.5f,", parent.longitude);
                    }
                    fmt.format("%s,", dateTimeFormatter.format(parent.dateTime));
                    if (showInput) {
                        fmt.format("%.2f,", parent.deltaT);
                    }
                    fmt.format("%s,", dateTimeFormatter.format(result.getSunrise()));
                    fmt.format("%s,", dateTimeFormatter.format(result.getTransit()));
                    fmt.format("%s%n", dateTimeFormatter.format(result.getSunset()));
                }

                case HUMAN -> {
                    if (showInput) {
                        fmt.format("latitude:    %24.4f%n", parent.latitude);
                        fmt.format("longitude:   %24.4f%n", parent.longitude);
                    }
                    String[] splitDateTime = dateTimeFormatter.format(parent.dateTime).split("T");
                    fmt.format("date:                      %s%n", splitDateTime[0]);
                    if (showInput) {
                        fmt.format("delta T:     %22.2f%n", parent.deltaT);
                    }
                    fmt.format("sunrise:    %25s%n", Main.ISO_LOCAL_TIME_REDUCED.format(result.getSunrise()));
                    fmt.format("sunrise:    %25s%n", Main.ISO_LOCAL_TIME_REDUCED.format(result.getTransit()));
                    fmt.format("sunset:     %25s%n", Main.ISO_LOCAL_TIME_REDUCED.format(result.getSunset()));
                }
            }
            return fmt.toString();
        }
    }
}
