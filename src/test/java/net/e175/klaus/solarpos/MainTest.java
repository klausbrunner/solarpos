package net.e175.klaus.solarpos;

import org.junit.jupiter.api.Test;

import java.time.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                ZonedDateTime.parse("2011-12-03T10:15:30+01:00[Europe/Paris]"),
                DateTimeConverter.lenientlyParseDateTime("2011-12-03T10:15:30+01:00[Europe/Paris]", clock));

        assertEquals(
                ZonedDateTime.parse("2023-01-01T13:30:15+03:00"),
                DateTimeConverter.lenientlyParseDateTime("2023-01-01T13:30:15+03:00", clock));

        assertEquals(
                LocalDateTime.parse("2023-01-01T13:15:30"),
                DateTimeConverter.lenientlyParseDateTime("2023-01-01", clock));

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
}
