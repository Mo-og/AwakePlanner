package moogl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    assertEquals(countries.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), countries);
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

  @Test
  void everyInhabitedUtcOffsetHasAPreset() {
    Map<String, LocationPreset> standardOffsets =
        Map.ofEntries(
            Map.entry("-11:00", LocationPreset.AMERICAN_SAMOA),
            Map.entry("-10:00", LocationPreset.USA_HAWAII),
            Map.entry("-09:30", LocationPreset.FRENCH_POLYNESIA_MARQUESAS),
            Map.entry("-09:00", LocationPreset.USA_ALASKA),
            Map.entry("-08:00", LocationPreset.USA_CALIFORNIA),
            Map.entry("-07:00", LocationPreset.USA_COLORADO),
            Map.entry("-06:00", LocationPreset.USA_TEXAS),
            Map.entry("-05:00", LocationPreset.USA_NEW_YORK),
            Map.entry("-04:00", LocationPreset.VENEZUELA),
            Map.entry("-03:30", LocationPreset.CANADA_NEWFOUNDLAND),
            Map.entry("-03:00", LocationPreset.BRAZIL),
            Map.entry("-02:00", LocationPreset.GREENLAND),
            Map.entry("-01:00", LocationPreset.CAPE_VERDE),
            Map.entry("+00:00", LocationPreset.UNITED_KINGDOM),
            Map.entry("+01:00", LocationPreset.GERMANY),
            Map.entry("+02:00", LocationPreset.UKRAINE),
            Map.entry("+03:00", LocationPreset.BELARUS),
            Map.entry("+03:30", LocationPreset.IRAN),
            Map.entry("+04:00", LocationPreset.GEORGIA),
            Map.entry("+04:30", LocationPreset.AFGHANISTAN),
            Map.entry("+05:00", LocationPreset.KAZAKHSTAN),
            Map.entry("+05:30", LocationPreset.INDIA),
            Map.entry("+05:45", LocationPreset.NEPAL),
            Map.entry("+06:00", LocationPreset.BANGLADESH),
            Map.entry("+06:30", LocationPreset.MYANMAR),
            Map.entry("+07:00", LocationPreset.THAILAND),
            Map.entry("+08:00", LocationPreset.CHINA),
            Map.entry("+08:45", LocationPreset.AUSTRALIA_EUCLA),
            Map.entry("+09:00", LocationPreset.JAPAN),
            Map.entry("+09:30", LocationPreset.AUSTRALIA_NORTHERN_TERRITORY),
            Map.entry("+10:00", LocationPreset.AUSTRALIA),
            Map.entry("+10:30", LocationPreset.AUSTRALIA_LORD_HOWE),
            Map.entry("+11:00", LocationPreset.SOLOMON_ISLANDS),
            Map.entry("+12:00", LocationPreset.NEW_ZEALAND),
            Map.entry("+12:45", LocationPreset.NEW_ZEALAND_CHATHAM),
            Map.entry("+13:00", LocationPreset.TONGA),
            Map.entry("+14:00", LocationPreset.KIRIBATI_LINE_ISLANDS));

    assertEquals(37, standardOffsets.size());
  }

  @Test
  void presetsHaveDistinctCoordinates() {
    Set<String> seen = new HashSet<>();
    for (LocationPreset preset : LocationPreset.values()) {
      if (preset != LocationPreset.CUSTOM) {
        assertTrue(seen.add(preset.latitude() + "," + preset.longitude()), preset.name());
        assertEquals(preset, LocationPreset.matching(preset.latitude(), preset.longitude()));
      }
    }
  }

  @Test
  void usStatesCoverEveryUsTimeZone() {
    assertTrue(
        Arrays.stream(LocationPreset.values())
                .filter(preset -> preset.country().startsWith("USA – "))
                .count()
            >= 6);
  }
}
