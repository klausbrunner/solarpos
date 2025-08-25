package net.e175.klaus.solarpos;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {
  final Clock clock =
      Clock.fixed(Instant.parse("2020-03-03T10:15:30.00Z"), ZoneOffset.ofHoursMinutes(3, 0));

  @Test
  void dateParsing() {
    final DateTimeConverter dtc = new DateTimeConverter(this.clock);

    assertEquals(ZonedDateTime.now(clock), dtc.convert("now"));
    assertEquals(ZonedDateTime.parse("2023-01-01T13:30:15Z"), dtc.convert("2023-01-01T13:30:15Z"));
    assertEquals(ZonedDateTime.parse("2023-01-01T13:30:15Z"), dtc.convert("2023-01-01 13:30:15Z"));
    assertEquals(
        ZonedDateTime.parse("2011-12-03T10:15:30+01:00[Europe/Paris]"),
        dtc.convert("2011-12-03T10:15:30+01:00[Europe/Paris]"));
    assertEquals(
        ZonedDateTime.parse("2011-12-03T10:15:30+01:00[Europe/Paris]"),
        dtc.convert("2011-12-03 10:15:30+01:00[Europe/Paris]"));
    assertEquals(
        ZonedDateTime.parse("2023-01-01T13:30:15+03:00"), dtc.convert("2023-01-01T13:30:15+03:00"));
    assertEquals(
        ZonedDateTime.parse("2023-01-01T13:30:15Z"), dtc.convert("2023-01-01T13:30:15.000Z"));
    assertEquals(ZonedDateTime.parse("2023-01-01T13:30:00Z"), dtc.convert("2023-01-01T13:30Z"));
    assertEquals(
        ZonedDateTime.parse("2023-01-01T13:30:15.750Z"), dtc.convert("2023-01-01T13:30:15.750Z"));
    assertEquals(
        ZonedDateTime.parse("2023-01-01T13:30:15.250+03:00"),
        dtc.convert("2023-01-01T13:30:15.250+03:00"));
    assertEquals(OffsetTime.parse("13:30:15.250+03:00"), dtc.convert("13:30:15.250+03:00"));
    assertEquals(OffsetTime.parse("13:30:15.000+03:00"), dtc.convert("13:30:15+03:00"));
    assertEquals(OffsetTime.parse("13:30:00.000+03:00"), dtc.convert("13:30+03:00"));

    // File input returns dummy temporal for @file syntax
    assertNotNull(dtc.convert("@times.txt"));
  }

  @Test
  void version() {
    var result = TestUtil.run("-V");
    assertEquals(0, result.returnCode());
    assertTrue(result.output().contains("solarpos"));
  }

  @Test
  void rejectsBadDates() {
    var result = TestUtil.run("25", "50", "20", "position");
    assertNotEquals(0, result.returnCode());

    result = TestUtil.run("25", "50", "99999", "position");
    assertNotEquals(0, result.returnCode());

    result = TestUtil.run("25", "50", "2024-12-32", "position");
    assertNotEquals(0, result.returnCode());
  }

  @Test
  void rejectsBadCoords() {
    var dateTime = "2023";

    var result = TestUtil.run("91", "0", dateTime, "position");
    assertNotEquals(0, result.returnCode());

    result = TestUtil.run("0", "200", dateTime, "position");
    assertNotEquals(0, result.returnCode());
  }

  @Test
  void supportsPairedDataFiles(@TempDir java.nio.file.Path tempDir) throws IOException {
    var pairedFile = tempDir.resolve("paired.txt");
    Files.writeString(pairedFile, "25.0 50.0 2023-06-21T12:00");

    var result = TestUtil.run("@" + pairedFile, "position");

    assertEquals(0, result.returnCode());
    assertTrue(result.output().contains("2023-06-21"));
    assertTrue(result.output().contains("azimuth"));
    assertTrue(result.output().contains("zenith"));
  }

  @Test
  void pairedDataSupportsMultipleEntries(@TempDir java.nio.file.Path tempDir) throws IOException {
    var pairedFile = tempDir.resolve("multi-paired.txt");
    Files.writeString(
        pairedFile,
        """
        25.0 50.0 2023-06-21T12:00
        30.0 60.0 2023-07-15T14:30
        """);

    var result = TestUtil.run("@" + pairedFile, "position");

    assertEquals(0, result.returnCode());
    assertTrue(result.output().contains("2023-06-21"));
    assertTrue(result.output().contains("2023-07-15"));
  }

  @Test
  void pairedDataRejectsInvalidFormat(@TempDir java.nio.file.Path tempDir) throws IOException {
    var invalidFile = tempDir.resolve("invalid.txt");
    Files.writeString(invalidFile, "25.0 50.0"); // Missing datetime

    var result = TestUtil.run("@" + invalidFile, "position");

    assertNotEquals(0, result.returnCode());
    // Just verify that the command fails with invalid format, don't check specific error message
    // The error is correctly detected as shown by non-zero exit code
  }

  @Test
  void distinguishesCoordinateFilesFromPairedData(@TempDir java.nio.file.Path tempDir)
      throws IOException {
    var coordFile = tempDir.resolve("coords.txt");
    Files.writeString(coordFile, "25.0 50.0\n30.0 60.0");

    // Coordinate file with separate datetime parameter
    var result = TestUtil.run("@" + coordFile, "2023-06-21T12:00", "position");

    assertEquals(0, result.returnCode());
    assertTrue(result.output().contains("2023-06-21"));
    // Should have results for both coordinates at the same time
    var lines = result.output().lines().filter(line -> line.contains("date/time")).toList();
    assertEquals(2, lines.size());
  }
}
