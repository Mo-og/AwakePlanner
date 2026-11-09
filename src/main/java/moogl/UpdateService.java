package moogl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateService {

  public interface Installer {
    void install(Path update, Path target) throws IOException;
  }

  public record ManualDownload(Version version, String reason, URI releasePage) {}

  public record Update(String message, boolean important, Optional<ManualDownload> manualDownload) {
    public Update(String message, boolean important) {
      this(message, important, Optional.empty());
    }
  }

  private static final Logger LOG = Logger.getLogger(UpdateService.class.getName());
  private static final Duration FIRST_CHECK = Duration.ofMinutes(2);
  private static final Duration TICK = Duration.ofHours(1);
  private static final Duration CHECK_INTERVAL = Duration.ofDays(1);
  private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(5);
  private static final String RUNTIME_ASSET = "AwakePlanner.runtime";
  private static final Pattern TAG_LOCATION = Pattern.compile("/releases/tag/([^/?#]+)$");

  private final URI releases;
  private final Version current;
  private final Optional<Path> target;
  private final Path workDir;
  private final Installer installer;
  private final Runnable onInstalled;
  private final Clock clock;
  private final RuntimeInfo runtime;
  private final HttpClient noRedirects;
  private final HttpClient followRedirects;
  private final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "update-check");
            thread.setDaemon(true);
            return thread;
          });
  private final List<Consumer<Update>> listeners = new CopyOnWriteArrayList<>();

  private volatile boolean autoUpdate;

  public UpdateService(
      URI releases,
      Version current,
      Optional<Path> target,
      Path workDir,
      Installer installer,
      Runnable onInstalled,
      Clock clock,
      RuntimeInfo runtime,
      boolean autoUpdate) {
    this.releases = releases.toString().endsWith("/") ? releases : URI.create(releases + "/");
    this.current = current;
    this.target = target;
    this.workDir = workDir;
    this.installer = installer;
    this.onInstalled = onInstalled;
    this.clock = clock;
    this.runtime = runtime;
    this.autoUpdate = autoUpdate;
    Duration connectTimeout = Duration.ofSeconds(15);
    noRedirects =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(connectTimeout)
            .build();
    followRedirects =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(connectTimeout)
            .build();
  }

  public static URI releasesOf(String repo) {
    return URI.create("https://github.com/" + repo + "/releases/");
  }

  public void addListener(Consumer<Update> listener) {
    listeners.add(listener);
  }

  public void start() {
    executor.scheduleWithFixedDelay(
        this::tick, FIRST_CHECK.toSeconds(), TICK.toSeconds(), TimeUnit.SECONDS);
  }

  public void setAutoUpdate(boolean enabled) {
    autoUpdate = enabled;
  }

  public void checkNow() {
    executor.execute(() -> check(true));
  }

  public void shutdown() {
    executor.shutdownNow();
  }

  void tick() {
    if (autoUpdate && checkDue()) {
      check(false);
    }
  }

  void check(boolean manual) {
    try {
      publish("Checking for updates…", false);
      Optional<Version> latest = latestVersion();
      recordCheck();
      if (latest.isEmpty()) {
        publish("No releases published yet", manual);
        return;
      }
      Version version = latest.get();
      if (!version.isNewerThan(current)) {
        publish("Up to date (" + current + ")", manual);
        return;
      }
      if (current.dev()) {
        publish(version.tag() + " is available on GitHub", manual);
        return;
      }
      if (target.isEmpty()) {
        requireManualDownload(version, "this copy can't replace its own file", manual);
        return;
      }
      Optional<String> unmet = requirement(version).flatMap(r -> r.unmetBy(runtime));
      if (unmet.isPresent()) {
        requireManualDownload(version, unmet.get(), manual);
        return;
      }
      install(version, target.get());
    } catch (IOException | RuntimeException e) {
      LOG.log(Level.WARNING, "Update failed", e);
      publish("Update failed: " + e.getMessage(), manual);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  Optional<Version> latestVersion() throws IOException, InterruptedException {
    HttpResponse<Void> response =
        noRedirects.send(request(releases.resolve("latest")), HttpResponse.BodyHandlers.discarding());
    int status = response.statusCode();
    if (status == 404) {
      return Optional.empty();
    }
    if (status / 100 != 3) {
      throw new IOException("GitHub answered HTTP " + status);
    }
    return response
        .headers()
        .firstValue("location")
        .map(TAG_LOCATION::matcher)
        .filter(Matcher::find)
        .flatMap(matcher -> Version.parse(matcher.group(1)));
  }

  Optional<RuntimeRequirement> requirement(Version version)
      throws IOException, InterruptedException {
    URI uri = releases.resolve("download/" + version.tag() + "/" + RUNTIME_ASSET);
    HttpResponse<String> response =
        followRedirects.send(request(uri), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() == 404) {
      return Optional.empty();
    }
    if (response.statusCode() != 200) {
      throw new IOException("download of " + uri + " answered HTTP " + response.statusCode());
    }
    return RuntimeRequirement.parse(response.body());
  }

  private void requireManualDownload(Version version, String reason, boolean manual) {
    URI page = releases.resolve("tag/" + version.tag());
    boolean prompt = manual || !promptedFor(version);
    if (prompt) {
      recordPrompt(version);
    }
    LOG.info(version.tag() + " needs a manual download: " + reason);
    Update update =
        new Update(
            version.tag() + " needs a manual download",
            prompt,
            Optional.of(new ManualDownload(version, reason, page)));
    listeners.forEach(listener -> listener.accept(update));
  }

  private boolean promptedFor(Version version) {
    try {
      return Files.readString(workDir.resolve("manual-notice")).trim().equals(version.tag());
    } catch (IOException e) {
      return false;
    }
  }

  private void recordPrompt(Version version) {
    try {
      Files.createDirectories(workDir);
      Files.writeString(workDir.resolve("manual-notice"), version.tag());
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Could not record update notice", e);
    }
  }

  private void install(Version version, Path file) throws IOException, InterruptedException {
    String asset = assetName(file);
    publish("Downloading " + version.tag() + "…", false);
    Files.createDirectories(workDir);
    Path download = workDir.resolve(asset);
    URI assetUri = releases.resolve("download/" + version.tag() + "/" + asset);

    String digest = download(assetUri, download);
    String expected = downloadText(URI.create(assetUri + ".sha256")).trim().split("\\s+")[0];
    if (!expected.equalsIgnoreCase(digest)) {
      Files.deleteIfExists(download);
      throw new IOException("checksum mismatch for " + asset);
    }

    installer.install(download, file);
    LOG.info("Installing " + version.tag() + " over " + file);
    publish("Installing " + version.tag() + ", restarting…", true);
    onInstalled.run();
  }

  private String download(URI uri, Path destination) throws IOException, InterruptedException {
    HttpResponse<InputStream> response =
        followRedirects.send(request(uri), HttpResponse.BodyHandlers.ofInputStream());
    try (InputStream body = response.body()) {
      if (response.statusCode() != 200) {
        throw new IOException("download of " + uri + " answered HTTP " + response.statusCode());
      }
      MessageDigest sha256 = sha256();
      try (InputStream digesting = new DigestInputStream(body, sha256)) {
        Files.copy(digesting, destination, StandardCopyOption.REPLACE_EXISTING);
      }
      return HexFormat.of().formatHex(sha256.digest());
    }
  }

  private String downloadText(URI uri) throws IOException, InterruptedException {
    HttpResponse<String> response =
        followRedirects.send(request(uri), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IOException("download of " + uri + " answered HTTP " + response.statusCode());
    }
    return response.body();
  }

  private HttpRequest request(URI uri) {
    return HttpRequest.newBuilder(uri)
        .timeout(DOWNLOAD_TIMEOUT)
        .header("User-Agent", "AwakePlanner/" + current)
        .GET()
        .build();
  }

  private boolean checkDue() {
    Path file = workDir.resolve("last-check");
    try {
      Instant last = Instant.parse(Files.readString(file).trim());
      return !clock.instant().isBefore(last.plus(CHECK_INTERVAL));
    } catch (IOException | DateTimeParseException e) {
      return true;
    }
  }

  private void recordCheck() {
    try {
      Files.createDirectories(workDir);
      Files.writeString(workDir.resolve("last-check"), clock.instant().toString());
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Could not record update check time", e);
    }
  }

  private void publish(String message, boolean important) {
    Update update = new Update(message, important);
    listeners.forEach(listener -> listener.accept(update));
  }

  static String assetName(Path file) {
    return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe")
        ? "AwakePlanner.exe"
        : "AwakePlanner.jar";
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
