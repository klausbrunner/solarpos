# solarpos

A simple command-line application to calculate topocentric solar coordinates and sunrise/sunset times, based
on [solarpositioning](https://github.com/KlausBrunner/solarpositioning). It supports time series and various output
formats for easy processing by other tools to create
e.g. [sun-path diagrams](https://github.com/KlausBrunner/sunpath-r/blob/main/sunpath.md).

Status: **"beta"** quality. Basic functionality works without known bugs, but needs more testing and polish.

## Requirements

Java 17 or newer. (If unsure how to get a recent Java version, try [Adoptium](https://adoptium.net/).)

For some platforms, native builds are now available. These don't require a separate Java runtime and can be started
directly like any other command-line program. However, they are generally less tested than the JAR distribution.

## Usage

For the plain Java distribution, get executable JAR
for [latest release](https://github.com/KlausBrunner/solarpos/releases/latest) (or build it
yourself) and run as:

```
java -jar solarpos.jar
```

Native builds can be started directly:

```
solarpos
```

For detailed usage, see built-in help.

```
Usage: solarpos [-hV] [--headers] [--show-inputs] [--deltat[=<deltaT>]]
                [--format=<format>] [--timezone=<timezone>] <latitude>
                <longitude> <dateTime> [COMMAND]
Calculates topocentric solar coordinates or sunrise/sunset times.
      <latitude>            latitude in decimal degrees (positive North of
                              equator)
      <longitude>           longitude in decimal degrees (positive East of
                              Greenwich)
      <dateTime>            date/time in ISO format yyyy[-MM[-dd[['T'][ ]HH:mm:
                              ss[.SSS][XXX['['VV']']]]]]. use 'now' for current
                              time and date.
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
Commands:
  position  calculates topocentric solar coordinates
  sunrise   calculates sunrise, transit, and sunset
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
* 14:00:13.312Z for a UTC time (today's date is assumed, timezone is UTC unless overriden by the timezone parameter)

### Usage examples

Get today's sunrise and sunset for Madrid, Spain, in UTC:

```
solarpos 40.4168 -3.7038 now --timezone UTC sunrise
```

Get the sun's position in Stockholm, Sweden, on 15 January 2023 at 12:30 Central European Time:

```
solarpos 59.334 18.063 2023-01-15T12:30:00+01:00 position 
```

Get a time series of sun positions for Berlin Alexanderplatz on 15 January 2023, one position every 10 minutes, with CSV
output, in local timezone and using a delta T estimate:

```
solarpos 52.5219 13.4132 2023-01-15 --timezone Europe/Berlin --deltat --format=csv position --step=600
```

