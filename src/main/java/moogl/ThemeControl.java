package moogl;

public interface ThemeControl {

  void apply(Theme theme, boolean apps, boolean system);

  Theme current();

  static ThemeControl forCurrentOs() {
    return System.getProperty("os.name", "").startsWith("Windows")
        ? new WindowsThemeControl()
        : new NoopThemeControl();
  }
}
