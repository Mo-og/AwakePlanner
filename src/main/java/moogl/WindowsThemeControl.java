package moogl;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

public final class WindowsThemeControl implements ThemeControl {

  private static final MemorySegment HKEY_CURRENT_USER = MemorySegment.ofAddress(0xFFFF_FFFF_8000_0001L);
  private static final MemorySegment HWND_BROADCAST = MemorySegment.ofAddress(0xFFFF);
  private static final String PERSONALIZE_KEY =
      "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize";
  private static final int REG_DWORD = 4;
  private static final int WM_SETTINGCHANGE = 0x001A;
  private static final int SMTO_ABORTIFHUNG = 0x0002;
  private static final int BROADCAST_TIMEOUT_MS = 2000;

  private final MethodHandle regSetKeyValue;
  private final MethodHandle sendMessageTimeout;

  public WindowsThemeControl() {
    Linker linker = Linker.nativeLinker();
    SymbolLookup advapi32 = SymbolLookup.libraryLookup("advapi32", Arena.global());
    SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
    regSetKeyValue =
        linker.downcallHandle(
            advapi32.findOrThrow("RegSetKeyValueW"),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));
    sendMessageTimeout =
        linker.downcallHandle(
            user32.findOrThrow("SendMessageTimeoutW"),
            FunctionDescriptor.of(
                JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS));
  }

  @Override
  public void apply(Theme theme, boolean apps, boolean system) {
    int light = theme == Theme.LIGHT ? 1 : 0;
    try (Arena arena = Arena.ofConfined()) {
      if (apps) {
        setDword(arena, "AppsUseLightTheme", light);
      }
      if (system) {
        setDword(arena, "SystemUsesLightTheme", light);
      }
      broadcastColorChange(arena);
    }
  }

  private void setDword(Arena arena, String name, int value) {
    int status;
    try {
      status =
          (int)
              regSetKeyValue.invokeExact(
                  HKEY_CURRENT_USER,
                  wide(arena, PERSONALIZE_KEY),
                  wide(arena, name),
                  REG_DWORD,
                  arena.allocateFrom(JAVA_INT, value),
                  (int) JAVA_INT.byteSize());
    } catch (Throwable e) {
      throw new IllegalStateException("RegSetKeyValueW call failed", e);
    }
    if (status != 0) {
      throw new IllegalStateException("Could not write " + name + ", error " + status);
    }
  }

  private void broadcastColorChange(Arena arena) {
    try {
      long ignored =
          (long)
              sendMessageTimeout.invokeExact(
                  HWND_BROADCAST,
                  WM_SETTINGCHANGE,
                  0L,
                  wide(arena, "ImmersiveColorSet"),
                  SMTO_ABORTIFHUNG,
                  BROADCAST_TIMEOUT_MS,
                  MemorySegment.NULL);
    } catch (Throwable e) {
      throw new IllegalStateException("SendMessageTimeoutW call failed", e);
    }
  }

  private static MemorySegment wide(Arena arena, String value) {
    return arena.allocateFrom(value, StandardCharsets.UTF_16LE);
  }
}
