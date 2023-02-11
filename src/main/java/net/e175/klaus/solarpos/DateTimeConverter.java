package net.e175.klaus.solarpos;

import picocli.CommandLine;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;

/**
 * A somewhat tolerant parser of ZonedDateTimes.
 */
final class DateTimeConverter implements CommandLine.ITypeConverter<TemporalAccessor> {
    private final Clock clock;

    /**
     * "Secret" property to inject a fixed clock time for testing purposes.
     */
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
        if (arg.equals("now")) {
            return ZonedDateTime.now(clock);
        }
        try {
            return Main.INPUT_DATE_TIME_FORMATTER.parseBest(arg,
                    ZonedDateTime::from, LocalDateTime::from, LocalDate::from, YearMonth::from, Year::from);
        } catch (DateTimeParseException dtpe) {
            return Main.INPUT_TIME_FORMATTER.parseBest(arg,
                    OffsetTime::from, LocalTime::from);
        }
    }

    @Override
    public TemporalAccessor convert(String arg) {
        try {
            return lenientlyParseDateTime(arg, clock);
        } catch (DateTimeParseException e) {
            throw new CommandLine.TypeConversionException("failed to parse date/time " + arg);
        }
    }
}
