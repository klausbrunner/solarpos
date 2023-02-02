package net.e175.klaus.solarpos;

import picocli.CommandLine;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;

/**
 * A somewhat tolerant parser of ZonedDateTimes.
 */
final class DateTimeConverter implements CommandLine.ITypeConverter<TemporalAccessor> {

    static TemporalAccessor lenientlyParseDateTime(String arg, Clock clock) {
        return arg.equals("now") ?
                ZonedDateTime.now(clock) :
                Main.INPUT_DATE_TIME_FORMATTER.parseBest(arg,
                        ZonedDateTime::from, LocalDateTime::from, LocalDate::from, YearMonth::from, Year::from);
    }

    @Override
    public TemporalAccessor convert(String arg) {
        try {
            return lenientlyParseDateTime(arg, Clock.systemDefaultZone());
        } catch (DateTimeParseException e) {
            throw new CommandLine.TypeConversionException("failed to parse date/time " + arg);
        }
    }
}
