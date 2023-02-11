package net.e175.klaus.solarpos;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.StringReader;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SunriseTest {

    @Test
    void testBasicUsageWithJson() {
        var lat = "52.0";
        var lon = "25.0";
        var dateTime = "2022-10-17T12:00:00Z";

        var result = TestUtil.executeIt(lat, lon, dateTime, "--format=json", "--deltat=69", "--show-inputs", "sunrise");
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
    @ValueSource(strings = {"12:00:00Z", "12:00:00+00:00", "12:00:00.000+00:00", "12:00Z", "12:00", "12:00:00"})
    void testTimePatterns(String time) {
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
    void testBasicUsageWithCsv() {
        var lat = "52.0";
        var lon = "25.0";
        var dateTime = "2022-10-17T12:00:00Z";

        var result = TestUtil.executeIt(lat, lon, dateTime, "--format=csv", "--deltat=69", "--show-inputs", "sunrise");
        assertEquals(0, result.returnCode());
        assertEquals("52.00000,25.00000,2022-10-17T12:00:00Z,69.000,2022-10-17T04:47:51Z,2022-10-17T10:05:21Z,2022-10-17T15:22:00Z", result.output().strip());
    }

    @Test
    void testFullYearWithCsv() throws IOException {
        var lat = "52.0";
        var lon = "25.0";
        var dateTime = "2023";

        var result = TestUtil.executeIt(lat, lon, dateTime, "--format=csv", "--deltat", "--show-inputs", "--timezone=UTC", "sunrise");
        assertEquals(0, result.returnCode());

        var outputRecords = CSVFormat.DEFAULT.parse(new StringReader(result.output())).getRecords();
        assertEquals(365, outputRecords.size());
        assertEquals("2023-01-01T00:00:00Z", outputRecords.get(0).get(2));
        assertEquals("2023-12-31T00:00:00Z", outputRecords.get(364).get(2));
    }

    @Test
    void testFullMonthWithCsv() throws IOException {
        var lat = "52.0";
        var lon = "25.0";
        var dateTime = "2023-02";

        var result = TestUtil.executeIt(lat, lon, dateTime, "--format=csv", "--deltat", "--show-inputs", "--timezone=UTC", "sunrise");
        assertEquals(0, result.returnCode());

        var outputRecords = CSVFormat.DEFAULT.parse(new StringReader(result.output())).getRecords();
        assertEquals(28, outputRecords.size());
        assertEquals("2023-02-01T00:00:00Z", outputRecords.get(0).get(2));
        assertEquals("2023-02-28T00:00:00Z", outputRecords.get(27).get(2));
    }

    @Test
    void testFullMonthWithHeaders() throws IOException {
        var lat = "52.0";
        var lon = "25.0";
        var dateTime = "2023-02";

        var result = TestUtil.executeIt(lat, lon, dateTime, "--headers", "--format=csv", "--deltat", "--show-inputs", "--timezone=UTC", "sunrise");
        assertEquals(0, result.returnCode());

        var outputRecords = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(new StringReader(result.output())).getRecords();
        assertEquals(28, outputRecords.size());
        assertEquals("2023-02-01T00:00:00Z", outputRecords.get(0).get("dateTime"));
        assertEquals("2023-02-28T00:00:00Z", outputRecords.get(27).get("dateTime"));
    }
}
