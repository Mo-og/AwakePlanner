package moogl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class LocationDetectorTest {

  @Test
  void parsesInvariantCultureOutput() {
    assertEquals(
        Optional.of(new LocationDetector.Coordinates(50.4501, 30.5234)),
        LocationDetector.parse("50.4501,30.5234\r\n"));
  }

  @Test
  void rejectsGarbageAndOutOfRangeValues() {
    assertEquals(Optional.empty(), LocationDetector.parse(""));
    assertEquals(Optional.empty(), LocationDetector.parse("50,4501;30,5234"));
    assertEquals(Optional.empty(), LocationDetector.parse("91,0"));
  }
}
