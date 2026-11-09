package moogl;

import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
import java.awt.Dimension;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

public final class TrayController {

  private final SystemTray tray = SystemTray.getSystemTray();
  private final TrayIcon icon;
  private final Map<Mode, CheckboxMenuItem> modeItems = new EnumMap<>(Mode.class);
  private final Map<ThemeMode, CheckboxMenuItem> themeModeItems = new EnumMap<>(ThemeMode.class);
  private final int iconSize;
  private String keepAwakeStatus = "";
  private String themeStatus = "";

  public TrayController(
      Runnable onOpen,
      Runnable onShowTimer,
      Consumer<Mode> onModeSelected,
      Consumer<ThemeMode> onThemeModeSelected,
      Runnable onCheckForUpdates,
      Runnable onExit)
      throws AWTException {
    Dimension size = tray.getTrayIconSize();
    iconSize = Math.min(size.width, size.height);

    MenuItem open = new MenuItem("Open");
    open.addActionListener(e -> onOpen.run());

    MenuItem showTimer = new MenuItem("Show timer");
    showTimer.addActionListener(e -> onShowTimer.run());

    Menu modeMenu = new Menu("Mode");
    for (Mode mode : Mode.values()) {
      CheckboxMenuItem item = new CheckboxMenuItem(mode.toString());
      item.addItemListener(e -> onModeSelected.accept(mode));
      modeItems.put(mode, item);
      modeMenu.add(item);
    }

    Menu themeMenu = new Menu("Dark mode");
    for (ThemeMode mode : ThemeMode.values()) {
      CheckboxMenuItem item = new CheckboxMenuItem(mode.toString());
      item.addItemListener(e -> onThemeModeSelected.accept(mode));
      themeModeItems.put(mode, item);
      themeMenu.add(item);
    }

    MenuItem checkForUpdates = new MenuItem("Check for updates");
    checkForUpdates.addActionListener(e -> onCheckForUpdates.run());

    MenuItem exit = new MenuItem("Exit");
    exit.addActionListener(e -> onExit.run());

    PopupMenu popup = new PopupMenu();
    popup.add(open);
    popup.add(showTimer);
    popup.add(modeMenu);
    popup.add(themeMenu);
    popup.add(checkForUpdates);
    popup.addSeparator();
    popup.add(exit);

    icon = new TrayIcon(StatusIcons.appIcon(false, iconSize), "AwakePlanner", popup);
    icon.setImageAutoSize(true);
    icon.addActionListener(e -> onOpen.run());
    icon.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 1) {
              onShowTimer.run();
            }
          }
        });
    tray.add(icon);
  }

  public void showStatus(Status status) {
    icon.setImage(StatusIcons.appIcon(status.active(), iconSize));
    keepAwakeStatus = status.description();
    updateToolTip();
  }

  public void showThemeStatus(String description) {
    themeStatus = description;
    updateToolTip();
  }

  public void showMessage(String message) {
    icon.displayMessage("AwakePlanner", message, TrayIcon.MessageType.INFO);
  }

  public void showThemeMode(ThemeMode mode) {
    themeModeItems.forEach((item, menuItem) -> menuItem.setState(item == mode));
  }

  private void updateToolTip() {
    icon.setToolTip("AwakePlanner – " + keepAwakeStatus + "\nDark mode: " + themeStatus);
  }

  public void showMode(Mode mode) {
    modeItems.forEach((item, menuItem) -> menuItem.setState(item == mode));
  }

  public void remove() {
    tray.remove(icon);
  }
}
