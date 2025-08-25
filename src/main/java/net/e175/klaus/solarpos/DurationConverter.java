package net.e175.klaus.solarpos;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import picocli.CommandLine;

final class DurationConverter implements CommandLine.ITypeConverter<Duration> {

  private static final Duration MIN_STEP = Duration.ofSeconds(1);
  private static final Duration MAX_STEP = Duration.ofDays(1);

  @Override
  public Duration convert(String value) {
    Duration duration = parseDuration(value);
    validateRange(duration);
    return duration;
  }

  private Duration parseDuration(String value) {
    if (value.matches("\\d+[smh]")) {
      return Duration.parse("PT" + value.toUpperCase());
    }
    if (value.matches("\\d+d")) {
      var days = value.substring(0, value.length() - 1);
      return Duration.parse("P" + days + "D");
    }
    if (value.matches("\\d+")) {
      return Duration.ofSeconds(Long.parseLong(value));
    }
    try {
      return Duration.parse(value);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("Invalid duration format: " + value, e);
    }
  }

  private void validateRange(Duration duration) {
    if (duration.compareTo(MIN_STEP) < 0 || duration.compareTo(MAX_STEP) > 0) {
      throw new IllegalArgumentException(
          String.format("step must be between %s and %s, got: %s", MIN_STEP, MAX_STEP, duration));
    }
  }
}
