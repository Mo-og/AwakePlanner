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
}
