package net.e175.klaus.solarpos;

import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

final class TestUtil {
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
}

