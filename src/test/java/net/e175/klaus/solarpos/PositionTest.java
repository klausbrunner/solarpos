package net.e175.klaus.solarpos;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PositionTest {

    @Test
    void testBasicUsageWithJson() {
        CommandLine cmd = Main.createCommandLine();
        StringWriter sw = new StringWriter();
        cmd.setOut(new PrintWriter(sw));

        String lat = "52.0";
        String lon = "25.0";
        String dateTime = "2022-10-17T12:00:00Z";

        int exitCode = cmd.execute(lat, lon, dateTime, "--format=json", "--deltat=69", "--show-inputs", "position");
        assertEquals(0, exitCode);
        String output = sw.toString();

        JsonObject jsonObject = JsonParser.parseString(output).getAsJsonObject();
        assertEquals(dateTime, jsonObject.get("dateTime").getAsString());
        assertEquals(211.17614, jsonObject.get("azimuth").getAsDouble());
        assertEquals(66.06678, jsonObject.get("zenith").getAsDouble());

        assertEquals(52.0, jsonObject.get("latitude").getAsDouble());
        assertEquals(25.0, jsonObject.get("longitude").getAsDouble());
    }

    @Test
    void testBasicUsageWithJsonGrena() {
        CommandLine cmd = Main.createCommandLine();
        StringWriter sw = new StringWriter();
        cmd.setOut(new PrintWriter(sw));

        String lat = "52.0";
        String lon = "25.0";
        String dateTime = "2022-10-17T12:00:00Z";

        int exitCode = cmd.execute(lat, lon, dateTime, "--format=json", "--deltat=69", "position", "--algorithm=grena3");
        assertEquals(0, exitCode);
        String output = sw.toString();

        JsonObject jsonObject = JsonParser.parseString(output).getAsJsonObject();
        assertEquals(dateTime, jsonObject.get("dateTime").getAsString());
        assertEquals(211.17436, jsonObject.get("azimuth").getAsDouble());
        assertEquals(66.06694, jsonObject.get("zenith").getAsDouble());
    }

    @Test
    void testBasicUsageWithCsv() {
        CommandLine cmd = Main.createCommandLine();
        StringWriter sw = new StringWriter();
        cmd.setOut(new PrintWriter(sw));

        String lat = "52.0";
        String lon = "25.0";
        String dateTime = "2003-10-17T12:00:00Z";

        int exitCode = cmd.execute(lat, lon, dateTime, "--format=csv", "--deltat=69", "position");
        assertEquals(0, exitCode);
        String output = sw.toString();

        assertEquals("2003-10-17T12:00:00Z,211.20726,65.92346", output.strip());
    }
}
