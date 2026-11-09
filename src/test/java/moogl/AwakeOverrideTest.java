package moogl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AwakeOverrideTest {

  private static final LocalDateTime MONDAY = LocalDateTime.of(2026, 11, 2, 0, 0);
  private static final Duration QUARTER = Duration.ofMinutes(15);
  private static final Duration HOUR = Duration.ofHours(1);

  private final Config config = Config.defaults();

  @TempDir Path dir;

  @Test
  void extendsActiveBlockFromItsEnd() {
    Optional<AwakeOverride> override =
        AwakeOverride.adjust(config, Optional.empty(), MONDAY.withHour(12), QUARTER);

    assertEquals(
        Optional.of(new AwakeOverride(MONDAY.withHour(19).withMinute(15), MONDAY.withHour(19))),
        override);
  }

  @Test
  void repeatedAdjustmentsStack() {
    LocalDateTime now = MONDAY.withHour(12);
    Optional<AwakeOverride> override = AwakeOverride.adjust(config, Optional.empty(), now, HOUR);
    override = AwakeOverride.adjust(config, override, now, HOUR);
    override = AwakeOverride.adjust(config, override, now, QUARTER.negated());

    assertEquals(MONDAY.withHour(20).withMinute(45), override.orElseThrow().until());
  }

  @Test
  void returningToNaturalEndClearsOverride() {
    LocalDateTime now = MONDAY.withHour(12);
    Optional<AwakeOverride> override = AwakeOverride.adjust(config, Optional.empty(), now, HOUR);

    assertEquals(
        Optional.empty(), AwakeOverride.adjust(config, override, now, HOUR.negated()));
  }

  @Test
  void idleStartsTemporarySessionFromNow() {
    LocalDateTime now = MONDAY.withHour(20).withMinute(0).withSecond(30);

    Optional<AwakeOverride> override =
        AwakeOverride.adjust(config.withMode(Mode.OFF), Optional.empty(), now, QUARTER);

    assertEquals(
        Optional.of(new AwakeOverride(MONDAY.withHour(20).withMinute(15).withSecond(30), null)),
        override);
  }

  @Test
  void removingTimeWhileIdleDoesNothing() {
    assertEquals(
        Optional.empty(),
        AwakeOverride.adjust(config, Optional.empty(), MONDAY.withHour(20), QUARTER.negated()));
  }

  @Test
  void shorteningPastNowStopsUntilBlockEnds() {
    LocalDateTime now = MONDAY.withHour(18).withMinute(30);

    AwakeOverride override =
        AwakeOverride.adjust(config, Optional.empty(), now, HOUR.negated()).orElseThrow();

    assertEquals(now, override.until());
    assertFalse(override.keepsAwake(now));
    assertFalse(override.isExpired(now.plusMinutes(29)));
    assertTrue(override.isExpired(MONDAY.withHour(19)));
    assertFalse(ScheduleEvaluator.status(config, Optional.of(override), now.plusMinutes(5)).active());
  }

  @Test
  void addingAfterShorteningRestartsFromNow() {
    LocalDateTime now = MONDAY.withHour(18).withMinute(30);
    Optional<AwakeOverride> stopped =
        AwakeOverride.adjust(config, Optional.empty(), now, HOUR.negated());

    AwakeOverride resumed =
        AwakeOverride.adjust(config, stopped, now.plusMinutes(10), QUARTER).orElseThrow();

    assertEquals(MONDAY.withHour(18).withMinute(55), resumed.until());
    assertEquals(MONDAY.withHour(19), resumed.replacedEnd());
  }

  @Test
  void extensionIntoNextBlockHandsOverToSchedule() {
    AwakeOverride override =
        new AwakeOverride(MONDAY.plusDays(1).withHour(9), MONDAY.withHour(19));
    LocalDateTime tuesdayTen = MONDAY.plusDays(1).withHour(10);

    assertTrue(override.isExpired(tuesdayTen));
    assertTrue(ScheduleEvaluator.status(config, Optional.of(override), tuesdayTen).active());
  }

  @Test
  void extensionIsCappedAtSevenDays() {
    LocalDateTime now = MONDAY.withHour(20);
    Optional<AwakeOverride> override = Optional.empty();
    for (int i = 0; i < 200; i++) {
      override = AwakeOverride.adjust(config, override, now, HOUR);
    }

    assertEquals(now.plusDays(7), override.orElseThrow().until());
  }

  @Test
  void alwaysOnIgnoresAdjustments() {
    assertEquals(
        Optional.empty(),
        AwakeOverride.adjust(
            config.withMode(Mode.ALWAYS_ON), Optional.empty(), MONDAY.withHour(12), HOUR));
  }

  @Test
  void nextBoundaryIsEarliestFutureTime() {
    AwakeOverride override = new AwakeOverride(MONDAY.withHour(12), MONDAY.withHour(19));

    assertEquals(Optional.of(MONDAY.withHour(12)), override.nextBoundary(MONDAY.withHour(11)));
    assertEquals(Optional.of(MONDAY.withHour(19)), override.nextBoundary(MONDAY.withHour(12)));
    assertEquals(Optional.empty(), override.nextBoundary(MONDAY.withHour(19)));
  }

  @Test
  void roundTripsThroughFile() throws IOException {
    Path file = dir.resolve("session.properties");
    Optional<AwakeOverride> override =
        Optional.of(new AwakeOverride(MONDAY.withHour(19).withMinute(15), MONDAY.withHour(19)));

    AwakeOverride.save(file, override);
    assertEquals(override, AwakeOverride.load(file));

    AwakeOverride.save(file, Optional.of(new AwakeOverride(MONDAY.withHour(21), null)));
    assertEquals(Optional.of(new AwakeOverride(MONDAY.withHour(21), null)), AwakeOverride.load(file));

    AwakeOverride.save(file, Optional.empty());
    assertFalse(Files.exists(file));
    assertEquals(Optional.empty(), AwakeOverride.load(file));
  }

  @Test
  void corruptFileIsIgnored() throws IOException {
    Path file = dir.resolve("session.properties");
    Files.writeString(file, "override.until=tomorrow\n");

    assertEquals(Optional.empty(), AwakeOverride.load(file));
  }
}
