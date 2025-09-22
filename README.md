# solarpos

`solarpos` is a command-line application for computing topocentric solar coordinates and solar events such as sunrise, sunset, transit, and twilight. It is designed for scripting and bulk processing: the tool supports time series, geographic sweeps, file input, and streaming, and produces machine-friendly output (CSV or JSON Lines) for use in data pipelines. Built on the [solarpositioning](https://github.com/klausbrunner/solarpositioning) library of high-accuracy algorithms.

**Note**: [sunce](https://github.com/klausbrunner/sunce) is a rewrite of `solarpos` with better performance and a few functional enhancements. While not quite as stable yet, it is expected to supersede this tool as I can't maintain both. 

## Use cases

![Global Solar Elevation Animation](docs/solar_elevation_animation.webp)
*Global solar elevation patterns animation created with a Python script using solarpos, combining time range and coordinate sweep*

- **Solar-energy operations** – compute sun angles and event times for panel tracking, production estimates, and scheduling.
- **Astronomical and simulation tools** – provide precise solar positions for rendering and sky models.
- **Geospatial processing** – integrate into GIS or ETL pipelines via command-line streaming and CSV/JSON output.

## Requirements and installation

- Java 21 or newer for running the JAR distribution.
- Native executables are provided for some platforms and do not require a Java runtime.

Download the latest release JAR from the [releases page](https://github.com/klausbrunner/solarpos/releases/latest) and
run with `java -jar solarpos.jar`, or directly use a native build where available. On MacOS, this is easiest using [Homebrew](https://brew.sh):

```shell
brew install klausbrunner/tap/solarpos-native
```

## Quick start

Run the JAR directly or use a native executable:

```bash

# Getting today's sunrise and sunset in Madrid
# JAR example
java -jar solarpos.jar 40.42 -3.70 now --timezone UTC sunrise

# Native executable (or shell wrapper) example
solarpos 40.42 -3.70 now --timezone UTC sunrise
```

### Example commands

```bash
# Sun position in Stockholm on 2026-01-15 at 12:30 CET
solarpos 59.334 18.063 2026-01-15T12:30:00+01:00 position

# Time series: positions in Berlin every 10 minutes, CSV output, with delta-T estimate
solarpos 52.522 13.413 2023-03-26 --timezone Europe/Berlin --deltat --format=csv position --step=600

# Geographic grid: positions across Central Europe at noon (1° resolution)
solarpos 45.0:50.0:1.0 5.0:15.0:1.0 2026-06-21T12:00:00Z --format=csv position

# Sunrise, sunset, and twilight times for Tokyo throughout March 2027, JSON output
solarpos 35.68 139.69 2027-03 --timezone Asia/Tokyo --format=json sunrise --twilight
```

## File input and streaming

`solarpos` accepts file input for coordinates and times and supports stdin streaming via the `@-` syntax. This is useful when composing pipelines or when input is generated programmatically.

Input modes:

- **Coordinate files:** pass `@coords.txt` as the latitude parameter to read coordinates from a file. Each line contains a latitude and longitude (space- or comma-separated).
- **Time files:** pass `@times.txt` as the date/time parameter to read timestamps from a file, one timestamp per line.
- **Paired data files:** pass `@data.txt` to provide explicit `latitude longitude datetime` records on each line; paired input is treated as one record per line with no cartesian expansion.
- **Stdin:** use `@-` in place of a filename to read the corresponding parameter from standard input. Only one parameter may read from stdin at a time.

Examples:

```bash
# Pipe a single paired record from stdin
echo "52.0,25.0,2023-06-21T12:00:00" | solarpos @- position

# Stream coordinate pairs from stdin and evaluate for a fixed time
cat coords.txt | solarpos @- 2023-06-21T12:00:00 position

# Generate timestamps and pipe them in for a single location
generate-times | solarpos 52.0 25.0 @- position
```

Files may include blank lines and comments (lines starting with `#`). Both space-separated and CSV style are accepted.

## Time series and geographic sweeps

- **Time ranges:** pass a year (e.g., `2026`) or year-month (`2026-06`) to obtain a daily series for that period when using the `sunrise` command. The `position` command produces per-step samples for the period or day specified; the step is controlled with `--step` (default 1 hour).
- **Geographic ranges:** use `start:end:step` syntax for latitude and/or longitude to define a grid (e.g., `40.0:45.0:0.5`). Geographic sweeps combine with time series to produce spatio-temporal datasets.

## Output formats

- `human` (default) – readable text for quick checks.
- `csv` – comma-separated values with headers by default; use `--no-headers` to omit them.
- `json` – JSON Lines (one JSON object per line) for streaming and line-oriented processing.

## Key options

- `--timezone=<tz>` – timezone as an offset (e.g., `+01:00`) or a TZ database name (e.g., `Europe/Berlin`).
- `--deltat[=<seconds>]` – specify delta-T explicitly, or provide the option without value to request an automatic estimate. For background on delta-T see [solarpositioning](https://github.com/klausbrunner/solarpositioning).
- `--format=<format>` – output format: `human`, `csv`, or `json`.
- `--[no-]headers` – include/omit header row for CSV output (default: headers on).
- `--[no-]parallel` – use parallel processing. Increases throughput, but also memory usage.
- `--[no-]show-inputs` – include input parameters in the output.
- `--step=<seconds>` – time step for `position` time series sampling.

Run `solarpos help` or `solarpos help <command>` for the full reference.

## License

This project is distributed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Contributing

Please open issues or pull requests for bugs, documentation improvements, and feature requests. Include command-line examples and expected output when reporting problems.
