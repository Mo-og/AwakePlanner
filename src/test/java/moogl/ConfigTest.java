package moogl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigTest {

  @TempDir Path dir;

  @Test
  void defaultsAreWeekdaysFromHalfEightTillSeven() {
    Config config = Config.defaults();

    assertEquals(
        new DaySchedule(true, LocalTime.of(8, 30), LocalTime.of(19, 0)),
        config.day(DayOfWeek.FRIDAY));
    assertEquals(false, config.day(DayOfWeek.SUNDAY).enabled());
    assertEquals(Mode.SCHEDULE, config.mode());
    assertEquals(true, config.autoUpdate());
  }

  @Test
  void missingFileGivesDefaults() {
    assertEquals(Config.defaults(), Config.load(dir.resolve("absent.properties")));
  }

  @Test
  void roundTripsThroughFile() throws IOException {
    Map<DayOfWeek, DaySchedule> days = new EnumMap<>(Config.defaults().days());
    days.put(DayOfWeek.SATURDAY, new DaySchedule(true, LocalTime.of(10, 0), LocalTime.of(14, 15)));
    Config config = new Config(
            days,
            Mode.ALWAYS_ON,
            true,
            false,
            false,
            ThemeSchedule.defaults(),
            new TimerSettings(Optional.of(new Hotkey(Hotkey.CTRL | Hotkey.SHIFT, "T")), true, 30));
    Path file = dir.resolve("nested/config.properties");

    config.save(file);

    assertEquals(config, Config.load(file));
  }

  @Test
  void corruptValuesFallBackToDefaults() throws IOException {
    Path file = dir.resolve("config.properties");
    Files.writeString(
        file,
        """
        mode=SOMETIMES
        mon.start=noon
        tue.start=20:00
        tue.end=09:00
        wed.end=17:45
        """);

    Config config = Config.load(file);
    Config defaults = Config.defaults();

    assertEquals(Mode.SCHEDULE, config.mode());
    assertEquals(defaults.day(DayOfWeek.MONDAY), config.day(DayOfWeek.MONDAY));
    assertEquals(defaults.day(DayOfWeek.TUESDAY), config.day(DayOfWeek.TUESDAY));
    assertEquals(LocalTime.of(17, 45), config.day(DayOfWeek.WEDNESDAY).end());
  }

  @Test
  void themeRoundTripsThroughFile() throws IOException {
    ThemeSchedule theme =
        new ThemeSchedule(
            ThemeMode.SUN,
            LocalTime.of(6, 45),
            LocalTime.of(20, 15),
            50.45,
            30.52,
            -15,
            30,
            true,
            false);
    Config defaults = Config.defaults();
    Config config = new Config(defaults.days(), Mode.OFF, true, false, true, theme, defaults.timer());
    Path file = dir.resolve("config.properties");

    config.save(file);

    assertEquals(theme, Config.load(file).theme());
  }

  @Test
  void invalidThemeFallsBackToDefaults() throws IOException {
    Path file = dir.resolve("config.properties");
    Files.writeString(
        file,
        """
        theme.mode=SUN
        theme.latitude=123.4
        """);

    assertEquals(ThemeSchedule.defaults(), Config.load(file).theme());
  }

  @Test
  void unparsableThemeValuesUseDefaultsPerField() throws IOException {
    Path file = dir.resolve("config.properties");
    Files.writeString(
        file,
        """
        theme.mode=SOMETIMES
        theme.longitude=east
        theme.sunsetOffset=30
        """);

    ThemeSchedule theme = Config.load(file).theme();

    assertEquals(ThemeMode.OFF, theme.mode());
    assertEquals(0.0, theme.longitude());
    assertEquals(30, theme.sunsetOffset());
  }

  @Test
  void clearedHotkeyRoundTripsThroughFile() throws IOException {
    Config defaults = Config.defaults();
    TimerSettings timer = new TimerSettings(Optional.empty(), false, 0);
    Config config =
        new Config(
            defaults.days(), Mode.SCHEDULE, false, true, true, defaults.theme(), timer);
    Path file = dir.resolve("config.properties");

    config.save(file);

    assertEquals(timer, Config.load(file).timer());
  }

  @Test
  void disabledHotkeyKeepsItsCombination() throws IOException {
    Path file = dir.resolve("config.properties");
    Files.writeString(
        file,
        """
        timer.hotkey=Ctrl+Alt+F12
        timer.hotkeyEnabled=false
        """);

    TimerSettings timer = Config.load(file).timer();

    assertEquals(Optional.of(Hotkey.DEFAULT), timer.hotkey());
    assertEquals(Optional.empty(), timer.activeHotkey());
  }

  @Test
  void invalidTimerSettingsFallBackToDefaults() throws IOException {
    Path file = dir.resolve("config.properties");
    Files.writeString(
        file,
        """
        timer.hotkey=Ctrl+Space
        timer.autoClose=2
        """);

    assertEquals(TimerSettings.defaults(), Config.load(file).timer());
  }

  @Test
  void withTimerKeepsOtherSettings() {
    Config config = Config.defaults().withMode(Mode.OFF);
    TimerSettings timer = new TimerSettings(Optional.empty(), false, 30);

    Config changed = config.withTimer(timer);

    assertEquals(timer, changed.timer());
    assertEquals(Mode.OFF, changed.mode());
    assertEquals(config.theme(), changed.theme());
  }
}
