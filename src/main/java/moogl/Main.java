package moogl;

import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.SystemTray;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.HyperlinkEvent;

public final class Main {

  private static final Logger LOG = Logger.getLogger(Main.class.getName());
  private static final Path APP_DIR = appDirectory();
  private static final Path CONFIG_FILE = APP_DIR.resolve("config.properties");
  private static final Path SESSION_FILE = APP_DIR.resolve("session.properties");

  private static FileLock instanceLock;

  private final ThemeControl themeControl = createThemeControl();
  private final KeepAwakeService service;
  private final ThemeService themeService;
  private final BuildInfo build = BuildInfo.load();
  private final UpdateService updates;
  private Config config;
  private MainWindow window;
  private TrayController tray;
  private NotchWindow notch;
  private GlobalHotkeys hotkeys;
  private boolean recordingHotkey;

  private Main(Config config) {
    this.config = config;
    this.service =
        new KeepAwakeService(
            createPowerControl(), Clock.systemDefaultZone(), config, SESSION_FILE);
    this.themeService = new ThemeService(themeControl, Clock.systemDefaultZone(), config);
    this.updates = createUpdateService(config);
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
    window =
        new MainWindow(
            this::changeMode,
            this::applyConfig,
            updates::checkNow,
            build.version(),
            trayAvailable,
            this::suspendHotkey,
            this::exit);
    window.showConfig(config);

    notch =
        new NotchWindow(
            service::currentStatus, themeControl::current, service::adjust, this::openSettings);
    notch.setAutoCloseSeconds(config.timer().autoCloseSeconds());
    hotkeys =
        GlobalHotkeys.forCurrentOs(
            () -> SwingUtilities.invokeLater(notch::toggle),
            binding -> SwingUtilities.invokeLater(() -> showHotkeyBinding(binding)));

    if (trayAvailable) {
      try {
        tray =
            new TrayController(
                this::openSettings,
                notch::toggle,
                this::changeMode,
                this::changeThemeMode,
                updates::checkNow,
                this::exit);
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
    updates.addListener(
        update ->
            SwingUtilities.invokeLater(
                () -> {
                  window.showUpdateStatus(update.message());
                  if (update.manualDownload().isPresent()) {
                    if (update.important()) {
                      showManualDownload(update.manualDownload().get());
                    }
                  } else if (update.important() && tray != null) {
                    tray.showMessage(update.message());
                  }
                }));
    Runtime.getRuntime().addShutdownHook(new Thread(service::shutdown, "release-power"));
    service.start();
    themeService.start();
    updates.start();
    hotkeys.bind(config.timer().activeHotkey());

    if (forceShow || !config.startMinimized() || tray == null) {
      window.showWindow();
    }
  }

  private void openSettings() {
    notch.close();
    window.showWindow();
  }

  private void suspendHotkey(boolean recording) {
    recordingHotkey = recording;
    hotkeys.bind(recording ? Optional.empty() : config.timer().activeHotkey());
  }

  private void showHotkeyBinding(GlobalHotkeys.Binding binding) {
    if (recordingHotkey) {
      return;
    }
    if (binding.hotkey().isEmpty()) {
      window.showHotkeyStatus("Hotkey off");
    } else if (!binding.supported()) {
      window.showHotkeyStatus("Hotkeys work on Windows only");
    } else if (binding.registered()) {
      window.showHotkeyStatus(binding.hotkey().get() + " opens the timer");
    } else {
      String message = binding.hotkey().get() + " is used by another app";
      window.showHotkeyStatus(message);
      if (tray != null) {
        tray.showMessage(message + ". Pick another hotkey in the settings.");
      }
    }
  }

  private void showManualDownload(UpdateService.ManualDownload download) {
    URI page = download.releasePage();
    JEditorPane text =
        new JEditorPane(
            "text/html",
            "<html>AwakePlanner "
                + download.version().tag()
                + " can't be installed automatically:<br>"
                + escape(download.reason())
                + ".<br><br>Download it from <a href=\""
                + page
                + "\">"
                + page
                + "</a></html>");
    text.setEditable(false);
    text.setOpaque(false);
    text.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    text.setFont(UIManager.getFont("Label.font"));
    text.addHyperlinkListener(
        e -> {
          if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            browse(page);
          }
        });
    Object[] options = {"Open releases page", "Close"};
    int choice =
        JOptionPane.showOptionDialog(
            window.isVisible() ? window : null,
            text,
            "AwakePlanner update",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.INFORMATION_MESSAGE,
            null,
            options,
            options[0]);
    if (choice == 0) {
      browse(page);
    }
  }

  private static void browse(URI page) {
    try {
      if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(page);
        return;
      }
    } catch (IOException | RuntimeException e) {
      LOG.log(Level.WARNING, "Could not open " + page, e);
    }
    JOptionPane.showMessageDialog(
        null, "Open this page in a browser:\n" + page, "AwakePlanner", JOptionPane.INFORMATION_MESSAGE);
  }

  private static String escape(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private void changeMode(Mode mode) {
    applyConfig(config.withMode(mode));
  }

  private void changeThemeMode(ThemeMode mode) {
    applyConfig(config.withThemeMode(mode));
  }

  private void applyConfig(Config newConfig) {
    boolean timerChanged = !newConfig.timer().equals(config.timer());
    config = newConfig;
    if (timerChanged) {
      hotkeys.bind(newConfig.timer().activeHotkey());
      notch.setAutoCloseSeconds(newConfig.timer().autoCloseSeconds());
    }
    window.showMode(newConfig.mode());
    window.showThemeMode(newConfig.theme().mode());
    if (tray != null) {
      tray.showMode(newConfig.mode());
      tray.showThemeMode(newConfig.theme().mode());
    }
    service.update(newConfig);
    themeService.update(newConfig);
    updates.setAutoUpdate(newConfig.autoUpdate());
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
    updates.shutdown();
    hotkeys.shutdown();
    notch.dispose();
    if (tray != null) {
      tray.remove();
    }
    window.dispose();
    System.exit(0);
  }

  private UpdateService createUpdateService(Config config) {
    boolean windows = isWindows();
    UpdateService.Installer installer =
        windows
            ? new WindowsInstaller()
            : (update, target) -> {
                throw new IOException("automatic install is only supported on Windows");
              };
    return new UpdateService(
        UpdateService.releasesOf(build.repo()),
        build.version(),
        windows ? runningFile() : Optional.empty(),
        APP_DIR.resolve("update"),
        installer,
        () -> SwingUtilities.invokeLater(this::exit),
        Clock.systemUTC(),
        RuntimeInfo.detect(),
        config.autoUpdate());
  }

  private static Optional<Path> runningFile() {
    try {
      Path file = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    } catch (URISyntaxException | RuntimeException e) {
      LOG.log(Level.WARNING, "Could not locate the running application file", e);
      return Optional.empty();
    }
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").startsWith("Windows");
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
