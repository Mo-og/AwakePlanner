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
    Map<DayOfWeek, DaySchedule> days,
    Mode mode,
    boolean simulateKeypress,
    boolean startMinimized,
    boolean autoUpdate,
    ThemeSchedule theme) {

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
    return new Config(days, Mode.SCHEDULE, false, true, true, ThemeSchedule.defaults());
  }

  public DaySchedule day(DayOfWeek day) {
    return days.get(day);
  }

  public Config withMode(Mode newMode) {
    return new Config(days, newMode, simulateKeypress, startMinimized, autoUpdate, theme);
  }

  public Config withThemeMode(ThemeMode newMode) {
    return new Config(
        days, mode, simulateKeypress, startMinimized, autoUpdate, theme.withMode(newMode));
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
        parseEnum(Mode.class, props.getProperty("mode"), defaults.mode()),
        parseBoolean(props.getProperty("simulateKeypress"), defaults.simulateKeypress()),
        parseBoolean(props.getProperty("startMinimized"), defaults.startMinimized()),
        parseBoolean(props.getProperty("autoUpdate"), defaults.autoUpdate()),
        loadTheme(props, defaults.theme()));
  }

  private static ThemeSchedule loadTheme(Properties props, ThemeSchedule fallback) {
    ThemeSchedule loaded =
        new ThemeSchedule(
            parseEnum(ThemeMode.class, props.getProperty("theme.mode"), fallback.mode()),
            parseTime(props.getProperty("theme.lightAt"), fallback.lightAt()),
            parseTime(props.getProperty("theme.darkAt"), fallback.darkAt()),
            parseDouble(props.getProperty("theme.latitude"), fallback.latitude()),
            parseDouble(props.getProperty("theme.longitude"), fallback.longitude()),
            parseInt(props.getProperty("theme.sunriseOffset"), fallback.sunriseOffset()),
            parseInt(props.getProperty("theme.sunsetOffset"), fallback.sunsetOffset()),
            parseBoolean(props.getProperty("theme.apps"), fallback.apps()),
            parseBoolean(props.getProperty("theme.system"), fallback.system()));
    return loaded.isValid() ? loaded : fallback;
  }

  public void save(Path file) throws IOException {
    Properties props = new Properties();
    props.setProperty("mode", mode.name());
    props.setProperty("simulateKeypress", Boolean.toString(simulateKeypress));
    props.setProperty("startMinimized", Boolean.toString(startMinimized));
    props.setProperty("autoUpdate", Boolean.toString(autoUpdate));
    days.forEach(
        (day, schedule) -> {
          String key = key(day);
          props.setProperty(key + ".enabled", Boolean.toString(schedule.enabled()));
          props.setProperty(key + ".start", schedule.start().toString());
          props.setProperty(key + ".end", schedule.end().toString());
        });
    props.setProperty("theme.mode", theme.mode().name());
    props.setProperty("theme.lightAt", theme.lightAt().toString());
    props.setProperty("theme.darkAt", theme.darkAt().toString());
    props.setProperty("theme.latitude", Double.toString(theme.latitude()));
    props.setProperty("theme.longitude", Double.toString(theme.longitude()));
    props.setProperty("theme.sunriseOffset", Integer.toString(theme.sunriseOffset()));
    props.setProperty("theme.sunsetOffset", Integer.toString(theme.sunsetOffset()));
    props.setProperty("theme.apps", Boolean.toString(theme.apps()));
    props.setProperty("theme.system", Boolean.toString(theme.system()));
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

  private static double parseDouble(String value, double fallback) {
    if (value == null) {
      return fallback;
    }
    try {
      double parsed = Double.parseDouble(value.trim());
      return Double.isFinite(parsed) ? parsed : fallback;
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static int parseInt(String value, int fallback) {
    if (value == null) {
      return fallback;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
    if (value == null) {
      return fallback;
    }
    try {
      return Enum.valueOf(type, value.trim());
    } catch (IllegalArgumentException e) {
      return fallback;
    }
  }
}
