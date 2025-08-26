package net.e175.klaus.solarpos;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StdinTest {
  private InputStream originalStdin;

  @BeforeEach
  void setUp() {
    originalStdin = System.in;
  }

  @AfterEach
  void tearDown() {
    System.setIn(originalStdin);
  }

  @Test
  void supportsStdinForPairedData() {
    setStdin("52.0,25.0,2023-06-21T12:00:00\n48.8,2.3,2023-06-21T14:00:00");

    var result = TestUtil.run("@-", "position");

    assertEquals(0, result.returnCode());
    assertTrue(result.output().contains("52.00000°"));
    assertTrue(result.output().contains("48.80000°"));
    assertTrue(result.output().contains("2023-06-21 12:00:00"));
    assertTrue(result.output().contains("2023-06-21 14:00:00"));
  }

  @Test
  void supportsStdinForCoordinates() {
    setStdin("52.0 25.0\n48.8 2.3");

    var result = TestUtil.run("@-", "2023-06-21T12:00:00", "position");

    assertEquals(0, result.returnCode());
    assertTrue(result.output().contains("52.00000°"));
    assertTrue(result.output().contains("48.80000°"));
    assertTrue(result.output().contains("2023-06-21 12:00:00"));
  }

  @Test
  void supportsStdinForTimes() {
    setStdin("2023-06-21T08:00:00\n2023-06-21T12:00:00\n2023-06-21T16:00:00");

    var result = TestUtil.run("52.0", "25.0", "@-", "position");

    assertEquals(0, result.returnCode());
    assertTrue(result.output().contains("08:00:00"));
    assertTrue(result.output().contains("12:00:00"));
    assertTrue(result.output().contains("16:00:00"));
  }

  @Test
  void supportsCommentsAndBlankLinesInStdin() {
    setStdin(
        "# This is a comment\n52.0,25.0,2023-06-21T12:00:00\n\n# Another comment\n48.8,2.3,2023-06-21T14:00:00");

    var result = TestUtil.run("@-", "position");

    assertEquals(0, result.returnCode());
    assertTrue(result.output().contains("52.00000°"));
    assertTrue(result.output().contains("48.80000°"));
  }

  @Test
  void supportsCsvFormatInStdin() {
    setStdin("52.0,25.0,2023-06-21T12:00:00");

    var result = TestUtil.run("@-", "position");

    assertEquals(0, result.returnCode());
    assertTrue(result.output().contains("52.00000°"));
    assertTrue(result.output().contains("25.00000°"));
  }

  @Test
  void supportsSpaceFormatInStdin() {
    setStdin("52.0 25.0 2023-06-21T12:00:00");

    var result = TestUtil.run("@-", "position");

    assertEquals(0, result.returnCode());
    assertTrue(result.output().contains("52.00000°"));
    assertTrue(result.output().contains("25.00000°"));
  }

  @Test
  void rejectsMultipleStdinUsage() {
    setStdin("should fail");

    var result = TestUtil.run("@-", "@-", "position");

    assertNotEquals(0, result.returnCode());
    // Error message is in stderr/exception, just verify non-zero exit code
  }

  @Test
  void handlesInvalidStdinData() {
    setStdin("invalid data format");

    var result = TestUtil.run("@-", "position");

    assertNotEquals(0, result.returnCode());
  }

  @Test
  void worksWithSunriseCommand() {
    setStdin("52.0,25.0,2023-06-21T12:00:00");

    var result = TestUtil.run("@-", "sunrise");

    assertEquals(0, result.returnCode());
    assertTrue(result.output().contains("sunrise"));
  }

  private void setStdin(String input) {
    System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
  }
}
