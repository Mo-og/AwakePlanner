package moogl;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Version(int major, int minor, int patch, boolean dev) implements Comparable<Version> {

  private static final Pattern FORMAT = Pattern.compile("v?(\\d+)\\.(\\d+)\\.(\\d+)(-.+)?");

  public static Optional<Version> parse(String text) {
    if (text == null) {
      return Optional.empty();
    }
    Matcher matcher = FORMAT.matcher(text.trim());
    if (!matcher.matches()) {
      return Optional.empty();
    }
    return Optional.of(
        new Version(
            Integer.parseInt(matcher.group(1)),
            Integer.parseInt(matcher.group(2)),
            Integer.parseInt(matcher.group(3)),
            matcher.group(4) != null));
  }

  public boolean isNewerThan(Version other) {
    return compareTo(other) > 0;
  }

  @Override
  public int compareTo(Version other) {
    int result = Integer.compare(major, other.major);
    if (result == 0) {
      result = Integer.compare(minor, other.minor);
    }
    if (result == 0) {
      result = Integer.compare(patch, other.patch);
    }
    if (result == 0) {
      result = Boolean.compare(other.dev, dev);
    }
    return result;
  }

  public String tag() {
    return "v" + major + "." + minor + "." + patch;
  }

  @Override
  public String toString() {
    return dev ? tag() + "-dev" : tag();
  }
}
