package moogl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

public record AwakeOverride(LocalDateTime until, LocalDateTime replacedEnd) {

  static final Duration MAX_AHEAD = Duration.ofDays(7);

  public boolean isExpired(LocalDateTime now) {
    return !now.isBefore(until) && (replacedEnd == null || !now.isBefore(replacedEnd));
  }

  public boolean keepsAwake(LocalDateTime now) {
    return now.isBefore(until);
  }

  public Optional<LocalDateTime> nextBoundary(LocalDateTime now) {
    return Stream.of(until, replacedEnd)
        .filter(time -> time != null && time.isAfter(now))
        .min(LocalDateTime::compareTo);
  }

  public static Optional<AwakeOverride> adjust(
      Config config, Optional<AwakeOverride> current, LocalDateTime now, Duration delta) {
    if (config.mode() == Mode.ALWAYS_ON) {
      return current;
    }
    Optional<AwakeOverride> live = current.filter(override -> !override.isExpired(now));
    LocalDateTime replacedEnd =
        live.isPresent()
            ? live.get().replacedEnd()
            : ScheduleEvaluator.naturalEnd(config, now).orElse(null);
    LocalDateTime effectiveEnd = live.map(AwakeOverride::until).orElse(replacedEnd);
    LocalDateTime base = effectiveEnd == null || effectiveEnd.isBefore(now) ? now : effectiveEnd;
    LocalDateTime until = latest(now, base.plus(delta));
    LocalDateTime limit = now.plus(MAX_AHEAD);
    if (until.isAfter(limit)) {
      until = limit;
    }
    AwakeOverride adjusted = new AwakeOverride(until.truncatedTo(ChronoUnit.SECONDS), replacedEnd);
    if (adjusted.isExpired(now) || adjusted.until().equals(replacedEnd)) {
      return Optional.empty();
    }
    return Optional.of(adjusted);
  }

  public static Optional<AwakeOverride> load(Path file) {
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    Properties props = new Properties();
    try (Reader reader = Files.newBufferedReader(file)) {
      props.load(reader);
      LocalDateTime until = parse(props.getProperty("override.until"));
      if (until == null) {
        return Optional.empty();
      }
      return Optional.of(new AwakeOverride(until, parse(props.getProperty("override.replacedEnd"))));
    } catch (IOException | DateTimeParseException e) {
      return Optional.empty();
    }
  }

  public static void save(Path file, Optional<AwakeOverride> override) throws IOException {
    if (override.isEmpty()) {
      Files.deleteIfExists(file);
      return;
    }
    Properties props = new Properties();
    props.setProperty("override.until", override.get().until().toString());
    if (override.get().replacedEnd() != null) {
      props.setProperty("override.replacedEnd", override.get().replacedEnd().toString());
    }
    Files.createDirectories(file.toAbsolutePath().getParent());
    try (Writer writer = Files.newBufferedWriter(file)) {
      props.store(writer, "AwakePlanner session");
    }
  }

  private static LocalDateTime parse(String value) {
    return value == null || value.isBlank() ? null : LocalDateTime.parse(value.trim());
  }

  private static LocalDateTime latest(LocalDateTime a, LocalDateTime b) {
    return a.isAfter(b) ? a : b;
  }
}
