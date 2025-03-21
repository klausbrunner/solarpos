package net.e175.klaus.solarpos;

import static net.e175.klaus.solarpos.Main.Format.HUMAN;

import java.io.PrintWriter;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import net.e175.klaus.solarpositioning.Grena3;
import net.e175.klaus.solarpositioning.SPA;
import net.e175.klaus.solarpositioning.SolarPosition;
import picocli.CommandLine;

@CommandLine.Command(name = "position", description = "Calculates topocentric solar coordinates.")
final class PositionCommand implements Callable<Integer> {

  enum Algorithm {
    SPA,
    GRENA3
  }

  @CommandLine.Spec CommandLine.Model.CommandSpec spec;

  @CommandLine.ParentCommand Main parent;

  @CommandLine.Option(
      names = {"-a", "--algorithm"},
      description = "One of ${COMPLETION-CANDIDATES}. Default: ${DEFAULT-VALUE}.",
      defaultValue = "spa")
  Algorithm algorithm;

  @CommandLine.Option(
      names = {"--elevation"},
      description = "Elevation above sea level, in meters. Default: ${DEFAULT-VALUE}.",
      defaultValue = "0")
  double elevation;

  @CommandLine.Option(
      names = {"--pressure"},
      description =
          "Avg. air pressure in millibars/hectopascals. Used for refraction correction. Default: ${DEFAULT-VALUE}.",
      defaultValue = "1013")
  double pressure;

  @CommandLine.Option(
      names = {"--temperature"},
      description =
          "Avg. air temperature in degrees Celsius. Used for refraction correction. Default: ${DEFAULT-VALUE}.",
      defaultValue = "15")
  double temperature;

  @CommandLine.Option(
      names = {"--step"},
      description = "Step interval for time series, in seconds. Default: ${DEFAULT-VALUE}.",
      defaultValue = "3600")
  int step;

  @CommandLine.Option(
      names = "--refraction",
      negatable = true,
      defaultValue = "true",
      fallbackValue = "true",
      description = "Apply refraction correction. Default: ${DEFAULT-VALUE}.")
  boolean refraction;

  @Override
  public Integer call() {
    parent.validate();
    validate();

    final Stream<ZonedDateTime> dateTimes = getDatetimes(parent.dateTime, parent.timezone, step);
    parent.printAnyHeaders(HEADERS);

    final PrintWriter out = parent.spec.commandLine().getOut();
    dateTimes.forEach(
        dateTime -> {
          final double deltaT = parent.getBestGuessDeltaT(dateTime);
          SolarPosition position =
              switch (this.algorithm) {
                case SPA ->
                    this.refraction
                        ? SPA.calculateSolarPosition(
                            dateTime,
                            parent.latitude,
                            parent.longitude,
                            elevation,
                            deltaT,
                            pressure,
                            temperature)
                        : SPA.calculateSolarPosition(
                            dateTime, parent.latitude, parent.longitude, elevation, deltaT);
                case GRENA3 ->
                    this.refraction
                        ? Grena3.calculateSolarPosition(
                            dateTime,
                            parent.latitude,
                            parent.longitude,
                            deltaT,
                            pressure,
                            temperature)
                        : Grena3.calculateSolarPosition(
                            dateTime, parent.latitude, parent.longitude, deltaT);
              };

          out.print(buildOutput(parent.format, dateTime, deltaT, position, parent.showInput));
        });
    out.flush();

    return 0;
  }

  private void validate() {
    if (step < 1 || step > 24 * 60 * 60) {
      throw new CommandLine.ParameterException(spec.commandLine(), "invalid step value");
    }

    if (pressure <= 0 || pressure > 2000) {
      throw new CommandLine.ParameterException(spec.commandLine(), "invalid pressure value");
    }

    if (temperature < -100 || temperature > 100) {
      throw new CommandLine.ParameterException(spec.commandLine(), "invalid temperature value");
    }
  }

  static Stream<ZonedDateTime> getDatetimes(
      TemporalAccessor dateTime, Optional<ZoneId> zoneId, int step) {
    final ZoneId overrideTz = zoneId.orElse(ZoneId.systemDefault());

    return switch (dateTime) {
      case Year y ->
          Stream.iterate(
              ZonedDateTime.of(LocalDate.of(y.getValue(), 1, 1), LocalTime.of(0, 0), overrideTz),
              i -> i.getYear() == y.getValue(),
              i -> i.plusSeconds(step));
      case YearMonth ym ->
          Stream.iterate(
              ZonedDateTime.of(
                  LocalDate.of(ym.getYear(), ym.getMonth(), 1), LocalTime.of(0, 0), overrideTz),
              i -> i.getMonth() == ym.getMonth(),
              i -> i.plusSeconds(step));
      case LocalDate ld ->
          Stream.iterate(
              ZonedDateTime.of(ld, LocalTime.of(0, 0), overrideTz),
              i -> i.getDayOfMonth() == ld.getDayOfMonth(),
              i -> i.plusSeconds(step));
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

  private static final Map<Boolean, String> JSON_FORMATS =
      Map.of(
          true,
              """
                    {"latitude":%.5f,"longitude":%.5f,"elevation":%.3f,"pressure":%.3f,"temperature":%.3f,"dateTime":"%s","deltaT":%.3f,"azimuth":%.5f,"zenith":%.5f}"""
                  + "\n",
          false,
              """
                    {"dateTime":"%6$s","azimuth":%8$.5f,"zenith":%9$.5f}"""
                  + "\n");

  private static final Map<Boolean, String> CSV_HEADERS =
      Map.of(
          true,
              "latitude,longitude,elevation,pressure,temperature,dateTime,deltaT,azimuth,zenith\r\n",
          false, "dateTime,azimuth,zenith\r\n");

  private static final Map<Boolean, String> CSV_FORMATS =
      Map.of(
          true, "%.5f,%.5f,%.3f,%.3f,%.3f,%s,%.3f,%.5f,%.5f\r\n",
          false, "%6$s,%8$.5f,%9$.5f\r\n");

  private static final Map<Boolean, String> HUMAN_FORMATS =
      Map.of(
          true,
              """
                    latitude:    %24.4f°
                    longitude:   %24.4f°
                    elevation:   %22.2f m
                    pressure:    %22.2f hPa
                    temperature: %22.2f °C
                    date/time:  %s
                    delta T:     %22.2f s
                    azimuth:     %24.4f°
                    zenith:      %24.4f°
                    """,
          false,
              """
                    date/time:  %6$s
                    azimuth:     %8$24.4f°
                    zenith:      %9$24.4f°
                    """);

  private static final Map<Main.Format, Map<Boolean, String>> HEADERS =
      Map.of(Main.Format.CSV, CSV_HEADERS);

  private static final Map<Main.Format, Map<Boolean, String>> TEMPLATES =
      Map.of(Main.Format.CSV, CSV_FORMATS, Main.Format.JSON, JSON_FORMATS, HUMAN, HUMAN_FORMATS);

  private String buildOutput(
      Main.Format format,
      ZonedDateTime dateTime,
      double deltaT,
      SolarPosition result,
      boolean showInput) {
    String template = TEMPLATES.get(format).get(showInput);
    DateTimeFormatter dtf =
        (format == HUMAN)
            ? Main.ISO_HUMAN_LOCAL_DATE_TIME_REDUCED
            : Main.ISO_LOCAL_DATE_TIME_REDUCED;
    return template.formatted(
        parent.latitude,
        parent.longitude,
        elevation,
        pressure,
        temperature,
        dtf.format(dateTime),
        deltaT,
        result.azimuth(),
        result.zenithAngle());
  }
}
