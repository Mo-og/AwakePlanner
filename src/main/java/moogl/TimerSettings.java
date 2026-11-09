package moogl;

import java.util.Optional;

public record TimerSettings(Optional<Hotkey> hotkey, boolean hotkeyEnabled, int autoCloseSeconds) {

  public static final int MIN_AUTO_CLOSE = 5;
  public static final int MAX_AUTO_CLOSE = 120;
  public static final int DEFAULT_AUTO_CLOSE = 15;

  public static TimerSettings defaults() {
    return new TimerSettings(Optional.of(Hotkey.DEFAULT), true, DEFAULT_AUTO_CLOSE);
  }

  public Optional<Hotkey> activeHotkey() {
    return hotkeyEnabled ? hotkey : Optional.empty();
  }

  public boolean isValid() {
    return hotkey != null
        && (!hotkeyEnabled || hotkey.isPresent())
        && (autoCloseSeconds == 0
            || autoCloseSeconds >= MIN_AUTO_CLOSE && autoCloseSeconds <= MAX_AUTO_CLOSE);
  }
}
