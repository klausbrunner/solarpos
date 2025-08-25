package net.e175.klaus.solarpos.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import net.e175.klaus.solarpos.CoordinatePair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DateTimeIteratorTest {

  @TempDir Path tempDir;

  @Test
  void parsesCoordinatesFromFile() throws IOException {
    var coordFile = tempDir.resolve("coords.txt");
    Files.writeString(
        coordFile,
        """
        40.7 -74.0
        37.7749 -122.4194
        # Comment line
        51.5074 -0.1278
        """);

    var coordinates = DateTimeIterator.coordinatesFromFile(coordFile).toList();

    assertEquals(3, coordinates.size());
    assertEquals(new CoordinatePair(40.7, -74.0), coordinates.get(0));
    assertEquals(new CoordinatePair(37.7749, -122.4194), coordinates.get(1));
    assertEquals(new CoordinatePair(51.5074, -0.1278), coordinates.get(2));
  }

  @Test
  void parsesCoordinatesWithCommas() throws IOException {
    var coordFile = tempDir.resolve("coords.txt");
    Files.writeString(
        coordFile,
        """
        40.7, -74.0
        37.7749,-122.4194
        51.5074 , -0.1278
        """);

    var coordinates = DateTimeIterator.coordinatesFromFile(coordFile).toList();

    assertEquals(3, coordinates.size());
    assertEquals(new CoordinatePair(40.7, -74.0), coordinates.get(0));
    assertEquals(new CoordinatePair(37.7749, -122.4194), coordinates.get(1));
    assertEquals(new CoordinatePair(51.5074, -0.1278), coordinates.get(2));
  }

  @Test
  void parsesTimesFromFile() throws IOException {
    var timeFile = tempDir.resolve("times.txt");
    Files.writeString(
        timeFile,
        """
        2024-06-21T06:00
        2024-06-21T12:00
        # Comment
        2024-06-21T18:00
        """);

    var times = DateTimeIterator.fromFile(timeFile, Optional.empty()).toList();

    assertEquals(3, times.size());
    assertEquals("2024-06-21T06:00", times.get(0).toLocalDateTime().toString());
    assertEquals("2024-06-21T12:00", times.get(1).toLocalDateTime().toString());
    assertEquals("2024-06-21T18:00", times.get(2).toLocalDateTime().toString());
  }

  @Test
  void handlesEmptyLines() throws IOException {
    var coordFile = tempDir.resolve("coords.txt");
    Files.writeString(
        coordFile,
        """
        40.7 -74.0

        37.7749 -122.4194

        """);

    var coordinates = DateTimeIterator.coordinatesFromFile(coordFile).toList();

    assertEquals(2, coordinates.size());
  }

  @Test
  void rejectsInvalidCoordinates() throws IOException {
    var coordFile = tempDir.resolve("coords.txt");
    Files.writeString(coordFile, "invalid coordinate");

    assertThrows(
        IllegalArgumentException.class,
        () -> DateTimeIterator.coordinatesFromFile(coordFile).toList());
  }

  @Test
  void rejectsInvalidTimes() throws IOException {
    var timeFile = tempDir.resolve("times.txt");
    Files.writeString(timeFile, "invalid time");

    assertThrows(
        IllegalArgumentException.class,
        () -> DateTimeIterator.fromFile(timeFile, Optional.empty()).toList());
  }

  @Test
  void parsesPairedDataFromFile() throws IOException {
    var pairedFile = tempDir.resolve("paired.txt");
    Files.writeString(
        pairedFile,
        """
        40.7 -74.0 2023-06-21T12:00
        37.7749 -122.4194 2023-07-15T14:30:15
        51.5074 -0.1278 2023-08-10
        """);

    var pairedData = DateTimeIterator.pairedDataFromFile(pairedFile, Optional.empty()).toList();

    assertEquals(3, pairedData.size());

    var first = pairedData.getFirst();
    assertEquals(new CoordinatePair(40.7, -74.0), first.coordinates());
    assertEquals(ZonedDateTime.parse("2023-06-21T12:00:00Z[UTC]"), first.dateTime());

    var second = pairedData.get(1);
    assertEquals(new CoordinatePair(37.7749, -122.4194), second.coordinates());
    assertEquals(ZonedDateTime.parse("2023-07-15T14:30:15Z[UTC]"), second.dateTime());

    var third = pairedData.get(2);
    assertEquals(new CoordinatePair(51.5074, -0.1278), third.coordinates());
    assertEquals(ZonedDateTime.parse("2023-08-10T00:00:00Z[UTC]"), third.dateTime());
  }

  @Test
  void parsesPairedDataWithTimezone() throws IOException {
    var pairedFile = tempDir.resolve("paired-tz.txt");
    Files.writeString(pairedFile, "25.0 50.0 2023-06-21T12:00");

    var timezone = Optional.of(ZoneId.of("Europe/Paris"));
    var pairedData = DateTimeIterator.pairedDataFromFile(pairedFile, timezone).toList();

    assertEquals(1, pairedData.size());
    var result = pairedData.getFirst();
    assertEquals(new CoordinatePair(25.0, 50.0), result.coordinates());
    assertEquals(ZonedDateTime.parse("2023-06-21T12:00:00+02:00[Europe/Paris]"), result.dateTime());
  }

  @Test
  void parsesPairedDataWithCommas() throws IOException {
    var pairedFile = tempDir.resolve("paired-comma.txt");
    Files.writeString(pairedFile, "40.7, -74.0, 2023-06-21T12:00");

    var pairedData = DateTimeIterator.pairedDataFromFile(pairedFile, Optional.empty()).toList();

    assertEquals(1, pairedData.size());
    assertEquals(new CoordinatePair(40.7, -74.0), pairedData.getFirst().coordinates());
  }

  @Test
  void pairedDataSkipsCommentsAndEmptyLines() throws IOException {
    var pairedFile = tempDir.resolve("paired-comments.txt");
    Files.writeString(
        pairedFile,
        """
        # This is a comment
        25.0 50.0 2023-06-21T12:00

        # Another comment
        30.0 60.0 2023-07-15T14:30
        """);

    var pairedData = DateTimeIterator.pairedDataFromFile(pairedFile, Optional.empty()).toList();

    assertEquals(2, pairedData.size());
    assertEquals(new CoordinatePair(25.0, 50.0), pairedData.get(0).coordinates());
    assertEquals(new CoordinatePair(30.0, 60.0), pairedData.get(1).coordinates());
  }

  @Test
  void pairedDataRejectsInvalidFormat() throws IOException {
    var pairedFile = tempDir.resolve("invalid-paired.txt");
    Files.writeString(pairedFile, "40.7 -74.0"); // Missing datetime

    assertThrows(
        IllegalArgumentException.class,
        () -> DateTimeIterator.pairedDataFromFile(pairedFile, Optional.empty()).toList());
  }

  @Test
  void pairedDataRejectsInvalidDateTime() throws IOException {
    var pairedFile = tempDir.resolve("invalid-datetime.txt");
    Files.writeString(pairedFile, "40.7 -74.0 invalid-date");

    assertThrows(
        RuntimeException.class,
        () -> DateTimeIterator.pairedDataFromFile(pairedFile, Optional.empty()).toList());
  }
}
