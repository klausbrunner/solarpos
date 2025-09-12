package net.e175.klaus.solarpos;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.StringReader;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SunriseTest {

  @Test
  void basicUsageWithJson() {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2022-10-17T12:00:00Z";

    var result =
        TestUtil.run(
            lat, lon, dateTime, "--format=json", "--deltat=69", "--show-inputs", "sunrise");
    assertEquals(0, result.returnCode());

    JsonObject jsonObject = JsonParser.parseString(result.output()).getAsJsonObject();
    assertEquals(dateTime, jsonObject.get("dateTime").getAsString());
    assertEquals("2022-10-17T04:47:51Z", jsonObject.get("sunrise").getAsString());
    assertEquals("2022-10-17T10:05:21Z", jsonObject.get("transit").getAsString());
    assertEquals("2022-10-17T15:22:00Z", jsonObject.get("sunset").getAsString());

    assertEquals(Double.parseDouble(lat), jsonObject.get("latitude").getAsDouble());
    assertEquals(Double.parseDouble(lon), jsonObject.get("longitude").getAsDouble());
  }

  @Test
  void basicUsageWithTwilightsWithJson() {
    var lat = "52.49";
    var lon = "-1.89";
    var dateTime = "2023-05-01T12:00:00+01:00";

    var result =
        TestUtil.run(
            lat,
            lon,
            dateTime,
            "--format=json",
            "--deltat=69",
            "--show-inputs",
            "sunrise",
            "--twilight");
    assertEquals(0, result.returnCode());

    JsonObject jsonObject = JsonParser.parseString(result.output()).getAsJsonObject();
    assertEquals(dateTime, jsonObject.get("dateTime").getAsString());
    assertEquals("2023-05-01T05:36:58+01:00", jsonObject.get("sunrise").getAsString());
    assertEquals("2023-05-01T20:33:32+01:00", jsonObject.get("sunset").getAsString());
    assertEquals("2023-05-01T04:57:41+01:00", jsonObject.get("civil_start").getAsString());
    assertEquals("2023-05-01T21:13:05+01:00", jsonObject.get("civil_end").getAsString());
    assertEquals("2023-05-01T04:06:25+01:00", jsonObject.get("nautical_start").getAsString());
    assertEquals("2023-05-01T22:04:53+01:00", jsonObject.get("nautical_end").getAsString());
    assertEquals("2023-05-01T03:01:18+01:00", jsonObject.get("astronomical_start").getAsString());
    assertEquals("2023-05-01T23:11:26+01:00", jsonObject.get("astronomical_end").getAsString());

    assertEquals(Double.parseDouble(lat), jsonObject.get("latitude").getAsDouble());
    assertEquals(Double.parseDouble(lon), jsonObject.get("longitude").getAsDouble());
  }

  @Test
  void basicUsageWithTwilightsWithCsv() throws IOException {
    var lat = "52.49";
    var lon = "-1.89";
    var dateTime = "2023-05-01T12:00:00+01:00";

    var result =
        TestUtil.run(
            lat,
            lon,
            dateTime,
            "--format=csv",
            "--deltat=69",
            "--show-inputs",
            "sunrise",
            "--twilight");
    assertEquals(0, result.returnCode());

    var outputRecords =
        TestUtil.CSV_WITH_HEADER.parse(new StringReader(result.output())).getRecords();
    assertEquals(1, outputRecords.size());
    var record = outputRecords.getFirst();
    assertEquals(dateTime, record.get("dateTime"));

    assertEquals("2023-05-01T05:36:58+01:00", record.get("sunrise"));
    assertEquals("2023-05-01T20:33:32+01:00", record.get("sunset"));
    assertEquals("2023-05-01T04:57:41+01:00", record.get("civil_start"));
    assertEquals("2023-05-01T21:13:05+01:00", record.get("civil_end"));
    assertEquals("2023-05-01T04:06:25+01:00", record.get("nautical_start"));
    assertEquals("2023-05-01T22:04:53+01:00", record.get("nautical_end"));
    assertEquals("2023-05-01T03:01:18+01:00", record.get("astronomical_start"));
    assertEquals("2023-05-01T23:11:26+01:00", record.get("astronomical_end"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "12:00:00Z",
        "12:00:00+00:00",
        "12:00:00.000+00:00",
        "12:00Z",
        "12:00",
        "12:00:00"
      })
  void timePatterns(String time) {
    var lat = "52.0";
    var lon = "25.0";

    try (var withClock = new TestUtil.WithFixedClock(ZonedDateTime.now())) {
      var result = TestUtil.run(lat, lon, time, "--format=json", "sunrise");
      assertEquals(0, result.returnCode());

      var jsonObject = JsonParser.parseString(result.output()).getAsJsonObject();
      var resultZdt = ZonedDateTime.parse(jsonObject.get("transit").getAsString());
      assertEquals(withClock.dateTime().toLocalDate(), resultZdt.toLocalDate());
    }
  }

  @Test
  void basicUsageWithCsv() {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2022-10-17T12:00:00Z";

    // Test with headers (default)
    var result =
        TestUtil.run(lat, lon, dateTime, "--format=csv", "--deltat=69", "--show-inputs", "sunrise");
    assertEquals(0, result.returnCode());
    var lines = result.output().lines().toList();
    assertEquals(2, lines.size());
    assertEquals("latitude,longitude,dateTime,deltaT,type,sunrise,transit,sunset", lines.get(0));
    assertEquals(
        "52.00000,25.00000,2022-10-17T12:00:00Z,69.000,NORMAL,2022-10-17T04:47:51Z,2022-10-17T10:05:21Z,2022-10-17T15:22:00Z",
        lines.get(1));

    // Test without headers
    result =
        TestUtil.run(
            lat,
            lon,
            dateTime,
            "--format=csv",
            "--no-headers",
            "--deltat=69",
            "--show-inputs",
            "sunrise");
    assertEquals(0, result.returnCode());
    assertEquals(
        "52.00000,25.00000,2022-10-17T12:00:00Z,69.000,NORMAL,2022-10-17T04:47:51Z,2022-10-17T10:05:21Z,2022-10-17T15:22:00Z",
        result.output().strip());
  }

  @Test
  void csvDefaultHasHeaders() {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2022-10-17T12:00:00Z";

    var result = TestUtil.run(lat, lon, dateTime, "--format=csv", "--deltat=69", "sunrise");
    assertEquals(0, result.returnCode());

    var lines = result.output().lines().toList();
    assertEquals(2, lines.size());
    // Without --show-inputs, headers don't include lat/lon
    assertEquals("type,sunrise,transit,sunset", lines.get(0));
    assertEquals(
        "NORMAL,2022-10-17T04:47:51Z,2022-10-17T10:05:21Z,2022-10-17T15:22:00Z", lines.get(1));
  }

  @Test
  void fullYearWithCsv() throws IOException {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2023";

    var result =
        TestUtil.run(
            lat,
            lon,
            dateTime,
            "--format=csv",
            "--no-headers",
            "--deltat",
            "--show-inputs",
            "--timezone=UTC",
            "sunrise");
    assertEquals(0, result.returnCode());

    var outputRecords = TestUtil.CSV.parse(new StringReader(result.output())).getRecords();
    assertEquals(365, outputRecords.size());
    assertEquals("2023-01-01T00:00:00Z", outputRecords.getFirst().get(2));
    assertEquals("2023-12-31T00:00:00Z", outputRecords.get(364).get(2));
  }

  @Test
  void fullMonthWithCsv() throws IOException {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2023-02";

    var result =
        TestUtil.run(
            lat,
            lon,
            dateTime,
            "--format=csv",
            "--no-headers",
            "--deltat",
            "--show-inputs",
            "--timezone=UTC",
            "sunrise");
    assertEquals(0, result.returnCode());

    var outputRecords = TestUtil.CSV.parse(new StringReader(result.output())).getRecords();
    assertEquals(28, outputRecords.size());
    assertEquals("2023-02-01T00:00:00Z", outputRecords.getFirst().get(2));
    assertEquals("2023-02-28T00:00:00Z", outputRecords.get(27).get(2));
  }

  @Test
  void fullMonthWithHeaders() throws IOException {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2023-02";

    var result =
        TestUtil.run(
            lat,
            lon,
            dateTime,
            "--format=csv",
            "--deltat",
            "--show-inputs",
            "--timezone=UTC",
            "sunrise");
    assertEquals(0, result.returnCode());

    var outputRecords =
        TestUtil.CSV_WITH_HEADER.parse(new StringReader(result.output())).getRecords();
    assertEquals(28, outputRecords.size());
    assertEquals("2023-02-01T00:00:00Z", outputRecords.getFirst().get("dateTime"));
    assertEquals("2023-02-28T00:00:00Z", outputRecords.get(27).get("dateTime"));
  }

  @Test
  void polarNightsCsv() throws IOException {
    var lat = "78.22";
    var lon = "15.63";
    var dateTime = "2023-02";

    var result = TestUtil.run(lat, lon, dateTime, "--format=csv", "--timezone=UTC", "sunrise");
    assertEquals(0, result.returnCode());

    var outputRecords =
        TestUtil.CSV_WITH_HEADER.parse(new StringReader(result.output())).getRecords();
    assertEquals(28, outputRecords.size());

    assertEquals("", outputRecords.getFirst().get("sunrise"));
    assertEquals("2023-02-28T07:38:45Z", outputRecords.get(27).get("sunrise"));

    result =
        TestUtil.run(
            lat, lon, dateTime, "--show-inputs", "--format=csv", "--timezone=UTC", "sunrise");
    assertEquals(0, result.returnCode());

    outputRecords = TestUtil.CSV_WITH_HEADER.parse(new StringReader(result.output())).getRecords();
    assertEquals(28, outputRecords.size());

    assertEquals("", outputRecords.getFirst().get("sunrise"));
    assertEquals("2023-02-28T07:38:45Z", outputRecords.get(27).get("sunrise"));
  }

  @Test
  void polarNightsJson() {
    var lat = "78.22";
    var lon = "15.63";

    var result = TestUtil.run(lat, lon, "2023-02-01", "--format=json", "--timezone=UTC", "sunrise");
    assertEquals(0, result.returnCode());

    var jsonObject = JsonParser.parseString(result.output()).getAsJsonObject();
    assertNotNull(jsonObject);
    assertTrue(jsonObject.get("sunrise").isJsonNull());

    result =
        TestUtil.run(
            lat, lon, "2023-02-28", "--show-inputs", "--format=json", "--timezone=UTC", "sunrise");
    assertEquals(0, result.returnCode());

    jsonObject = JsonParser.parseString(result.output()).getAsJsonObject();
    assertNotNull(jsonObject);
    assertEquals("2023-02-28T07:38:45Z", jsonObject.get("sunrise").getAsString());
  }

  @Test
  void humanFormatOutput() {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2022-10-17T12:00:00Z";

    var result =
        TestUtil.run(
            lat, lon, dateTime, "--format=human", "--deltat=69", "--show-inputs", "sunrise");
    assertEquals(0, result.returnCode());

    var output = result.output();

    // Check the general structure and formatting
    assertTrue(output.contains("latitude"));
    assertTrue(output.contains("longitude"));
    assertTrue(output.contains("date/time"));
    assertTrue(output.contains("delta T"));
    assertTrue(output.contains("type"));
    assertTrue(output.contains("sunrise"));
    assertTrue(output.contains("transit"));
    assertTrue(output.contains("sunset"));

    // Check degree symbols and formatting for coordinates
    assertTrue(output.contains("52.00000°"));
    assertTrue(output.contains("25.00000°"));

    // Check time formatting (human format uses spaces instead of 'T')
    assertTrue(output.contains("2022-10-17 12:00:00Z"));
    assertTrue(output.contains("2022-10-17 04:47:51Z"));
    assertTrue(output.contains("2022-10-17 10:05:21Z"));
    assertTrue(output.contains("2022-10-17 15:22:00Z"));

    // Check proper value for type field in human format
    assertTrue(output.contains("normal"));

    // Check for delta T unit
    assertTrue(output.contains("69.000 s"));
  }

  @Test
  void humanFormatOutputWithTwilight() {
    var lat = "52.49";
    var lon = "-1.89";
    var dateTime = "2023-05-01T12:00:00+01:00";

    var result =
        TestUtil.run(
            lat,
            lon,
            dateTime,
            "--format=human",
            "--deltat=69",
            "--show-inputs",
            "sunrise",
            "--twilight");
    assertEquals(0, result.returnCode());

    var output = result.output();

    // Check for twilight fields
    assertTrue(output.contains("astronomical_start"));
    assertTrue(output.contains("nautical_start"));
    assertTrue(output.contains("civil_start"));
    assertTrue(output.contains("civil_end"));
    assertTrue(output.contains("nautical_end"));
    assertTrue(output.contains("astronomical_end"));

    // Check formatting for a few key values
    assertTrue(output.contains("52.49000°"));
    assertTrue(output.contains("-1.89000°"));

    // Verify each sunrise/sunset time appears only once (no duplicates)
    assertEquals(1, countOccurrences(output, "2023-05-01 05:36:58+01:00"));
    assertEquals(1, countOccurrences(output, "2023-05-01 20:33:32+01:00"));
  }

  @Test
  void negativeCoordinates() {
    // Test with negative latitude (southern hemisphere) and negative longitude (western hemisphere)
    // Using coordinates for Rio de Janeiro, Brazil
    var lat = "-22.9068";
    var lon = "-43.1729";
    var date = "2023-05-01"; // Autumn in southern hemisphere

    var result =
        TestUtil.run(lat, lon, date, "--format=json", "--deltat=69", "--show-inputs", "sunrise");
    assertEquals(0, result.returnCode());

    var jsonObject = JsonParser.parseString(result.output()).getAsJsonObject();
    assertEquals(Double.parseDouble(lat), jsonObject.get("latitude").getAsDouble());
    assertEquals(Double.parseDouble(lon), jsonObject.get("longitude").getAsDouble());

    // Verify we get valid sunrise/sunset times for southern hemisphere
    assertNotNull(jsonObject.get("sunrise"));
    assertNotNull(jsonObject.get("sunset"));
    assertNotNull(jsonObject.get("transit"));

    // In autumn in southern hemisphere, days are getting shorter
    var sunrise = jsonObject.get("sunrise").getAsString();
    var sunset = jsonObject.get("sunset").getAsString();
    assertTrue(sunrise.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"));
    assertTrue(sunset.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"));
  }

  private int countOccurrences(String str, String substr) {
    int count = 0;
    int index = 0;
    while ((index = str.indexOf(substr, index)) != -1) {
      count++;
      index += substr.length();
    }
    return count;
  }
}
