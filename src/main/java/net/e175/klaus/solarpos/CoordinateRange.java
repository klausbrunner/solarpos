package net.e175.klaus.solarpos;

import java.util.stream.DoubleStream;

public record CoordinateRange(double start, double end, double step) {

  private static final double LATITUDE_MIN = -90.0;
  private static final double LATITUDE_MAX = 90.0;
  private static final double LONGITUDE_MIN = -180.0;
  private static final double LONGITUDE_MAX = 180.0;
  private static final double MIN_STEP = 0.001; // ~100 meters at equator

  public CoordinateRange {
    if (step <= 0) {
      throw new IllegalArgumentException("step must be positive");
    }
    if (step < MIN_STEP && start != end) {
      throw new IllegalArgumentException(
          String.format(
              "step must be at least %.3f degrees for coordinate ranges, got %.4f",
              MIN_STEP, step));
    }
    if (start > end) {
      throw new IllegalArgumentException("start must be <= end");
    }
  }

  public static CoordinateRange parse(String rangeStr) {
    var trimmed = rangeStr != null ? rangeStr.trim() : "";
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("range string cannot be null or empty");
    }

    var parts = trimmed.split(":", -1); // -1 to preserve empty strings
    return switch (parts.length) {
      case 1 -> point(parseDouble(parts[0]));
      case 3 ->
          new CoordinateRange(parseDouble(parts[0]), parseDouble(parts[1]), parseDouble(parts[2]));
      default ->
          throw new IllegalArgumentException(
              "invalid range format: expected 'value' or 'start:end:step', got: " + rangeStr);
    };
  }

  public static CoordinateRange point(double value) {
    return new CoordinateRange(value, value, 1.0);
  }

  private static double parseDouble(String str) {
    try {
      return Double.parseDouble(str.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("invalid number: " + str, e);
    }
  }

  public DoubleStream stream() {
    if (isSinglePoint()) {
      return DoubleStream.of(start);
    }

    int count = (int) Math.round((end - start) / step) + 1;
    return DoubleStream.iterate(start, i -> i + step).limit(count);
  }

  public boolean isSinglePoint() {
    return start == end;
  }

  public void validateLatitude() {
    validateCoordinateRange(LATITUDE_MIN, LATITUDE_MAX, "latitude");
  }

  public void validateLongitude() {
    validateCoordinateRange(LONGITUDE_MIN, LONGITUDE_MAX, "longitude");
  }

  private void validateCoordinateRange(double min, double max, String type) {
    validateBounds(min, max, type);
    validateStep();
  }

  private void validateBounds(double min, double max, String type) {
    if (start < min || start > max || end < min || end > max) {
      throw new IllegalArgumentException(
          "%s must be between %.0f° and %.0f°, got start=%.4f°, end=%.4f°"
              .formatted(type, min, max, start, end));
    }
  }

  private void validateStep() {
    if (step < MIN_STEP && !isSinglePoint()) {
      throw new IllegalArgumentException(
          "Step must be at least %.3f° for ranges, got %.4f°".formatted(MIN_STEP, step));
    }
  }
}
