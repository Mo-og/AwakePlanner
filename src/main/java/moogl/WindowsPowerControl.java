package moogl;

import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

public final class WindowsPowerControl implements PowerControl {

  private static final int ES_SYSTEM_REQUIRED = 0x00000001;
  private static final int ES_DISPLAY_REQUIRED = 0x00000002;
  private static final int ES_CONTINUOUS = 0x80000000;

  private final MethodHandle setThreadExecutionState;

  public WindowsPowerControl() {
    SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
    setThreadExecutionState =
        Linker.nativeLinker()
            .downcallHandle(
                kernel32.findOrThrow("SetThreadExecutionState"),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT));
  }

  @Override
  public void keepAwake() {
    apply(ES_CONTINUOUS | ES_SYSTEM_REQUIRED | ES_DISPLAY_REQUIRED);
  }

  @Override
  public void release() {
    apply(ES_CONTINUOUS);
  }

  private void apply(int flags) {
    int previous;
    try {
      previous = (int) setThreadExecutionState.invokeExact(flags);
    } catch (Throwable e) {
      throw new IllegalStateException("SetThreadExecutionState call failed", e);
    }
    if (previous == 0) {
      throw new IllegalStateException(
          "SetThreadExecutionState rejected flags " + Integer.toHexString(flags));
    }
  }
}
