package moogl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class ThemeServiceTest {

  private static final ZoneId ZONE = ZoneId.of("Europe/Kyiv");

  private final MockThemeControl control = new MockThemeControl();
  private final StubClock clock =
      new StubClock(LocalDate.of(2026, 11, 2).atTime(12, 0).atZone(ZONE).toInstant());
  private final Config config = Config.defaults().withThemeMode(ThemeMode.FIXED_TIMES);

  @Test
  void appliesOnFirstTickAndOnlyAgainAtBoundary() {
    ThemeService service = new ThemeService(control, clock, config);

    service.tick();
    clock.advance(Duration.ofHours(1));
    service.tick();
    clock.advance(Duration.ofHours(6));
    service.tick();
    service.tick();

    assertEquals(List.of(Theme.LIGHT, Theme.DARK), control.applied);
  }

  @Test
  void offNeverTouchesTheTheme() {
    ThemeService service = new ThemeService(control, clock, Config.defaults());

    service.tick();
    clock.advance(Duration.ofHours(12));
    service.tick();

    assertEquals(List.of(), control.applied);
  }

  @Test
  void changedScheduleIsAppliedOnUpdate() throws InterruptedException {
    ThemeService service = new ThemeService(control, clock, config);
    service.tick();
    ThemeSchedule darkFromEleven =
        new ThemeSchedule(
            ThemeMode.FIXED_TIMES, LocalTime.of(7, 0), LocalTime.of(11, 0), 0, 0, 0, 0, true, true);

    service.update(
        new Config(
            config.days(),
            config.mode(),
            config.simulateKeypress(),
            config.startMinimized(),
            config.autoUpdate(),
            darkFromEleven,
            config.timer()));
    control.awaitApplied(2);
    service.shutdown();

    assertEquals(List.of(Theme.LIGHT, Theme.DARK), control.applied);
  }

  private static final class MockThemeControl implements ThemeControl {
    final List<Theme> applied = new CopyOnWriteArrayList<>();

    @Override
    public void apply(Theme theme, boolean apps, boolean system) {
      applied.add(theme);
    }

    @Override
    public Theme current() {
      return applied.isEmpty() ? Theme.LIGHT : applied.getLast();
    }

    void awaitApplied(int count) throws InterruptedException {
      long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
      while (applied.size() < count && System.nanoTime() < deadline) {
        Thread.sleep(10);
      }
    }
  }

  private static final class StubClock extends Clock {
    private Instant now;

    StubClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
