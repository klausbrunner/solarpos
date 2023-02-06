package net.e175.klaus.solarpos;

import net.e175.klaus.solarpositioning.DeltaT;
import picocli.CommandLine;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static java.time.temporal.ChronoField.*;

@CommandLine.Command(name = "solarpos", subcommands = {PositionCommand.class, SunriseCommand.class}, mixinStandardHelpOptions = true, description = "Calculates topocentric solar coordinates or sunrise/sunset times.", versionProvider = Main.ManifestBasedVersionProviderWithVariables.class)
public final class Main {
    static final String INPUT_DATE_TIME_PATTERN = "yyyy[-MM[-dd[['T'][ ]HH:mm:ss[.SSS][XXX['['VV']']]]]]";
    static final DateTimeFormatter INPUT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(INPUT_DATE_TIME_PATTERN);
    static final DateTimeFormatter ISO_LOCAL_TIME_REDUCED = new DateTimeFormatterBuilder().appendValue(HOUR_OF_DAY, 2).appendLiteral(':').appendValue(MINUTE_OF_HOUR, 2).optionalStart().appendLiteral(':').appendValue(SECOND_OF_MINUTE, 2).appendOffsetId().toFormatter();
    static final DateTimeFormatter ISO_LOCAL_DATE_TIME_REDUCED = new DateTimeFormatterBuilder().parseCaseInsensitive().append(ISO_LOCAL_DATE).appendLiteral('T').append(ISO_LOCAL_TIME_REDUCED).toFormatter();
    static final DateTimeFormatter ISO_HUMAN_LOCAL_DATE_TIME_REDUCED = new DateTimeFormatterBuilder().parseCaseInsensitive().append(ISO_LOCAL_DATE).appendLiteral(' ').append(ISO_LOCAL_TIME_REDUCED).toFormatter();

    enum Format {HUMAN, CSV, JSON}

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Parameters(index = "0", description = "latitude in decimal degrees (positive North of equator)")
    double latitude;

    @CommandLine.Parameters(index = "1", description = "longitude in decimal degrees (positive East of Greenwich)")
    double longitude;

    @CommandLine.Parameters(index = "2", description = "date/time in ISO format " + INPUT_DATE_TIME_PATTERN + ". use 'now' for current time and date.", converter = DateTimeConverter.class)
    TemporalAccessor dateTime;

    @CommandLine.Option(names = {"--timezone"}, description = "timezone as offset (e.g. +01:00) and/or zone id (e.g. America/Los_Angeles). overrides any timezone info found in dateTime.")
    Optional<ZoneId> timezone;

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
    }

    double getBestGuessDeltaT(ZonedDateTime dateTime) {
        return Double.isNaN(deltaT) ? DeltaT.estimate(dateTime.toLocalDate()) : deltaT;
    }

    static CommandLine createCommandLine() {
        return new CommandLine(new Main()).setCaseInsensitiveEnumValuesAllowed(true).setAbbreviatedOptionsAllowed(true);
    }

    public static void main(String[] args) {
        int exitCode = createCommandLine().execute(args);
        System.exit(exitCode);
    }

    static final class ManifestBasedVersionProviderWithVariables implements CommandLine.IVersionProvider {
        public String[] getVersion() {
            // this requires Implementation-Version in the MANIFEST file to work
            String version = getClass().getPackage().getImplementationVersion();
            return new String[]{
                    "${COMMAND-FULL-NAME} " + (version == null ? "" : version),
                    String.format(" %s %s", System.getProperty("java.vm.name"), System.getProperty("java.vm.version")),
                    String.format(" %s %s %s", System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"))
            };
        }
    }
}

