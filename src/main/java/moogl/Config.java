package moogl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public record Config(
    Map<DayOfWeek, DaySchedule> days, Mode mode, boolean simulateKeypress, boolean startMinimized) {

  private static final LocalTime DEFAULT_START = LocalTime.of(8, 30);
  private static final LocalTime DEFAULT_END = LocalTime.of(19, 0);

  public Config {
    days = Collections.unmodifiableMap(new EnumMap<>(days));
  }

  public static Config defaults() {
    Map<DayOfWeek, DaySchedule> days = new EnumMap<>(DayOfWeek.class);
    for (DayOfWeek day : DayOfWeek.values()) {
      days.put(day, new DaySchedule(isWorkday(day), DEFAULT_START, DEFAULT_END));
    }
    return new Config(days, Mode.SCHEDULE, false, true);
  }

  public DaySchedule day(DayOfWeek day) {
    return days.get(day);
  }

  public Config withMode(Mode newMode) {
    return new Config(days, newMode, simulateKeypress, startMinimized);
  }

  public static Config load(Path file) {
    Config defaults = defaults();
    if (!Files.isRegularFile(file)) {
      return defaults;
    }
    Properties props = new Properties();
    try (Reader reader = Files.newBufferedReader(file)) {
      props.load(reader);
    } catch (IOException e) {
      return defaults;
    }

    Map<DayOfWeek, DaySchedule> days = new EnumMap<>(DayOfWeek.class);
    for (DayOfWeek day : DayOfWeek.values()) {
      DaySchedule fallback = defaults.day(day);
      String key = key(day);
      DaySchedule loaded =
          new DaySchedule(
              parseBoolean(props.getProperty(key + ".enabled"), fallback.enabled()),
              parseTime(props.getProperty(key + ".start"), fallback.start()),
              parseTime(props.getProperty(key + ".end"), fallback.end()));
      days.put(day, loaded.isValid() ? loaded : fallback);
    }
    return new Config(
        days,
        parseMode(props.getProperty("mode"), defaults.mode()),
        parseBoolean(props.getProperty("simulateKeypress"), defaults.simulateKeypress()),
        parseBoolean(props.getProperty("startMinimized"), defaults.startMinimized()));
  }

  public void save(Path file) throws IOException {
    Properties props = new Properties();
    props.setProperty("mode", mode.name());
    props.setProperty("simulateKeypress", Boolean.toString(simulateKeypress));
    props.setProperty("startMinimized", Boolean.toString(startMinimized));
    days.forEach(
        (day, schedule) -> {
          String key = key(day);
          props.setProperty(key + ".enabled", Boolean.toString(schedule.enabled()));
          props.setProperty(key + ".start", schedule.start().toString());
          props.setProperty(key + ".end", schedule.end().toString());
        });
    Files.createDirectories(file.toAbsolutePath().getParent());
    try (Writer writer = Files.newBufferedWriter(file)) {
      props.store(writer, "AwakePlanner settings");
    }
  }

  private static boolean isWorkday(DayOfWeek day) {
    return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
  }

  private static String key(DayOfWeek day) {
    return day.name().substring(0, 3).toLowerCase(Locale.ROOT);
  }

  private static boolean parseBoolean(String value, boolean fallback) {
    if ("true".equalsIgnoreCase(value)) {
      return true;
    }
    if ("false".equalsIgnoreCase(value)) {
      return false;
    }
    return fallback;
  }

  private static LocalTime parseTime(String value, LocalTime fallback) {
    if (value == null) {
      return fallback;
    }
    try {
      return LocalTime.parse(value.trim());
    } catch (DateTimeParseException e) {
      return fallback;
    }
  }

  private static Mode parseMode(String value, Mode fallback) {
    if (value == null) {
      return fallback;
    }
    try {
      return Mode.valueOf(value.trim());
    } catch (IllegalArgumentException e) {
      return fallback;
    }
  }
}
