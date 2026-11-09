package moogl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class NotchWindowTest {

  @Test
  void remainingTimeShowsSecondsAndDays() {
    assertEquals("03:12:45", NotchWindow.formatRemaining(Duration.ofSeconds(3 * 3600 + 12 * 60 + 45)));
    assertEquals("00:00:01", NotchWindow.formatRemaining(Duration.ofMillis(200)));
    assertEquals("00:00:00", NotchWindow.formatRemaining(Duration.ofSeconds(-5)));
    assertEquals("2d 01:00:00", NotchWindow.formatRemaining(Duration.ofHours(49)));
  }

  @Test
  void closesSoonAfterPointerLeavesOnceHovered() {
    assertEquals(15_000, NotchWindow.closeDelayMs(false, false, 15));
    assertEquals(0, NotchWindow.closeDelayMs(false, false, 0));
    assertEquals(0, NotchWindow.closeDelayMs(true, true, 15));
    assertEquals(NotchWindow.LEAVE_CLOSE_MS, NotchWindow.closeDelayMs(true, false, 15));
    assertEquals(NotchWindow.LEAVE_CLOSE_MS, NotchWindow.closeDelayMs(true, false, 0));
  }
}
