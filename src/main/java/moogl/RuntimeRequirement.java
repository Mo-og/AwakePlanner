package moogl;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public record RuntimeRequirement(int java, Set<String> modules) {

  public static Optional<RuntimeRequirement> parse(String text) {
    Properties props = new Properties();
    try {
      props.load(new StringReader(text));
      int java = Integer.parseInt(props.getProperty("java", "").trim());
      Set<String> modules =
          Arrays.stream(props.getProperty("modules", "").split(","))
              .map(String::trim)
              .filter(module -> !module.isEmpty())
              .collect(Collectors.toUnmodifiableSet());
      return Optional.of(new RuntimeRequirement(java, modules));
    } catch (IOException | NumberFormatException e) {
      return Optional.empty();
    }
  }

  public Optional<String> unmetBy(RuntimeInfo runtime) {
    if (java > runtime.javaFeature()) {
      return Optional.of(
          runtime.bundled()
              ? "it needs a newer bundled Java runtime (Java " + java + ")"
              : "it needs Java " + java + ", but this PC runs Java " + runtime.javaFeature());
    }
    return runtime
        .bundledModules()
        .map(
            bundled -> {
              Set<String> missing = new TreeSet<>(modules);
              missing.removeAll(bundled);
              return missing;
            })
        .filter(missing -> !missing.isEmpty())
        .map(missing -> "the bundled Java runtime lacks " + String.join(", ", missing));
  }
}
