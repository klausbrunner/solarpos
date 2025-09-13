package net.e175.klaus.solarpos;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class DateTimeConverterTest {

  @Test
  void parsesNowKeyword() {
    var clock = Clock.fixed(Instant.parse("2024-01-15T10:30:00Z"), ZoneOffset.UTC);
    var converter = new DateTimeConverter(clock);

    var result = converter.convert("now");

    assertInstanceOf(ZonedDateTime.class, result);
    assertEquals(ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC), result);
  }

  @Test
  void parsesNowKeywordCaseInsensitive() {
    var clock = Clock.fixed(Instant.parse("2024-01-15T10:30:00Z"), ZoneOffset.UTC);
    var converter = new DateTimeConverter(clock);

    assertInstanceOf(ZonedDateTime.class, converter.convert("NOW"));
    assertInstanceOf(ZonedDateTime.class, converter.convert("Now"));
    assertInstanceOf(ZonedDateTime.class, converter.convert("nOw"));
  }

  @Test
  void parsesZonedDateTime() {
    var converter = new DateTimeConverter();

    var result = converter.convert("2024-01-15T10:30:00+02:00");

    assertInstanceOf(ZonedDateTime.class, result);
    var zdt = (ZonedDateTime) result;
    assertEquals(2024, zdt.getYear());
    assertEquals(1, zdt.getMonthValue());
    assertEquals(15, zdt.getDayOfMonth());
    assertEquals(10, zdt.getHour());
    assertEquals(30, zdt.getMinute());
    assertEquals(ZoneOffset.ofHours(2), zdt.getOffset());
  }

  @Test
  void parsesLocalDateTime() {
    var converter = new DateTimeConverter();

    var result = converter.convert("2024-01-15T10:30:00");

    assertInstanceOf(LocalDateTime.class, result);
    var ldt = (LocalDateTime) result;
    assertEquals(2024, ldt.getYear());
    assertEquals(1, ldt.getMonthValue());
    assertEquals(15, ldt.getDayOfMonth());
    assertEquals(10, ldt.getHour());
    assertEquals(30, ldt.getMinute());
  }

  @Test
  void parsesLocalDate() {
    var converter = new DateTimeConverter();

    var result = converter.convert("2024-01-15");

    assertInstanceOf(LocalDate.class, result);
    var ld = (LocalDate) result;
    assertEquals(2024, ld.getYear());
    assertEquals(1, ld.getMonthValue());
    assertEquals(15, ld.getDayOfMonth());
  }

  @Test
  void parsesYearMonth() {
    var converter = new DateTimeConverter();

    var result = converter.convert("2024-01");

    assertInstanceOf(YearMonth.class, result);
    var ym = (YearMonth) result;
    assertEquals(2024, ym.getYear());
    assertEquals(1, ym.getMonthValue());
  }

  @Test
  void parsesYear() {
    var converter = new DateTimeConverter();

    var result = converter.convert("2024");

    assertInstanceOf(Year.class, result);
    var y = (Year) result;
    assertEquals(2024, y.getValue());
  }

  @Test
  void parsesOffsetTime() {
    var converter = new DateTimeConverter();

    var result = converter.convert("10:30:00+02:00");

    assertInstanceOf(OffsetTime.class, result);
    var ot = (OffsetTime) result;
    assertEquals(10, ot.getHour());
    assertEquals(30, ot.getMinute());
    assertEquals(0, ot.getSecond());
    assertEquals(ZoneOffset.ofHours(2), ot.getOffset());
  }

  @Test
  void parsesLocalTime() {
    var converter = new DateTimeConverter();

    var result = converter.convert("10:30:00");

    assertInstanceOf(LocalTime.class, result);
    var lt = (LocalTime) result;
    assertEquals(10, lt.getHour());
    assertEquals(30, lt.getMinute());
    assertEquals(0, lt.getSecond());
  }

  @Test
  void parsesShortTime() {
    var converter = new DateTimeConverter();

    var result = converter.convert("10:30");

    assertInstanceOf(LocalTime.class, result);
    var lt = (LocalTime) result;
    assertEquals(10, lt.getHour());
    assertEquals(30, lt.getMinute());
    assertEquals(0, lt.getSecond());
  }

  @Test
  void handlesFileInputMarker() {
    var clock = Clock.fixed(Instant.parse("2024-01-15T10:30:00Z"), ZoneOffset.UTC);
    var converter = new DateTimeConverter(clock);

    var result = converter.convert("@somefile.txt");

    assertInstanceOf(ZonedDateTime.class, result);
    assertEquals(ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC), result);
  }

  @Test
  void handlesStdinMarker() {
    var clock = Clock.fixed(Instant.parse("2024-01-15T10:30:00Z"), ZoneOffset.UTC);
    var converter = new DateTimeConverter(clock);

    var result = converter.convert("@-");

    assertInstanceOf(ZonedDateTime.class, result);
  }

  @Test
  void throwsTypeConversionExceptionForInvalidInput() {
    var converter = new DateTimeConverter();

    var ex =
        assertThrows(
            CommandLine.TypeConversionException.class, () -> converter.convert("not-a-date"));
    assertTrue(ex.getMessage().contains("failed to parse date/time not-a-date"));
  }

  @Test
  void throwsTypeConversionExceptionForEmptyInput() {
    var converter = new DateTimeConverter();

    var ex = assertThrows(CommandLine.TypeConversionException.class, () -> converter.convert(""));
    assertTrue(ex.getMessage().contains("failed to parse date/time"));
  }

  @Test
  void usesSystemPropertyForClock() {
    System.setProperty(DateTimeConverter.CLOCK_PROPERTY, "2024-06-15T14:30:00+05:00");
    try {
      var converter = new DateTimeConverter();

      var result = converter.convert("now");

      assertInstanceOf(ZonedDateTime.class, result);
      var zdt = (ZonedDateTime) result;
      assertEquals(2024, zdt.getYear());
      assertEquals(6, zdt.getMonthValue());
      assertEquals(15, zdt.getDayOfMonth());
      assertEquals(14, zdt.getHour());
      assertEquals(30, zdt.getMinute());
      assertEquals(ZoneOffset.ofHours(5), zdt.getOffset());
    } finally {
      System.clearProperty(DateTimeConverter.CLOCK_PROPERTY);
    }
  }

  @Test
  void parsesDateTimeWithMilliseconds() {
    var converter = new DateTimeConverter();

    var result = converter.convert("2024-01-15T10:30:45.123");

    assertInstanceOf(LocalDateTime.class, result);
    var ldt = (LocalDateTime) result;
    assertEquals(2024, ldt.getYear());
    assertEquals(45, ldt.getSecond());
    assertEquals(123_000_000, ldt.getNano());
  }

  @Test
  void rejectsDateTimeWithBracketedTimeZone() {
    var converter = new DateTimeConverter();

    assertThrows(
        CommandLine.TypeConversionException.class,
        () -> converter.convert("2024-01-15T10:30:00[Europe/Berlin]"));
  }
}
