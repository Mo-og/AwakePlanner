package moogl;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WindowsGlobalHotkeys implements GlobalHotkeys {

  private static final Logger LOG = Logger.getLogger(WindowsGlobalHotkeys.class.getName());
  private static final int HOTKEY_ID = 1;
  private static final int MOD_NOREPEAT = 0x4000;
  private static final int WM_HOTKEY = 0x0312;
  private static final int PM_REMOVE = 0x0001;
  private static final long MSG_SIZE = 48;
  private static final long MSG_MESSAGE_OFFSET = 8;
  private static final long POLL_MS = 50;

  private final MethodHandle registerHotKey;
  private final MethodHandle unregisterHotKey;
  private final MethodHandle peekMessage;
  private final StructLayout callState = Linker.Option.captureStateLayout();
  private final VarHandle lastError =
      callState.varHandle(MemoryLayout.PathElement.groupElement("GetLastError"));
  private final Runnable onPressed;
  private final Consumer<Binding> onBound;
  private final AtomicReference<Optional<Hotkey>> wanted = new AtomicReference<>();
  private volatile boolean running = true;
  private Optional<Hotkey> bound = Optional.empty();
  private boolean registered;

  public WindowsGlobalHotkeys(Runnable onPressed, Consumer<Binding> onBound) {
    this.onPressed = onPressed;
    this.onBound = onBound;
    Linker linker = Linker.nativeLinker();
    SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
    Linker.Option captureError = Linker.Option.captureCallState("GetLastError");
    registerHotKey =
        linker.downcallHandle(
            user32.findOrThrow("RegisterHotKey"),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT),
            captureError);
    unregisterHotKey =
        linker.downcallHandle(
            user32.findOrThrow("UnregisterHotKey"),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT),
            captureError);
    peekMessage =
        linker.downcallHandle(
            user32.findOrThrow("PeekMessageW"),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));
    Thread thread = new Thread(this::run, "hotkey");
    thread.setDaemon(true);
    thread.start();
  }

  @Override
  public void bind(Optional<Hotkey> hotkey) {
    wanted.set(hotkey);
  }

  @Override
  public void shutdown() {
    running = false;
  }

  private void run() {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment msg = arena.allocate(MSG_SIZE);
      MemorySegment state = arena.allocate(callState);
      while (running) {
        Optional<Hotkey> next = wanted.getAndSet(null);
        if (next != null) {
          apply(next, state);
        }
        while ((int) peekMessage.invokeExact(msg, MemorySegment.NULL, 0, 0, PM_REMOVE) != 0) {
          if (msg.get(JAVA_INT, MSG_MESSAGE_OFFSET) == WM_HOTKEY) {
            onPressed.run();
          }
        }
        Thread.sleep(POLL_MS);
      }
      unregister(state);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Throwable e) {
      LOG.log(Level.SEVERE, "Global hotkey thread stopped", e);
    }
  }

  private void apply(Optional<Hotkey> hotkey, MemorySegment state) throws Throwable {
    unregister(state);
    bound = hotkey;
    if (hotkey.isPresent()) {
      Hotkey key = hotkey.get();
      int ok =
          (int)
              registerHotKey.invokeExact(
                  state, MemorySegment.NULL, HOTKEY_ID, key.modifiers() | MOD_NOREPEAT, key.virtualKey());
      registered = ok != 0;
      if (registered) {
        LOG.info("Registered hotkey " + key);
      } else {
        LOG.warning("Could not register hotkey " + key + ", error " + error(state));
      }
    } else {
      LOG.info("Hotkey turned off");
    }
    onBound.accept(new Binding(bound, registered, true));
  }

  private void unregister(MemorySegment state) throws Throwable {
    if (!registered) {
      return;
    }
    int ok = (int) unregisterHotKey.invokeExact(state, MemorySegment.NULL, HOTKEY_ID);
    if (ok != 0) {
      LOG.info("Unregistered hotkey " + bound.map(Hotkey::toString).orElse(""));
    } else {
      LOG.warning("Could not unregister hotkey, error " + error(state));
    }
    registered = false;
  }

  private int error(MemorySegment state) {
    return (int) lastError.get(state, 0L);
  }
}
