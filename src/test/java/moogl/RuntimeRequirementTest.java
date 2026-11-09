package moogl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuntimeRequirementTest {

  private static final RuntimeRequirement JAVA_25 =
      new RuntimeRequirement(25, Set.of("java.base", "java.desktop"));

  @Test
  void parsesReleaseAsset() {
    assertEquals(
        Optional.of(JAVA_25), RuntimeRequirement.parse("java=25\nmodules=java.base, java.desktop\n"));
    assertEquals(Optional.empty(), RuntimeRequirement.parse("modules=java.base\n"));
  }

  @Test
  void installedJavaIsCheckedByVersionOnly() {
    assertEquals(Optional.empty(), JAVA_25.unmetBy(new RuntimeInfo(25, Optional.empty())));
    assertEquals(
        Optional.of("it needs Java 25, but this PC runs Java 24"),
        JAVA_25.unmetBy(new RuntimeInfo(24, Optional.empty())));
  }

  @Test
  void bundledRuntimeMustHaveEveryModule() {
    assertEquals(
        Optional.empty(),
        JAVA_25.unmetBy(new RuntimeInfo(25, Optional.of(Set.of("java.base", "java.desktop")))));
    assertEquals(
        Optional.of("the bundled Java runtime lacks java.desktop"),
        JAVA_25.unmetBy(new RuntimeInfo(25, Optional.of(Set.of("java.base")))));
    assertEquals(
        Optional.of("it needs a newer bundled Java runtime (Java 25)"),
        JAVA_25.unmetBy(new RuntimeInfo(21, Optional.of(Set.of("java.base")))));
  }

  @Test
  void readsModulesFromRuntimeReleaseFile() {
    String release =
        """
        IMPLEMENTOR="Eclipse Adoptium"
        JAVA_VERSION="25.0.1"
        MODULES="java.base java.logging java.desktop"
        """;

    assertEquals(
        Optional.of(Set.of("java.base", "java.logging", "java.desktop")),
        RuntimeInfo.parseModules(release));
    assertEquals(Optional.empty(), RuntimeInfo.parseModules("JAVA_VERSION=\"25\"\n"));
  }
}
