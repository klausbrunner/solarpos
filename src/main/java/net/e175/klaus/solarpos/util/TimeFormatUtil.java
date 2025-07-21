package net.e175.klaus.solarpos.util;

import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/**
 * Utility class for date/time formatting patterns and formatters. Centralizes all date/time format
 * related constants and utilities.
 */
public final class TimeFormatUtil {

  private TimeFormatUtil() {
    // Utility class, no instances allowed
  }

  /** Pattern for flexible date/time input with optional components. */
  public static final String INPUT_DATE_TIME_PATTERN =
      "yyyy[-MM[-dd[['T'][ ]HH:mm[:ss[.SSS]][XXX['['VV']']]]]]";

  /** Pattern for flexible time input with optional components. */
  public static final String INPUT_TIME_PATTERN = "HH:mm[:ss[.SSS]][XXX['['VV']']]";

  /** Formatter for parsing user input date/time strings. */
  public static final DateTimeFormatter INPUT_DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern(INPUT_DATE_TIME_PATTERN);

  /** Formatter for parsing user input time strings. */
  public static final DateTimeFormatter INPUT_TIME_FORMATTER =
      DateTimeFormatter.ofPattern(INPUT_TIME_PATTERN);

  /** ISO-like time formatter with optional seconds and offset. */
  public static final DateTimeFormatter ISO_LOCAL_TIME_REDUCED =
      new DateTimeFormatterBuilder()
          .appendValue(HOUR_OF_DAY, 2)
          .appendLiteral(':')
          .appendValue(MINUTE_OF_HOUR, 2)
          .optionalStart()
          .appendLiteral(':')
          .appendValue(SECOND_OF_MINUTE, 2)
          .appendOffsetId()
          .optionalEnd()
          .toFormatter();

  /** ISO-like date-time formatter with T separator. */
  public static final DateTimeFormatter ISO_LOCAL_DATE_TIME_REDUCED =
      new DateTimeFormatterBuilder()
          .parseCaseInsensitive()
          .append(DateTimeFormatter.ISO_LOCAL_DATE)
          .appendLiteral('T')
          .append(ISO_LOCAL_TIME_REDUCED)
          .toFormatter();

  /** Human-friendly date-time formatter with space separator. */
  public static final DateTimeFormatter ISO_HUMAN_LOCAL_DATE_TIME_REDUCED =
      new DateTimeFormatterBuilder()
          .parseCaseInsensitive()
          .append(DateTimeFormatter.ISO_LOCAL_DATE)
          .appendLiteral(' ')
          .append(ISO_LOCAL_TIME_REDUCED)
          .toFormatter();
}
