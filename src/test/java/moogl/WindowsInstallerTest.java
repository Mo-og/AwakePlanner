package moogl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WindowsInstallerTest {

  private static final Path JAVAW = Path.of("C:/jdk/bin/javaw.exe");

  @Test
  void jarIsRestartedWithJavaw() {
    String script =
        new WindowsInstaller(42, JAVAW, Optional.empty())
            .script(Path.of("update/AwakePlanner.jar"), Path.of("AwakePlanner.jar"));

    assertTrue(script.contains("start \"\" \"" + JAVAW + "\" -jar \"AwakePlanner.jar\""));
  }

  @Test
  void bundledBuildIsRestartedThroughItsLauncher() {
    Path launcher = Path.of("AwakePlanner/AwakePlanner.exe");

    String script =
        new WindowsInstaller(42, JAVAW, Optional.of(launcher))
            .script(Path.of("update/AwakePlanner.jar"), Path.of("AwakePlanner/app/AwakePlanner.jar"));

    assertTrue(script.contains("start \"\" \"" + launcher + "\""));
    assertFalse(script.contains("-jar"));
  }
}
