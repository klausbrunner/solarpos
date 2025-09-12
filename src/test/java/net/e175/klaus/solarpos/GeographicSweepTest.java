package net.e175.klaus.solarpos;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.util.List;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

final class GeographicSweepTest {

  private static List<CSVRecord> parseCSV(String csvOutput) throws Exception {
    return TestUtil.CSV.parse(new StringReader(csvOutput)).getRecords();
  }

  private static void assertCoordinateGrid(List<CSVRecord> records, String... expectedCoords) {
    assertEquals(expectedCoords.length, records.size());
    for (int i = 0; i < expectedCoords.length; i++) {
      var parts = expectedCoords[i].split(",");
      assertEquals(parts[0], records.get(i).get(0)); // lat
      assertEquals(parts[1], records.get(i).get(1)); // lng
    }
  }

  @Test
  void geographicSweepPosition() throws Exception {
    var result =
        TestUtil.run(
            "--format=csv",
            "--no-headers",
            "40.0:41.0:1.0",
            "73.0:74.0:1.0",
            "2024-06-21T12:00",
            "position");

    assertEquals(0, result.returnCode());
    var records = parseCSV(result.output());

    assertCoordinateGrid(
        records,
        "40.00000,73.00000",
        "40.00000,74.00000",
        "41.00000,73.00000",
        "41.00000,74.00000");
  }

  @Test
  void geographicSweepSunrise() throws Exception {
    var result =
        TestUtil.run(
            "--format=csv",
            "--no-headers",
            "40.0:41.0:1.0",
            "73.0:74.0:1.0",
            "2024-06-21",
            "sunrise");

    assertEquals(0, result.returnCode());
    var records = parseCSV(result.output());

    assertCoordinateGrid(
        records,
        "40.00000,73.00000",
        "40.00000,74.00000",
        "41.00000,73.00000",
        "41.00000,74.00000");
  }

  @Test
  void combinedTimeAndGeographicSweep() throws Exception {
    var result =
        TestUtil.run(
            "--format=csv",
            "--no-headers",
            "40.0:41.0:1.0",
            "73.0:74.0:1.0",
            "2024-06-21",
            "position",
            "--step=43200");

    assertEquals(0, result.returnCode());
    var records = parseCSV(result.output());
    assertEquals(8, records.size()); // 2x2 grid × 2 times (00:00, 12:00)

    // Verify we have coordinates for both time points
    assertTrue(records.stream().anyMatch(r -> r.get(5).contains("00:00:00"))); // dateTime column
    assertTrue(records.stream().anyMatch(r -> r.get(5).contains("12:00:00")));
  }

  @Test
  void backwardCompatibilitySingleCoordinate() throws Exception {
    var result =
        TestUtil.run(
            "--format=csv", "--no-headers", "40.7", "-74.0", "2024-06-21T12:00", "position");

    assertEquals(0, result.returnCode());
    var records = parseCSV(result.output());
    assertEquals(1, records.size()); // Single coordinate

    // Single coordinates don't auto-enable show-inputs, so only 3 columns: dateTime, azimuth,
    // zenith
    assertEquals(3, records.getFirst().size());
    assertTrue(records.getFirst().get(0).contains("2024-06-21T12:00:00")); // dateTime column
  }

  @Test
  void validatesLatitudeRange() {
    var result = TestUtil.run("95.0:96.0:1.0", "0.0", "2024-06-21T12:00", "position");
    assertNotEquals(0, result.returnCode());
  }

  @Test
  void validatesLongitudeRange() {
    var result = TestUtil.run("40.0", "185.0:186.0:1.0", "2024-06-21T12:00", "position");
    assertNotEquals(0, result.returnCode());
  }

  @Test
  void rejectsInvalidRangeSyntax() {
    var result = TestUtil.run("40:41", "0.0", "2024-06-21T12:00", "position");
    assertNotEquals(0, result.returnCode());

    var result2 = TestUtil.run("abc", "0.0", "2024-06-21T12:00", "position");
    assertNotEquals(0, result2.returnCode());

    var result3 = TestUtil.run("40.0", "73:74:0.5:extra", "2024-06-21T12:00", "position");
    assertNotEquals(0, result3.returnCode());
  }

  @Test
  void autoShowInputsForLatitudeRange() throws Exception {
    var result =
        TestUtil.run(
            "--format=csv",
            "--no-headers",
            "40.0:41.0:1.0",
            "73.0",
            "2024-06-21T12:00",
            "position");

    assertEquals(0, result.returnCode());
    var records = parseCSV(result.output());
    assertEquals(2, records.size());

    // Auto show-inputs enabled, check coordinates
    assertEquals("40.00000", records.get(0).get(0)); // lat
    assertEquals("73.00000", records.get(0).get(1)); // lng
    assertEquals("41.00000", records.get(1).get(0));
    assertEquals("73.00000", records.get(1).get(1));
  }

  @Test
  void autoShowInputsForLongitudeRange() throws Exception {
    var result =
        TestUtil.run(
            "--format=csv",
            "--no-headers",
            "40.0",
            "73.0:74.0:1.0",
            "2024-06-21T12:00",
            "position");

    assertEquals(0, result.returnCode());
    var records = parseCSV(result.output());
    assertEquals(2, records.size());

    // Auto show-inputs enabled, check coordinates
    assertEquals("40.00000", records.get(0).get(0)); // lat
    assertEquals("73.00000", records.get(0).get(1)); // lng
    assertEquals("40.00000", records.get(1).get(0));
    assertEquals("74.00000", records.get(1).get(1));
  }

  @Test
  void canDisableAutoShowInputs() throws Exception {
    var result =
        TestUtil.run(
            "--format=csv",
            "--no-headers",
            "--no-show-inputs",
            "40.0:41.0:1.0",
            "73.0",
            "2024-06-21T12:00",
            "position");

    assertEquals(0, result.returnCode());
    var records = parseCSV(result.output());
    assertEquals(2, records.size());

    // --no-show-inputs disables auto show-inputs, so only 3 columns: dateTime, azimuth, zenith
    assertEquals(3, records.getFirst().size());
    assertTrue(records.getFirst().get(0).contains("2024-06-21T12:00:00")); // dateTime column
  }
}
