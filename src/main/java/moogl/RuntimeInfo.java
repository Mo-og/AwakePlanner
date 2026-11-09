package moogl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record RuntimeInfo(int javaFeature, Optional<Set<String>> bundledModules) {

  private static final Pattern MODULES = Pattern.compile("(?m)^MODULES=\"?([^\"\\r\\n]*)\"?\\s*$");

  public static RuntimeInfo detect() {
    int feature = Runtime.version().feature();
    if (launcher().isEmpty()) {
      return new RuntimeInfo(feature, Optional.empty());
    }
    try {
      String release = Files.readString(Path.of(System.getProperty("java.home"), "release"));
      return new RuntimeInfo(feature, parseModules(release));
    } catch (IOException | RuntimeException e) {
      return new RuntimeInfo(feature, Optional.empty());
    }
  }

  public static Optional<Path> launcher() {
    return Optional.ofNullable(System.getProperty("jpackage.app-path"))
        .filter(path -> !path.isBlank())
        .map(Path::of);
  }

  static Optional<Set<String>> parseModules(String release) {
    Matcher matcher = MODULES.matcher(release);
    if (!matcher.find()) {
      return Optional.empty();
    }
    return Optional.of(
        Arrays.stream(matcher.group(1).trim().split("\\s+"))
            .filter(module -> !module.isEmpty())
            .collect(Collectors.toUnmodifiableSet()));
  }

  public boolean bundled() {
    return bundledModules.isPresent();
  }
}
