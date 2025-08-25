package net.e175.klaus.solarpos;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.apache.commons.csv.CSVFormat;
import picocli.CommandLine;

final class TestUtil {
  static final CSVFormat CSV_WITH_HEADER =
      CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get();
  static final CSVFormat CSV = CSVFormat.DEFAULT;

  record Result(int returnCode, String output) {}

  static Result run(String... args) {
    // Preprocess arguments like main() does
    args = Main.preprocessCoordinateFileArgs(args);
    CommandLine cmd = Main.createCommandLine();
    StringWriter outputWriter = new StringWriter();
    cmd.setOut(new PrintWriter(outputWriter));
    int exitCode = cmd.execute(args);
    String output = outputWriter.toString();
    return new Result(exitCode, output);
  }

  record WithFixedClock(ZonedDateTime dateTime) implements AutoCloseable {
    WithFixedClock(ZonedDateTime dateTime) {
      this.dateTime = dateTime;
      System.setProperty(
          DateTimeConverter.CLOCK_PROPERTY,
          dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    @Override
    public void close() {
      System.clearProperty(DateTimeConverter.CLOCK_PROPERTY);
    }
  }
}
