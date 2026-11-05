package moogl;

import java.awt.AWTException;
import java.awt.SystemTray;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.Arrays;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public final class Main {

  private static final Logger LOG = Logger.getLogger(Main.class.getName());
  private static final Path APP_DIR = appDirectory();
  private static final Path CONFIG_FILE = APP_DIR.resolve("config.properties");

  private static FileLock instanceLock;

  private final KeepAwakeService service;
  private final ThemeService themeService;
  private Config config;
  private MainWindow window;
  private TrayController tray;

  private Main(Config config) {
    this.config = config;
    this.service = new KeepAwakeService(createPowerControl(), Clock.systemDefaultZone(), config);
    this.themeService = new ThemeService(createThemeControl(), Clock.systemDefaultZone(), config);
  }

  public static void main(String[] args) throws IOException {
    Files.createDirectories(APP_DIR);
    if (!acquireInstanceLock()) {
      SwingUtilities.invokeLater(
          () ->
              JOptionPane.showMessageDialog(
                  null,
                  "AwakePlanner is already running – look for it in the system tray.",
                  "AwakePlanner",
                  JOptionPane.INFORMATION_MESSAGE));
      return;
    }
    setUpLogging();
    boolean forceShow = Arrays.asList(args).contains("--show");
    Main app = new Main(Config.load(CONFIG_FILE));
    SwingUtilities.invokeLater(() -> app.start(forceShow));
  }

  private void start(boolean forceShow) {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
      LOG.log(Level.FINE, "System look and feel unavailable", e);
    }

    boolean trayAvailable = SystemTray.isSupported();
    window = new MainWindow(this::changeMode, this::applyConfig, trayAvailable, this::exit);
    window.showConfig(config);

    if (trayAvailable) {
      try {
        tray =
            new TrayController(
                window::showWindow, this::changeMode, this::changeThemeMode, this::exit);
        tray.showMode(config.mode());
        tray.showThemeMode(config.theme().mode());
      } catch (AWTException e) {
        LOG.log(Level.WARNING, "Could not add tray icon", e);
      }
    }

    service.addListener(
        status ->
            SwingUtilities.invokeLater(
                () -> {
                  window.showStatus(status);
                  if (tray != null) {
                    tray.showStatus(status);
                  }
                }));
    themeService.addListener(
        description ->
            SwingUtilities.invokeLater(
                () -> {
                  window.showThemeStatus(description);
                  if (tray != null) {
                    tray.showThemeStatus(description);
                  }
                }));
    Runtime.getRuntime().addShutdownHook(new Thread(service::shutdown, "release-power"));
    service.start();
    themeService.start();

    if (forceShow || !config.startMinimized() || tray == null) {
      window.showWindow();
    }
  }

  private void changeMode(Mode mode) {
    applyConfig(config.withMode(mode));
  }

  private void changeThemeMode(ThemeMode mode) {
    applyConfig(config.withThemeMode(mode));
  }

  private void applyConfig(Config newConfig) {
    config = newConfig;
    window.showMode(newConfig.mode());
    window.showThemeMode(newConfig.theme().mode());
    if (tray != null) {
      tray.showMode(newConfig.mode());
      tray.showThemeMode(newConfig.theme().mode());
    }
    service.update(newConfig);
    themeService.update(newConfig);
    try {
      newConfig.save(CONFIG_FILE);
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Could not save settings", e);
      JOptionPane.showMessageDialog(
          window,
          "Could not save settings: " + e.getMessage(),
          "AwakePlanner",
          JOptionPane.WARNING_MESSAGE);
    }
  }

  private void exit() {
    service.shutdown();
    themeService.shutdown();
    if (tray != null) {
      tray.remove();
    }
    window.dispose();
    System.exit(0);
  }

  private static PowerControl createPowerControl() {
    try {
      return PowerControl.forCurrentOs();
    } catch (RuntimeException e) {
      LOG.log(Level.SEVERE, "Native power control unavailable, running without it", e);
      return new NoopPowerControl();
    }
  }

  private static ThemeControl createThemeControl() {
    try {
      return ThemeControl.forCurrentOs();
    } catch (RuntimeException e) {
      LOG.log(Level.SEVERE, "Native theme control unavailable, running without it", e);
      return new NoopThemeControl();
    }
  }

  private static boolean acquireInstanceLock() throws IOException {
    FileChannel channel =
        FileChannel.open(
            APP_DIR.resolve("app.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    instanceLock = channel.tryLock();
    if (instanceLock == null) {
      channel.close();
      return false;
    }
    return true;
  }

  private static void setUpLogging() {
    try {
      FileHandler handler =
          new FileHandler(APP_DIR.resolve("app.log").toString(), 1_000_000, 1, true);
      handler.setFormatter(new SimpleFormatter());
      Logger.getLogger("moogl").addHandler(handler);
    } catch (IOException e) {
      LOG.log(Level.WARNING, "File logging unavailable", e);
    }
  }

  private static Path appDirectory() {
    String appData = System.getenv("APPDATA");
    Path base =
        appData != null ? Path.of(appData) : Path.of(System.getProperty("user.home"), ".config");
    return base.resolve("AwakePlanner");
  }
}
