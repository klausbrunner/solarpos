package net.e175.klaus.solarpos.util;

import java.time.*;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import java.util.stream.Stream;

public final class DateTimeIterator {

  private DateTimeIterator() {}

  /**
   * Creates a stream of ZonedDateTime values based on the input temporal and step duration.
   *
   * @param dateTime the temporal accessor defining the time range
   * @param zoneId optional timezone override
   * @param step the step duration between iterations
   * @return stream of ZonedDateTime values
   */
  public static Stream<ZonedDateTime> iterate(
      TemporalAccessor dateTime, Optional<ZoneId> zoneId, Duration step) {
    final ZoneId overrideTz = zoneId.orElse(ZoneId.systemDefault());

    return switch (dateTime) {
      case Year y -> iterateYear(y, overrideTz, step);
      case YearMonth ym -> iterateYearMonth(ym, overrideTz, step);
      case LocalDate ld -> iterateLocalDate(ld, overrideTz, step);
      case LocalDateTime ldt -> Stream.of(ZonedDateTime.of(ldt, overrideTz));
      case LocalTime lt -> Stream.of(ZonedDateTime.of(LocalDate.now(), lt, overrideTz));
      case OffsetTime ot ->
          Stream.of(
              ZonedDateTime.of(
                  LocalDate.now(),
                  ot.toLocalTime(),
                  zoneId.isPresent() ? overrideTz : ot.getOffset()));
      case ZonedDateTime zdt ->
          Stream.of(
              zoneId.isPresent()
                  ? ZonedDateTime.of(zdt.toLocalDate(), zdt.toLocalTime(), overrideTz)
                  : zdt);
      default -> throw new IllegalStateException("unexpected date/time type " + dateTime);
    };
  }

  private static Stream<ZonedDateTime> iterateYear(Year year, ZoneId zoneId, Duration step) {
    var start = ZonedDateTime.of(LocalDate.of(year.getValue(), 1, 1), LocalTime.of(0, 0), zoneId);
    return Stream.iterate(start, i -> i.getYear() == year.getValue(), i -> i.plus(step));
  }

  private static Stream<ZonedDateTime> iterateYearMonth(
      YearMonth yearMonth, ZoneId zoneId, Duration step) {
    var start =
        ZonedDateTime.of(
            LocalDate.of(yearMonth.getYear(), yearMonth.getMonth(), 1), LocalTime.of(0, 0), zoneId);
    return Stream.iterate(start, i -> i.getMonth() == yearMonth.getMonth(), i -> i.plus(step));
  }

  private static Stream<ZonedDateTime> iterateLocalDate(
      LocalDate localDate, ZoneId zoneId, Duration step) {
    var start = ZonedDateTime.of(localDate, LocalTime.of(0, 0), zoneId);
    // For single days, if step is 1 day or more, return single value
    // Otherwise iterate within the day
    if (step.toDays() >= 1) {
      return Stream.of(start);
    }
    return Stream.iterate(
        start, i -> i.getDayOfMonth() == localDate.getDayOfMonth(), i -> i.plus(step));
  }
}
