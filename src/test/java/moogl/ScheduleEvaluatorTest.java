package moogl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScheduleEvaluatorTest {

  private static final LocalDateTime MONDAY = LocalDateTime.of(2026, 11, 2, 0, 0);
  private final Config config = Config.defaults();

  @Test
  void activeOnlyInsideWorkdayWindow() {
    assertFalse(ScheduleEvaluator.isActive(config, MONDAY.withHour(8).withMinute(29)));
    assertTrue(ScheduleEvaluator.isActive(config, MONDAY.withHour(8).withMinute(30)));
    assertTrue(ScheduleEvaluator.isActive(config, MONDAY.withHour(18).withMinute(59)));
    assertFalse(ScheduleEvaluator.isActive(config, MONDAY.withHour(19)));
  }

  @Test
  void idleOnWeekend() {
    assertFalse(ScheduleEvaluator.isActive(config, MONDAY.plusDays(5).withHour(12)));
  }

  @Test
  void nextStartAfterFridayEveningIsMondayMorning() {
    LocalDateTime fridayEvening = MONDAY.plusDays(4).withHour(19).withMinute(30);

    assertEquals(
        Optional.of(MONDAY.plusDays(7).withHour(8).withMinute(30)),
        ScheduleEvaluator.nextStart(config, fridayEvening));
  }

  @Test
  void nextStartIsLaterTodayBeforeWindow() {
    assertEquals(
        Optional.of(MONDAY.withHour(8).withMinute(30)),
        ScheduleEvaluator.nextStart(config, MONDAY.withHour(7)));
  }

  @Test
  void noNextStartWhenAllDaysDisabled() {
    Map<DayOfWeek, DaySchedule> days = new EnumMap<>(DayOfWeek.class);
    for (DayOfWeek day : DayOfWeek.values()) {
      days.put(day, new DaySchedule(false, LocalTime.of(8, 30), LocalTime.of(19, 0)));
    }
    Config disabled = new Config(days, Mode.SCHEDULE, false, true, true, ThemeSchedule.defaults());

    assertEquals(Optional.empty(), ScheduleEvaluator.nextStart(disabled, MONDAY));
    assertEquals(
        "Idle – no days scheduled", ScheduleEvaluator.status(disabled, MONDAY).description());
  }

  @Test
  void modesOverrideSchedule() {
    assertTrue(ScheduleEvaluator.isActive(config.withMode(Mode.ALWAYS_ON), MONDAY.plusDays(6)));
    assertFalse(ScheduleEvaluator.isActive(config.withMode(Mode.OFF), MONDAY.withHour(12)));
  }

  @Test
  void statusDescribesState() {
    assertEquals(
        new Status(true, "Active – until 19:00"),
        ScheduleEvaluator.status(config, MONDAY.withHour(12)));
    assertEquals(
        new Status(false, "Idle – next start Tue 08:30"),
        ScheduleEvaluator.status(config, MONDAY.withHour(20)));
    assertEquals(
        new Status(false, "Idle – next start today 08:30"),
        ScheduleEvaluator.status(config, MONDAY.withHour(6)));
  }
}
