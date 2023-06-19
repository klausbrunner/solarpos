# solarpos

A simple command-line application to calculate topocentric solar coordinates and sunrise/sunset times, based
on [solarpositioning](https://github.com/klausbrunner/solarpositioning), a library of high-quality solar
positioning algorithms. Supports time series and output formats like JSON and CSV for easy scripting and processing by other tools.

Status: _beta_. Current functionality works, but needs more user testing and polish.

## Requirements and installation

Solarpos is a Java application, requiring Java 17 or newer. See the [latest release](https://github.com/klausbrunner/solarpos/releases/latest) or build from source using `mvn package`.

### Native builds

Native builds are available for some platforms. These do not require a Java runtime and can be used stand-alone, like any other native application.

### macOS: Homebrew installation

A [Homebrew](https://brew.sh) formula for solarpos is available in a separate tap. This takes care of downloading and all requirements automatically with a single command:

```shell
brew install klausbrunner/tap/solarpos
```

## Usage

For the plain Java distribution, you may have to use the java command:

```shell
java -jar solarpos.jar
```

Native (and homebrew) builds can be started directly:

```shell
solarpos
```

For detailed usage, see built-in help.

```text
Usage: solarpos [-hV] [--headers] [--show-inputs] [--deltat[=<deltaT>]]
                [--format=<format>] [--timezone=<timezone>] [@<filename>...]
                <latitude> <longitude> <dateTime> [COMMAND]
Calculates topocentric solar coordinates or sunrise/sunset times.
      [@<filename>...]      One or more argument files containing options.
      <latitude>            Latitude in decimal degrees (positive North of
                              equator).
      <longitude>           Longitude in decimal degrees (positive East of
                              Greenwich).
      <dateTime>            Date/time in ISO format yyyy[-MM[-dd[['T'][ ]HH:mm[:
                              ss[.SSS]][XXX['['VV']']]]]]. Use 'now' for
                              current time and date.
      --deltat[=<deltaT>]   Delta T in seconds; an estimate is used if this
                              option is given without a value.
      --format=<format>     Output format, one of HUMAN, CSV, JSON.
  -h, --help                Show this help message and exit.
      --headers             Show headers in output (currently applies only to
                              CSV).
      --show-inputs         Show all inputs in output.
      --timezone=<timezone> Timezone as offset (e.g. +01:00) and/or zone id (e.
                              g. America/Los_Angeles). Overrides any timezone
                              info found in dateTime.
  -V, --version             Print version information and exit.
Commands:
  help      Display help information about the specified command.
  position  Calculates topocentric solar coordinates.
  sunrise   Calculates sunrise, transit, sunset and (optionally) twilight times.
```

### Time series

There is built-in support for calculating time series.

* If you pass only a year (e.g. 2023) or a year-month (e.g. 2023-01) to the sunrise command, you will get results for
  each day of that year or month.
* Similarly, the position command will calculate a time series of sun positions for the given day, month or even year.
  The interval is determined by the `--step` option (default: 1 hour).

### Date and Time Formats

Dates and times should be given in ISO 8601 format like "2011-12-03T10:15:30+01:00" or an unambiguous subset, such as:

* "2025-12-03" for a local date (timezone is taken from the timezone parameter if available, else the system default is used)
* "11:00" for a local time (today's date is assumed, timezone is determined as above)
* "14:00:13.312Z" for a UTC time (today's date is assumed, timezone is UTC unless overridden by the timezone parameter)

### Timezones

Timezones may be specified as part of the time specification or with the separate `--timezone` parameter. The format variants are:

* A fixed offset specified in hours and minutes, e.g. "-03:00".
* The "Z" shorthand for UTC (fixed zero offset).
* A TZ database name, such as "Asia/Singapore" (see the [Wikipedia article](https://en.wikipedia.org/wiki/List_of_tz_database_time_zones) for a full list). If one of these is used, daylight savings time (if any) will be applied automatically according to the location's rules.

### Delta T

The difference between universal and terrestrial time affects all solar position calculations. This is an observed value that cannot be reliably predicted into the (far) future, but past values are known at least for the last few centuries and decent estimates can be given for the next few years. Use the `--deltat` option without a value to request such an estimate. See the documentation of [solarpositioning](https://github.com/klausbrunner/solarpositioning) for more detail.

### Output formats

The tool supports three output formats selectable with the `--format` parameter:

* `human`: Default. Simple text output for, well, humans. Most useful for single values.
* `csv`: The popular comma-separated values format, most useful for time series output and processing in various tools. Use the `--headers` parameter to add a header row.
* `json`: JSON, or more precisely JSON Lines (one object on each line, without an enclosing array).

### Usage examples

Get today's sunrise and sunset for Madrid, Spain, in UTC:

```shell
solarpos 40.417 -3.704 now --timezone UTC sunrise
```

Get the sun's position in Stockholm, Sweden, on 15 January 2023 at 12:30 Central European Time:

```shell
solarpos 59.334 18.063 2023-01-15T12:30:00+01:00 position 
```

Get a time series of sun positions for Berlin, Germany, on 26 March 2023, one position every 10 minutes, with CSV
output, in local timezone and using a delta T estimate. As the transition to DST happens on this day, you'll see the changed offset in the data.

```shell
solarpos 52.522 13.413 2023-03-26 --timezone Europe/Berlin --deltat --format=csv position --step=600
```

Get a full calendar of sunrise/sunset and twilight times for Mumbai, India for the year 2025 assuming a delta T of 69 seconds, in JSON lines format. Use the local timezone, which is Asia/Kolkata as per the tz database.

```shell
solarpos 18.97 72.83 2025 --timezone Asia/Kolkata --deltat=69 --format=json sunrise --twilight
```

Sample R notebooks demonstrating how to use timeseries output to create diagrams can be found at [klausbrunner/sunpath-r](https://github.com/klausbrunner/sunpath-r/blob/main/sunpath.md).
