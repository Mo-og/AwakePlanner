package moogl;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

final class AppTheme {

  private static final Logger LOG = Logger.getLogger(AppTheme.class.getName());
  private static final int CHECK_MS = 2000;

  private final Supplier<Theme> current;
  private final Runnable onChanged;
  private final Timer watcher = new Timer(CHECK_MS, e -> refresh());
  private Theme applied;

  AppTheme(Supplier<Theme> current, Runnable onChanged) {
    this.current = current;
    this.onChanged = onChanged;
  }

  void install() {
    applied = current.get();
    if (!setup(applied)) {
      useSystemLookAndFeel();
      return;
    }
    watcher.start();
  }

  void stop() {
    watcher.stop();
  }

  private void refresh() {
    Theme theme = current.get();
    if (theme == applied) {
      return;
    }
    applied = theme;
    if (setup(theme)) {
      FlatLaf.updateUI();
      onChanged.run();
    }
  }

  private static boolean setup(Theme theme) {
    try {
      return theme == Theme.DARK ? FlatDarkLaf.setup() : FlatLightLaf.setup();
    } catch (RuntimeException | LinkageError e) {
      LOG.log(Level.WARNING, "FlatLaf unavailable", e);
      return false;
    }
  }

  private static void useSystemLookAndFeel() {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
      LOG.log(Level.FINE, "System look and feel unavailable", e);
    }
  }
}
