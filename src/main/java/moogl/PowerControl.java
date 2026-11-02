package moogl;

public interface PowerControl {

  void keepAwake();

  void release();

  static PowerControl forCurrentOs() {
    return System.getProperty("os.name", "").startsWith("Windows")
        ? new WindowsPowerControl()
        : new NoopPowerControl();
  }
}
