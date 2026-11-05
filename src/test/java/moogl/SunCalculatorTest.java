package moogl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class SunCalculatorTest {

  private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");
  private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");
  private static final Duration TOLERANCE = Duration.ofMinutes(3);

  @Test
  void kyivSummerSolstice() {
    LocalDate date = LocalDate.of(2026, 6, 21);
    SunTimes.Regular sun =
        assertInstanceOf(SunTimes.Regular.class, SunCalculator.compute(date, 50.45, 30.52, KYIV));

    assertNear(date.atTime(4, 47), sun.sunrise());
    assertNear(date.atTime(21, 12), sun.sunset());
  }

  @Test
  void kyivWinterSolstice() {
    LocalDate date = LocalDate.of(2026, 12, 21);
    SunTimes.Regular sun =
        assertInstanceOf(SunTimes.Regular.class, SunCalculator.compute(date, 50.45, 30.52, KYIV));

    assertNear(date.atTime(7, 55), sun.sunrise());
    assertNear(date.atTime(15, 55), sun.sunset());
  }

  @Test
  void polarDayAndNightInTromso() {
    assertEquals(
        SunTimes.Polar.DAY, SunCalculator.compute(LocalDate.of(2026, 6, 21), 69.65, 18.96, OSLO));
    assertEquals(
        SunTimes.Polar.NIGHT,
        SunCalculator.compute(LocalDate.of(2026, 12, 21), 69.65, 18.96, OSLO));
  }

  @Test
  void poleDoesNotProduceNaN() {
    assertEquals(
        SunTimes.Polar.DAY, SunCalculator.compute(LocalDate.of(2026, 6, 21), 90, 0, OSLO));
  }

  private static void assertNear(LocalDateTime expected, ZonedDateTime actual) {
    Duration difference = Duration.between(expected, actual.toLocalDateTime()).abs();
    assertTrue(
        difference.compareTo(TOLERANCE) <= 0, "expected ~" + expected + " but was " + actual);
  }
}
