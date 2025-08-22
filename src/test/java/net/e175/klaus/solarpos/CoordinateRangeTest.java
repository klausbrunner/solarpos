package net.e175.klaus.solarpos;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class CoordinateRangeTest {

  @Test
  void parsesSingleValue() {
    var range = CoordinateRange.parse("40.5");
    assertEquals(40.5, range.start());
    assertEquals(40.5, range.end());
    assertTrue(range.isSinglePoint());
  }

  @Test
  void parsesRange() {
    var range = CoordinateRange.parse("40.0:42.0:0.5");
    assertEquals(40.0, range.start());
    assertEquals(42.0, range.end());
    assertEquals(0.5, range.step());
    assertFalse(range.isSinglePoint());
  }

  @Test
  void parsesNegativeValues() {
    var range = CoordinateRange.parse("-74.0:-73.0:0.25");
    assertEquals(-74.0, range.start());
    assertEquals(-73.0, range.end());
    assertEquals(0.25, range.step());
  }

  @Test
  void rejectsInvalidStep() {
    assertThrows(IllegalArgumentException.class, () -> CoordinateRange.parse("40:42:0"));
    assertThrows(IllegalArgumentException.class, () -> CoordinateRange.parse("40:42:-0.5"));
  }

  @Test
  void rejectsInvalidStartEnd() {
    assertThrows(IllegalArgumentException.class, () -> CoordinateRange.parse("42:40:0.5"));
  }

  @Test
  void rejectsInvalidFormat() {
    assertThrows(IllegalArgumentException.class, () -> CoordinateRange.parse("40:42"));
    assertThrows(IllegalArgumentException.class, () -> CoordinateRange.parse("40:42:0.5:extra"));
    assertThrows(IllegalArgumentException.class, () -> CoordinateRange.parse(""));
  }

  @Test
  void rejectsInvalidNumbers() {
    assertThrows(IllegalArgumentException.class, () -> CoordinateRange.parse("abc"));
    assertThrows(IllegalArgumentException.class, () -> CoordinateRange.parse("40:xyz:0.5"));
  }

  @Test
  void validatesLatitude() {
    var validLat = CoordinateRange.parse("40.0:42.0:0.5");
    assertDoesNotThrow(validLat::validateLatitude);

    var invalidLat = CoordinateRange.parse("-95.0:95.0:1.0");
    assertThrows(IllegalArgumentException.class, invalidLat::validateLatitude);
  }

  @Test
  void validatesLongitude() {
    var validLng = CoordinateRange.parse("-180.0:180.0:1.0");
    assertDoesNotThrow(validLng::validateLongitude);

    var invalidLng = CoordinateRange.parse("-185.0:185.0:1.0");
    assertThrows(IllegalArgumentException.class, invalidLng::validateLongitude);
  }

  @Test
  void generatesCorrectStream() {
    var range = CoordinateRange.parse("40.0:41.0:0.5");
    var values = range.stream().boxed().toList();
    assertEquals(3, values.size());
    assertEquals(40.0, values.get(0));
    assertEquals(40.5, values.get(1));
    assertEquals(41.0, values.get(2));
  }

  @Test
  void generatesSinglePointStream() {
    var range = CoordinateRange.parse("40.5");
    var values = range.stream().boxed().toList();
    assertEquals(1, values.size());
    assertEquals(40.5, values.get(0));
  }

  @Test
  void rejectsStepTooSmall() {
    // Steps smaller than 0.001 degrees (~100m) should be rejected for ranges
    var ex =
        assertThrows(
            IllegalArgumentException.class, () -> CoordinateRange.parse("40.0:41.0:0.0005"));
    assertTrue(ex.getMessage().contains("step must be at least"));
  }

  @Test
  void acceptsMinimumStep() {
    // Exactly 0.001 degrees should be accepted
    assertDoesNotThrow(() -> CoordinateRange.parse("40.0:41.0:0.001"));
  }

  @Test
  void allowsSmallStepForSinglePoint() {
    // Single points should not be affected by step size validation
    assertDoesNotThrow(() -> CoordinateRange.parse("40.5"));
    assertDoesNotThrow(() -> CoordinateRange.point(40.5));
  }
}
