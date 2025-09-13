package net.e175.klaus.solarpos;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InputModeTest {

  @Test
  void coordinateRangesGeneratesCartesianProduct() {
    var mode =
        new InputMode.CoordinateRanges(
            new CoordinateRange(0, 10, 10),
            new CoordinateRange(20, 30, 10),
            ZonedDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneId.of("UTC")),
            Optional.empty());

    var coords = mode.coordinates().toList();

    assertEquals(4, coords.size());
    assertArrayEquals(
        new CoordinatePair[] {
          new CoordinatePair(0, 20),
          new CoordinatePair(0, 30),
          new CoordinatePair(10, 20),
          new CoordinatePair(10, 30)
        },
        coords.toArray());
  }

  @Test
  void coordinateRangesSinglePointCoordinates() {
    var mode =
        new InputMode.CoordinateRanges(
            new CoordinateRange(45.5, 45.5, 1),
            new CoordinateRange(-122.3, -122.3, 1),
            ZonedDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneId.of("UTC")),
            Optional.empty());

    var coords = mode.coordinates().toList();

    assertEquals(1, coords.size());
    assertEquals(new CoordinatePair(45.5, -122.3), coords.getFirst());
  }

  @Test
  void coordinateRangesValidatesLatitudeBounds() {
    var mode =
        new InputMode.CoordinateRanges(
            new CoordinateRange(-91, -80, 5),
            new CoordinateRange(0, 10, 10),
            ZonedDateTime.now(),
            Optional.empty());

    var ex = assertThrows(IllegalArgumentException.class, mode::validate);
    assertTrue(ex.getMessage().toLowerCase().contains("latitude"));
  }

  @Test
  void coordinateRangesValidatesLongitudeBounds() {
    var mode =
        new InputMode.CoordinateRanges(
            new CoordinateRange(0, 10, 10),
            new CoordinateRange(-180, 181, 361),
            ZonedDateTime.now(),
            Optional.empty());

    var ex = assertThrows(IllegalArgumentException.class, mode::validate);
    assertTrue(ex.getMessage().toLowerCase().contains("longitude"));
  }

  @Test
  void coordinateRangesGeneratesTimeSeries() {
    var mode =
        new InputMode.CoordinateRanges(
            new CoordinateRange(0, 0, 1),
            new CoordinateRange(0, 0, 1),
            LocalDate.of(2024, 1, 15),
            Optional.of(ZoneId.of("UTC")));

    var times = mode.times(Duration.ofHours(6)).limit(4).toList();

    assertEquals(4, times.size());
    assertEquals(ZonedDateTime.of(2024, 1, 15, 0, 0, 0, 0, ZoneId.of("UTC")), times.get(0));
    assertEquals(ZonedDateTime.of(2024, 1, 15, 6, 0, 0, 0, ZoneId.of("UTC")), times.get(1));
    assertEquals(ZonedDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneId.of("UTC")), times.get(2));
    assertEquals(ZonedDateTime.of(2024, 1, 15, 18, 0, 0, 0, ZoneId.of("UTC")), times.get(3));
  }

  @Test
  void coordinateRangesShouldShowInputsForMultipleCoordinates() {
    var mode =
        new InputMode.CoordinateRanges(
            new CoordinateRange(0, 10, 10),
            new CoordinateRange(0, 10, 10),
            ZonedDateTime.now(),
            Optional.empty());

    assertTrue(mode.shouldShowInputs());
  }

  @Test
  void coordinateRangesShouldNotShowInputsForSinglePoint() {
    var mode =
        new InputMode.CoordinateRanges(
            new CoordinateRange(45.5, 45.5, 1),
            new CoordinateRange(-122.3, -122.3, 1),
            ZonedDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneId.of("UTC")),
            Optional.empty());

    assertFalse(mode.shouldShowInputs());
  }

  @Test
  void coordinateRangesShouldShowInputsForMultipleTimes() {
    var mode =
        new InputMode.CoordinateRanges(
            new CoordinateRange(45.5, 45.5, 1),
            new CoordinateRange(-122.3, -122.3, 1),
            LocalDate.of(2024, 1, 15),
            Optional.empty());

    assertTrue(mode.shouldShowInputs());
  }

  @Test
  void isStdinPathRecognizesStdin() {
    assertTrue(InputMode.isStdinPath(Paths.get("-")));
    assertFalse(InputMode.isStdinPath(Paths.get("/some/file.txt")));
    assertFalse(InputMode.isStdinPath(Paths.get("./data.csv")));
  }

  @Test
  void hasMultipleTimesDetectsTemporalTypes() {
    assertTrue(InputMode.hasMultipleTimes(Year.of(2024)));
    assertTrue(InputMode.hasMultipleTimes(YearMonth.of(2024, 1)));
    assertTrue(InputMode.hasMultipleTimes(LocalDate.of(2024, 1, 15)));
    assertFalse(InputMode.hasMultipleTimes(ZonedDateTime.now()));
  }

  @Test
  void coordinateFileAlwaysShowsInputs() {
    var mode =
        new InputMode.CoordinateFile(
            Paths.get("coords.txt"), ZonedDateTime.now(), Optional.empty());

    assertTrue(mode.shouldShowInputs());
  }

  @Test
  void timeFileValidatesCoordinateRanges() {
    var mode =
        new InputMode.TimeFile(
            new CoordinateRange(100, 110, 10),
            new CoordinateRange(0, 10, 10),
            Paths.get("times.txt"),
            Optional.empty());

    var ex = assertThrows(IllegalArgumentException.class, mode::validate);
    assertTrue(ex.getMessage().toLowerCase().contains("latitude"));
  }

  @Test
  void timeFileAlwaysShowsInputs() {
    var mode =
        new InputMode.TimeFile(
            new CoordinateRange(0, 0, 1),
            new CoordinateRange(0, 0, 1),
            Paths.get("times.txt"),
            Optional.empty());

    assertTrue(mode.shouldShowInputs());
  }

  @Test
  void pairedDataIsPairedData() {
    var mode = new InputMode.PairedData(Paths.get("paired.txt"), Optional.empty());

    assertTrue(mode.isPairedData());
    assertTrue(mode.shouldShowInputs());
  }

  @Test
  void pairedDataThrowsForCoordinates() {
    var mode = new InputMode.PairedData(Paths.get("paired.txt"), Optional.empty());

    var ex = assertThrows(UnsupportedOperationException.class, mode::coordinates);
    assertTrue(ex.getMessage().contains("Use pairedData()"));
  }

  @Test
  void pairedDataThrowsForTimes() {
    var mode = new InputMode.PairedData(Paths.get("paired.txt"), Optional.empty());

    var ex =
        assertThrows(UnsupportedOperationException.class, () -> mode.times(Duration.ofHours(1)));
    assertTrue(ex.getMessage().contains("Use pairedData()"));
  }

  @Test
  void defaultPairedDataReturnsEmptyStream() {
    var mode =
        new InputMode.CoordinateRanges(
            new CoordinateRange(0, 0, 1),
            new CoordinateRange(0, 0, 1),
            ZonedDateTime.now(),
            Optional.empty());

    assertEquals(0, mode.pairedData().count());
    assertFalse(mode.isPairedData());
  }

  @Test
  void coordinateRangeRejectsZeroStep() {
    assertThrows(IllegalArgumentException.class, () -> new CoordinateRange(45.5, 45.5, 0));
  }

  @Test
  void coordinateRangeRejectsNegativeSteps() {
    assertThrows(IllegalArgumentException.class, () -> new CoordinateRange(10, 0, -5));
  }
}
