package net.e175.klaus.solarpos;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    final Clock clock = Clock.fixed(Instant.parse("2020-03-03T10:15:30.00Z"), ZoneOffset.ofHoursMinutes(3, 0));

    @Test
    void testDateParsing() {
        assertEquals(
                ZonedDateTime.now(clock),
                DateTimeConverter.lenientlyParseDateTime("now", clock));

        assertEquals(
                ZonedDateTime.parse("2023-01-01T13:30:15Z"),
                DateTimeConverter.lenientlyParseDateTime("2023-01-01T13:30:15Z", clock));

        assertEquals(
                ZonedDateTime.parse("2023-01-01T13:30:15Z"),
                DateTimeConverter.lenientlyParseDateTime("2023-01-01 13:30:15Z", clock));

        assertEquals(
                ZonedDateTime.parse("2011-12-03T10:15:30+01:00[Europe/Paris]"),
                DateTimeConverter.lenientlyParseDateTime("2011-12-03T10:15:30+01:00[Europe/Paris]", clock));

        assertEquals(
                ZonedDateTime.parse("2011-12-03T10:15:30+01:00[Europe/Paris]"),
                DateTimeConverter.lenientlyParseDateTime("2011-12-03 10:15:30+01:00[Europe/Paris]", clock));

        assertEquals(
                ZonedDateTime.parse("2023-01-01T13:30:15+03:00"),
                DateTimeConverter.lenientlyParseDateTime("2023-01-01T13:30:15+03:00", clock));

        assertEquals(
                ZonedDateTime.parse("2023-01-01T13:30:15Z"),
                DateTimeConverter.lenientlyParseDateTime("2023-01-01T13:30:15.000Z", clock));

        assertEquals(
                ZonedDateTime.parse("2023-01-01T13:30:15.750Z"),
                DateTimeConverter.lenientlyParseDateTime("2023-01-01T13:30:15.750Z", clock));

        assertEquals(
                ZonedDateTime.parse("2023-01-01T13:30:15.250+03:00"),
                DateTimeConverter.lenientlyParseDateTime("2023-01-01T13:30:15.250+03:00", clock));
    }

    @Test
    void testVersion() {
        var result = TestUtil.executeIt("-V");
        assertEquals(0, result.returnCode());
        assertTrue(result.output().contains("solarpos"));
    }

    @Test
    void testRejectsBadDates() {
        var result = TestUtil.executeIt("25", "50", "20", "position");
        assertNotEquals(0, result.returnCode());

        result = TestUtil.executeIt("25", "50", "99999", "position");
        assertNotEquals(0, result.returnCode());

        result = TestUtil.executeIt("25", "50", "2024-12-32", "position");
        assertNotEquals(0, result.returnCode());
    }

    @Test
    void testRejectsBadCoords() {
        var dateTime = "2023";

        var result = TestUtil.executeIt("91", "0", dateTime, "position");
        assertNotEquals(0, result.returnCode());

        result = TestUtil.executeIt("0", "200", dateTime, "position");
        assertNotEquals(0, result.returnCode());
    }
}
