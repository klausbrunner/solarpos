# solarpos

A simple command-line application to calculate topocentric solar coordinates and sunrise/sunset times, based
on [solarpositioning](https://github.com/klausbrunner/solarpositioning), a library of high-quality solar
positioning algorithms. It supports time series and output formats like JSON and CSV for easy processing by other tools.

Status: "beta" quality. Current functionality works (with a few known [issues](https://github.com/klausbrunner/solarpos/issues)), but needs more testing and polish.

## Requirements and installation

Solarpos is a Java application, requiring Java 17 or newer. See the [latest release](https://github.com/klausbrunner/solarpos/releases/latest) or build it from source using Maven.

### Native builds

Native builds are available for some platforms. These do not require a Java runtime and can be used like any other command-line program.

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

```
Usage: solarpos [-hV] [--headers] [--show-inputs] [--deltat[=<deltaT>]]
                [--format=<format>] [--timezone=<timezone>] [@<filename>...]
                <latitude> <longitude> <dateTime> [COMMAND]
Calculates topocentric solar coordinates or sunrise/sunset times.
      [@<filename>...]      One or more argument files containing options.
      <latitude>            latitude in decimal degrees (positive North of
                              equator)
      <longitude>           longitude in decimal degrees (positive East of
                              Greenwich)
      <dateTime>            date/time in ISO format yyyy[-MM[-dd[['T'][ ]HH:mm[:
                              ss[.SSS]][XXX['['VV']']]]]]. use 'now' for
                              current time and date.
      --deltat[=<deltaT>]   delta T in seconds; an estimate is used if this
                              option is given without a value
      --format=<format>     output format, one of HUMAN, CSV, JSON
  -h, --help                Show this help message and exit.
      --headers             show headers in output (currently applies only to
                              CSV)
      --show-inputs         show all inputs in output
      --timezone=<timezone> timezone as offset (e.g. +01:00) and/or zone id (e.
                              g. America/Los_Angeles). overrides any timezone
                              info found in dateTime.
  -V, --version             Print version information and exit.
```

### Time series

There is built-in support for calculating time series.

* If you pass only a year (e.g. 2023) or a year-month (e.g. 2023-01) to the sunrise command, you will get results for
  each day of that year or month.
* Similarly, the position command will calculate a time series of sun positions for the given day, month or even year.
  The interval is determined by the step option (default: 1 hour).

### Date and Time Formats

Dates and times should be given in ISO 8601 format like 2011-12-03T10:15:30+01:00 or an unambiguous subset, such as:

* 2025-12-03 for a local date (timezone is taken from the timezone parameter if available, else the system default is used)
* 11:00 for a local time (today's date is assumed, timezone is determined as above)
* 14:00:13.312Z for a UTC time (today's date is assumed, timezone is UTC unless overridden by the timezone parameter)

### Timezones

Timezones may be specified as part of the time specification or with the separate --timezone parameter. The format variants are:

* A fixed offset specified in minutes and hours, e.g. "-03:00".
* The "Z" shorthand for UTC (fixed zero offset).
* A TZ database name, such as "Asia/Singapore" (see the [Wikipedia article](https://en.wikipedia.org/wiki/List_of_tz_database_time_zones) for a full list). If one of these is used, daylight savings time (if any) will be applied automatically according to the location's rules.

### Delta T

The difference between universal and terrestrial time affects all solar position calculations. This is an observed value that cannot be reliably predicted into the (far) future, but past values are known at least for the last few centuries and decent estimates can be given for the next few years. Use the --deltat option without a value to request such an estimate. See the documentation of [solarpositioning](https://github.com/klausbrunner/solarpositioning) for more detail.

### Usage examples

Get today's sunrise and sunset for Madrid, Spain, in UTC:

```shell
solarpos 40.4168 -3.7038 now --timezone UTC sunrise
```

Get the sun's position in Stockholm, Sweden, on 15 January 2023 at 12:30 Central European Time:

```shell
solarpos 59.334 18.063 2023-01-15T12:30:00+01:00 position 
```

Get a time series of sun positions for Berlin Alexanderplatz on 15 January 2023, one position every 10 minutes, with CSV
output, in local timezone and using a delta T estimate:

```shell
solarpos 52.5219 13.4132 2023-01-15 --timezone Europe/Berlin --deltat --format=csv position --step=600
```

Sample R notebooks demonstrating how to use timeseries output to create diagrams can be found at [klausbrunner/sunpath-r](https://github.com/klausbrunner/sunpath-r/blob/main/sunpath.md).
