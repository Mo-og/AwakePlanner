package moogl;

public enum LocationPreset {
  CUSTOM("Custom", null, Double.NaN, Double.NaN),
  AZERBAIJAN("Azerbaijan", "Baku", 40.4093, 49.8671),
  BELARUS("Belarus", "Minsk", 53.9006, 27.5590),
  CYPRUS("Cyprus", "Nicosia", 35.1856, 33.3823),
  GEORGIA("Georgia", "Tbilisi", 41.7151, 44.8271),
  GERMANY("Germany", "Berlin", 52.5200, 13.4050),
  HUNGARY("Hungary", "Budapest", 47.4979, 19.0402),
  INDIA("India", "New Delhi", 28.6139, 77.2090),
  KAZAKHSTAN("Kazakhstan", "Astana", 51.1694, 71.4491),
  LITHUANIA("Lithuania", "Vilnius", 54.6872, 25.2797),
  POLAND("Poland", "Warsaw", 52.2297, 21.0122),
  UKRAINE("Ukraine", "Kyiv", 50.4501, 30.5234);

  private static final double TOLERANCE = 0.00005;

  private final String country;
  private final String city;
  private final double latitude;
  private final double longitude;

  LocationPreset(String country, String city, double latitude, double longitude) {
    this.country = country;
    this.city = city;
    this.latitude = latitude;
    this.longitude = longitude;
  }

  public String country() {
    return country;
  }

  public double latitude() {
    return latitude;
  }

  public double longitude() {
    return longitude;
  }

  public static LocationPreset matching(double latitude, double longitude) {
    for (LocationPreset preset : values()) {
      if (Math.abs(preset.latitude - latitude) < TOLERANCE
          && Math.abs(preset.longitude - longitude) < TOLERANCE) {
        return preset;
      }
    }
    return CUSTOM;
  }

  @Override
  public String toString() {
    return city == null ? country : country + " (" + city + ")";
  }
}
