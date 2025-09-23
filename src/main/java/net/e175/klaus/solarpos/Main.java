package net.e175.klaus.solarpos;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import net.e175.klaus.solarpos.util.DateTimeIterator;
import net.e175.klaus.solarpositioning.DeltaT;
import picocli.CommandLine;
import picocli.CommandLine.HelpCommand;

@CommandLine.Command(
    name = "solarpos",
    subcommands = {HelpCommand.class, PositionCommand.class, SunriseCommand.class},
    mixinStandardHelpOptions = true,
    description = {
      "Calculates topocentric solar coordinates or sunrise/sunset times.",
      "",
      "Examples:",
      "  solarpos 52.0 13.4 2024-01-01 position",
      "  solarpos 52:53:0.1 13:14:0.1 2024 position --format=csv",
      "  solarpos @coords.txt @times.txt position",
      "  solarpos @data.txt position  # paired lat,lng,datetime data",
      "  echo '52.0 13.4 2024-01-01T12:00:00' | solarpos @- position"
    },
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

  private InputMode inputMode;

  @CommandLine.Parameters(
      index = "0",
      description = {
        "Latitude: decimal degrees, range, or file",
        "  52.5        single coordinate",
        "  52:53:0.1   range from 52° to 53° in 0.1° steps",
        "  @coords.txt file with coordinates (or @- for stdin)"
      },
      converter = CoordinateRangeConverter.class)
  CoordinateRange latitude;

  @CommandLine.Parameters(
      index = "1",
      description = {
        "Longitude: decimal degrees, range, or file",
        "  13.4        single coordinate",
        "  13:14:0.1   range from 13° to 14° in 0.1° steps",
        "  @coords.txt file with coordinates (or @- for stdin)"
      },
      converter = CoordinateRangeConverter.class)
  CoordinateRange longitude;

  @CommandLine.Parameters(
      index = "2",
      description = {
        "Date/time: ISO format, partial dates, or file",
        "  2024-01-01           specific date (midnight)",
        "  2024-01-01T12:00:00  specific date and time",
        "  2024                 entire year (with --step)",
        "  now                  current date and time",
        "  @times.txt           file with times (or @- for stdin)",
        "                       (files require explicit dates like 2024-01-15)"
      },
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
          "Show all inputs in output. Automatically enabled for coordinate ranges, time series, files unless --no-show-inputs is used.")
  boolean showInput;

  @CommandLine.Option(
      names = {"--parallel"},
      negatable = true,
      defaultValue = "false",
      fallbackValue = "true",
      description =
          "Enable parallel processing for better performance on multi-core systems. May cause memory pressure with large datasets. Default: ${DEFAULT-VALUE}.")
  boolean parallel;

  @CommandLine.Option(
      names = {"--format"},
      description = "Output format, one of ${COMPLETION-CANDIDATES}.",
      defaultValue = "human")
  Format format;

  @CommandLine.Option(
      names = {"--headers"},
      negatable = true,
      defaultValue = "true",
      fallbackValue = "true",
      description = "Show headers in output (CSV only). Default: ${DEFAULT-VALUE}")
  boolean headers;

  @CommandLine.Option(
      names = {"--deltat"},
      arity = "0..1",
      defaultValue = "0",
      fallbackValue = "NaN",
      description =
          "Delta T in seconds; an estimate is used if this option is given without a value. Use --deltat=<value> to specify an explicit value.")
  double deltaT;

  @CommandLine.Option(
      names = {"--perf"},
      hidden = true,
      description = "Show performance statistics.")
  boolean showPerformance;

  void validate() {
    getInputMode().validate();
  }

  boolean shouldShowInputs() {
    if (spec.commandLine().getParseResult().hasMatchedOption("--show-inputs")) {
      return showInput;
    }
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

    validateStdinUsage(latParam, dateTimeParam);

    return switch (determineInputType(latParam, dateTimeParam)) {
      case COORDINATE_FILE -> createCoordinateFileMode(latParam);
      case TIME_FILE ->
          new InputMode.TimeFile(latitude, longitude, pathFromFileParam(dateTimeParam), timezone);
      case COORDINATE_RANGES ->
          new InputMode.CoordinateRanges(latitude, longitude, dateTime, timezone);
    };
  }

  private enum InputType {
    COORDINATE_FILE,
    TIME_FILE,
    COORDINATE_RANGES
  }

  private InputType determineInputType(String latParam, String dateTimeParam) {
    if (latParam.startsWith("@")) return InputType.COORDINATE_FILE;
    if (dateTimeParam.startsWith("@")) return InputType.TIME_FILE;
    return InputType.COORDINATE_RANGES;
  }

  private InputMode createCoordinateFileMode(String latParam) {
    var coordFile = pathFromFileParam(latParam);
    var dateTimeParam = getPositionalParam(2);
    return "now".equals(dateTimeParam)
        ? new InputMode.PairedData(coordFile, timezone)
        : new InputMode.CoordinateFile(coordFile, dateTime, timezone);
  }

  /** Validates that stdin is not used multiple times. */
  private void validateStdinUsage(String latParam, String dateTimeParam) {
    var stdinUsageCount =
        Stream.of(latParam, dateTimeParam).mapToLong(param -> "@-".equals(param) ? 1 : 0).sum();

    if (stdinUsageCount > 1) {
      throw new IllegalArgumentException(
          "Cannot use stdin (@-) for multiple inputs simultaneously");
    }
  }

  /** Converts a file parameter (@filename) to a Path. */
  private static java.nio.file.Path pathFromFileParam(String fileParam) {
    return java.nio.file.Path.of(fileParam.substring(1));
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

  Stream<ZonedDateTime> getDateTimesStream(
      Duration step, DateTimeIterator.TimePrecision precision) {
    return getInputMode().times(step, precision);
  }

  Stream<net.e175.klaus.solarpos.util.DateTimeIterator.CoordinateTimePair> getPairedDataStream() {
    return getInputMode().pairedData();
  }

  Stream<net.e175.klaus.solarpos.util.DateTimeIterator.CoordinateTimePair> getPairedDataStream(
      DateTimeIterator.TimePrecision precision) {
    return getInputMode().pairedData(precision);
  }

  boolean isPairedData() {
    return getInputMode().isPairedData();
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
      return insertDummyParameters(args, firstPositionalIndex, "0", "now");
    } else {
      return insertDummyParameters(args, firstPositionalIndex, "0");
    }
  }

  private static int findFirstPositionalIndex(String[] args) {
    return java.util.stream.IntStream.range(0, args.length)
        .filter(i -> !args[i].startsWith("-"))
        .findFirst()
        .orElse(-1);
  }

  private static final java.util.Set<String> COMMANDS =
      java.util.Set.of("position", "sunrise", "help");

  private static int countPositionalsBeforeCommand(String[] args, int startIndex) {
    int count = 0;
    for (int i = startIndex; i < args.length; i++) {
      if (!args[i].startsWith("-")) {
        if (COMMANDS.contains(args[i])) {
          break;
        }
        count++;
      }
    }
    return count;
  }

  private static String[] insertDummyParameters(String[] args, int insertIndex, String... dummies) {
    return Stream.concat(
            Stream.concat(Stream.of(args).limit(insertIndex + 1), Stream.of(dummies)),
            Stream.of(args).skip(insertIndex + 1))
        .toArray(String[]::new);
  }

  static final class CoordinateRangeConverter
      implements CommandLine.ITypeConverter<CoordinateRange> {
    @Override
    public CoordinateRange convert(String value) {
      if (value.startsWith("@")) {
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
