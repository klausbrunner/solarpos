# solarpos-cli

![CI](https://github.com/KlausBrunner/solarpos-cli/workflows/CI/badge.svg)

A simple command-line application to calculate topocentric solar coordinates and sunrise/sunset times, based
on [solarpositioning](https://github.com/KlausBrunner/solarpositioning).

Status: **early stage**, "alpha" quality. Basic functionality works, but lacks real-world testing and polish.

## Requirements

Java 17 or newer.

## Usage

Get executable JAR for [latest release](https://github.com/KlausBrunner/solarpos-cli/releases/latest) (or build it
yourself) and run as usual:

```
java -jar solarpos-cli.jar
```

On Linux, macOS and other POSIX-like systems, it should be enough to set the executable flag and run the JAR directly.

Then, see built-in help.

```
Usage: solarpos-cli [-hV] [--show-inputs] [--deltat[=<deltaT>]]
                    [--format=<format>] <latitude> <longitude> <dateTime>
                    [COMMAND]
Calculates topocentric solar coordinates or sunrise/sunset times.
      <latitude>            latitude in decimal degrees (positive North of
                              equator)
      <longitude>           longitude in decimal degrees (positive East of
                              Greenwich)
      <dateTime>            date/time in ISO format yyyy-MM-dd['T'HH:mm:ss[.SSS]
                              [XXX]]. use 'now' for current time and date.
      --deltat[=<deltaT>]   delta T in seconds; an estimate is used if this
                              option is given without a value
      --format=<format>     output format, one of HUMAN, CSV, JSON
  -h, --help                Show this help message and exit.
      --show-inputs         show all inputs in output
  -V, --version             Print version information and exit.
Commands:
  position  calculates topocentric solar coordinates
  sunrise   calculates sunrise, transit, and sunset
```

### Time series

There is some basic built-in support for time series.

* If you pass only a year (e.g. 2023) or a year-month (e.g. 2023-01) to the sunrise command, you will get results for
  each day of that year or month.
* If you pass a day but no time, the position command will calculate a series of values for that entire day, tracking
  the sun's path from 00:00 to 24:00. The interval is determined by the step option.
