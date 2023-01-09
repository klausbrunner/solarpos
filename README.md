# solarpos-cli

![CI](https://github.com/KlausBrunner/solarpos-cli/workflows/CI/badge.svg)

A simple command-line application to calculate topocentric solar coordinates and sunrise/sunset times, based on [solarpositioning](https://github.com/KlausBrunner/solarpositioning).

Very much a work in progress and only semi-usable so far.

## Usage

See built-in help.

```
Usage: solarpos-cli [-hV] [--show-inputs] [--deltat[=<deltaT>]]
                    [--format=<format>] <latitude> <longitude> <dateTime>
                    [COMMAND]
Calculates topocentric solar coordinates or sunrise/sunset times.
      <latitude>            latitude in decimal degrees (positive North of
                              equator)
      <longitude>           longitude in decimal degrees (positive East of
                              Greenwich)
      <dateTime>            date/time in ISO format yyyy-MM-dd['T'HH:mm:ss[X]].
                              use 'now' for current time and date.
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

## Requirements

Java 17 or newer.
