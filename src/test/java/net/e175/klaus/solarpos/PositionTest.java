package net.e175.klaus.solarpos;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PositionTest {

  @Test
  void basicUsageWithJson() {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2022-10-17T12:00:00Z";

    var result =
        TestUtil.run(
            lat, lon, dateTime, "--format=json", "--deltat=69", "--show-inputs", "position");
    assertEquals(0, result.returnCode());

    var jsonObject = JsonParser.parseString(result.output()).getAsJsonObject();
    assertEquals(dateTime, jsonObject.get("dateTime").getAsString());
    assertEquals(211.17614, jsonObject.get("azimuth").getAsDouble());
    assertEquals(66.06832, jsonObject.get("zenith").getAsDouble());

    assertEquals(Double.parseDouble(lat), jsonObject.get("latitude").getAsDouble());
    assertEquals(Double.parseDouble(lon), jsonObject.get("longitude").getAsDouble());
  }

  @ParameterizedTest
  @ValueSource(strings = {"12:00:00Z", "12:00:00+00:00", "12:00:00.000+00:00", "12:00Z"})
  void timePatterns(String time) {
    var lat = "52.0";
    var lon = "25.0";

    var inputTime = OffsetTime.parse(time);
    try (var withClock = new TestUtil.WithFixedClock(ZonedDateTime.now())) {
      var result = TestUtil.run(lat, lon, time, "--format=json", "position");
      assertEquals(0, result.returnCode());

      var jsonObject = JsonParser.parseString(result.output()).getAsJsonObject();
      var resultZdt = ZonedDateTime.parse(jsonObject.get("dateTime").getAsString());
      assertEquals(
          ZonedDateTime.of(
              LocalDateTime.of(withClock.dateTime().toLocalDate(), inputTime.toLocalTime()),
              inputTime.getOffset()),
          resultZdt);
    }
  }

  @Test
  void basicUsageWithJsonGrena() {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2022-10-17T12:00:00Z";

    var result =
        TestUtil.run(
            lat,
            lon,
            dateTime,
            "--headers",
            "--format=json",
            "--deltat=69",
            "position",
            "--algorithm=grena3");
    assertEquals(0, result.returnCode());

    var jsonObject = JsonParser.parseString(result.output()).getAsJsonObject();
    assertEquals(dateTime, jsonObject.get("dateTime").getAsString());
    assertEquals(211.17436, jsonObject.get("azimuth").getAsDouble());
    assertEquals(66.0685, jsonObject.get("zenith").getAsDouble());
  }

  @Test
  void basicUsageWithCsv() {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2003-10-17T12:00:00Z";

    var result = TestUtil.run(lat, lon, dateTime, "--format=csv", "--deltat=69", "position");
    assertEquals(0, result.returnCode());
    assertEquals("2003-10-17T12:00:00Z,211.20726,65.92499", result.output().strip());

    result =
        TestUtil.run(
            lat, lon, dateTime, "--format=csv", "--show-inputs", "--deltat=69", "position");
    assertEquals(0, result.returnCode());

    assertEquals(
        "52.00000,25.00000,0.000,1013.000,15.000,2003-10-17T12:00:00Z,69.000,211.20726,65.92499",
        result.output().strip());
  }

  @Test
  void seriesUsageWithCsv() throws IOException {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2003-10-17";

    var result =
        TestUtil.run(
            lat,
            lon,
            dateTime,
            "--format=csv",
            "--deltat=69",
            "--timezone=UTC",
            "position",
            "--step=7200");
    assertEquals(0, result.returnCode());

    var outputRecords = TestUtil.CSV.parse(new StringReader(result.output()));

    var referenceRecords =
        TestUtil.CSV.parse(
            new StringReader(
                """
                2003-10-17T00:00:00Z,38.87778,131.09385
                2003-10-17T02:00:00Z,69.90910,116.13739
                2003-10-17T04:00:00Z,94.54534,97.98688
                2003-10-17T06:00:00Z,118.48590,80.32356
                2003-10-17T08:00:00Z,146.00826,66.76879
                2003-10-17T10:00:00Z,178.46662,61.15269
                2003-10-17T12:00:00Z,211.20726,65.92499
                2003-10-17T14:00:00Z,239.15181,78.98078
                2003-10-17T16:00:00Z,263.21613,96.46308
                2003-10-17T18:00:00Z,287.52922,114.74832
                2003-10-17T20:00:00Z,317.71947,130.28269
                2003-10-17T22:00:00Z,358.05561,137.33998"""));

    assertIterableEquals(referenceRecords, outputRecords);
  }

  @Test
  void fullYearWithCsv() throws IOException {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2003";

    var result =
        TestUtil.run(
            lat,
            lon,
            dateTime,
            "--format=csv",
            "--deltat=69",
            "--timezone=UTC",
            "position",
            "--step=7200");
    assertEquals(0, result.returnCode());

    var outputRecords = TestUtil.CSV.parse(new StringReader(result.output())).getRecords();

    assertEquals(4380, outputRecords.size());
    assertEquals("2003-01-01T00:00:00Z", outputRecords.getFirst().get(0));
    assertEquals("2003-12-31T22:00:00Z", outputRecords.getLast().get(0));
  }

  @Test
  void rejectsInvalidStepValues() {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2003";

    var result = TestUtil.run(lat, lon, dateTime, "position", "--step=0.1");
    assertNotEquals(0, result.returnCode());

    result = TestUtil.run(lat, lon, dateTime, "position", "--step=999999");
    assertNotEquals(0, result.returnCode());
  }

  @Test
  void fullMonthWithCsv() throws IOException {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2024-02";

    var result =
        TestUtil.run(
            lat,
            lon,
            dateTime,
            "--format=csv",
            "--deltat=69",
            "--timezone=UTC",
            "position",
            "--step=7200");
    assertEquals(0, result.returnCode());

    var outputRecords = TestUtil.CSV.parse(new StringReader(result.output())).getRecords();

    assertEquals(348, outputRecords.size());
    assertEquals("2024-02-01T00:00:00Z", outputRecords.getFirst().get(0));
    assertEquals("2024-02-29T22:00:00Z", outputRecords.getLast().get(0));
  }

  @Test
  void fullMonthWithCsvHeaders() throws IOException {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2024-02";

    var result =
        TestUtil.run(
            lat,
            lon,
            dateTime,
            "--headers",
            "--format=csv",
            "--deltat=69",
            "--show-inputs",
            "--timezone=UTC",
            "position",
            "--step=7200");
    assertEquals(0, result.returnCode());

    var outputRecords =
        TestUtil.CSV_WITH_HEADER.parse(new StringReader(result.output())).getRecords();

    assertEquals(348, outputRecords.size());
    assertEquals("2024-02-01T00:00:00Z", outputRecords.getFirst().get("dateTime"));
    assertEquals("2024-02-29T22:00:00Z", outputRecords.getLast().get("dateTime"));
  }

  @Test
  void refractionParametersNotIncludedWhenDisabled() throws IOException {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2022-10-17T12:00:00Z";

    // Test with refraction enabled (default)
    var resultWithRefraction =
        TestUtil.run(lat, lon, dateTime, "--format=csv", "--headers", "--show-inputs", "position");
    assertEquals(0, resultWithRefraction.returnCode());

    var recordsWithRefraction =
        TestUtil.CSV_WITH_HEADER
            .parse(new StringReader(resultWithRefraction.output()))
            .getRecords();

    // Verify pressure and temperature are included when refraction is enabled
    assertNotNull(recordsWithRefraction.getFirst().get("pressure"));
    assertNotNull(recordsWithRefraction.getFirst().get("temperature"));

    // Test with refraction explicitly disabled
    var resultWithoutRefraction =
        TestUtil.run(
            lat,
            lon,
            dateTime,
            "--format=csv",
            "--headers",
            "--show-inputs",
            "position",
            "--no-refraction");
    assertEquals(0, resultWithoutRefraction.returnCode());

    var recordsWithoutRefraction =
        TestUtil.CSV_WITH_HEADER
            .parse(new StringReader(resultWithoutRefraction.output()))
            .getRecords();

    // Verify pressure and temperature are NOT included when refraction is disabled
    var headerLine = resultWithoutRefraction.output().lines().findFirst().orElseThrow();
    assertFalse(headerLine.contains("pressure"));
    assertFalse(headerLine.contains("temperature"));

    // Verify other fields are still present
    assertNotNull(recordsWithoutRefraction.getFirst().get("latitude"));
    assertNotNull(recordsWithoutRefraction.getFirst().get("longitude"));
    assertNotNull(recordsWithoutRefraction.getFirst().get("elevation"));
    assertNotNull(recordsWithoutRefraction.getFirst().get("dateTime"));
    assertNotNull(recordsWithoutRefraction.getFirst().get("deltaT"));
    assertNotNull(recordsWithoutRefraction.getFirst().get("azimuth"));
    assertNotNull(recordsWithoutRefraction.getFirst().get("zenith"));
  }

  @Test
  void humanFormatOutput() {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2022-10-17T12:00:00Z";

    var result =
        TestUtil.run(
            lat, lon, dateTime, "--format=human", "--deltat=69", "--show-inputs", "position");
    assertEquals(0, result.returnCode());

    var output = result.output();

    // Check the general structure and formatting
    assertTrue(output.contains("latitude"));
    assertTrue(output.contains("longitude"));
    assertTrue(output.contains("elevation"));
    assertTrue(output.contains("pressure"));
    assertTrue(output.contains("temperature"));
    assertTrue(output.contains("date/time")); // Human format uses "date/time" instead of "dateTime"
    assertTrue(output.contains("delta T")); // Human format uses "delta T" instead of "deltaT"
    assertTrue(output.contains("azimuth"));
    assertTrue(output.contains("zenith"));

    // Check for unit symbols and proper formatting
    assertTrue(output.contains("52.00000°"));
    assertTrue(output.contains("25.00000°"));
    assertTrue(output.contains("0.000 m"));
    assertTrue(output.contains("1013.000 hPa"));
    assertTrue(output.contains("15.000 °C"));
    assertTrue(output.contains("69.000 s"));

    // Check time formatting (human format uses spaces instead of 'T')
    assertTrue(output.contains("2022-10-17 12:00:00Z"));

    // Check angle measurements have degree symbols
    assertTrue(output.contains("211.17614°"));
    assertTrue(output.contains("66.06832°"));
  }

  @Test
  void humanFormatOutputWithoutRefraction() {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2022-10-17T12:00:00Z";

    var result =
        TestUtil.run(
            lat,
            lon,
            dateTime,
            "--format=human",
            "--deltat=69",
            "--show-inputs",
            "position",
            "--no-refraction");
    assertEquals(0, result.returnCode());

    var output = result.output();

    // Verify required fields are present
    assertTrue(output.contains("latitude"));
    assertTrue(output.contains("longitude"));
    assertTrue(output.contains("elevation"));
    assertTrue(output.contains("date/time"));
    assertTrue(output.contains("delta T"));
    assertTrue(output.contains("azimuth"));
    assertTrue(output.contains("zenith"));

    // Verify refraction parameters are NOT included
    assertFalse(output.contains("pressure"));
    assertFalse(output.contains("temperature"));

    // Verify units are still properly displayed
    assertTrue(output.contains("52.00000°"));
    assertTrue(output.contains("25.00000°"));
    assertTrue(output.contains("0.000 m"));
    assertTrue(output.contains("69.000 s"));
  }

  @Test
  void elevationAngleOutputWithJson() {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2022-10-17T12:00:00Z";

    var result =
        TestUtil.run(
            lat, lon, dateTime, "--format=json", "--deltat=69", "position", "--elevation-angle");
    assertEquals(0, result.returnCode());

    var jsonObject = JsonParser.parseString(result.output()).getAsJsonObject();
    assertEquals(dateTime, jsonObject.get("dateTime").getAsString());
    assertEquals(211.17614, jsonObject.get("azimuth").getAsDouble());
    assertEquals(23.93168, jsonObject.get("elevation-angle").getAsDouble(), 0.00001);
    assertFalse(jsonObject.has("zenith"));
  }

  @Test
  void elevationAngleOutputWithCsv() {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2003-10-17T12:00:00Z";

    var result =
        TestUtil.run(
            lat, lon, dateTime, "--format=csv", "--deltat=69", "position", "--elevation-angle");
    assertEquals(0, result.returnCode());
    assertEquals("2003-10-17T12:00:00Z,211.20726,24.07501", result.output().strip());
  }

  @Test
  void elevationAngleOutputWithCsvHeaders() {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2003-10-17T12:00:00Z";

    var result =
        TestUtil.run(
            lat,
            lon,
            dateTime,
            "--format=csv",
            "--headers",
            "--deltat=69",
            "position",
            "--elevation-angle");
    assertEquals(0, result.returnCode());

    var output = result.output();
    assertTrue(output.contains("elevation-angle"));
    assertFalse(output.contains("zenith"));
  }

  @Test
  void elevationAngleOutputWithHuman() {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2022-10-17T12:00:00Z";

    var result =
        TestUtil.run(
            lat, lon, dateTime, "--format=human", "--deltat=69", "position", "--elevation-angle");
    assertEquals(0, result.returnCode());

    var output = result.output();
    assertTrue(output.contains("elevation-angle"));
    assertTrue(output.contains("23.93168°"));
    assertFalse(output.contains("zenith"));
    assertTrue(output.contains("azimuth"));
    assertTrue(output.contains("211.17614°"));
  }

  @Test
  void elevationAngleMathematicalCorrectness() {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2022-10-17T12:00:00Z";

    var zenithResult = TestUtil.run(lat, lon, dateTime, "--format=json", "--deltat=69", "position");
    assertEquals(0, zenithResult.returnCode());
    var zenithJson = JsonParser.parseString(zenithResult.output()).getAsJsonObject();
    var zenithAngle = zenithJson.get("zenith").getAsDouble();

    var elevationResult =
        TestUtil.run(
            lat, lon, dateTime, "--format=json", "--deltat=69", "position", "--elevation-angle");
    assertEquals(0, elevationResult.returnCode());
    var elevationJson = JsonParser.parseString(elevationResult.output()).getAsJsonObject();
    var elevationAngle = elevationJson.get("elevation-angle").getAsDouble();

    assertEquals(90.0, zenithAngle + elevationAngle, 0.00001);
  }

  @Test
  void negativeCoordinates() {
    // Test with negative latitude (southern hemisphere) and negative longitude (western hemisphere)
    // Using coordinates for Buenos Aires, Argentina
    var lat = "-34.6118";
    var lon = "-58.3960";
    var dateTime = "2022-10-17T12:00:00-03:00";

    var result =
        TestUtil.run(
            lat, lon, dateTime, "--format=json", "--deltat=69", "--show-inputs", "position");
    assertEquals(0, result.returnCode());

    var jsonObject = JsonParser.parseString(result.output()).getAsJsonObject();
    assertEquals(Double.parseDouble(lat), jsonObject.get("latitude").getAsDouble());
    assertEquals(Double.parseDouble(lon), jsonObject.get("longitude").getAsDouble());
    assertEquals(dateTime, jsonObject.get("dateTime").getAsString());

    // Verify we get reasonable solar position values for southern hemisphere in spring
    var azimuth = jsonObject.get("azimuth").getAsDouble();
    var zenith = jsonObject.get("zenith").getAsDouble();
    assertTrue(azimuth >= 0 && azimuth <= 360, "Azimuth should be valid");
    assertTrue(zenith >= 0 && zenith <= 180, "Zenith should be valid");
  }
}
