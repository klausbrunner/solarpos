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
        TestUtil.executeIt(
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
      var result = TestUtil.executeIt(lat, lon, time, "--format=json", "sunrise");
      assertEquals(0, result.returnCode());

      var jsonObject = JsonParser.parseString(result.output()).getAsJsonObject();
      var resultZdt = ZonedDateTime.parse(jsonObject.get("transit").getAsString());
      assertEquals(withClock.get().toLocalDate(), resultZdt.toLocalDate());
    }
  }

  @Test
  void basicUsageWithCsv() {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2022-10-17T12:00:00Z";

    var result =
        TestUtil.executeIt(
            lat, lon, dateTime, "--format=csv", "--deltat=69", "--show-inputs", "sunrise");
    assertEquals(0, result.returnCode());
    assertEquals(
        "52.00000,25.00000,2022-10-17T12:00:00Z,69.000,NORMAL,2022-10-17T04:47:51Z,2022-10-17T10:05:21Z,2022-10-17T15:22:00Z",
        result.output().strip());
  }

  @Test
  void fullYearWithCsv() throws IOException {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2023";

    var result =
        TestUtil.executeIt(
            lat,
            lon,
            dateTime,
            "--format=csv",
            "--deltat",
            "--show-inputs",
            "--timezone=UTC",
            "sunrise");
    assertEquals(0, result.returnCode());

    var outputRecords = TestUtil.CSV.parse(new StringReader(result.output())).getRecords();
    assertEquals(365, outputRecords.size());
    assertEquals("2023-01-01T00:00:00Z", outputRecords.get(0).get(2));
    assertEquals("2023-12-31T00:00:00Z", outputRecords.get(364).get(2));
  }

  @Test
  void fullMonthWithCsv() throws IOException {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2023-02";

    var result =
        TestUtil.executeIt(
            lat,
            lon,
            dateTime,
            "--format=csv",
            "--deltat",
            "--show-inputs",
            "--timezone=UTC",
            "sunrise");
    assertEquals(0, result.returnCode());

    var outputRecords = TestUtil.CSV.parse(new StringReader(result.output())).getRecords();
    assertEquals(28, outputRecords.size());
    assertEquals("2023-02-01T00:00:00Z", outputRecords.get(0).get(2));
    assertEquals("2023-02-28T00:00:00Z", outputRecords.get(27).get(2));
  }

  @Test
  void fullMonthWithHeaders() throws IOException {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2023-02";

    var result =
        TestUtil.executeIt(
            lat,
            lon,
            dateTime,
            "--headers",
            "--format=csv",
            "--deltat",
            "--show-inputs",
            "--timezone=UTC",
            "sunrise");
    assertEquals(0, result.returnCode());

    var outputRecords =
        TestUtil.CSV_WITH_HEADER.parse(new StringReader(result.output())).getRecords();
    assertEquals(28, outputRecords.size());
    assertEquals("2023-02-01T00:00:00Z", outputRecords.get(0).get("dateTime"));
    assertEquals("2023-02-28T00:00:00Z", outputRecords.get(27).get("dateTime"));
  }

  @Test
  void polarNightsCsv() throws IOException {
    var lat = "78.22";
    var lon = "15.63";
    var dateTime = "2023-02";

    var result =
        TestUtil.executeIt(
            lat, lon, dateTime, "--headers", "--format=csv", "--timezone=UTC", "sunrise");
    assertEquals(0, result.returnCode());

    var outputRecords =
        TestUtil.CSV_WITH_HEADER.parse(new StringReader(result.output())).getRecords();
    assertEquals(28, outputRecords.size());

    assertEquals("", outputRecords.get(0).get("sunrise"));
    assertEquals("2023-02-28T07:38:45Z", outputRecords.get(27).get("sunrise"));

    result =
        TestUtil.executeIt(
            lat,
            lon,
            dateTime,
            "--show-inputs",
            "--headers",
            "--format=csv",
            "--timezone=UTC",
            "sunrise");
    assertEquals(0, result.returnCode());

    outputRecords = TestUtil.CSV_WITH_HEADER.parse(new StringReader(result.output())).getRecords();
    assertEquals(28, outputRecords.size());

    assertEquals("", outputRecords.get(0).get("sunrise"));
    assertEquals("2023-02-28T07:38:45Z", outputRecords.get(27).get("sunrise"));
  }

  @Test
  void polarNightsJson() {
    var lat = "78.22";
    var lon = "15.63";

    var result =
        TestUtil.executeIt(
            lat, lon, "2023-02-01", "--headers", "--format=json", "--timezone=UTC", "sunrise");
    assertEquals(0, result.returnCode());

    var jsonObject = JsonParser.parseString(result.output()).getAsJsonObject();
    assertNotNull(jsonObject);
    assertTrue(jsonObject.get("sunrise").isJsonNull());

    result =
        TestUtil.executeIt(
            lat, lon, "2023-02-28", "--show-inputs", "--format=json", "--timezone=UTC", "sunrise");
    assertEquals(0, result.returnCode());

    jsonObject = JsonParser.parseString(result.output()).getAsJsonObject();
    assertNotNull(jsonObject);
    assertEquals("2023-02-28T07:38:45Z", jsonObject.get("sunrise").getAsString());
  }
}
