package moogl;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public record Hotkey(int modifiers, String key) {

  public static final int ALT = 0x0001;
  public static final int CTRL = 0x0002;
  public static final int SHIFT = 0x0004;
  public static final int WIN = 0x0008;

  private static final Pattern KEY = Pattern.compile("[A-Z0-9]|F([1-9]|1[0-9]|2[0-4])");
  private static final int[] ORDER = {CTRL, ALT, SHIFT, WIN};
  private static final String[] NAMES = {"Ctrl", "Alt", "Shift", "Win"};

  public static final Hotkey DEFAULT = new Hotkey(CTRL | ALT, "F12");

  public Hotkey {
    if ((modifiers & (CTRL | ALT | SHIFT | WIN)) == 0 || (modifiers & ~(CTRL | ALT | SHIFT | WIN)) != 0) {
      throw new IllegalArgumentException("a hotkey needs Ctrl, Alt, Shift or Win");
    }
    if (key == null || !KEY.matcher(key).matches()) {
      throw new IllegalArgumentException("unsupported hotkey key: " + key);
    }
  }

  public static Optional<Hotkey> parse(String text) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    int modifiers = 0;
    String key = null;
    for (String part : text.split("\\+")) {
      String token = part.trim();
      int modifier = modifierNamed(token);
      if (modifier != 0) {
        modifiers |= modifier;
      } else if (key == null) {
        key = token.toUpperCase(Locale.ROOT);
      } else {
        return Optional.empty();
      }
    }
    try {
      return Optional.of(new Hotkey(modifiers, key));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  public static Optional<Hotkey> from(KeyEvent event) {
    int modifiers =
        (event.isControlDown() ? CTRL : 0)
            | (event.isAltDown() ? ALT : 0)
            | (event.isShiftDown() ? SHIFT : 0)
            | (event.isMetaDown() ? WIN : 0);
    return keyName(event.getKeyCode())
        .filter(key -> modifiers != 0)
        .map(key -> new Hotkey(modifiers, key));
  }

  static Optional<String> keyName(int keyCode) {
    if (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z
        || keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9) {
      return Optional.of(String.valueOf((char) keyCode));
    }
    if (keyCode >= KeyEvent.VK_F1 && keyCode <= KeyEvent.VK_F12) {
      return Optional.of("F" + (keyCode - KeyEvent.VK_F1 + 1));
    }
    if (keyCode >= KeyEvent.VK_F13 && keyCode <= KeyEvent.VK_F24) {
      return Optional.of("F" + (keyCode - KeyEvent.VK_F13 + 13));
    }
    return Optional.empty();
  }

  public int virtualKey() {
    if (key.length() == 1) {
      return key.charAt(0);
    }
    return 0x70 + Integer.parseInt(key.substring(1)) - 1;
  }

  @Override
  public String toString() {
    List<String> parts = new ArrayList<>();
    for (int i = 0; i < ORDER.length; i++) {
      if ((modifiers & ORDER[i]) != 0) {
        parts.add(NAMES[i]);
      }
    }
    parts.add(key);
    return String.join("+", parts);
  }

  private static int modifierNamed(String token) {
    return switch (token.toLowerCase(Locale.ROOT)) {
      case "ctrl", "control" -> CTRL;
      case "alt" -> ALT;
      case "shift" -> SHIFT;
      case "win", "windows", "meta" -> WIN;
      default -> 0;
    };
  }
}
