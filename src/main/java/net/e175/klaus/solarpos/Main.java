package net.e175.klaus.solarpos;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import java.util.Optional;
import net.e175.klaus.solarpos.util.TimeFormats;
import net.e175.klaus.solarpositioning.DeltaT;
import picocli.CommandLine;
import picocli.CommandLine.HelpCommand;

@CommandLine.Command(
    name = "solarpos",
    subcommands = {HelpCommand.class, PositionCommand.class, SunriseCommand.class},
    mixinStandardHelpOptions = true,
    description = "Calculates topocentric solar coordinates or sunrise/sunset times.",
    versionProvider = Main.ManifestBasedVersionProviderWithVariables.class,
    showAtFileInUsageHelp = true)
public final class Main {
  static {
    Locale.setDefault(Locale.ENGLISH);
  }

  enum Format {
    HUMAN,
    CSV,
    JSON
  }

  @CommandLine.Spec CommandLine.Model.CommandSpec spec;

  @CommandLine.Parameters(
      index = "0",
      description =
          "Latitude in decimal degrees or range (start:end:step). Positive North of equator.",
      converter = CoordinateRangeConverter.class)
  CoordinateRange latitude;

  @CommandLine.Parameters(
      index = "1",
      description =
          "Longitude in decimal degrees or range (start:end:step). Positive East of Greenwich.",
      converter = CoordinateRangeConverter.class)
  CoordinateRange longitude;

  @CommandLine.Parameters(
      index = "2",
      description =
          "Date/time in ISO format "
              + TimeFormats.INPUT_DATE_TIME_PATTERN
              + ". Use 'now' for current time and date.",
      converter = DateTimeConverter.class)
  TemporalAccessor dateTime;

  @CommandLine.Option(
      names = {"--timezone"},
      description =
          "Timezone as offset (e.g. +01:00) and/or zone id (e.g. America/Los_Angeles). Overrides any timezone info found in dateTime.")
  Optional<ZoneId> timezone;

  @CommandLine.Option(
      names = {"--show-inputs"},
      negatable = true,
      description =
          "Show all inputs in output. Automatically enabled for coordinate ranges unless --no-show-inputs is used.")
  boolean showInput;

  @CommandLine.Option(
      names = {"--format"},
      description = "Output format, one of ${COMPLETION-CANDIDATES}.",
      defaultValue = "human")
  Format format;

  @CommandLine.Option(
      names = {"--headers"},
      description = "Show headers in output (CSV only).")
  boolean headers;

  @CommandLine.Option(
      names = {"--deltat"},
      arity = "0..1",
      defaultValue = "0",
      fallbackValue = "NaN",
      description =
          "Delta T in seconds; an estimate is used if this option is given without a value.")
  double deltaT;

  void validate() {
    latitude.validateLatitude();
    longitude.validateLongitude();
  }

  boolean shouldShowInputs() {
    // If explicitly set by user (either --show-inputs or --no-show-inputs), respect it
    if (spec.commandLine().getParseResult().hasMatchedOption("--show-inputs")) {
      return showInput;
    }
    // Auto-enable for coordinate ranges when not explicitly set
    return !latitude.isSinglePoint() || !longitude.isSinglePoint();
  }

  double getBestGuessDeltaT(ZonedDateTime dateTime) {
    return Double.isFinite(deltaT) ? deltaT : DeltaT.estimate(dateTime.toLocalDate());
  }

  static CommandLine createCommandLine() {
    return new CommandLine(new Main())
        .setCaseInsensitiveEnumValuesAllowed(true)
        .setAbbreviatedOptionsAllowed(true)
        .setUnmatchedOptionsArePositionalParams(true);
  }

  public static void main(String[] args) {
    int exitCode = createCommandLine().execute(args);
    System.exit(exitCode);
  }

  static final class CoordinateRangeConverter
      implements CommandLine.ITypeConverter<CoordinateRange> {
    @Override
    public CoordinateRange convert(String value) {
      return CoordinateRange.parse(value);
    }
  }

  static final class ManifestBasedVersionProviderWithVariables
      implements CommandLine.IVersionProvider {
    public String[] getVersion() {
      String version = getClass().getPackage().getImplementationVersion();
      return new String[] {
        "${COMMAND-FULL-NAME} " + (version == null ? "" : version),
        String.format(
            " %s %s", System.getProperty("java.vm.name"), System.getProperty("java.vm.version")),
        String.format(
            " %s %s %s",
            System.getProperty("os.name"),
            System.getProperty("os.version"),
            System.getProperty("os.arch"))
      };
    }
  }
}
