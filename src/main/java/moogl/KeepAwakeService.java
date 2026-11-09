package moogl;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class KeepAwakeService {

  private static final Logger LOG = Logger.getLogger(KeepAwakeService.class.getName());
  private static final Duration TICK = Duration.ofSeconds(30);
  private static final Duration KEYPRESS_INTERVAL = Duration.ofSeconds(60);

  private final PowerControl power;
  private final Clock clock;
  private final Path overrideFile;
  private final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "keep-awake");
            thread.setDaemon(true);
            return thread;
          });
  private final List<Consumer<Status>> listeners = new CopyOnWriteArrayList<>();

  private volatile Config config;
  private volatile Status status = new Status(false, "Starting…", Optional.empty());
  private Optional<AwakeOverride> override;
  private ScheduledFuture<?> boundaryTick;
  private Boolean lastActive;
  private Instant lastKeypress = Instant.MIN;
  private Robot robot;

  public KeepAwakeService(PowerControl power, Clock clock, Config config, Path overrideFile) {
    this.power = power;
    this.clock = clock;
    this.config = config;
    this.overrideFile = overrideFile;
    this.override = AwakeOverride.load(overrideFile);
  }

  public Status currentStatus() {
    return status;
  }

  public void addListener(Consumer<Status> listener) {
    listeners.add(listener);
  }

  public void start() {
    executor.scheduleAtFixedRate(this::tick, 0, TICK.toSeconds(), TimeUnit.SECONDS);
  }

  public void update(Config newConfig) {
    executor.execute(
        () -> {
          if (newConfig.mode() != config.mode()) {
            setOverride(Optional.empty());
          }
          config = newConfig;
          tick();
        });
  }

  public void adjust(Duration delta) {
    executor.execute(
        () -> {
          setOverride(
              AwakeOverride.adjust(config, override, LocalDateTime.now(clock), delta));
          tick();
        });
  }

  public void shutdown() {
    if (executor.isShutdown()) {
      return;
    }
    try {
      executor.submit(power::release).get(2, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException | TimeoutException e) {
      LOG.log(Level.WARNING, "Could not release power request", e);
    } finally {
      executor.shutdownNow();
    }
  }

  private void tick() {
    try {
      Config current = config;
      LocalDateTime now = LocalDateTime.now(clock);
      if (override.isPresent() && override.get().isExpired(now)) {
        setOverride(Optional.empty());
      }
      Status status = ScheduleEvaluator.status(current, override, now);
      this.status = status;
      scheduleBoundaryTick(now);
      if (status.active()) {
        power.keepAwake();
        if (current.simulateKeypress()) {
          pressF15IfDue();
        }
      } else {
        power.release();
      }
      if (!Boolean.valueOf(status.active()).equals(lastActive)) {
        LOG.info(status.description());
        lastActive = status.active();
      }
      listeners.forEach(listener -> listener.accept(status));
    } catch (RuntimeException e) {
      LOG.log(Level.SEVERE, "Keep-awake tick failed", e);
    }
  }

  private void setOverride(Optional<AwakeOverride> newOverride) {
    if (newOverride.equals(override)) {
      return;
    }
    override = newOverride;
    LOG.info(newOverride.map(o -> "Awake override until " + o.until()).orElse("Awake override cleared"));
    try {
      AwakeOverride.save(overrideFile, newOverride);
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Could not save awake override", e);
    }
  }

  private void scheduleBoundaryTick(LocalDateTime now) {
    if (boundaryTick != null) {
      boundaryTick.cancel(false);
      boundaryTick = null;
    }
    override
        .flatMap(o -> o.nextBoundary(now))
        .map(boundary -> Duration.between(now, boundary))
        .filter(delay -> delay.compareTo(TICK) < 0)
        .ifPresent(
            delay ->
                boundaryTick =
                    executor.schedule(
                        this::tick, delay.toMillis() + 50, TimeUnit.MILLISECONDS));
  }

  private void pressF15IfDue() {
    Instant now = clock.instant();
    if (Duration.between(lastKeypress, now).compareTo(KEYPRESS_INTERVAL) < 0) {
      return;
    }
    try {
      if (robot == null) {
        robot = new Robot();
      }
      robot.keyPress(KeyEvent.VK_F15);
      robot.keyRelease(KeyEvent.VK_F15);
      lastKeypress = now;
    } catch (AWTException | SecurityException e) {
      LOG.log(Level.WARNING, "Could not simulate keypress", e);
    }
  }
}
