package moogl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class VersionTest {

  @Test
  void parsesTagsWithAndWithoutPrefix() {
    assertEquals(Optional.of(new Version(1, 2, 3, false)), Version.parse("v1.2.3"));
    assertEquals(Optional.of(new Version(10, 0, 7, false)), Version.parse("10.0.7"));
    assertEquals(Optional.of(new Version(0, 0, 0, true)), Version.parse("0.0.0-dev"));
  }

  @Test
  void rejectsMalformedVersions() {
    assertEquals(Optional.empty(), Version.parse("1.2"));
    assertEquals(Optional.empty(), Version.parse("latest"));
    assertEquals(Optional.empty(), Version.parse(null));
  }

  @Test
  void comparesNumericallyNotAlphabetically() {
    assertTrue(Version.parse("1.10.0").orElseThrow().isNewerThan(Version.parse("1.9.9").orElseThrow()));
    assertFalse(Version.parse("1.2.3").orElseThrow().isNewerThan(Version.parse("1.2.3").orElseThrow()));
  }

  @Test
  void releaseIsNewerThanDevBuildOfSameNumber() {
    assertTrue(
        Version.parse("1.2.3").orElseThrow().isNewerThan(Version.parse("1.2.3-dev").orElseThrow()));
  }

  @Test
  void tagAlwaysHasPrefix() {
    assertEquals("v1.2.3", Version.parse("1.2.3").orElseThrow().tag());
  }
}
