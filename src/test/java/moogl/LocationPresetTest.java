package moogl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocationPresetTest {

  @Test
  void countriesAreAlphabeticalAfterCustom() {
    List<String> countries =
        Arrays.stream(LocationPreset.values())
            .skip(1)
            .map(LocationPreset::country)
            .toList();

    assertEquals(LocationPreset.CUSTOM, LocationPreset.values()[0]);
    assertEquals(countries.stream().sorted().toList(), countries);
    assertTrue(
        countries.containsAll(
            List.of(
                "Germany", "Poland", "Georgia", "Ukraine", "Azerbaijan", "Belarus", "Cyprus",
                "Hungary", "India", "Kazakhstan", "Lithuania")));
  }

  @Test
  void presetCoordinatesAreValid() {
    for (LocationPreset preset : LocationPreset.values()) {
      if (preset != LocationPreset.CUSTOM) {
        assertTrue(Math.abs(preset.latitude()) <= 90, preset.name());
        assertTrue(Math.abs(preset.longitude()) <= 180, preset.name());
      }
    }
  }

  @Test
  void matchesPresetCoordinatesOrFallsBackToCustom() {
    assertEquals(LocationPreset.UKRAINE, LocationPreset.matching(50.4501, 30.5234));
    assertEquals(LocationPreset.CUSTOM, LocationPreset.matching(50.4501, 30.6));
    assertEquals(LocationPreset.CUSTOM, LocationPreset.matching(0, 0));
  }
}
