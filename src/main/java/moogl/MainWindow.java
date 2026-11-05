package moogl;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Component;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.DayOfWeek;
import java.time.Duration;
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
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
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
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

public final class MainWindow extends JFrame {

  private static final LocalDate EPOCH = LocalDate.of(1970, 1, 1);
  private static final Duration DETECT_TIMEOUT = Duration.ofSeconds(15);

  private final JLabel statusLabel = new JLabel(" ");
  private final JComboBox<Mode> modeBox = new JComboBox<>(Mode.values());
  private final Map<DayOfWeek, DayRow> rows = new EnumMap<>(DayOfWeek.class);
  private final JCheckBox simulateKeypress =
      new JCheckBox("Simulate F15 keypress (beats policy screen lock)");
  private final JCheckBox startMinimized = new JCheckBox("Start hidden in tray");
  private final JComboBox<ThemeMode> themeModeBox = new JComboBox<>(ThemeMode.values());
  private final JLabel themeStatusLabel = new JLabel(" ");
  private final JSpinner lightAt = timeSpinner();
  private final JSpinner darkAt = timeSpinner();
  private final JSpinner latitude = coordinateSpinner(90);
  private final JSpinner longitude = coordinateSpinner(180);
  private final JButton detectLocation = new JButton("Detect");
  private final JSpinner sunriseOffset = offsetSpinner();
  private final JSpinner sunsetOffset = offsetSpinner();
  private final JCheckBox themeApps = new JCheckBox("Apps");
  private final JCheckBox themeSystem = new JCheckBox("Windows (taskbar, Start)");
  private final List<Component> fixedTimeControls;
  private final List<Component> sunControls;
  private final List<Component> themeTargetControls;
  private boolean updatingMode;
  private boolean detecting;

  public MainWindow(
      Consumer<Mode> onModeSelected, Consumer<Config> onSave, boolean hideToTray, Runnable onExit) {
    super("AwakePlanner");
    setIconImage(StatusIcons.dot(true, 32));

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
    JLabel latitudeLabel = new JLabel("Latitude");
    JLabel longitudeLabel = new JLabel("Longitude");
    JLabel sunriseOffsetLabel = new JLabel("Sunrise offset, min");
    JLabel sunsetOffsetLabel = new JLabel("Sunset offset, min");
    JLabel targetsLabel = new JLabel("Apply to");
    fixedTimeControls = List.of(lightAtLabel, lightAt, darkAtLabel, darkAt);
    sunControls =
        List.of(
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
    detectLocation.addActionListener(e -> detectLocation());

    JPanel themePanel = new JPanel(new GridBagLayout());
    themePanel.setBorder(BorderFactory.createTitledBorder("Dark mode"));
    addRow(themePanel, 0, new JLabel("Switch"), themeModeBox, themeStatusLabel);
    addRow(themePanel, 1, lightAtLabel, lightAt, darkAtLabel, darkAt);
    addRow(themePanel, 2, latitudeLabel, latitude, longitudeLabel, longitude, detectLocation);
    addRow(
        themePanel, 3, sunriseOffsetLabel, sunriseOffset, sunsetOffsetLabel, sunsetOffset);
    addRow(themePanel, 4, targetsLabel, themeApps, themeSystem);

    JPanel center = new JPanel(new BorderLayout(0, 12));
    center.add(scheduleGrid(), BorderLayout.NORTH);
    center.add(themePanel, BorderLayout.CENTER);
    content.add(center, BorderLayout.CENTER);
    content.add(footer(onSave), BorderLayout.SOUTH);
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
    showTheme(config.theme());
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
    statusLabel.setIcon(new ImageIcon(StatusIcons.dot(status.active(), 14)));
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

  private JPanel footer(Consumer<Config> onSave) {
    JPanel options = new JPanel();
    options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
    options.add(simulateKeypress);
    options.add(startMinimized);

    JButton reset = new JButton("Reset defaults");
    reset.addActionListener(
        e -> {
          Config defaults = Config.defaults();
          rows.forEach((day, row) -> row.set(defaults.day(day)));
          simulateKeypress.setSelected(defaults.simulateKeypress());
          startMinimized.setSelected(defaults.startMinimized());
          showTheme(defaults.theme());
        });

    JButton save = new JButton("Save");
    save.addActionListener(e -> save(onSave));
    getRootPane().setDefaultButton(save);

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    buttons.add(reset);
    buttons.add(save);

    JPanel panel = new JPanel(new BorderLayout(0, 8));
    panel.add(options, BorderLayout.CENTER);
    panel.add(buttons, BorderLayout.SOUTH);
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
            theme));
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
    detectLocation.setEnabled(mode == ThemeMode.SUN && !detecting);
  }

  private void detectLocation() {
    detecting = true;
    updateThemeEnabled();
    new SwingWorker<Optional<LocationDetector.Coordinates>, Void>() {
      @Override
      protected Optional<LocationDetector.Coordinates> doInBackground() {
        return LocationDetector.detect(DETECT_TIMEOUT);
      }

      @Override
      protected void done() {
        detecting = false;
        updateThemeEnabled();
        Optional<LocationDetector.Coordinates> result;
        try {
          result = get();
        } catch (InterruptedException | ExecutionException e) {
          result = Optional.empty();
        }
        result.ifPresentOrElse(
            coordinates -> {
              latitude.setValue(roundCoordinate(coordinates.latitude()));
              longitude.setValue(roundCoordinate(coordinates.longitude()));
            },
            () ->
                JOptionPane.showMessageDialog(
                    MainWindow.this,
                    "Could not detect your location. Turn on location services in Windows"
                        + " Settings → Privacy & security → Location, or enter the coordinates"
                        + " manually.",
                    "AwakePlanner",
                    JOptionPane.WARNING_MESSAGE));
      }
    }.execute();
  }

  private static void addRow(JPanel panel, int row, Component... cells) {
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(2, 6, 2, 6);
    c.anchor = GridBagConstraints.WEST;
    c.gridy = row;
    for (int column = 0; column < cells.length; column++) {
      c.gridx = column;
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

  private static double roundCoordinate(double value) {
    return Math.round(value * 10_000) / 10_000.0;
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
