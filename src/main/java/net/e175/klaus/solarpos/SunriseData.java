package net.e175.klaus.solarpos;

import java.time.ZonedDateTime;

/** Data class for sunrise/sunset output. */
public record SunriseData(
    double latitude,
    double longitude,
    ZonedDateTime dateTime,
    double deltaT,
    String type,
    ZonedDateTime sunrise,
    ZonedDateTime transit,
    ZonedDateTime sunset,
    ZonedDateTime civilStart,
    ZonedDateTime civilEnd,
    ZonedDateTime nauticalStart,
    ZonedDateTime nauticalEnd,
    ZonedDateTime astronomicalStart,
    ZonedDateTime astronomicalEnd) {}
