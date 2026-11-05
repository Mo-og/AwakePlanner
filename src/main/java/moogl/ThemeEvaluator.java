package moogl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ThemeEvaluator {

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
  private static final int LOOKAHEAD_DAYS = 2;

  private ThemeEvaluator() {}

  public record ThemeSwitch(ZonedDateTime at, Theme theme) {}

  public static Optional<Theme> expected(ThemeSchedule schedule, ZonedDateTime now) {
    if (schedule.mode() == ThemeMode.OFF) {
      return Optional.empty();
    }
    return Optional.of(phase(schedule, now));
  }

  public static Optional<ThemeSwitch> nextSwitch(ThemeSchedule schedule, ZonedDateTime now) {
    if (schedule.mode() == ThemeMode.OFF) {
      return Optional.empty();
    }
    Theme current = phase(schedule, now);
    List<ZonedDateTime> boundaries = new ArrayList<>();
    for (int offset = 0; offset <= LOOKAHEAD_DAYS; offset++) {
      boundaries.addAll(boundaries(schedule, now.toLocalDate().plusDays(offset), now));
    }
    return boundaries.stream()
        .filter(at -> at.isAfter(now))
        .sorted(Comparator.naturalOrder())
        .map(at -> new ThemeSwitch(at, phase(schedule, at)))
        .filter(next -> next.theme() != current)
        .findFirst();
  }

  public static String describe(ThemeSchedule schedule, ZonedDateTime now) {
    Optional<Theme> theme = expected(schedule, now);
    if (theme.isEmpty()) {
      return "Not managed";
    }
    String next =
        nextSwitch(schedule, now)
            .map(change -> describe(schedule.mode(), change, now))
            .orElse(theme.get() == Theme.LIGHT ? "polar day" : "polar night");
    return theme.get() + " – " + next;
  }

  private static String describe(ThemeMode mode, ThemeSwitch change, ZonedDateTime now) {
    String text = change.theme().toString().toLowerCase(Locale.ROOT) + " " + when(change.at(), now);
    if (mode == ThemeMode.SUN) {
      text += change.theme() == Theme.LIGHT ? " (sunrise)" : " (sunset)";
    }
    return text;
  }

  private static String when(ZonedDateTime at, ZonedDateTime now) {
    LocalDate today = now.toLocalDate();
    LocalDate day = at.toLocalDate();
    String prefix;
    if (day.equals(today)) {
      prefix = "at";
    } else if (day.equals(today.plusDays(1))) {
      prefix = "tomorrow";
    } else {
      prefix = day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }
    return prefix + " " + TIME.format(at);
  }

  private static Theme phase(ThemeSchedule schedule, ZonedDateTime now) {
    if (schedule.mode() == ThemeMode.FIXED_TIMES) {
      return fixedPhase(schedule, now.toLocalTime());
    }
    return switch (sunTimes(schedule, now.toLocalDate(), now)) {
      case SunTimes.Polar polar -> polar == SunTimes.Polar.DAY ? Theme.LIGHT : Theme.DARK;
      case SunTimes.Regular regular -> {
        ZonedDateTime light = regular.sunrise().plusMinutes(schedule.sunriseOffset());
        ZonedDateTime dark = regular.sunset().plusMinutes(schedule.sunsetOffset());
        yield !now.isBefore(light) && now.isBefore(dark) ? Theme.LIGHT : Theme.DARK;
      }
    };
  }

  private static Theme fixedPhase(ThemeSchedule schedule, LocalTime time) {
    LocalTime light = schedule.lightAt();
    LocalTime dark = schedule.darkAt();
    boolean isLight =
        light.isBefore(dark)
            ? !time.isBefore(light) && time.isBefore(dark)
            : !time.isBefore(light) || time.isBefore(dark);
    return isLight ? Theme.LIGHT : Theme.DARK;
  }

  private static List<ZonedDateTime> boundaries(
      ThemeSchedule schedule, LocalDate date, ZonedDateTime now) {
    if (schedule.mode() == ThemeMode.FIXED_TIMES) {
      return List.of(
          date.atTime(schedule.lightAt()).atZone(now.getZone()),
          date.atTime(schedule.darkAt()).atZone(now.getZone()));
    }
    return switch (sunTimes(schedule, date, now)) {
      case SunTimes.Polar polar -> List.of();
      case SunTimes.Regular regular ->
          List.of(
              regular.sunrise().plusMinutes(schedule.sunriseOffset()),
              regular.sunset().plusMinutes(schedule.sunsetOffset()));
    };
  }

  private static SunTimes sunTimes(ThemeSchedule schedule, LocalDate date, ZonedDateTime now) {
    return SunCalculator.compute(date, schedule.latitude(), schedule.longitude(), now.getZone());
  }
}
