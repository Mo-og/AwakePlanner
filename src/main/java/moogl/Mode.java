package moogl;

public enum Mode {
  SCHEDULE("Schedule"),
  ALWAYS_ON("Always on"),
  OFF("Off");

  private final String label;

  Mode(String label) {
    this.label = label;
  }

  @Override
  public String toString() {
    return label;
  }
}
