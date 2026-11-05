package moogl;

public enum Theme {
  LIGHT("Light"),
  DARK("Dark");

  private final String label;

  Theme(String label) {
    this.label = label;
  }

  @Override
  public String toString() {
    return label;
  }
}
