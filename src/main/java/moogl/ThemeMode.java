package moogl;

public enum ThemeMode {
  OFF("Off"),
  FIXED_TIMES("Fixed times"),
  SUN("Sunrise–sunset");

  private final String label;

  ThemeMode(String label) {
    this.label = label;
  }

  @Override
  public String toString() {
    return label;
  }
}
