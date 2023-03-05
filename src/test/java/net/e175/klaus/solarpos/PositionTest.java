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
        TestUtil.executeIt(
            lat, lon, dateTime, "--format=json", "--deltat=69", "--show-inputs", "position");
    assertEquals(0, result.returnCode());

    var jsonObject = JsonParser.parseString(result.output()).getAsJsonObject();
    assertEquals(dateTime, jsonObject.get("dateTime").getAsString());
    assertEquals(211.17614, jsonObject.get("azimuth").getAsDouble());
    assertEquals(66.06678, jsonObject.get("zenith").getAsDouble());

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
      var result = TestUtil.executeIt(lat, lon, time, "--format=json", "position");
      assertEquals(0, result.returnCode());

      var jsonObject = JsonParser.parseString(result.output()).getAsJsonObject();
      var resultZdt = ZonedDateTime.parse(jsonObject.get("dateTime").getAsString());
      assertEquals(
          ZonedDateTime.of(
              LocalDateTime.of(withClock.get().toLocalDate(), inputTime.toLocalTime()),
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
        TestUtil.executeIt(
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
    assertEquals(66.06694, jsonObject.get("zenith").getAsDouble());
  }

  @Test
  void basicUsageWithCsv() {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2003-10-17T12:00:00Z";

    var result = TestUtil.executeIt(lat, lon, dateTime, "--format=csv", "--deltat=69", "position");
    assertEquals(0, result.returnCode());
    assertEquals("2003-10-17T12:00:00Z,211.20726,65.92346", result.output().strip());

    result =
        TestUtil.executeIt(
            lat, lon, dateTime, "--format=csv", "--show-inputs", "--deltat=69", "position");
    assertEquals(0, result.returnCode());

    assertEquals(
        "52.00000,25.00000,0.000,1000.000,0.000,2003-10-17T12:00:00Z,69.000,211.20726,65.92346",
        result.output().strip());
  }

  @Test
  void seriesUsageWithCsv() throws IOException {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2003-10-17";

    var result =
        TestUtil.executeIt(
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
                2003-10-17T06:00:00Z,118.48590,80.31973
                2003-10-17T08:00:00Z,146.00826,66.76720
                2003-10-17T10:00:00Z,178.46662,61.15144
                2003-10-17T12:00:00Z,211.20726,65.92346
                2003-10-17T14:00:00Z,239.15181,78.97740
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
        TestUtil.executeIt(
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
    assertEquals("2003-01-01T00:00:00Z", outputRecords.get(0).get(0));
    assertEquals("2003-12-31T22:00:00Z", outputRecords.get(outputRecords.size() - 1).get(0));
  }

  @Test
  void rejectsInvalidStepValues() {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2003";

    var result = TestUtil.executeIt(lat, lon, dateTime, "position", "--step=0.1");
    assertNotEquals(0, result.returnCode());

    result = TestUtil.executeIt(lat, lon, dateTime, "position", "--step=999999");
    assertNotEquals(0, result.returnCode());
  }

  @Test
  void fullMonthWithCsv() throws IOException {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2024-02";

    var result =
        TestUtil.executeIt(
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
    assertEquals("2024-02-01T00:00:00Z", outputRecords.get(0).get(0));
    assertEquals("2024-02-29T22:00:00Z", outputRecords.get(outputRecords.size() - 1).get(0));
  }

  @Test
  void fullMonthWithCsvHeaders() throws IOException {
    var lat = "52.0";
    var lon = "25.0";
    var dateTime = "2024-02";

    var result =
        TestUtil.executeIt(
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
    assertEquals("2024-02-01T00:00:00Z", outputRecords.get(0).get("dateTime"));
    assertEquals(
        "2024-02-29T22:00:00Z", outputRecords.get(outputRecords.size() - 1).get("dateTime"));
  }
}
