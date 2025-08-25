package net.e175.klaus.solarpos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DurationConverterTest {

  private final DurationConverter converter = new DurationConverter();

  @Test
  void convertsSimpleFormatsCorrectly() {
    assertEquals(Duration.ofSeconds(30), converter.convert("30s"));
    assertEquals(Duration.ofMinutes(15), converter.convert("15m"));
    assertEquals(Duration.ofHours(2), converter.convert("2h"));
    assertEquals(Duration.ofDays(1), converter.convert("1d"));
  }

  @Test
  void convertsBareNumbersAsSeconds() {
    assertEquals(Duration.ofSeconds(1), converter.convert("1"));
    assertEquals(Duration.ofHours(1), converter.convert("3600"));
    assertEquals(Duration.ofDays(1), converter.convert("86400"));
  }

  @Test
  void convertsIsoFormatsCorrectly() {
    assertEquals(Duration.ofSeconds(30), converter.convert("PT30S"));
    assertEquals(Duration.ofMinutes(15), converter.convert("PT15M"));
    assertEquals(Duration.ofHours(2), converter.convert("PT2H"));
    assertEquals(Duration.ofDays(1), converter.convert("P1D"));
  }

  @Test
  void acceptsEdgeCases() {
    assertEquals(Duration.ofSeconds(1), converter.convert("1s"));
    assertEquals(Duration.ofDays(1), converter.convert("86400s"));
    assertEquals(Duration.ofDays(1), converter.convert("24h"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0s", "0", "2d", "172800", "25h", "1441m"})
  void rejectsOutOfRange(String input) {
    var ex = assertThrows(IllegalArgumentException.class, () -> converter.convert(input));
    assertTrue(ex.getMessage().contains("step must be between"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "30x", "", "PT", "1.5h", "-1s", "-5"})
  void rejectsInvalidFormats(String input) {
    assertThrows(IllegalArgumentException.class, () -> converter.convert(input));
  }

  @Test
  void providesDetailedErrorMessage() {
    var ex = assertThrows(IllegalArgumentException.class, () -> converter.convert("2d"));
    assertTrue(ex.getMessage().contains("PT1S"));
    assertTrue(ex.getMessage().contains("PT24H"));
    assertTrue(ex.getMessage().contains("PT48H"));
  }

  @Test
  void handlesNumberFormatException() {
    assertThrows(IllegalArgumentException.class, () -> converter.convert("999999999999999999999"));
  }
}
