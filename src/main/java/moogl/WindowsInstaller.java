package moogl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class WindowsInstaller implements UpdateService.Installer {

  private final long pid;
  private final Path javaw;

  public WindowsInstaller() {
    this(
        ProcessHandle.current().pid(),
        Path.of(System.getProperty("java.home"), "bin", "javaw.exe"));
  }

  WindowsInstaller(long pid, Path javaw) {
    this.pid = pid;
    this.javaw = javaw;
  }

  @Override
  public void install(Path update, Path target) throws IOException {
    Path script = update.resolveSibling("apply-update.cmd");
    Files.writeString(script, script(update, target), StandardCharsets.UTF_8);
    new ProcessBuilder("cmd.exe", "/c", "start", "AwakePlanner update", "/min", script.toString())
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start();
  }

  String script(Path update, Path target) {
    String launch =
        target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe")
            ? "start \"\" \"" + target + "\""
            : "start \"\" \"" + javaw + "\" -jar \"" + target + "\"";
    return String.join(
        "\r\n",
        "@echo off",
        "chcp 65001 >nul",
        "set tries=0",
        ":wait",
        "tasklist /FI \"PID eq " + pid + "\" /NH 2>nul | find \" " + pid + " \" >nul",
        "if errorlevel 1 goto replace",
        "set /a tries+=1",
        "if %tries% geq 60 goto replace",
        "ping -n 2 127.0.0.1 >nul",
        "goto wait",
        ":replace",
        "set moves=0",
        ":move",
        "move /y \"" + update + "\" \"" + target + "\" >nul 2>&1",
        "if not errorlevel 1 goto launch",
        "set /a moves+=1",
        "if %moves% geq 30 goto launch",
        "ping -n 2 127.0.0.1 >nul",
        "goto move",
        ":launch",
        launch,
        "(goto) 2>nul & del \"%~f0\"",
        "");
  }
}
