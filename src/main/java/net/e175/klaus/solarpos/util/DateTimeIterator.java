package net.e175.klaus.solarpos.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import java.util.stream.Stream;
import net.e175.klaus.solarpos.CoordinatePair;

public final class DateTimeIterator {

  private DateTimeIterator() {}

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
    try (var lines = Files.lines(timesFile)) {
      return lines
          .filter(line -> !line.trim().isEmpty() && !line.trim().startsWith("#"))
          .map(line -> parseDateTime(line.trim(), zoneId))
          .toList()
          .stream();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read times from: " + timesFile, e);
    }
  }

  private static ZonedDateTime parseDateTime(String dateTimeStr, Optional<ZoneId> zoneId) {
    try {
      var temporal =
          TimeFormats.INPUT_DATE_TIME_FORMATTER.parseBest(
              dateTimeStr,
              ZonedDateTime::from,
              LocalDateTime::from,
              LocalDate::from,
              YearMonth::from,
              Year::from);
      return convertToZonedDateTime(temporal, zoneId);
    } catch (Exception e) {
      try {
        var temporal =
            TimeFormats.INPUT_TIME_FORMATTER.parseBest(
                dateTimeStr, OffsetTime::from, LocalTime::from);
        return convertToZonedDateTime(temporal, zoneId);
      } catch (Exception ex) {
        throw new IllegalArgumentException("Failed to parse date/time: " + dateTimeStr, ex);
      }
    }
  }

  public static Stream<CoordinatePair> coordinatesFromFile(Path coordFile) {
    try (var lines = Files.lines(coordFile)) {
      return lines
          .filter(line -> !line.trim().isEmpty() && !line.trim().startsWith("#"))
          .map(DateTimeIterator::parseCoordinateLine)
          .toList()
          .stream();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read coordinates from: " + coordFile, e);
    }
  }

  /** Parses paired coordinate-time data from file (latitude longitude datetime per line). */
  public static Stream<CoordinateTimePair> pairedDataFromFile(
      Path dataFile, Optional<ZoneId> zoneId) {
    try (var lines = Files.lines(dataFile)) {
      return lines
          .filter(line -> !line.trim().isEmpty() && !line.trim().startsWith("#"))
          .map(line -> parsePairedDataLine(line, zoneId))
          .toList()
          .stream();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read paired data from: " + dataFile, e);
    }
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

  private static CoordinateTimePair parsePairedDataLine(String line, Optional<ZoneId> zoneId) {
    var parts = line.replace(',', ' ').trim().split("\\s+");
    if (parts.length != 3) {
      throw new IllegalArgumentException(
          "Invalid paired data format (expected lat lon datetime): " + line);
    }

    var coordinates =
        new CoordinatePair(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
    var temporalAccessor =
        TimeFormats.INPUT_DATE_TIME_FORMATTER.parseBest(
            parts[2], ZonedDateTime::from, LocalDateTime::from, LocalDate::from);
    var dateTime = convertToZonedDateTime(temporalAccessor, zoneId);

    return new CoordinateTimePair(coordinates, dateTime);
  }

  private static CoordinatePair parseCoordinateLine(String line) {
    var parts = line.replace(',', ' ').trim().split("\\s+");
    if (parts.length != 2) {
      throw new IllegalArgumentException("Invalid coordinate format: " + line);
    }
    return new CoordinatePair(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
  }
}
