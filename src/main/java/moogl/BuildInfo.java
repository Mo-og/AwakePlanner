package moogl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public record BuildInfo(String repo, Version version) {

  private static final Version UNKNOWN = new Version(0, 0, 0, true);

  public static BuildInfo load() {
    Properties props = new Properties();
    try (InputStream in = BuildInfo.class.getResourceAsStream("/update.properties")) {
      if (in != null) {
        props.load(in);
      }
    } catch (IOException e) {
      return new BuildInfo("", UNKNOWN);
    }
    return new BuildInfo(
        props.getProperty("repo", ""),
        Version.parse(props.getProperty("version")).orElse(UNKNOWN));
  }
}
