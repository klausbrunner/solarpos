package net.e175.klaus.solarpos;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
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

  // Cached input mode to avoid repeated parameter parsing
  private InputMode inputMode;

  @CommandLine.Parameters(
      index = "0",
      description =
          "Latitude in decimal degrees, range (start:end:step), or @file with coordinates.",
      converter = CoordinateRangeConverter.class)
  CoordinateRange latitude;

  @CommandLine.Parameters(
      index = "1",
      description =
          "Longitude in decimal degrees, range (start:end:step), or @file with coordinates.",
      converter = CoordinateRangeConverter.class)
  CoordinateRange longitude;

  @CommandLine.Parameters(
      index = "2",
      description =
          "Date/time in ISO format "
              + TimeFormats.INPUT_DATE_TIME_PATTERN
              + ", or @file with times. Use 'now' for current time and date.",
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
    getInputMode().validate();
  }

  boolean shouldShowInputs() {
    // If explicitly set by user (either --show-inputs or --no-show-inputs), respect it
    if (spec.commandLine().getParseResult().hasMatchedOption("--show-inputs")) {
      return showInput;
    }
    // Auto-enable for coordinate ranges when not explicitly set
    return getInputMode().shouldShowInputs();
  }

  /** Gets or creates the input mode based on current parameters. */
  private InputMode getInputMode() {
    if (inputMode == null) {
      inputMode = createInputMode();
    }
    return inputMode;
  }

  /** Factory method to determine input mode from parameters. */
  private InputMode createInputMode() {
    var latParam = getPositionalParam(0);
    var dateTimeParam = getPositionalParam(2);

    if (latParam.startsWith("@")) {
      if ("now".equals(dateTimeParam)) {
        // Paired data: @file + dummy datetime
        return InputMode.PairedData.from(java.nio.file.Path.of(latParam.substring(1)), timezone);
      } else {
        // Coordinate file: @file + real datetime
        return new InputMode.CoordinateFile(
            java.nio.file.Path.of(latParam.substring(1)), dateTime, timezone);
      }
    } else if (dateTimeParam.startsWith("@")) {
      // Time file: coordinates + @file
      return new InputMode.TimeFile(
          latitude, longitude, java.nio.file.Path.of(dateTimeParam.substring(1)), timezone);
    } else {
      // Standard coordinate ranges
      return new InputMode.CoordinateRanges(latitude, longitude, dateTime, timezone);
    }
  }

  /** Helper method to get positional parameter values. */
  private String getPositionalParam(int index) {
    return spec.commandLine()
        .getParseResult()
        .matchedPositionals()
        .get(index)
        .stringValues()
        .getFirst();
  }

  double getBestGuessDeltaT(ZonedDateTime dateTime) {
    return Double.isFinite(deltaT) ? deltaT : DeltaT.estimate(dateTime.toLocalDate());
  }

  Stream<CoordinatePair> getCoordinatesStream() {
    return getInputMode().coordinates();
  }

  Stream<ZonedDateTime> getDateTimesStream(Duration step) {
    return getInputMode().times(step);
  }

  static CommandLine createCommandLine() {
    return new CommandLine(new Main())
        .setCaseInsensitiveEnumValuesAllowed(true)
        .setAbbreviatedOptionsAllowed(true)
        .setUnmatchedOptionsArePositionalParams(true)
        .setExpandAtFiles(false);
  }

  public static void main(String[] args) {
    args = preprocessCoordinateFileArgs(args);
    int exitCode = createCommandLine().execute(args);
    System.exit(exitCode);
  }

  /**
   * Preprocesses command line arguments to handle file input elegantly. Supports both coordinate
   * files (@coords.txt) and paired coordinate-time files (@data.txt).
   */
  static String[] preprocessCoordinateFileArgs(String[] args) {
    int firstPositionalIndex = findFirstPositionalIndex(args);
    if (firstPositionalIndex == -1 || !args[firstPositionalIndex].startsWith("@")) {
      return args;
    }

    int positionalsBeforeCommand = countPositionalsBeforeCommand(args, firstPositionalIndex);

    if (positionalsBeforeCommand == 1) {
      // @file command → paired coordinate-time file, insert dummy longitude and datetime
      return insertDummyParameters(args, firstPositionalIndex, "0", "now");
    } else {
      // @file datetime command → coordinate file, insert dummy longitude only
      return insertDummyParameters(args, firstPositionalIndex, "0");
    }
  }

  private static int findFirstPositionalIndex(String[] args) {
    for (int i = 0; i < args.length; i++) {
      if (!args[i].startsWith("-")) {
        return i;
      }
    }
    return -1;
  }

  private static int countPositionalsBeforeCommand(String[] args, int startIndex) {
    int count = 0;
    for (int i = startIndex; i < args.length; i++) {
      if (!args[i].startsWith("-")) {
        // Stop at known commands - don't count them
        if ("position".equals(args[i]) || "sunrise".equals(args[i]) || "help".equals(args[i])) {
          break;
        }
        count++;
      }
    }
    return count;
  }

  private static String[] insertDummyParameters(String[] args, int insertIndex, String... dummies) {
    var newArgs = new String[args.length + dummies.length];
    System.arraycopy(args, 0, newArgs, 0, insertIndex + 1);
    System.arraycopy(dummies, 0, newArgs, insertIndex + 1, dummies.length);
    System.arraycopy(
        args,
        insertIndex + 1,
        newArgs,
        insertIndex + 1 + dummies.length,
        args.length - insertIndex - 1);
    return newArgs;
  }

  static final class CoordinateRangeConverter
      implements CommandLine.ITypeConverter<CoordinateRange> {
    @Override
    public CoordinateRange convert(String value) {
      if (value.startsWith("@")) {
        // For file input, return a dummy range - actual coordinates will be read in
        // getCoordinatesStream()
        return CoordinateRange.point(0.0);
      }
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
