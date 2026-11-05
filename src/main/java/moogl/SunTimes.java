package moogl;

import java.time.ZonedDateTime;

public sealed interface SunTimes {

  record Regular(ZonedDateTime sunrise, ZonedDateTime sunset) implements SunTimes {}

  enum Polar implements SunTimes {
    DAY,
    NIGHT
  }
}
