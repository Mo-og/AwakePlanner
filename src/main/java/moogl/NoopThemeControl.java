package moogl;

public final class NoopThemeControl implements ThemeControl {

  @Override
  public void apply(Theme theme, boolean apps, boolean system) {}

  @Override
  public Theme current() {
    return Theme.LIGHT;
  }
}
