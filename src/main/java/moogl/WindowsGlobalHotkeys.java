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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WindowsGlobalHotkeys implements GlobalHotkeys {

  private static final Logger LOG = Logger.getLogger(WindowsGlobalHotkeys.class.getName());
  private static final int HOTKEY_ID = 1;
  private static final int MOD_NOREPEAT = 0x4000;
  private static final int WM_QUIT = 0x0012;
  private static final int WM_HOTKEY = 0x0312;
  private static final int WM_USER = 0x0400;
  private static final int WM_APP = 0x8000;
  private static final int PM_NOREMOVE = 0x0000;
  private static final long MSG_SIZE = 48;
  private static final long MSG_MESSAGE_OFFSET = 8;

  private final MethodHandle registerHotKey;
  private final MethodHandle unregisterHotKey;
  private final MethodHandle getMessage;
  private final MethodHandle peekMessage;
  private final MethodHandle postThreadMessage;
  private final MethodHandle getCurrentThreadId;
  private final Runnable onPressed;
  private final Consumer<Binding> onBound;
  private final AtomicReference<Optional<Hotkey>> wanted = new AtomicReference<>(Optional.empty());
  private volatile int threadId;
  private boolean registered;

  public WindowsGlobalHotkeys(Runnable onPressed, Consumer<Binding> onBound) {
    this.onPressed = onPressed;
    this.onBound = onBound;
    Linker linker = Linker.nativeLinker();
    SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
    SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
    registerHotKey =
        linker.downcallHandle(
            user32.findOrThrow("RegisterHotKey"),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));
    unregisterHotKey =
        linker.downcallHandle(
            user32.findOrThrow("UnregisterHotKey"),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    getMessage =
        linker.downcallHandle(
            user32.findOrThrow("GetMessageW"),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));
    peekMessage =
        linker.downcallHandle(
            user32.findOrThrow("PeekMessageW"),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));
    postThreadMessage =
        linker.downcallHandle(
            user32.findOrThrow("PostThreadMessageW"),
            FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG));
    getCurrentThreadId =
        linker.downcallHandle(
            kernel32.findOrThrow("GetCurrentThreadId"), FunctionDescriptor.of(JAVA_INT));
    Thread thread = new Thread(this::run, "hotkey");
    thread.setDaemon(true);
    thread.start();
  }

  @Override
  public void bind(Optional<Hotkey> hotkey) {
    wanted.set(hotkey);
    post(WM_APP);
  }

  @Override
  public void shutdown() {
    post(WM_QUIT);
  }

  private void post(int message) {
    int id = threadId;
    if (id == 0) {
      return;
    }
    try {
      int ignored = (int) postThreadMessage.invokeExact(id, message, 0L, 0L);
    } catch (Throwable e) {
      LOG.log(Level.WARNING, "PostThreadMessageW call failed", e);
    }
  }

  private void run() {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment msg = arena.allocate(MSG_SIZE);
      int ignored = (int) peekMessage.invokeExact(msg, MemorySegment.NULL, WM_USER, WM_USER, PM_NOREMOVE);
      threadId = (int) getCurrentThreadId.invokeExact();
      apply(wanted.get());
      while (true) {
        int result = (int) getMessage.invokeExact(msg, MemorySegment.NULL, 0, 0);
        if (result == 0 || result == -1) {
          break;
        }
        int message = msg.get(JAVA_INT, MSG_MESSAGE_OFFSET);
        if (message == WM_HOTKEY) {
          onPressed.run();
        } else if (message == WM_APP) {
          apply(wanted.get());
        }
      }
      unregister();
    } catch (Throwable e) {
      LOG.log(Level.SEVERE, "Global hotkey thread stopped", e);
    }
  }

  private void apply(Optional<Hotkey> hotkey) throws Throwable {
    unregister();
    if (hotkey.isPresent()) {
      Hotkey key = hotkey.get();
      registered =
          (int)
                  registerHotKey.invokeExact(
                      MemorySegment.NULL,
                      HOTKEY_ID,
                      key.modifiers() | MOD_NOREPEAT,
                      key.virtualKey())
              != 0;
      if (!registered) {
        LOG.warning("Could not register hotkey " + key + ", it is taken by another app");
      }
    }
    onBound.accept(new Binding(hotkey, registered, true));
  }

  private void unregister() throws Throwable {
    if (registered) {
      int ignored = (int) unregisterHotKey.invokeExact(MemorySegment.NULL, HOTKEY_ID);
      registered = false;
    }
  }
}
