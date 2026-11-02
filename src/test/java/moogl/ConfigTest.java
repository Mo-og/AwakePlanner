package moogl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.Map;
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
  }

  @Test
  void missingFileGivesDefaults() {
    assertEquals(Config.defaults(), Config.load(dir.resolve("absent.properties")));
  }

  @Test
  void roundTripsThroughFile() throws IOException {
    Map<DayOfWeek, DaySchedule> days = new EnumMap<>(Config.defaults().days());
    days.put(DayOfWeek.SATURDAY, new DaySchedule(true, LocalTime.of(10, 0), LocalTime.of(14, 15)));
    Config config = new Config(days, Mode.ALWAYS_ON, true, false);
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
}
