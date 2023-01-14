package net.e175.klaus.solarpos;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SunriseTest {

    @Test
    void testBasicUsageWithJson() {
        CommandLine cmd = Main.createCommandLine();
        StringWriter sw = new StringWriter();
        cmd.setOut(new PrintWriter(sw));

        String lat = "52.0";
        String lon = "25.0";
        String dateTime = "2022-10-17T12:00:00Z";

        int exitCode = cmd.execute(lat, lon, dateTime, "--format=json", "--deltat=69", "--show-inputs", "sunrise");
        assertEquals(0, exitCode);
        String output = sw.toString();

        JsonObject jsonObject = JsonParser.parseString(output).getAsJsonObject();
        assertEquals(dateTime, jsonObject.get("dateTime").getAsString());
        assertEquals("2022-10-17T04:47:51Z", jsonObject.get("sunrise").getAsString());
        assertEquals("2022-10-17T10:05:21Z", jsonObject.get("transit").getAsString());
        assertEquals("2022-10-17T15:22:00Z", jsonObject.get("sunset").getAsString());

        assertEquals(52.0, jsonObject.get("latitude").getAsDouble());
        assertEquals(25.0, jsonObject.get("longitude").getAsDouble());
    }

    @Test
    void testBasicUsageWithCsv() {
        CommandLine cmd = Main.createCommandLine();
        StringWriter sw = new StringWriter();
        cmd.setOut(new PrintWriter(sw));

        String lat = "52.0";
        String lon = "25.0";
        String dateTime = "2022-10-17T12:00:00Z";

        int exitCode = cmd.execute(lat, lon, dateTime, "--format=csv", "--deltat=69", "--show-inputs", "sunrise");
        assertEquals(0, exitCode);
        String output = sw.toString();
        assertEquals("52.00000,25.00000,2022-10-17T12:00:00Z,69.000,2022-10-17T04:47:51Z,2022-10-17T10:05:21Z,2022-10-17T15:22:00Z", output.strip());
    }
}
