package net.e175.klaus.solarpos;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;

class MainTest {
  final Clock clock =
      Clock.fixed(Instant.parse("2020-03-03T10:15:30.00Z"), ZoneOffset.ofHoursMinutes(3, 0));

  @Test
  void testDateParsing() {
    final DateTimeConverter dtc = new DateTimeConverter(this.clock);

    assertEquals(ZonedDateTime.now(clock), dtc.convert("now"));

    assertEquals(ZonedDateTime.parse("2023-01-01T13:30:15Z"), dtc.convert("2023-01-01T13:30:15Z"));

    assertEquals(ZonedDateTime.parse("2023-01-01T13:30:15Z"), dtc.convert("2023-01-01 13:30:15Z"));

    assertEquals(
        ZonedDateTime.parse("2011-12-03T10:15:30+01:00[Europe/Paris]"),
        dtc.convert("2011-12-03T10:15:30+01:00[Europe/Paris]"));

    assertEquals(
        ZonedDateTime.parse("2011-12-03T10:15:30+01:00[Europe/Paris]"),
        dtc.convert("2011-12-03 10:15:30+01:00[Europe/Paris]"));

    assertEquals(
        ZonedDateTime.parse("2023-01-01T13:30:15+03:00"), dtc.convert("2023-01-01T13:30:15+03:00"));

    assertEquals(
        ZonedDateTime.parse("2023-01-01T13:30:15Z"), dtc.convert("2023-01-01T13:30:15.000Z"));

    assertEquals(ZonedDateTime.parse("2023-01-01T13:30:00Z"), dtc.convert("2023-01-01T13:30Z"));

    assertEquals(
        ZonedDateTime.parse("2023-01-01T13:30:15.750Z"), dtc.convert("2023-01-01T13:30:15.750Z"));

    assertEquals(
        ZonedDateTime.parse("2023-01-01T13:30:15.250+03:00"),
        dtc.convert("2023-01-01T13:30:15.250+03:00"));

    assertEquals(OffsetTime.parse("13:30:15.250+03:00"), dtc.convert("13:30:15.250+03:00"));

    assertEquals(OffsetTime.parse("13:30:15.000+03:00"), dtc.convert("13:30:15+03:00"));

    assertEquals(OffsetTime.parse("13:30:00.000+03:00"), dtc.convert("13:30+03:00"));
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
