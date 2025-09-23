package net.e175.klaus.solarpos.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.e175.klaus.solarpos.CoordinatePair;

public final class DateTimeIterator {

  private static final Pattern WHITESPACE_OR_COMMA = Pattern.compile("[\\s,]+");

  private DateTimeIterator() {}

  /** Specifies the required precision for time inputs. */
  public enum TimePrecision {
    /** Requires explicit time (for position calculations). */
    TIME_REQUIRED,
    /** Date precision is sufficient (for sunrise calculations). */
    DATE_SUFFICIENT
  }

  /** Represents a coordinate pair with its associated time. */
  public record CoordinateTimePair(CoordinatePair coordinates, ZonedDateTime dateTime) {}

  /**
   * Creates a stream of ZonedDateTime values based on the input temporal and step duration.
   *
   * @param dateTime the temporal accessor defining the time range
   * @param zoneId optional timezone override
   * @param step the step duration between iterations
   * @return stream of ZonedDateTime values
   */
  public static Stream<ZonedDateTime> iterate(
      TemporalAccessor dateTime, Optional<ZoneId> zoneId, Duration step) {
    final ZoneId overrideTz = zoneId.orElse(ZoneId.systemDefault());

    return switch (dateTime) {
      case Year y -> iterateYear(y, overrideTz, step);
      case YearMonth ym -> iterateYearMonth(ym, overrideTz, step);
      case LocalDate ld -> iterateLocalDate(ld, overrideTz, step);
      case LocalDateTime ldt -> Stream.of(ZonedDateTime.of(ldt, overrideTz));
      case LocalTime lt -> Stream.of(ZonedDateTime.of(LocalDate.now(), lt, overrideTz));
      case OffsetTime ot ->
          Stream.of(
              ZonedDateTime.of(
                  LocalDate.now(),
                  ot.toLocalTime(),
                  zoneId.isPresent() ? overrideTz : ot.getOffset()));
      case ZonedDateTime zdt ->
          Stream.of(
              zoneId.isPresent()
                  ? ZonedDateTime.of(zdt.toLocalDate(), zdt.toLocalTime(), overrideTz)
                  : zdt);
      default -> throw new IllegalStateException("unexpected date/time type " + dateTime);
    };
  }

  private static Stream<ZonedDateTime> iterateYear(Year year, ZoneId zoneId, Duration step) {
    var start = ZonedDateTime.of(LocalDate.of(year.getValue(), 1, 1), LocalTime.of(0, 0), zoneId);
    return Stream.iterate(start, i -> i.getYear() == year.getValue(), i -> i.plus(step));
  }

  private static Stream<ZonedDateTime> iterateYearMonth(
      YearMonth yearMonth, ZoneId zoneId, Duration step) {
    var start =
        ZonedDateTime.of(
            LocalDate.of(yearMonth.getYear(), yearMonth.getMonth(), 1), LocalTime.of(0, 0), zoneId);
    return Stream.iterate(start, i -> i.getMonth() == yearMonth.getMonth(), i -> i.plus(step));
  }

  private static Stream<ZonedDateTime> iterateLocalDate(
      LocalDate localDate, ZoneId zoneId, Duration step) {
    var start = ZonedDateTime.of(localDate, LocalTime.of(0, 0), zoneId);
    // For single days, if step is 1 day or more, return single value
    // Otherwise iterate within the day
    if (step.toDays() >= 1) {
      return Stream.of(start);
    }
    return Stream.iterate(
        start, i -> i.getDayOfMonth() == localDate.getDayOfMonth(), i -> i.plus(step));
  }

  public static Stream<ZonedDateTime> fromFile(Path timesFile, Optional<ZoneId> zoneId) {
    return fromFile(timesFile, zoneId, TimePrecision.DATE_SUFFICIENT);
  }

  public static Stream<ZonedDateTime> fromFile(
      Path timesFile, Optional<ZoneId> zoneId, TimePrecision precision) {
    return readLinesFromPath(timesFile, line -> parseDateTime(line, zoneId, precision));
  }

  private static ZonedDateTime parseDateTime(
      String dateTimeStr, Optional<ZoneId> zoneId, TimePrecision precision) {
    TemporalAccessor temporal;
    try {
      temporal =
          DateTimeFormatter.ISO_ZONED_DATE_TIME.parseBest(
              dateTimeStr, ZonedDateTime::from, OffsetDateTime::from);
    } catch (Exception e1) {
      try {
        temporal =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME.parseBest(dateTimeStr, LocalDateTime::from);
      } catch (Exception e2) {
        try {
          temporal =
              TimeFormats.INPUT_DATE_TIME_FORMATTER.parseBest(
                  dateTimeStr,
                  ZonedDateTime::from,
                  LocalDateTime::from,
                  LocalDate::from,
                  YearMonth::from,
                  Year::from);
        } catch (Exception e3) {
          try {
            temporal =
                TimeFormats.INPUT_TIME_FORMATTER.parseBest(
                    dateTimeStr, OffsetTime::from, LocalTime::from);
            return convertToZonedDateTime(temporal, zoneId);
          } catch (Exception ex) {
            throw new IllegalArgumentException(
                "Failed to parse date/time: "
                    + dateTimeStr
                    + ". File inputs require explicit dates (e.g., '2024-01-15' or '2024-01-15T12:30:00'). "
                    + "For time series generation, use command-line date arguments instead.",
                ex);
          }
        }
      }
    }

    validatePrecision(temporal, dateTimeStr, precision);
    return convertToZonedDateTime(temporal, zoneId);
  }

  public static Stream<CoordinatePair> coordinatesFromFile(Path coordFile) {
    return readLinesFromPath(coordFile, DateTimeIterator::parseCoordinateLine);
  }

  /** Parses paired coordinate-time data from file (latitude longitude datetime per line). */
  public static Stream<CoordinateTimePair> pairedDataFromFile(
      Path dataFile, Optional<ZoneId> zoneId) {
    return pairedDataFromFile(dataFile, zoneId, TimePrecision.DATE_SUFFICIENT);
  }

  public static Stream<CoordinateTimePair> pairedDataFromFile(
      Path dataFile, Optional<ZoneId> zoneId, TimePrecision precision) {
    return readLinesFromPath(dataFile, line -> parsePairedDataLine(line, zoneId, precision));
  }

  private static ZonedDateTime convertToZonedDateTime(
      TemporalAccessor temporal, Optional<ZoneId> zoneId) {
    var overrideTz = zoneId.orElse(ZoneId.systemDefault());

    return switch (temporal) {
      case LocalDateTime ldt -> ZonedDateTime.of(ldt, overrideTz);
      case LocalTime lt -> ZonedDateTime.of(LocalDate.now(), lt, overrideTz);
      case OffsetTime ot ->
          ZonedDateTime.of(
              LocalDate.now(), ot.toLocalTime(), zoneId.isPresent() ? overrideTz : ot.getOffset());
      case ZonedDateTime zdt ->
          zoneId.isPresent()
              ? ZonedDateTime.of(zdt.toLocalDate(), zdt.toLocalTime(), overrideTz)
              : zdt;
      case LocalDate ld -> ZonedDateTime.of(ld, LocalTime.of(0, 0), overrideTz);
      default -> throw new IllegalStateException("unexpected date/time type " + temporal);
    };
  }

  private static CoordinateTimePair parsePairedDataLine(
      String line, Optional<ZoneId> zoneId, TimePrecision precision) {
    var parts = WHITESPACE_OR_COMMA.split(line.trim(), 3);
    if (parts.length != 3) {
      throw new IllegalArgumentException(
          "Invalid paired data format (expected lat lon datetime): " + line);
    }

    var coordinates = parseCoordinates(parts[0], parts[1]);
    var dateTime = parseDateTime(parts[2], zoneId, precision);
    return new CoordinateTimePair(coordinates, dateTime);
  }

  private static void validatePrecision(
      TemporalAccessor temporal, String input, TimePrecision precision) {
    if (precision == TimePrecision.TIME_REQUIRED && temporal instanceof LocalDate) {
      throw new IllegalArgumentException(
          "Position calculations require explicit time (e.g., '2024-01-15T12:30:00'). "
              + "Date-only input '"
              + input
              + "' is ambiguous for solar position.");
    }
  }

  private static CoordinatePair parseCoordinateLine(String line) {
    var parts = WHITESPACE_OR_COMMA.split(line.trim(), 2);
    if (parts.length != 2) {
      throw new IllegalArgumentException("Invalid coordinate format: " + line);
    }
    return parseCoordinates(parts[0], parts[1]);
  }

  private static CoordinatePair parseCoordinates(String latStr, String lngStr) {
    return new CoordinatePair(Double.parseDouble(latStr), Double.parseDouble(lngStr));
  }

  private static void closeQuietly(AutoCloseable resource) {
    try {
      resource.close();
    } catch (Exception ignored) {
      // Ignore close failures
    }
  }

  /** Unified method to read lines from either file or stdin. */
  private static <T> Stream<T> readLinesFromPath(
      Path path, java.util.function.Function<String, T> mapper) {
    String pathStr = path.toString();
    Stream<String> rawLines = "-".equals(pathStr) ? createStdinStream() : createFileStream(path);

    return rawLines
        .map(String::trim)
        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
        .map(mapper);
  }

  private static Stream<String> createStdinStream() {
    var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    return reader.lines().onClose(() -> closeQuietly(reader));
  }

  private static Stream<String> createFileStream(Path path) {
    try {
      var lines = Files.lines(path);
      return lines.onClose(() -> closeQuietly(lines));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read from: " + path, e);
    }
  }
}
