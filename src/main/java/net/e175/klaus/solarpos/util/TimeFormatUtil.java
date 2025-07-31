package net.e175.klaus.solarpos.util;

import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

public final class TimeFormatUtil {

  private TimeFormatUtil() {}

  public static final String INPUT_DATE_TIME_PATTERN =
      "yyyy[-MM[-dd[['T'][ ]HH:mm[:ss[.SSS]][XXX['['VV']']]]]]";

  public static final String INPUT_TIME_PATTERN = "HH:mm[:ss[.SSS]][XXX['['VV']']]";

  public static final DateTimeFormatter INPUT_DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern(INPUT_DATE_TIME_PATTERN);

  public static final DateTimeFormatter INPUT_TIME_FORMATTER =
      DateTimeFormatter.ofPattern(INPUT_TIME_PATTERN);

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

  public static final DateTimeFormatter ISO_LOCAL_DATE_TIME_REDUCED =
      new DateTimeFormatterBuilder()
          .parseCaseInsensitive()
          .append(DateTimeFormatter.ISO_LOCAL_DATE)
          .appendLiteral('T')
          .append(ISO_LOCAL_TIME_REDUCED)
          .toFormatter();

  public static final DateTimeFormatter ISO_HUMAN_LOCAL_DATE_TIME_REDUCED =
      new DateTimeFormatterBuilder()
          .parseCaseInsensitive()
          .append(DateTimeFormatter.ISO_LOCAL_DATE)
          .appendLiteral(' ')
          .append(ISO_LOCAL_TIME_REDUCED)
          .toFormatter();
}
