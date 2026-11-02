package moogl;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Date;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
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
import javax.swing.WindowConstants;

public final class MainWindow extends JFrame {

  private static final LocalDate EPOCH = LocalDate.of(1970, 1, 1);

  private final JLabel statusLabel = new JLabel(" ");
  private final JComboBox<Mode> modeBox = new JComboBox<>(Mode.values());
  private final Map<DayOfWeek, DayRow> rows = new EnumMap<>(DayOfWeek.class);
  private final JCheckBox simulateKeypress =
      new JCheckBox("Simulate F15 keypress (beats policy screen lock)");
  private final JCheckBox startMinimized = new JCheckBox("Start hidden in tray");
  private boolean updatingMode;

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
    content.add(scheduleGrid(), BorderLayout.CENTER);
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
    onSave.accept(
        new Config(
            days,
            (Mode) modeBox.getSelectedItem(),
            simulateKeypress.isSelected(),
            startMinimized.isSelected()));
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
