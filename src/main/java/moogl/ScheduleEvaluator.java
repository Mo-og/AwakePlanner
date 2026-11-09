package moogl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;

public final class ScheduleEvaluator {

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

  private ScheduleEvaluator() {}

  public static boolean isActive(Config config, LocalDateTime now) {
    return switch (config.mode()) {
      case ALWAYS_ON -> true;
      case OFF -> false;
      case SCHEDULE -> config.day(now.getDayOfWeek()).contains(now.toLocalTime());
    };
  }

  public static Optional<LocalDateTime> naturalEnd(Config config, LocalDateTime now) {
    if (config.mode() != Mode.SCHEDULE) {
      return Optional.empty();
    }
    DaySchedule schedule = config.day(now.getDayOfWeek());
    return schedule.contains(now.toLocalTime())
        ? Optional.of(now.toLocalDate().atTime(schedule.end()))
        : Optional.empty();
  }

  public static Optional<LocalDateTime> nextStart(Config config, LocalDateTime now) {
    if (config.mode() != Mode.SCHEDULE) {
      return Optional.empty();
    }
    for (int offset = 0; offset <= 7; offset++) {
      var date = now.toLocalDate().plusDays(offset);
      DaySchedule schedule = config.day(date.getDayOfWeek());
      if (schedule.enabled() && schedule.isValid()) {
        LocalDateTime start = date.atTime(schedule.start());
        if (start.isAfter(now)) {
          return Optional.of(start);
        }
      }
    }
    return Optional.empty();
  }

  public static Status status(Config config, LocalDateTime now) {
    return status(config, Optional.empty(), now);
  }

  public static Status status(
      Config config, Optional<AwakeOverride> override, LocalDateTime now) {
    if (config.mode() == Mode.ALWAYS_ON) {
      return new Status(true, "Active – always on", Optional.empty());
    }
    Optional<AwakeOverride> live = override.filter(o -> !o.isExpired(now));
    if (live.isPresent()) {
      return overridden(config, live.get(), now);
    }
    if (config.mode() == Mode.OFF) {
      return new Status(false, "Idle – turned off", Optional.empty());
    }
    Optional<LocalDateTime> end = naturalEnd(config, now);
    if (end.isPresent()) {
      return new Status(true, "Active – until " + TIME.format(end.get()), end);
    }
    return new Status(false, idleDescription(config, now), Optional.empty());
  }

  private static Status overridden(Config config, AwakeOverride override, LocalDateTime now) {
    if (override.keepsAwake(now)) {
      String note;
      if (override.replacedEnd() == null) {
        note = "temporary";
      } else if (override.until().isAfter(override.replacedEnd())) {
        note = "extended";
      } else {
        note = "shortened";
      }
      return new Status(
          true,
          "Active – until " + describeEnd(override.until(), now) + " (" + note + ")",
          Optional.of(override.until()));
    }
    String next =
        nextStart(config, override.replacedEnd())
            .map(start -> ", next start " + describe(start, now))
            .orElse("");
    return new Status(false, "Idle – stopped early" + next, Optional.empty());
  }

  private static String idleDescription(Config config, LocalDateTime now) {
    return nextStart(config, now)
        .map(start -> "Idle – next start " + describe(start, now))
        .orElse("Idle – no days scheduled");
  }

  private static String describeEnd(LocalDateTime end, LocalDateTime now) {
    return end.toLocalDate().equals(now.toLocalDate()) ? TIME.format(end) : describe(end, now);
  }

  private static String describe(LocalDateTime start, LocalDateTime now) {
    String day =
        start.toLocalDate().equals(now.toLocalDate())
            ? "today"
            : start.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    return day + " " + TIME.format(start);
  }
}
