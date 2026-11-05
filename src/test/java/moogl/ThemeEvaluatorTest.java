package moogl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ThemeEvaluatorTest {

  private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");
  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

  private static ThemeSchedule fixed(LocalTime light, LocalTime dark) {
    return new ThemeSchedule(ThemeMode.FIXED_TIMES, light, dark, 0, 0, 0, 0, true, true);
  }

  private static ThemeSchedule sun(double latitude, double longitude, int riseOffset, int setOffset) {
    return new ThemeSchedule(
        ThemeMode.SUN,
        LocalTime.of(7, 0),
        LocalTime.of(19, 0),
        latitude,
        longitude,
        riseOffset,
        setOffset,
        true,
        true);
  }

  private static ZonedDateTime at(LocalDate date, int hour, int minute) {
    return date.atTime(hour, minute).atZone(KYIV);
  }

  @Test
  void offIsNotManaged() {
    ThemeSchedule off = ThemeSchedule.defaults();

    assertEquals(Optional.empty(), ThemeEvaluator.expected(off, at(MONDAY, 12, 0)));
    assertEquals(Optional.empty(), ThemeEvaluator.nextSwitch(off, at(MONDAY, 12, 0)));
    assertEquals("Not managed", ThemeEvaluator.describe(off, at(MONDAY, 12, 0)));
  }

  @Test
  void fixedTimesSwitchExactlyAtBoundaries() {
    ThemeSchedule schedule = fixed(LocalTime.of(7, 0), LocalTime.of(19, 0));

    assertEquals(Optional.of(Theme.DARK), ThemeEvaluator.expected(schedule, at(MONDAY, 6, 59)));
    assertEquals(Optional.of(Theme.LIGHT), ThemeEvaluator.expected(schedule, at(MONDAY, 7, 0)));
    assertEquals(Optional.of(Theme.LIGHT), ThemeEvaluator.expected(schedule, at(MONDAY, 18, 59)));
    assertEquals(Optional.of(Theme.DARK), ThemeEvaluator.expected(schedule, at(MONDAY, 19, 0)));
  }

  @Test
  void fixedTimesWrapAroundMidnight() {
    ThemeSchedule schedule = fixed(LocalTime.of(22, 0), LocalTime.of(6, 0));

    assertEquals(Optional.of(Theme.LIGHT), ThemeEvaluator.expected(schedule, at(MONDAY, 23, 0)));
    assertEquals(Optional.of(Theme.LIGHT), ThemeEvaluator.expected(schedule, at(MONDAY, 5, 59)));
    assertEquals(Optional.of(Theme.DARK), ThemeEvaluator.expected(schedule, at(MONDAY, 12, 0)));
  }

  @Test
  void nextFixedSwitchAfterEveningIsTomorrowMorning() {
    ThemeSchedule schedule = fixed(LocalTime.of(7, 0), LocalTime.of(19, 0));

    assertEquals(
        Optional.of(new ThemeEvaluator.ThemeSwitch(at(MONDAY.plusDays(1), 7, 0), Theme.LIGHT)),
        ThemeEvaluator.nextSwitch(schedule, at(MONDAY, 20, 0)));
    assertEquals(
        "Dark – light tomorrow 07:00", ThemeEvaluator.describe(schedule, at(MONDAY, 20, 0)));
    assertEquals("Light – dark at 19:00", ThemeEvaluator.describe(schedule, at(MONDAY, 12, 0)));
  }

  @Test
  void sunScheduleFollowsSunriseAndSunsetWithOffsets() {
    ThemeSchedule plain = sun(50.45, 30.52, 0, 0);
    ThemeSchedule shifted = sun(50.45, 30.52, 0, 60);
    SunTimes.Regular times =
        (SunTimes.Regular) SunCalculator.compute(MONDAY, 50.45, 30.52, KYIV);
    ZonedDateTime justAfterSunset = times.sunset().plusMinutes(1);

    assertEquals(
        Optional.of(Theme.DARK), ThemeEvaluator.expected(plain, times.sunrise().minusMinutes(1)));
    assertEquals(Optional.of(Theme.LIGHT), ThemeEvaluator.expected(plain, times.sunrise()));
    assertEquals(Optional.of(Theme.DARK), ThemeEvaluator.expected(plain, justAfterSunset));
    assertEquals(Optional.of(Theme.LIGHT), ThemeEvaluator.expected(shifted, justAfterSunset));
    assertEquals(
        Optional.of(new ThemeEvaluator.ThemeSwitch(times.sunset().plusMinutes(60), Theme.DARK)),
        ThemeEvaluator.nextSwitch(shifted, justAfterSunset));
  }

  @Test
  void sunDescriptionNamesTheTrigger() {
    String description = ThemeEvaluator.describe(sun(50.45, 30.52, 0, 0), at(MONDAY, 12, 0));

    assertTrue(description.startsWith("Light – dark at "), description);
    assertTrue(description.endsWith(" (sunset)"), description);
  }

  @Test
  void polarNightStaysDarkWithoutSwitch() {
    ThemeSchedule tromso = sun(69.65, 18.96, 0, 0);
    ZonedDateTime noon = LocalDate.of(2026, 12, 21).atTime(12, 0).atZone(ZoneId.of("Europe/Oslo"));

    assertEquals(Optional.of(Theme.DARK), ThemeEvaluator.expected(tromso, noon));
    assertEquals(Optional.empty(), ThemeEvaluator.nextSwitch(tromso, noon));
    assertEquals("Dark – polar night", ThemeEvaluator.describe(tromso, noon));
  }
}
