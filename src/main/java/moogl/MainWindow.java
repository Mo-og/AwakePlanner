package moogl;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;

public final class MainWindow extends JFrame {

  private static final LocalDate EPOCH = LocalDate.of(1970, 1, 1);

  private final JLabel statusLabel = new JLabel(" ");
  private final JComboBox<Mode> modeBox = new JComboBox<>(Mode.values());
  private final Map<DayOfWeek, DayRow> rows = new EnumMap<>(DayOfWeek.class);
  private final JCheckBox simulateKeypress =
      new JCheckBox("Simulate F15 keypress (beats policy screen lock)");
  private final JCheckBox startMinimized = new JCheckBox("Start hidden in tray");
  private final JCheckBox autoUpdate = new JCheckBox("Install updates automatically");
  private final JLabel updateStatusLabel = new JLabel(" ");
  private final JComboBox<ThemeMode> themeModeBox = new JComboBox<>(ThemeMode.values());
  private final JLabel themeStatusLabel = new JLabel(" ");
  private final JSpinner lightAt = timeSpinner();
  private final JSpinner darkAt = timeSpinner();
  private final JSpinner latitude = coordinateSpinner(90);
  private final JSpinner longitude = coordinateSpinner(180);
  private final JComboBox<LocationPreset> locationPreset =
      new JComboBox<>(LocationPreset.values());
  private final JSpinner sunriseOffset = offsetSpinner();
  private final JSpinner sunsetOffset = offsetSpinner();
  private final JCheckBox themeApps = new JCheckBox("Apps");
  private final JCheckBox themeSystem = new JCheckBox("Windows (taskbar, Start)");
  private final JCheckBox hotkeyEnabled = new JCheckBox("Hotkey");
  private final HotkeyField hotkeyField = new HotkeyField();
  private final JButton clearHotkey = new JButton("Clear");
  private final JLabel hotkeyStatusLabel = new JLabel(" ");
  private final JCheckBox autoClose = new JCheckBox("Close automatically after");
  private final JSpinner autoCloseSeconds =
      new JSpinner(
          new SpinnerNumberModel(
              TimerSettings.DEFAULT_AUTO_CLOSE,
              TimerSettings.MIN_AUTO_CLOSE,
              TimerSettings.MAX_AUTO_CLOSE,
              5));
  private final List<Component> fixedTimeControls;
  private final List<Component> sunControls;
  private final List<Component> themeTargetControls;
  private boolean updatingMode;
  private boolean updatingTimer;
  private Consumer<TimerSettings> onTimerChanged = timer -> {};
  private boolean syncingLocation;

  public MainWindow(
      Consumer<Mode> onModeSelected,
      Consumer<Config> onSave,
      Runnable onCheckForUpdates,
      Version version,
      boolean hideToTray,
      Consumer<Boolean> onHotkeyRecording,
      Consumer<TimerSettings> onTimerChanged,
      Runnable onExit) {
    super("AwakePlanner");
    setIconImages(
        IntStream.of(16, 24, 32, 48, 64)
            .mapToObj(size -> (Image) StatusIcons.appIcon(true, size))
            .toList());

    modeBox.addActionListener(
        e -> {
          if (!updatingMode) {
            onModeSelected.accept((Mode) modeBox.getSelectedItem());
          }
        });

    JPanel content = new JPanel(new BorderLayout(0, 12));
    content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    content.add(header(), BorderLayout.NORTH);
    JLabel lightAtLabel = new JLabel("Light at");
    JLabel darkAtLabel = new JLabel("Dark at");
    JLabel countryLabel = new JLabel("Country");
    JLabel latitudeLabel = new JLabel("Latitude");
    JLabel longitudeLabel = new JLabel("Longitude");
    JLabel sunriseOffsetLabel = new JLabel("Sunrise offset, min");
    JLabel sunsetOffsetLabel = new JLabel("Sunset offset, min");
    JLabel targetsLabel = new JLabel("Apply to");
    fixedTimeControls = List.of(lightAtLabel, lightAt, darkAtLabel, darkAt);
    sunControls =
        List.of(
            countryLabel,
            locationPreset,
            latitudeLabel,
            latitude,
            longitudeLabel,
            longitude,
            sunriseOffsetLabel,
            sunriseOffset,
            sunsetOffsetLabel,
            sunsetOffset);
    themeTargetControls = List.of(targetsLabel, themeApps, themeSystem);
    themeModeBox.addActionListener(e -> updateThemeEnabled());
    locationPreset.addActionListener(e -> applyLocationPreset());
    latitude.addChangeListener(e -> syncLocationPreset());
    longitude.addChangeListener(e -> syncLocationPreset());

    JPanel themePanel = new JPanel(new GridBagLayout());
    themePanel.setBorder(BorderFactory.createTitledBorder("Dark mode"));
    addRow(themePanel, 0, new JLabel("Switch"), themeModeBox, themeStatusLabel);
    addRow(themePanel, 1, lightAtLabel, lightAt, darkAtLabel, darkAt);
    addRow(themePanel, 2, countryLabel, locationPreset);
    addRow(themePanel, 3, latitudeLabel, latitude, longitudeLabel, longitude);
    addRow(
        themePanel, 4, sunriseOffsetLabel, sunriseOffset, sunsetOffsetLabel, sunsetOffset);
    addRow(themePanel, 5, targetsLabel, themeApps, themeSystem);

    this.onTimerChanged = onTimerChanged;
    hotkeyField.onRecording = onHotkeyRecording;
    hotkeyField.onChange =
        () -> {
          hotkeyEnabled.setSelected(true);
          timerEdited();
        };
    hotkeyEnabled.addActionListener(e -> timerEdited());
    autoClose.addActionListener(e -> timerEdited());
    autoCloseSeconds.addChangeListener(e -> timerEdited());
    clearHotkey.addActionListener(
        e -> {
          hotkeyField.setHotkey(Optional.empty());
          hotkeyEnabled.setSelected(false);
          timerEdited();
        });

    JPanel timerPanel = new JPanel(new GridBagLayout());
    timerPanel.setBorder(BorderFactory.createTitledBorder("Timer window"));
    addRow(timerPanel, 0, hotkeyEnabled, hotkeyField, clearHotkey);
    GridBagConstraints statusCell = new GridBagConstraints();
    statusCell.insets = new Insets(0, 6, 4, 6);
    statusCell.anchor = GridBagConstraints.WEST;
    statusCell.gridx = 1;
    statusCell.gridy = 1;
    statusCell.gridwidth = GridBagConstraints.REMAINDER;
    timerPanel.add(hotkeyStatusLabel, statusCell);
    JPanel autoCloseDelay = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    autoCloseDelay.add(autoCloseSeconds);
    autoCloseDelay.add(new JLabel("seconds"));
    addRow(timerPanel, 2, autoClose, autoCloseDelay);

    JPanel settings = new JPanel(new BorderLayout(0, 12));
    settings.add(themePanel, BorderLayout.NORTH);
    settings.add(timerPanel, BorderLayout.CENTER);

    JPanel center = new JPanel(new BorderLayout(0, 12));
    center.add(scheduleGrid(), BorderLayout.NORTH);
    center.add(settings, BorderLayout.CENTER);
    content.add(center, BorderLayout.CENTER);
    content.add(footer(onSave, onCheckForUpdates, version), BorderLayout.SOUTH);
    setContentPane(content);

    if (hideToTray) {
      setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
      addWindowListener(
          new WindowAdapter() {
            @Override
            public void windowIconified(WindowEvent e) {
              setVisible(false);
              setExtendedState(NORMAL);
            }
          });
    } else {
      setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
      addWindowListener(
          new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
              onExit.run();
            }
          });
    }

    pack();
    setResizable(false);
    setLocationRelativeTo(null);
  }

  public void showWindow() {
    setVisible(true);
    setExtendedState(NORMAL);
    toFront();
    requestFocus();
  }

  public void showConfig(Config config) {
    showMode(config.mode());
    rows.forEach((day, row) -> row.set(config.day(day)));
    simulateKeypress.setSelected(config.simulateKeypress());
    startMinimized.setSelected(config.startMinimized());
    autoUpdate.setSelected(config.autoUpdate());
    showTheme(config.theme());
    showTimer(config.timer());
  }

  public void showHotkeyStatus(String description) {
    hotkeyStatusLabel.setText(description);
    refit();
  }

  public void showUpdateStatus(String description) {
    updateStatusLabel.setText(description);
    refit();
  }

  public void refit() {
    Dimension preferred = getPreferredSize();
    if (preferred.width > getWidth() || preferred.height > getHeight()) {
      pack();
    }
  }

  public void showThemeMode(ThemeMode mode) {
    themeModeBox.setSelectedItem(mode);
  }

  public void showThemeStatus(String description) {
    themeStatusLabel.setText(description);
  }

  public void showMode(Mode mode) {
    updatingMode = true;
    try {
      modeBox.setSelectedItem(mode);
    } finally {
      updatingMode = false;
    }
  }

  public void showStatus(Status status) {
    statusLabel.setIcon(new ImageIcon(StatusIcons.appIcon(status.active(), 14)));
    statusLabel.setText(status.description());
  }

  private JPanel header() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
    statusLabel.setFont(statusLabel.getFont().deriveFont(statusLabel.getFont().getSize2D() + 2f));
    panel.add(statusLabel);
    panel.add(Box.createHorizontalGlue());
    panel.add(new JLabel("Mode: "));
    panel.add(modeBox);
    return panel;
  }

  private JPanel scheduleGrid() {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createTitledBorder("Schedule"));
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(2, 6, 2, 6);
    c.anchor = GridBagConstraints.WEST;

    c.gridy = 0;
    c.gridx = 1;
    panel.add(new JLabel("From"), c);
    c.gridx = 2;
    panel.add(new JLabel("Till"), c);

    for (DayOfWeek day : DayOfWeek.values()) {
      DayRow row = new DayRow(day);
      rows.put(day, row);
      c.gridy++;
      c.gridx = 0;
      panel.add(row.enabled, c);
      c.gridx = 1;
      panel.add(row.start, c);
      c.gridx = 2;
      panel.add(row.end, c);
    }
    return panel;
  }

  private JPanel footer(Consumer<Config> onSave, Runnable onCheckForUpdates, Version version) {
    JPanel options = new JPanel();
    options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
    options.add(simulateKeypress);
    options.add(startMinimized);
    options.add(autoUpdate);

    JButton reset = new JButton("Reset defaults");
    reset.addActionListener(
        e -> {
          Config defaults = Config.defaults();
          rows.forEach((day, row) -> row.set(defaults.day(day)));
          simulateKeypress.setSelected(defaults.simulateKeypress());
          startMinimized.setSelected(defaults.startMinimized());
          autoUpdate.setSelected(defaults.autoUpdate());
          showTheme(defaults.theme());
          showTimer(defaults.timer());
        });

    JButton save = new JButton("Save");
    save.addActionListener(e -> save(onSave));
    getRootPane().setDefaultButton(save);

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    buttons.add(reset);
    buttons.add(save);

    JButton checkForUpdates = new JButton("Check for updates");
    checkForUpdates.addActionListener(e -> onCheckForUpdates.run());

    JPanel updates = new JPanel();
    updates.setLayout(new BoxLayout(updates, BoxLayout.X_AXIS));
    updates.add(new JLabel("Version " + version + "  "));
    updates.add(updateStatusLabel);
    updates.add(Box.createHorizontalGlue());
    updates.add(checkForUpdates);

    JPanel south = new JPanel(new BorderLayout(0, 8));
    south.add(updates, BorderLayout.NORTH);
    south.add(buttons, BorderLayout.SOUTH);

    JPanel panel = new JPanel(new BorderLayout(0, 8));
    panel.add(options, BorderLayout.CENTER);
    panel.add(south, BorderLayout.SOUTH);
    return panel;
  }

  private void save(Consumer<Config> onSave) {
    Map<DayOfWeek, DaySchedule> days = new EnumMap<>(DayOfWeek.class);
    for (var entry : rows.entrySet()) {
      DaySchedule schedule = entry.getValue().get();
      if (schedule.enabled() && !schedule.isValid()) {
        JOptionPane.showMessageDialog(
            this,
            entry.getKey().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + ": end time must be after start time.",
            "AwakePlanner",
            JOptionPane.WARNING_MESSAGE);
        return;
      }
      days.put(entry.getKey(), schedule);
    }
    ThemeSchedule theme = themeFromFields();
    if (theme.mode() == ThemeMode.FIXED_TIMES && theme.lightAt().equals(theme.darkAt())) {
      JOptionPane.showMessageDialog(
          this,
          "Dark mode: light and dark times must differ.",
          "AwakePlanner",
          JOptionPane.WARNING_MESSAGE);
      return;
    }
    onSave.accept(
        new Config(
            days,
            (Mode) modeBox.getSelectedItem(),
            simulateKeypress.isSelected(),
            startMinimized.isSelected(),
            autoUpdate.isSelected(),
            theme,
            timerFromFields()));
  }

  private void showTimer(TimerSettings timer) {
    updatingTimer = true;
    try {
      hotkeyField.setHotkey(timer.hotkey());
      hotkeyEnabled.setSelected(timer.hotkeyEnabled());
      autoClose.setSelected(timer.autoCloseSeconds() > 0);
      if (timer.autoCloseSeconds() > 0) {
        autoCloseSeconds.setValue(timer.autoCloseSeconds());
      }
      updateTimerEnabled();
    } finally {
      updatingTimer = false;
    }
  }

  private void timerEdited() {
    updateTimerEnabled();
    if (!updatingTimer) {
      onTimerChanged.accept(timerFromFields());
    }
  }

  private TimerSettings timerFromFields() {
    Optional<Hotkey> hotkey = hotkeyField.hotkey;
    return new TimerSettings(
        hotkey,
        hotkeyEnabled.isSelected() && hotkey.isPresent(),
        autoClose.isSelected() ? ((Number) autoCloseSeconds.getValue()).intValue() : 0);
  }

  private void updateTimerEnabled() {
    boolean hasHotkey = hotkeyField.hotkey.isPresent();
    if (!hasHotkey) {
      hotkeyEnabled.setSelected(false);
    }
    hotkeyEnabled.setEnabled(hasHotkey);
    clearHotkey.setEnabled(hasHotkey);
    autoCloseSeconds.setEnabled(autoClose.isSelected());
  }

  private void showTheme(ThemeSchedule theme) {
    themeModeBox.setSelectedItem(theme.mode());
    lightAt.setValue(toDate(theme.lightAt()));
    darkAt.setValue(toDate(theme.darkAt()));
    latitude.setValue(theme.latitude());
    longitude.setValue(theme.longitude());
    sunriseOffset.setValue(theme.sunriseOffset());
    sunsetOffset.setValue(theme.sunsetOffset());
    themeApps.setSelected(theme.apps());
    themeSystem.setSelected(theme.system());
    syncLocationPreset();
    updateThemeEnabled();
  }

  private ThemeSchedule themeFromFields() {
    return new ThemeSchedule(
        (ThemeMode) themeModeBox.getSelectedItem(),
        toTime(lightAt.getValue()),
        toTime(darkAt.getValue()),
        ((Number) latitude.getValue()).doubleValue(),
        ((Number) longitude.getValue()).doubleValue(),
        ((Number) sunriseOffset.getValue()).intValue(),
        ((Number) sunsetOffset.getValue()).intValue(),
        themeApps.isSelected(),
        themeSystem.isSelected());
  }

  private void updateThemeEnabled() {
    ThemeMode mode = (ThemeMode) themeModeBox.getSelectedItem();
    fixedTimeControls.forEach(control -> control.setEnabled(mode == ThemeMode.FIXED_TIMES));
    sunControls.forEach(control -> control.setEnabled(mode == ThemeMode.SUN));
    themeTargetControls.forEach(control -> control.setEnabled(mode != ThemeMode.OFF));
  }

  private void applyLocationPreset() {
    LocationPreset preset = (LocationPreset) locationPreset.getSelectedItem();
    if (syncingLocation || preset == null || preset == LocationPreset.CUSTOM) {
      return;
    }
    syncingLocation = true;
    try {
      latitude.setValue(preset.latitude());
      longitude.setValue(preset.longitude());
    } finally {
      syncingLocation = false;
    }
  }

  private void syncLocationPreset() {
    if (syncingLocation) {
      return;
    }
    syncingLocation = true;
    try {
      locationPreset.setSelectedItem(
          LocationPreset.matching(
              ((Number) latitude.getValue()).doubleValue(),
              ((Number) longitude.getValue()).doubleValue()));
    } finally {
      syncingLocation = false;
    }
  }

  private static void addRow(JPanel panel, int row, Component... cells) {
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(2, 6, 2, 6);
    c.anchor = GridBagConstraints.WEST;
    c.gridy = row;
    for (int column = 0; column < cells.length; column++) {
      c.gridx = column;
      c.fill =
          cells[column] instanceof JTextField ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
      c.gridwidth = column == cells.length - 1 ? GridBagConstraints.REMAINDER : 1;
      panel.add(cells[column], c);
    }
  }

  private static JSpinner coordinateSpinner(double limit) {
    JSpinner spinner = new JSpinner(new SpinnerNumberModel(0.0, -limit, limit, 0.0001));
    JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "0.0000");
    editor.getTextField().setColumns(7);
    spinner.setEditor(editor);
    return spinner;
  }

  private static JSpinner offsetSpinner() {
    return new JSpinner(
        new SpinnerNumberModel(0, -ThemeSchedule.MAX_OFFSET, ThemeSchedule.MAX_OFFSET, 5));
  }


  private static JSpinner timeSpinner() {
    JSpinner spinner = new JSpinner(new SpinnerDateModel());
    spinner.setEditor(new JSpinner.DateEditor(spinner, "HH:mm"));
    return spinner;
  }

  private static Date toDate(LocalTime time) {
    return Date.from(EPOCH.atTime(time).atZone(ZoneId.systemDefault()).toInstant());
  }

  private static LocalTime toTime(Object value) {
    return ((Date) value)
        .toInstant()
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .withSecond(0)
        .withNano(0);
  }

  private static final class HotkeyField extends JTextField {
    Optional<Hotkey> hotkey = Optional.empty();
    Consumer<Boolean> onRecording = recording -> {};
    Runnable onChange = () -> {};

    HotkeyField() {
      super(16);
      setEditable(false);
      setFocusTraversalKeysEnabled(false);
      setToolTipText("Click, then press a key combination. Esc cancels, Backspace restores the default.");
      addFocusListener(
          new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
              setText("Press keys…");
              onRecording.accept(true);
            }

            @Override
            public void focusLost(FocusEvent e) {
              showHotkey();
              onRecording.accept(false);
            }
          });
      addKeyListener(
          new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
              e.consume();
              switch (e.getKeyCode()) {
                case KeyEvent.VK_ESCAPE -> transferFocus();
                case KeyEvent.VK_TAB -> transferFocus();
                case KeyEvent.VK_BACK_SPACE -> record(Optional.of(Hotkey.DEFAULT));
                default -> Hotkey.from(e).ifPresent(key -> record(Optional.of(key)));
              }
            }
          });
    }

    void setHotkey(Optional<Hotkey> value) {
      hotkey = value;
      showHotkey();
    }

    private void record(Optional<Hotkey> value) {
      setHotkey(value);
      onChange.run();
      transferFocus();
    }

    private void showHotkey() {
      setText(hotkey.map(Hotkey::toString).orElse("Not set"));
    }
  }

  private static final class DayRow {
    final JCheckBox enabled;
    final JSpinner start = timeSpinner();
    final JSpinner end = timeSpinner();

    DayRow(DayOfWeek day) {
      enabled = new JCheckBox(day.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
      enabled.addActionListener(e -> updateEnabled());
    }

    void set(DaySchedule schedule) {
      enabled.setSelected(schedule.enabled());
      start.setValue(toDate(schedule.start()));
      end.setValue(toDate(schedule.end()));
      updateEnabled();
    }

    DaySchedule get() {
      return new DaySchedule(
          enabled.isSelected(), toTime(start.getValue()), toTime(end.getValue()));
    }

    private void updateEnabled() {
      start.setEnabled(enabled.isSelected());
      end.setEnabled(enabled.isSelected());
    }
  }
}
