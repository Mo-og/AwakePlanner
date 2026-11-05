package moogl;

import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
import java.awt.Dimension;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
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
      Consumer<Mode> onModeSelected,
      Consumer<ThemeMode> onThemeModeSelected,
      Runnable onExit)
      throws AWTException {
    Dimension size = tray.getTrayIconSize();
    iconSize = Math.min(size.width, size.height);

    MenuItem open = new MenuItem("Open");
    open.addActionListener(e -> onOpen.run());

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

    MenuItem exit = new MenuItem("Exit");
    exit.addActionListener(e -> onExit.run());

    PopupMenu popup = new PopupMenu();
    popup.add(open);
    popup.add(modeMenu);
    popup.add(themeMenu);
    popup.addSeparator();
    popup.add(exit);

    icon = new TrayIcon(StatusIcons.dot(false, iconSize), "AwakePlanner", popup);
    icon.setImageAutoSize(true);
    icon.addActionListener(e -> onOpen.run());
    tray.add(icon);
  }

  public void showStatus(Status status) {
    icon.setImage(StatusIcons.dot(status.active(), iconSize));
    keepAwakeStatus = status.description();
    updateToolTip();
  }

  public void showThemeStatus(String description) {
    themeStatus = description;
    updateToolTip();
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
