package net.e175.klaus.solarpos;

import org.apache.commons.csv.CSVFormat;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

final class TestUtil {
    static final CSVFormat CSV_WITH_HEADER = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
    static final CSVFormat CSV = CSVFormat.DEFAULT;

    record Result(int returnCode, String output) {
    }

    static Result executeIt(String... args) {
        CommandLine cmd = Main.createCommandLine();
        StringWriter outputWriter = new StringWriter();
        cmd.setOut(new PrintWriter(outputWriter));
        int exitCode = cmd.execute(args);
        String output = outputWriter.toString();
        return new Result(exitCode, output);
    }

    record WithFixedClock(ZonedDateTime zonedDateTime) implements AutoCloseable {
        WithFixedClock(ZonedDateTime zonedDateTime) {
            this.zonedDateTime = zonedDateTime;
            System.setProperty(DateTimeConverter.CLOCK_PROPERTY, zonedDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }

        ZonedDateTime get() {
            return zonedDateTime;
        }

        @Override
        public void close() {
            System.clearProperty(DateTimeConverter.CLOCK_PROPERTY);
        }
    }

}

