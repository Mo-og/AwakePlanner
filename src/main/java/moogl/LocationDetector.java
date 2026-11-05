package moogl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LocationDetector {

  private static final Logger LOG = Logger.getLogger(LocationDetector.class.getName());
  private static final Duration PROCESS_GRACE = Duration.ofSeconds(10);
  private static final String SCRIPT =
      """
      Add-Type -AssemblyName System.Device
      $w = New-Object System.Device.Location.GeoCoordinateWatcher
      $w.Start()
      $deadline = (Get-Date).AddSeconds(%d)
      while ($w.Position.Location.IsUnknown -and $w.Permission -ne 'Denied' -and (Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 200
      }
      $l = $w.Position.Location
      $w.Stop()
      if ($l.IsUnknown) { exit 1 }
      [string]::Format([Globalization.CultureInfo]::InvariantCulture, '{0},{1}', $l.Latitude, $l.Longitude)
      """;

  public record Coordinates(double latitude, double longitude) {}

  private LocationDetector() {}

  public static Optional<Coordinates> detect(Duration timeout) {
    if (!System.getProperty("os.name", "").startsWith("Windows")) {
      return Optional.empty();
    }
    String encoded =
        Base64.getEncoder()
            .encodeToString(
                SCRIPT.formatted(timeout.toSeconds()).getBytes(StandardCharsets.UTF_16LE));
    try {
      Process process =
          new ProcessBuilder(
                  "powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();
      if (!process.waitFor(timeout.plus(PROCESS_GRACE).toMillis(), TimeUnit.MILLISECONDS)) {
        process.destroyForcibly();
        return Optional.empty();
      }
      if (process.exitValue() != 0) {
        return Optional.empty();
      }
      return parse(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Location detection failed", e);
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }

  static Optional<Coordinates> parse(String output) {
    String[] parts = output.trim().split(",");
    if (parts.length != 2) {
      return Optional.empty();
    }
    try {
      double latitude = Double.parseDouble(parts[0].trim());
      double longitude = Double.parseDouble(parts[1].trim());
      if (Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
        return Optional.empty();
      }
      return Optional.of(new Coordinates(latitude, longitude));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }
}
