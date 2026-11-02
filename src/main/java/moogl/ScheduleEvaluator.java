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
    boolean active = isActive(config, now);
    String description =
        switch (config.mode()) {
          case ALWAYS_ON -> "Active – always on";
          case OFF -> "Idle – turned off";
          case SCHEDULE ->
              active
                  ? "Active – until " + TIME.format(config.day(now.getDayOfWeek()).end())
                  : nextStart(config, now)
                      .map(start -> "Idle – next start " + describe(start, now))
                      .orElse("Idle – no days scheduled");
        };
    return new Status(active, description);
  }

  private static String describe(LocalDateTime start, LocalDateTime now) {
    String day =
        start.toLocalDate().equals(now.toLocalDate())
            ? "today"
            : start.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    return day + " " + TIME.format(start);
  }
}
