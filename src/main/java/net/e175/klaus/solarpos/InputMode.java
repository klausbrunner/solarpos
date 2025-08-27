package net.e175.klaus.solarpos;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.e175.klaus.solarpos.util.DateTimeIterator;
import net.e175.klaus.solarpos.util.DateTimeIterator.CoordinateTimePair;

/**
 * Represents the different input modes for coordinates and time data using a sealed interface
 * hierarchy.
 */
sealed interface InputMode
    permits InputMode.CoordinateRanges,
        InputMode.CoordinateFile,
        InputMode.TimeFile,
        InputMode.PairedData {

  Stream<CoordinatePair> coordinates();

  Stream<ZonedDateTime> times(Duration step);

  void validate();

  /** Checks if this input mode uses stdin. */
  default boolean usesStdin() {
    return false;
  }

  /** Helper to check if a path represents stdin. */
  static boolean isStdinPath(java.nio.file.Path path) {
    return "-".equals(path.toString());
  }

  boolean shouldShowInputs();

  /** Returns true if the temporal accessor will generate multiple time points. */
  static boolean hasMultipleTimes(TemporalAccessor dateTime) {
    return dateTime instanceof java.time.Year
        || dateTime instanceof java.time.YearMonth
        || dateTime instanceof java.time.LocalDate;
  }

  /** Standard coordinate ranges with Cartesian product */
  record CoordinateRanges(
      CoordinateRange latitude,
      CoordinateRange longitude,
      TemporalAccessor dateTime,
      Optional<ZoneId> timezone)
      implements InputMode {

    @Override
    public Stream<CoordinatePair> coordinates() {
      return latitude.stream()
          .boxed()
          .flatMap(lat -> longitude.stream().mapToObj(lng -> new CoordinatePair(lat, lng)));
    }

    @Override
    public Stream<ZonedDateTime> times(Duration step) {
      return DateTimeIterator.iterate(dateTime, timezone, step);
    }

    @Override
    public void validate() {
      latitude.validateLatitude();
      longitude.validateLongitude();
    }

    @Override
    public boolean shouldShowInputs() {
      return !latitude.isSinglePoint() || !longitude.isSinglePoint() || hasMultipleTimes(dateTime);
    }
  }

  /** Coordinate file with separate time parameter */
  record CoordinateFile(
      java.nio.file.Path coordFile, TemporalAccessor dateTime, Optional<ZoneId> timezone)
      implements InputMode {

    @Override
    public Stream<CoordinatePair> coordinates() {
      return DateTimeIterator.coordinatesFromFile(coordFile);
    }

    @Override
    public Stream<ZonedDateTime> times(Duration step) {
      return DateTimeIterator.iterate(dateTime, timezone, step);
    }

    @Override
    public void validate() {
      // File validation happens during stream creation
    }

    @Override
    public boolean usesStdin() {
      return isStdinPath(coordFile);
    }

    @Override
    public boolean shouldShowInputs() {
      return true;
    }
  }

  /** Coordinate ranges with time file */
  record TimeFile(
      CoordinateRange latitude,
      CoordinateRange longitude,
      java.nio.file.Path timeFile,
      Optional<ZoneId> timezone)
      implements InputMode {

    @Override
    public Stream<CoordinatePair> coordinates() {
      return latitude.stream()
          .boxed()
          .flatMap(lat -> longitude.stream().mapToObj(lng -> new CoordinatePair(lat, lng)));
    }

    @Override
    public Stream<ZonedDateTime> times(Duration step) {
      return DateTimeIterator.fromFile(timeFile, timezone);
    }

    @Override
    public void validate() {
      latitude.validateLatitude();
      longitude.validateLongitude();
    }

    @Override
    public boolean usesStdin() {
      return isStdinPath(timeFile);
    }

    @Override
    public boolean shouldShowInputs() {
      return true;
    }
  }

  /** Paired coordinate-time data from file */
  record PairedData(
      java.nio.file.Path dataFile,
      Optional<ZoneId> timezone,
      java.util.List<CoordinateTimePair> data)
      implements InputMode {

    public PairedData {
      data = List.copyOf(data);
    }

    public static PairedData from(java.nio.file.Path dataFile, Optional<ZoneId> timezone) {
      var data = DateTimeIterator.pairedDataFromFile(dataFile, timezone).toList();
      return new PairedData(dataFile, timezone, data);
    }

    @Override
    public Stream<CoordinatePair> coordinates() {
      return data.stream().map(CoordinateTimePair::coordinates);
    }

    @Override
    public Stream<ZonedDateTime> times(Duration step) {
      return data.stream().map(CoordinateTimePair::dateTime);
    }

    @Override
    public void validate() {
      // Validation happens during paired data loading
    }

    @Override
    public boolean usesStdin() {
      return isStdinPath(dataFile);
    }

    @Override
    public boolean shouldShowInputs() {
      return true;
    }
  }
}
