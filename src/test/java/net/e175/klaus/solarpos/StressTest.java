package net.e175.klaus.solarpos;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Stress tests for geographic sweep functionality.
 *
 * <p>These tests are tagged as "stress" and excluded from normal CI builds. To run them explicitly:
 *
 * <pre>
 * mvn test -Pstress-test                      # Run all stress tests (recommended)
 * mvn test -Pstress-test -Dtest=StressTest    # Run all tests in this class
 * mvn test -Pstress-test -Dgroups=memory      # Run only memory stress tests
 * mvn test -Pstress-test -Dgroups=extreme     # Run only extreme stress tests
 * </pre>
 */
final class StressTest {

  private static List<CSVRecord> parseCSV(String csvOutput) throws Exception {
    return TestUtil.CSV.parse(new StringReader(csvOutput)).getRecords();
  }

  private static void printPerformanceStats(String testName, int recordCount, Duration duration) {
    System.out.printf(
        "%s completed: %d calculations in %.1f seconds (%.0f calc/sec)%n",
        testName,
        recordCount,
        duration.toMillis() / 1000.0,
        recordCount * 1000.0 / duration.toMillis());
  }

  @Test
  @Tag("stress")
  void stressTestGeographicAndTimeSweep() throws Exception {
    var startTime = Instant.now();

    // Geographic box: 5x5 degree area with 0.25 degree steps = 21x21 = 441 coordinates
    // Time series: June 2024 (30 days) with 6 hour steps = 30*4 = 120 time points
    // Total: 21 * 21 * 120 = 52,920 calculations

    System.out.println("Starting stress test with geographic and time sweep...");

    var result =
        TestUtil.run(
            "--format",
            "csv",
            "40.0:45.0:0.25", // 21 latitude points (5 degree range, 0.25 step)
            "69.0:74.0:0.25", // 21 longitude points (5 degree range, 0.25 step)
            "2024-06", // June 2024 (30 days)
            "position",
            "--step",
            "21600"); // 6 hour steps (21600 seconds)

    var endTime = Instant.now();
    var duration = Duration.between(startTime, endTime);

    assertEquals(0, result.returnCode(), "Stress test should complete successfully");
    assertFalse(result.output().isEmpty(), "Should produce output");

    var records = parseCSV(result.output());
    int expectedCalculations = 21 * 21 * 120; // lat_points * lng_points * time_points
    assertEquals(
        expectedCalculations,
        records.size(),
        "Should perform exactly " + expectedCalculations + " calculations");

    // Verify we have the expected coordinate range
    assertTrue(
        records.stream().anyMatch(r -> r.get(0).startsWith("40.0")), "Should include min latitude");
    assertTrue(
        records.stream().anyMatch(r -> r.get(0).startsWith("45.0")), "Should include max latitude");
    assertTrue(
        records.stream().anyMatch(r -> r.get(1).startsWith("69.0")),
        "Should include min longitude");
    assertTrue(
        records.stream().anyMatch(r -> r.get(1).startsWith("74.0")),
        "Should include max longitude");

    // Verify we have the expected time range
    assertTrue(
        records.stream().anyMatch(r -> r.get(5).contains("2024-06-01T00:00:00")),
        "Should include start time");
    assertTrue(
        records.stream().anyMatch(r -> r.get(5).contains("2024-06-30")),
        "Should include end time range");

    printPerformanceStats("Stress test", records.size(), duration);

    // Performance expectations - should complete within reasonable time
    assertTrue(duration.toSeconds() < 300, "Should complete within 5 minutes");
  }

  @Test
  @Tag("stress")
  @Tag("extreme")
  void extremeStressTestMillionCalculations() throws Exception {
    var startTime = Instant.now();

    System.out.println("Starting extreme stress test with ~160K calculations...");

    // 21x21 geographic grid with 0.25 degree steps = 441 coordinates
    // January 2024 with 2 hour steps = 31*12 = 372 time points
    // Total: 441 * 372 = 164,052 calculations (~164K)

    var result =
        TestUtil.run(
            "--format",
            "csv",
            "35.0:40.0:0.25", // 21 latitude points
            "75.0:80.0:0.25", // 21 longitude points
            "2024-01", // January 2024
            "position",
            "--step",
            "7200"); // 2 hour steps

    var endTime = Instant.now();
    var duration = Duration.between(startTime, endTime);

    assertEquals(0, result.returnCode(), "Extreme stress test should complete successfully");

    var records = parseCSV(result.output());
    int expectedCalculations = 21 * 21 * 372; // ~164K calculations
    assertEquals(expectedCalculations, records.size());

    printPerformanceStats("Extreme stress test", records.size(), duration);

    // Should still complete in reasonable time with parallel processing
    assertTrue(duration.toMinutes() < 15, "Should complete within 15 minutes");
    assertTrue(records.size() > 150_000, "Should perform over 150K calculations");
  }

  @Test
  @Tag("stress")
  @Tag("memory")
  void memoryStressTest() throws Exception {
    System.gc(); // Clean up before test
    var initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

    System.out.println("Starting memory stress test...");

    // Large geographic area with fine granularity but limited time points
    // 101x101 grid = 10,201 coordinates, single time point = 10,201 calculations
    var result =
        TestUtil.run(
            "--format",
            "csv",
            "30.0:40.0:0.1", // 101 latitude points (30.0, 30.1, ..., 40.0)
            "80.0:90.0:0.1", // 101 longitude points (80.0, 80.1, ..., 90.0)
            "2024-06-21T12:00", // Single time point
            "position");

    assertEquals(0, result.returnCode());
    var records = parseCSV(result.output());
    assertEquals(10_201, records.size(), "Should perform exactly 10,201 calculations");

    System.gc();
    var peakMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    var memoryUsed = (peakMemory - initialMemory) / (1024 * 1024); // MB

    System.out.printf(
        "Memory stress test completed: 10,000 calculations used ~%d MB%n", memoryUsed);

    // Memory usage should be reasonable (less than 500MB for 10k calculations)
    assertTrue(memoryUsed < 500, "Memory usage should be reasonable");
  }
}
