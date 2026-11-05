package moogl;

import java.time.LocalTime;

public record ThemeSchedule(
    ThemeMode mode,
    LocalTime lightAt,
    LocalTime darkAt,
    double latitude,
    double longitude,
    int sunriseOffset,
    int sunsetOffset,
    boolean apps,
    boolean system) {

  public static final int MAX_OFFSET = 180;

  public static ThemeSchedule defaults() {
    return new ThemeSchedule(
        ThemeMode.OFF, LocalTime.of(7, 0), LocalTime.of(19, 0), 0, 0, 0, 0, true, true);
  }

  public ThemeSchedule withMode(ThemeMode newMode) {
    return new ThemeSchedule(
        newMode, lightAt, darkAt, latitude, longitude, sunriseOffset, sunsetOffset, apps, system);
  }

  public boolean isValid() {
    return mode != null
        && lightAt != null
        && darkAt != null
        && !lightAt.equals(darkAt)
        && Math.abs(latitude) <= 90
        && Math.abs(longitude) <= 180
        && Math.abs(sunriseOffset) <= MAX_OFFSET
        && Math.abs(sunsetOffset) <= MAX_OFFSET;
  }
}
