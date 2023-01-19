package net.e175.klaus.solarpos;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SunriseTest {

    @Test
    void testBasicUsageWithJson() {
        String lat = "52.0";
        String lon = "25.0";
        String dateTime = "2022-10-17T12:00:00Z";

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

    @Test
    void testBasicUsageWithCsv() {
        String lat = "52.0";
        String lon = "25.0";
        String dateTime = "2022-10-17T12:00:00Z";

        var result = TestUtil.executeIt(lat, lon, dateTime, "--format=csv", "--deltat=69", "--show-inputs", "sunrise");
        assertEquals(0, result.returnCode());
        assertEquals("52.00000,25.00000,2022-10-17T12:00:00Z,69.000,2022-10-17T04:47:51Z,2022-10-17T10:05:21Z,2022-10-17T15:22:00Z", result.output().strip());
    }
}
