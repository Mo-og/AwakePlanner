package moogl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpdateServiceTest {

  private static final byte[] EXE = "new exe bytes".getBytes(StandardCharsets.UTF_8);
  private static final Version INSTALLED = new Version(1, 1, 0, false);
  private static final Instant NOW = Instant.parse("2026-11-05T09:00:00Z");

  @TempDir Path dir;

  private HttpServer server;
  private final AtomicInteger latestRequests = new AtomicInteger();
  private final List<String> messages = new ArrayList<>();
  private final MockInstaller installer = new MockInstaller();
  private final AtomicInteger restarts = new AtomicInteger();
  private String latestTag = "v1.2.0";
  private String checksum = sha256(EXE);

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/owner/repo/releases/latest",
        exchange -> {
          latestRequests.incrementAndGet();
          if (latestTag == null) {
            respond(exchange, 404, new byte[0]);
            return;
          }
          exchange
              .getResponseHeaders()
              .add("Location", "https://github.com/owner/repo/releases/tag/" + latestTag);
          respond(exchange, 302, new byte[0]);
        });
    server.createContext(
        "/owner/repo/releases/download/v1.2.0/AwakePlanner.exe",
        exchange -> respond(exchange, 200, EXE));
    server.createContext(
        "/owner/repo/releases/download/v1.2.0/AwakePlanner.exe.sha256",
        exchange ->
            respond(
                exchange,
                200,
                (checksum + "  AwakePlanner.exe\n").getBytes(StandardCharsets.UTF_8)));
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void installsNewerReleaseAndRestarts() throws IOException {
    Path target = dir.resolve("AwakePlanner.exe");

    service(INSTALLED, Optional.of(target), NOW).check(true);

    assertEquals(target, installer.target);
    assertArrayEquals(EXE, Files.readAllBytes(installer.update));
    assertEquals(1, restarts.get());
    assertEquals("Installing v1.2.0, restarting…", messages.getLast());
  }

  @Test
  void sameVersionIsLeftAlone() {
    latestTag = "v1.1.0";

    service(INSTALLED, Optional.of(dir.resolve("AwakePlanner.exe")), NOW).check(true);

    assertEquals(null, installer.target);
    assertEquals("Up to date (v1.1.0)", messages.getLast());
  }

  @Test
  void checksumMismatchAbortsInstall() {
    checksum = "0".repeat(64);

    service(INSTALLED, Optional.of(dir.resolve("AwakePlanner.exe")), NOW).check(true);

    assertEquals(null, installer.target);
    assertEquals(0, restarts.get());
    assertFalse(Files.exists(dir.resolve("update/AwakePlanner.exe")));
    assertTrue(messages.getLast().startsWith("Update failed: checksum mismatch"));
  }

  @Test
  void devBuildOnlyReportsAvailableRelease() {
    Version dev = new Version(0, 0, 0, true);

    service(dev, Optional.of(dir.resolve("AwakePlanner.exe")), NOW).check(true);

    assertEquals(null, installer.target);
    assertEquals("v1.2.0 is available on GitHub", messages.getLast());
  }

  @Test
  void repositoryWithoutReleasesIsReported() {
    latestTag = null;

    service(INSTALLED, Optional.empty(), NOW).check(true);

    assertEquals("No releases published yet", messages.getLast());
  }

  @Test
  void automaticCheckRunsAtMostOncePerDay() {
    latestTag = "v1.1.0";

    service(INSTALLED, Optional.empty(), NOW).tick();
    service(INSTALLED, Optional.empty(), NOW.plus(Duration.ofHours(23))).tick();
    assertEquals(1, latestRequests.get());

    service(INSTALLED, Optional.empty(), NOW.plus(Duration.ofHours(24))).tick();
    assertEquals(2, latestRequests.get());
  }

  @Test
  void automaticCheckIsSkippedWhenDisabled() {
    UpdateService service = service(INSTALLED, Optional.empty(), NOW);
    service.setAutoUpdate(false);

    service.tick();

    assertEquals(0, latestRequests.get());
  }

  @Test
  void assetFollowsRunningFileType() {
    assertEquals("AwakePlanner.exe", UpdateService.assetName(Path.of("Renamed.EXE")));
    assertEquals("AwakePlanner.jar", UpdateService.assetName(Path.of("AwakePlanner.jar")));
  }

  private UpdateService service(Version current, Optional<Path> target, Instant now) {
    UpdateService service =
        new UpdateService(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/owner/repo/releases"),
            current,
            target,
            dir.resolve("update"),
            installer,
            restarts::incrementAndGet,
            Clock.fixed(now, ZoneOffset.UTC),
            true);
    service.addListener(update -> messages.add(update.message()));
    return service;
  }

  private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
    exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
    try (var out = exchange.getResponseBody()) {
      out.write(body);
    }
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static final class MockInstaller implements UpdateService.Installer {
    Path update;
    Path target;

    @Override
    public void install(Path update, Path target) {
      this.update = update;
      this.target = target;
    }
  }
}
