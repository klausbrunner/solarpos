package net.e175.klaus.solarpos;

import net.e175.klaus.solarpositioning.DeltaT;
import picocli.CommandLine;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Stack;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static java.time.temporal.ChronoField.*;

@CommandLine.Command(name = "solarpos-cli", subcommands = {PositionCommand.class, SunriseCommand.class}, mixinStandardHelpOptions = true, description = "Calculates topocentric solar coordinates or sunrise/sunset times.", versionProvider = ManifestBasedVersionProviderWithVariables.class)
public final class Main {
    static final String INPUT_DATE_TIME_PATTERN = "yyyy-MM-dd['T'HH:mm:ss[.SSS][XXX]]";
    static final DateTimeFormatter INPUT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(INPUT_DATE_TIME_PATTERN);
    static final DateTimeFormatter ISO_LOCAL_TIME_REDUCED = new DateTimeFormatterBuilder().appendValue(HOUR_OF_DAY, 2).appendLiteral(':').appendValue(MINUTE_OF_HOUR, 2).optionalStart().appendLiteral(':').appendValue(SECOND_OF_MINUTE, 2).appendOffsetId().toFormatter();
    static final DateTimeFormatter ISO_LOCAL_DATE_TIME_REDUCED = new DateTimeFormatterBuilder().parseCaseInsensitive().append(ISO_LOCAL_DATE).appendLiteral('T').append(ISO_LOCAL_TIME_REDUCED).toFormatter();

    enum Format {HUMAN, CSV, JSON}

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Parameters(index = "0", description = "latitude in decimal degrees (positive North of equator)")
    double latitude;

    @CommandLine.Parameters(index = "1", description = "longitude in decimal degrees (positive East of Greenwich)")
    double longitude;

    @CommandLine.Parameters(index = "2", description = "date/time in ISO format " + INPUT_DATE_TIME_PATTERN + ". use 'now' for current time and date.", parameterConsumer = DateTimeConsumer.class)
    ZonedDateTime dateTime;

    @CommandLine.Option(names = {"--show-inputs"}, description = "show all inputs in output")
    boolean showInput;

    @CommandLine.Option(names = {"--format"}, description = "output format, one of ${COMPLETION-CANDIDATES}", defaultValue = "human")
    Format format;

    @CommandLine.Option(names = {"--deltat"}, arity = "0..1", defaultValue = "0", fallbackValue = "NaN", description = "delta T in seconds; an estimate is used if this option is given without a value")
    double deltaT;

    void validate() {
        if (latitude > 90.0 || latitude < -90.0) {
            throw new CommandLine.ParameterException(spec.commandLine(), "invalid latitude");
        }
        if (longitude > 180.0 || longitude < -180.0) {
            throw new CommandLine.ParameterException(spec.commandLine(), "invalid longitude");
        }

        this.deltaT = Double.isNaN(deltaT) ? DeltaT.estimate(dateTime.toLocalDate()) : deltaT;
    }

    static CommandLine createCommandLine() {
        return new CommandLine(new Main()).setCaseInsensitiveEnumValuesAllowed(true).setAbbreviatedOptionsAllowed(true);
    }

    public static void main(String[] args) {
        int exitCode = createCommandLine().execute(args);
        System.exit(exitCode);
    }

}

/**
 * A somewhat tolerant parser of ZonedDateTimes.
 */
final class DateTimeConsumer implements CommandLine.IParameterConsumer {

    public void consumeParameters(Stack<String> args, CommandLine.Model.ArgSpec argSpec, CommandLine.Model.CommandSpec commandSpec) {
        if (!args.isEmpty()) {
            String arg = args.pop();
            try {
                ZonedDateTime dateTime = lenientlyParseDateTime(arg, Clock.systemDefaultZone());
                argSpec.setValue(dateTime);
            } catch (DateTimeParseException e3) {
                throw new CommandLine.ParameterException(commandSpec.commandLine(), "failed to parse date/time " + arg, argSpec, arg);
            }
        }
    }

    static ZonedDateTime lenientlyParseDateTime(String arg, Clock clock) {
        final var nowDateTime = ZonedDateTime.now(clock);
        ZonedDateTime dateTime;
        if (arg.equals("now")) {
            dateTime = nowDateTime;
        } else {
            var temporal = Main.INPUT_DATE_TIME_FORMATTER.parseBest(arg, ZonedDateTime::from, LocalDateTime::from, LocalDate::from);

            if (temporal instanceof ZonedDateTime zdt) {
                dateTime = zdt;
            } else if (temporal instanceof LocalDateTime ldt) {
                dateTime = ZonedDateTime.of(ldt, nowDateTime.getZone());
            } else if (temporal instanceof LocalDate ld) {
                dateTime = ZonedDateTime.of(ld, nowDateTime.toLocalTime(), nowDateTime.getZone());
            } else {
                throw new DateTimeParseException("unable to parse", arg, 0);
            }
        }
        return dateTime;
    }
}

final class ManifestBasedVersionProviderWithVariables implements CommandLine.IVersionProvider {
    public String[] getVersion() {
        // this requires Implementation-Version in the MANIFEST file to work
        String version = getClass().getPackage().getImplementationVersion();
        return new String[]{"${COMMAND-FULL-NAME} " + (version == null ? "" : version)};
    }
}
