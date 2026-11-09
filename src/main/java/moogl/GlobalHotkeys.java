package moogl;

import java.util.Optional;
import java.util.function.Consumer;

public interface GlobalHotkeys {

  record Binding(Optional<Hotkey> hotkey, boolean registered, boolean supported) {}

  void bind(Optional<Hotkey> hotkey);

  void shutdown();

  static GlobalHotkeys forCurrentOs(Runnable onPressed, Consumer<Binding> onBound) {
    return System.getProperty("os.name", "").startsWith("Windows")
        ? new WindowsGlobalHotkeys(onPressed, onBound)
        : new NoopGlobalHotkeys(onBound);
  }
}
