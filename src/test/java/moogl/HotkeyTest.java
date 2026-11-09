package moogl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.event.KeyEvent;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HotkeyTest {

  @Test
  void defaultIsCtrlAltF12() {
    assertEquals("Ctrl+Alt+F12", Hotkey.DEFAULT.toString());
    assertEquals(0x7B, Hotkey.DEFAULT.virtualKey());
  }

  @Test
  void parsesRegardlessOfCaseAndOrder() {
    assertEquals(
        Optional.of(new Hotkey(Hotkey.CTRL | Hotkey.SHIFT, "T")), Hotkey.parse("shift + ctrl + t"));
    assertEquals(Optional.of(Hotkey.DEFAULT), Hotkey.parse(Hotkey.DEFAULT.toString()));
  }

  @Test
  void rejectsUnsupportedCombinations() {
    assertEquals(Optional.empty(), Hotkey.parse("F12"));
    assertEquals(Optional.empty(), Hotkey.parse("Ctrl+Space"));
    assertEquals(Optional.empty(), Hotkey.parse("Ctrl+A+B"));
    assertEquals(Optional.empty(), Hotkey.parse("Ctrl+F25"));
    assertEquals(Optional.empty(), Hotkey.parse(""));
  }

  @Test
  void keyNamesMapToWin32VirtualKeys() {
    assertEquals(0x41, new Hotkey(Hotkey.ALT, "A").virtualKey());
    assertEquals(0x37, new Hotkey(Hotkey.ALT, "7").virtualKey());
    assertEquals(0x70, new Hotkey(Hotkey.ALT, "F1").virtualKey());
    assertEquals(0x87, new Hotkey(Hotkey.ALT, "F24").virtualKey());
  }

  @Test
  void javaKeyCodesMapToKeyNames() {
    assertEquals(Optional.of("Q"), Hotkey.keyName(KeyEvent.VK_Q));
    assertEquals(Optional.of("0"), Hotkey.keyName(KeyEvent.VK_0));
    assertEquals(Optional.of("F12"), Hotkey.keyName(KeyEvent.VK_F12));
    assertEquals(Optional.of("F13"), Hotkey.keyName(KeyEvent.VK_F13));
    assertEquals(Optional.empty(), Hotkey.keyName(KeyEvent.VK_SPACE));
  }
}
