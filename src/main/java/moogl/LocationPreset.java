package moogl;

public enum LocationPreset {
  CUSTOM("Custom", null, Double.NaN, Double.NaN),
  AFGHANISTAN("Afghanistan", "Kabul", 34.5553, 69.2075),
  AMERICAN_SAMOA("American Samoa", "Pago Pago", -14.2756, -170.7020),
  ARGENTINA("Argentina", "Buenos Aires", -34.6037, -58.3816),
  AUSTRALIA("Australia", "Canberra", -35.2809, 149.1300),
  AUSTRALIA_EUCLA("Australia – Eucla", null, -31.6770, 128.8890),
  AUSTRALIA_LORD_HOWE("Australia – Lord Howe Island", null, -31.5553, 159.0821),
  AUSTRALIA_NORTHERN_TERRITORY("Australia – Northern Territory", "Darwin", -12.4634, 130.8456),
  AZERBAIJAN("Azerbaijan", "Baku", 40.4093, 49.8671),
  BANGLADESH("Bangladesh", "Dhaka", 23.8103, 90.4125),
  BELARUS("Belarus", "Minsk", 53.9006, 27.5590),
  BRAZIL("Brazil", "Brasília", -15.7939, -47.8828),
  CANADA_NEWFOUNDLAND("Canada – Newfoundland", "St. John's", 47.5615, -52.7126),
  CAPE_VERDE("Cape Verde", "Praia", 14.9330, -23.5133),
  CHINA("China", "Beijing", 39.9042, 116.4074),
  CYPRUS("Cyprus", "Nicosia", 35.1856, 33.3823),
  FRENCH_POLYNESIA_MARQUESAS("French Polynesia – Marquesas", "Taiohae", -8.9110, -140.1000),
  GEORGIA("Georgia", "Tbilisi", 41.7151, 44.8271),
  GERMANY("Germany", "Berlin", 52.5200, 13.4050),
  GREENLAND("Greenland", "Nuuk", 64.1814, -51.6941),
  HUNGARY("Hungary", "Budapest", 47.4979, 19.0402),
  ICELAND("Iceland", "Reykjavík", 64.1466, -21.9426),
  INDIA("India", "New Delhi", 28.6139, 77.2090),
  IRAN("Iran", "Tehran", 35.6892, 51.3890),
  JAPAN("Japan", "Tokyo", 35.6762, 139.6503),
  KAZAKHSTAN("Kazakhstan", "Astana", 51.1694, 71.4491),
  KIRIBATI_LINE_ISLANDS("Kiribati – Line Islands", "Kiritimati", 1.8721, -157.4278),
  LITHUANIA("Lithuania", "Vilnius", 54.6872, 25.2797),
  MYANMAR("Myanmar", "Naypyidaw", 19.7633, 96.0785),
  NEPAL("Nepal", "Kathmandu", 27.7172, 85.3240),
  NEW_ZEALAND("New Zealand", "Wellington", -41.2865, 174.7762),
  NEW_ZEALAND_CHATHAM("New Zealand – Chatham Islands", "Waitangi", -43.9535, -176.5597),
  POLAND("Poland", "Warsaw", 52.2297, 21.0122),
  SINGAPORE("Singapore", null, 1.3521, 103.8198),
  SOLOMON_ISLANDS("Solomon Islands", "Honiara", -9.4456, 159.9729),
  THAILAND("Thailand", "Bangkok", 13.7563, 100.5018),
  TONGA("Tonga", "Nukuʻalofa", -21.1394, -175.2032),
  UKRAINE("Ukraine", "Kyiv", 50.4501, 30.5234),
  UNITED_KINGDOM("United Kingdom", "London", 51.5074, -0.1278),
  USA_ALASKA("USA – Alaska", "Juneau", 58.3019, -134.4197),
  USA_ARIZONA("USA – Arizona", "Phoenix", 33.4484, -112.0740),
  USA_CALIFORNIA("USA – California", "Sacramento", 38.5816, -121.4944),
  USA_COLORADO("USA – Colorado", "Denver", 39.7392, -104.9903),
  USA_FLORIDA("USA – Florida", "Tallahassee", 30.4383, -84.2807),
  USA_GEORGIA("USA – Georgia", "Atlanta", 33.7490, -84.3880),
  USA_HAWAII("USA – Hawaii", "Honolulu", 21.3099, -157.8581),
  USA_ILLINOIS("USA – Illinois", "Springfield", 39.7817, -89.6501),
  USA_NEW_YORK("USA – New York", "Albany", 42.6526, -73.7562),
  USA_TEXAS("USA – Texas", "Austin", 30.2672, -97.7431),
  USA_WASHINGTON("USA – Washington", "Olympia", 47.0379, -122.9007),
  VENEZUELA("Venezuela", "Caracas", 10.4806, -66.9036);

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
