package net.e175.klaus.solarpos;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import net.e175.klaus.solarpos.util.TimeFormats;
import picocli.CommandLine;

/** A somewhat tolerant parser of ZonedDateTimes. */
final class DateTimeConverter implements CommandLine.ITypeConverter<TemporalAccessor> {
  private final Clock clock;

  /** "Secret" property to inject a fixed clock time for testing purposes. */
  static final String CLOCK_PROPERTY = "solarpos.test.clock";

  DateTimeConverter() {
    var clockInput = System.getProperty(CLOCK_PROPERTY);
    if (clockInput == null) {
      clock = Clock.systemDefaultZone();
    } else {
      ZonedDateTime clockZdt = ZonedDateTime.parse(clockInput);
      clock = Clock.fixed(clockZdt.toInstant(), clockZdt.getOffset());
    }
  }

  DateTimeConverter(Clock clock) {
    this.clock = clock;
  }

  private TemporalAccessor lenientlyParseDateTime(String arg, Clock clock) {
    if (arg.equalsIgnoreCase("now")) {
      return ZonedDateTime.now(clock);
    }

    // Try each parsing strategy in order of specificity without exception-based control flow
    return tryParseTimeOnly(arg)
        .or(() -> tryParseIsoDateTime(arg))
        .or(() -> tryParseCustomDateTime(arg))
        .orElseThrow(() -> new DateTimeParseException("Unable to parse date/time: " + arg, arg, 0));
  }

  private java.util.Optional<TemporalAccessor> tryParseTimeOnly(String arg) {
    // Time-only patterns (HH:mm format)
    if (!arg.matches("\\d{1,2}:\\d{2}.*")) {
      return java.util.Optional.empty();
    }

    try {
      return java.util.Optional.of(OffsetTime.from(DateTimeFormatter.ISO_OFFSET_TIME.parse(arg)));
    } catch (DateTimeParseException e) {
      try {
        return java.util.Optional.of(LocalTime.from(TimeFormats.INPUT_TIME_FORMATTER.parse(arg)));
      } catch (DateTimeParseException ignored) {
        return java.util.Optional.empty();
      }
    }
  }

  private java.util.Optional<TemporalAccessor> tryParseIsoDateTime(String arg) {
    // ISO datetime patterns with 'T' separator
    if (!arg.contains("T")) {
      return java.util.Optional.empty();
    }

    // Try zoned/offset datetime first (has timezone indicators)
    if (arg.contains("+") || arg.contains("-") || arg.endsWith("Z")) {
      try {
        return java.util.Optional.of(
            DateTimeFormatter.ISO_ZONED_DATE_TIME.parseBest(
                arg, ZonedDateTime::from, OffsetDateTime::from));
      } catch (DateTimeParseException ignored) {
        // Fall through to local datetime
      }
    }

    // Try local datetime
    try {
      return java.util.Optional.of(
          LocalDateTime.from(DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(arg)));
    } catch (DateTimeParseException ignored) {
      return java.util.Optional.empty();
    }
  }

  private java.util.Optional<TemporalAccessor> tryParseCustomDateTime(String arg) {
    // Use the comprehensive formatter that supports milliseconds and various patterns
    try {
      return java.util.Optional.of(
          TimeFormats.INPUT_DATE_TIME_FORMATTER_WITH_FRACTIONS.parseBest(
              arg,
              ZonedDateTime::from,
              LocalDateTime::from,
              LocalDate::from,
              YearMonth::from,
              Year::from));
    } catch (DateTimeParseException ignored) {
      return java.util.Optional.empty();
    }
  }

  @Override
  public TemporalAccessor convert(String arg) {
    if (arg.startsWith("@")) {
      // For file input, return a dummy temporal - actual times will be read in getDateTimesStream()
      return ZonedDateTime.now(clock);
    }
    try {
      return lenientlyParseDateTime(arg, clock);
    } catch (DateTimeParseException e) {
      throw new CommandLine.TypeConversionException("failed to parse date/time " + arg);
    }
  }
}
