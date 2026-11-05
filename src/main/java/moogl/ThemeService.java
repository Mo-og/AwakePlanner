package moogl;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ThemeService {

  private static final Logger LOG = Logger.getLogger(ThemeService.class.getName());
  private static final Duration TICK = Duration.ofSeconds(30);

  private final ThemeControl control;
  private final Clock clock;
  private final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "theme-switch");
            thread.setDaemon(true);
            return thread;
          });
  private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

  private volatile Config config;
  private Applied lastApplied;

  private record Applied(ThemeSchedule schedule, Theme theme) {}

  public ThemeService(ThemeControl control, Clock clock, Config config) {
    this.control = control;
    this.clock = clock;
    this.config = config;
  }

  public void addListener(Consumer<String> listener) {
    listeners.add(listener);
  }

  public void start() {
    executor.scheduleAtFixedRate(this::tick, 0, TICK.toSeconds(), TimeUnit.SECONDS);
  }

  public void update(Config newConfig) {
    config = newConfig;
    executor.execute(this::tick);
  }

  public void shutdown() {
    executor.shutdownNow();
  }

  void tick() {
    try {
      ThemeSchedule schedule = config.theme();
      ZonedDateTime now = ZonedDateTime.now(clock);
      Optional<Theme> expected = ThemeEvaluator.expected(schedule, now);
      if (expected.isEmpty()) {
        lastApplied = null;
      } else {
        Applied wanted = new Applied(schedule, expected.get());
        if (!wanted.equals(lastApplied)) {
          lastApplied = wanted;
          control.apply(wanted.theme(), schedule.apps(), schedule.system());
          LOG.info("Switched Windows theme to " + wanted.theme());
        }
      }
      String description = ThemeEvaluator.describe(schedule, now);
      listeners.forEach(listener -> listener.accept(description));
    } catch (RuntimeException e) {
      LOG.log(Level.SEVERE, "Theme switch tick failed", e);
    }
  }
}
