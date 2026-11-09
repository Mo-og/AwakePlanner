package moogl;

import java.util.Optional;
import java.util.function.Consumer;

public final class NoopGlobalHotkeys implements GlobalHotkeys {

  private final Consumer<Binding> onBound;

  public NoopGlobalHotkeys(Consumer<Binding> onBound) {
    this.onBound = onBound;
  }

  @Override
  public void bind(Optional<Hotkey> hotkey) {
    onBound.accept(new Binding(hotkey, false, false));
  }

  @Override
  public void shutdown() {}
}
