package net.e175.klaus.solarpos.util;

import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

public final class TimeFormats {

  private TimeFormats() {}

  public static final String INPUT_DATE_TIME_PATTERN =
      "yyyy[-MM[-dd[['T'][ ]HH:mm[:ss[.S[SS]]][XXX['['VV']']]]]]";

  public static final String INPUT_TIME_PATTERN = "HH:mm[:ss[.S[SS]]][XXX['['VV']']]";

  public static final String OUTPUT_DATE_TIME_HUMAN_PATTERN = "yyyy-MM-dd HH:mm:ssXXX";
  public static final String OUTPUT_DATE_TIME_ISO_PATTERN = "yyyy-MM-dd'T'HH:mm:ssXXX";

  public static final DateTimeFormatter INPUT_DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern(INPUT_DATE_TIME_PATTERN);

  public static final DateTimeFormatter INPUT_TIME_FORMATTER =
      DateTimeFormatter.ofPattern(INPUT_TIME_PATTERN);

  public static final DateTimeFormatter INPUT_DATE_TIME_FORMATTER_WITH_FRACTIONS =
      new DateTimeFormatterBuilder()
          .appendPattern("yyyy")
          .optionalStart()
          .appendPattern("-MM")
          .optionalStart()
          .appendPattern("-dd")
          .optionalStart()
          .appendPattern("[['T'][ ]HH:mm")
          .optionalStart()
          .appendPattern(":ss")
          .optionalStart()
          .appendLiteral('.')
          .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
          .optionalEnd()
          .optionalEnd()
          .optionalStart()
          .appendPattern("[XXX['['VV']']]")
          .optionalEnd()
          .optionalEnd()
          .optionalEnd()
          .optionalEnd()
          .toFormatter();

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
