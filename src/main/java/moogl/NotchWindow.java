package moogl;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.Timer;
import javax.swing.ToolTipManager;

final class NotchWindow extends JWindow {

  private static final int BODY_WIDTH = 360;
  private static final int BODY_HEIGHT = 136;
  private static final int EAR = 14;
  private static final int RADIUS = 22;
  private static final int SHADOW = 16;
  private static final int OVERSHOOT = 12;
  private static final int PADDING = 20;
  private static final double OPEN_MS = 460;
  private static final double CLOSE_MS = 280;
  private static final Color TRANSPARENT = new Color(0, 0, 0, 0);
  private static final String FONT = "Segoe UI";

  private enum Phase {
    HIDDEN,
    OPENING,
    SHOWN,
    CLOSING
  }

  private record Palette(
      Color surface,
      Color border,
      Color text,
      Color secondary,
      Color button,
      Color buttonHover,
      Color buttonPressed,
      Color active,
      Color idle,
      Color shadow) {}

  private static final Palette LIGHT =
      new Palette(
          new Color(0xF7, 0xF7, 0xF9),
          new Color(0, 0, 0, 22),
          new Color(0x16, 0x17, 0x1A),
          new Color(0x6B, 0x6F, 0x78),
          new Color(0xE8, 0xE9, 0xED),
          new Color(0xDD, 0xDF, 0xE4),
          new Color(0xCF, 0xD1, 0xD8),
          new Color(0x1F, 0x9D, 0x55),
          new Color(0x9A, 0xA0, 0xA6),
          new Color(0, 0, 0, 16));
  private static final Palette DARK =
      new Palette(
          new Color(0x0B, 0x0B, 0x0D),
          new Color(255, 255, 255, 20),
          new Color(0xF2, 0xF3, 0xF5),
          new Color(0x9C, 0xA0, 0xA8),
          new Color(0x1F, 0x20, 0x24),
          new Color(0x2B, 0x2C, 0x31),
          new Color(0x36, 0x37, 0x3D),
          new Color(0x34, 0xC7, 0x59),
          new Color(0x7C, 0x80, 0x89),
          new Color(0, 0, 0, 40));

  private final Supplier<Status> status;
  private final Supplier<Theme> theme;
  private final Surface surface = new Surface();
  private final List<Control> controls = new ArrayList<>();
  private final Control[] adjusters;
  private final Timer frames = new Timer(15, e -> advance());
  private final Timer refresh = new Timer(250, e -> refresh());
  private final Timer autoClose = new Timer(0, e -> close());
  private final boolean translucent;

  private Phase phase = Phase.HIDDEN;
  private double t;
  private double phaseFrom;
  private long phaseStart;
  private int refreshCount;
  private Palette palette = LIGHT;
  private int autoCloseSeconds = TimerSettings.DEFAULT_AUTO_CLOSE;
  private Control hovered;
  private Control pressed;
  private boolean pointerInside;

  NotchWindow(
      Supplier<Status> status,
      Supplier<Theme> theme,
      Consumer<Duration> onAdjust,
      Runnable onOpenSettings) {
    this.status = status;
    this.theme = theme;
    setAlwaysOnTop(true);
    setFocusableWindowState(false);
    setType(Type.POPUP);
    translucent =
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getDefaultScreenDevice()
            .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);
    if (translucent) {
      setBackground(TRANSPARENT);
    }
    setContentPane(surface);
    setSize(windowWidth(), windowHeight());
    autoClose.setRepeats(false);

    double left = bodyLeft() + PADDING;
    double right = bodyLeft() + BODY_WIDTH - PADDING;
    Control closeButton =
        new Control(Control.Kind.CLOSE, "", "Close", new Rectangle2D.Double(right - 24, 10, 24, 24), this::close);
    Control settingsButton =
        new Control(
            Control.Kind.GEAR,
            "",
            "Open settings",
            new Rectangle2D.Double(right - 54, 10, 24, 24),
            () -> {
              close();
              onOpenSettings.run();
            });
    String[] labels = {"−1h", "−15m", "+15m", "+1h"};
    Duration[] deltas = {
      Duration.ofHours(-1), Duration.ofMinutes(-15), Duration.ofMinutes(15), Duration.ofHours(1)
    };
    double gap = 8;
    double pillWidth = (right - left - gap * (labels.length - 1)) / labels.length;
    adjusters = new Control[labels.length];
    for (int i = 0; i < labels.length; i++) {
      Duration delta = deltas[i];
      adjusters[i] =
          new Control(
              Control.Kind.PILL,
              labels[i],
              "",
              new Rectangle2D.Double(left + i * (pillWidth + gap), 98, pillWidth, 26),
              () -> {
                onAdjust.accept(delta);
                restartAutoClose();
              });
      controls.add(adjusters[i]);
    }
    controls.add(settingsButton);
    controls.add(closeButton);
    ToolTipManager.sharedInstance().registerComponent(surface);
  }

  void setAutoCloseSeconds(int seconds) {
    autoCloseSeconds = seconds;
    if (phase == Phase.OPENING || phase == Phase.SHOWN) {
      restartAutoClose();
    }
  }

  void toggle() {
    if (phase == Phase.OPENING || phase == Phase.SHOWN) {
      close();
    } else {
      open();
    }
  }

  void open() {
    if (phase == Phase.OPENING || phase == Phase.SHOWN) {
      restartAutoClose();
      return;
    }
    palette = theme.get() == Theme.DARK ? DARK : LIGHT;
    if (phase == Phase.HIDDEN) {
      position();
      t = 0;
      setVisible(true);
    }
    startPhase(Phase.OPENING);
    refresh.start();
    restartAutoClose();
  }

  void close() {
    if (phase == Phase.HIDDEN || phase == Phase.CLOSING) {
      return;
    }
    autoClose.stop();
    hovered = null;
    pressed = null;
    startPhase(Phase.CLOSING);
  }

  private void startPhase(Phase next) {
    phase = next;
    phaseFrom = t;
    phaseStart = System.nanoTime();
    frames.start();
  }

  private void advance() {
    double elapsed = (System.nanoTime() - phaseStart) / 1_000_000.0;
    if (phase == Phase.OPENING) {
      t = Math.min(1, phaseFrom + elapsed / OPEN_MS);
      if (t >= 1) {
        phase = Phase.SHOWN;
        frames.stop();
      }
    } else if (phase == Phase.CLOSING) {
      t = Math.max(0, phaseFrom - elapsed / CLOSE_MS);
      if (t <= 0) {
        phase = Phase.HIDDEN;
        frames.stop();
        refresh.stop();
        setVisible(false);
      }
    } else {
      frames.stop();
    }
    surface.repaint();
  }

  private void refresh() {
    if (++refreshCount % 4 == 0) {
      palette = theme.get() == Theme.DARK ? DARK : LIGHT;
    }
    surface.repaint();
  }

  private void restartAutoClose() {
    autoClose.stop();
    if (autoCloseSeconds > 0 && !pointerInside && phase != Phase.CLOSING && phase != Phase.HIDDEN) {
      autoClose.setInitialDelay(autoCloseSeconds * 1000);
      autoClose.start();
    }
  }

  private void position() {
    GraphicsConfiguration screen = screenUnderPointer();
    Rectangle bounds = screen.getBounds();
    Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(screen);
    setLocation(bounds.x + (bounds.width - getWidth()) / 2, bounds.y + insets.top);
  }

  private static GraphicsConfiguration screenUnderPointer() {
    PointerInfo pointer = MouseInfo.getPointerInfo();
    if (pointer != null) {
      Point location = pointer.getLocation();
      for (GraphicsDevice device :
          GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
        GraphicsConfiguration configuration = device.getDefaultConfiguration();
        if (configuration.getBounds().contains(location)) {
          return configuration;
        }
      }
    }
    return GraphicsEnvironment.getLocalGraphicsEnvironment()
        .getDefaultScreenDevice()
        .getDefaultConfiguration();
  }

  private static int windowWidth() {
    return BODY_WIDTH + 2 * EAR + 2 * SHADOW;
  }

  private static int windowHeight() {
    return BODY_HEIGHT + OVERSHOOT + SHADOW;
  }

  private static double bodyLeft() {
    return (windowWidth() - BODY_WIDTH) / 2.0;
  }

  private static Path2D notchShape(double centerX, double width, double height) {
    double ear = Math.min(EAR, height / 2);
    double radius = Math.min(RADIUS, Math.min(height - ear, width / 2));
    double left = centerX - width / 2;
    double right = centerX + width / 2;
    Path2D path = new Path2D.Double();
    path.moveTo(left - ear, 0);
    path.quadTo(left, 0, left, ear);
    path.lineTo(left, height - radius);
    path.quadTo(left, height, left + radius, height);
    path.lineTo(right - radius, height);
    path.quadTo(right, height, right, height - radius);
    path.lineTo(right, ear);
    path.quadTo(right, 0, right + ear, 0);
    path.closePath();
    return path;
  }

  private static double easeOutBack(double x) {
    double c1 = 1.3;
    double c3 = c1 + 1;
    return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
  }

  private static double easeOutCubic(double x) {
    return 1 - Math.pow(1 - x, 3);
  }

  private static double smoothstep(double x) {
    double clamped = Math.max(0, Math.min(1, x));
    return clamped * clamped * (3 - 2 * clamped);
  }

  static String formatRemaining(Duration remaining) {
    long seconds = Math.max(0, (remaining.toMillis() + 999) / 1000);
    long days = seconds / 86_400;
    String clock =
        String.format("%02d:%02d:%02d", seconds % 86_400 / 3600, seconds % 3600 / 60, seconds % 60);
    return days > 0 ? days + "d " + clock : clock;
  }

  private static String subtitle(Status status) {
    String description = status.description();
    int dash = description.indexOf(" – ");
    String detail = dash >= 0 ? description.substring(dash + 3) : description;
    if (!status.active()) {
      return "Not keeping awake · " + detail;
    }
    if (status.until().isEmpty()) {
      return "Switch to Schedule to adjust";
    }
    return detail;
  }

  private static String ellipsize(String text, FontMetrics metrics, double width) {
    if (metrics.stringWidth(text) <= width) {
      return text;
    }
    String shortened = text;
    while (!shortened.isEmpty() && metrics.stringWidth(shortened + "…") > width) {
      shortened = shortened.substring(0, shortened.length() - 1);
    }
    return shortened + "…";
  }

  private static Color withAlpha(Color color, double alpha) {
    return new Color(
        color.getRed(),
        color.getGreen(),
        color.getBlue(),
        (int) Math.round(color.getAlpha() * Math.max(0, Math.min(1, alpha))));
  }

  private static final class Control {
    enum Kind {
      PILL,
      GEAR,
      CLOSE
    }

    final Kind kind;
    final String label;
    String tooltip;
    final Rectangle2D.Double bounds;
    final Runnable action;
    boolean enabled = true;

    Control(Kind kind, String label, String tooltip, Rectangle2D.Double bounds, Runnable action) {
      this.kind = kind;
      this.label = label;
      this.tooltip = tooltip;
      this.bounds = bounds;
      this.action = action;
    }
  }

  private final class Surface extends JComponent {

    Surface() {
      setOpaque(false);
      MouseAdapter mouse =
          new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
              pointerInside = true;
              autoClose.stop();
            }

            @Override
            public void mouseExited(MouseEvent e) {
              pointerInside = false;
              hovered = null;
              pressed = null;
              setCursor(Cursor.getDefaultCursor());
              repaint();
              restartAutoClose();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
              Control target = controlAt(e.getPoint());
              if (target != hovered) {
                hovered = target;
                setCursor(
                    target != null
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
                repaint();
              }
            }

            @Override
            public void mousePressed(MouseEvent e) {
              if (e.getButton() == MouseEvent.BUTTON1) {
                pressed = controlAt(e.getPoint());
                repaint();
              }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
              Control target = controlAt(e.getPoint());
              Control wasPressed = pressed;
              pressed = null;
              repaint();
              if (target != null && target == wasPressed) {
                target.action.run();
              }
            }
          };
      addMouseListener(mouse);
      addMouseMotionListener(mouse);
    }

    private Control controlAt(Point point) {
      if (phase != Phase.SHOWN && phase != Phase.OPENING) {
        return null;
      }
      for (Control control : controls) {
        if (control.enabled && control.bounds.contains(point)) {
          return control;
        }
      }
      return null;
    }

    @Override
    public String getToolTipText(MouseEvent event) {
      for (Control control : controls) {
        if (control.bounds.contains(event.getPoint()) && !control.tooltip.isEmpty()) {
          return control.tooltip;
        }
      }
      return null;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D g = (Graphics2D) graphics.create();
      try {
        if (translucent) {
          g.setComposite(AlphaComposite.Src);
          g.setColor(TRANSPARENT);
          g.fillRect(0, 0, getWidth(), getHeight());
          g.setComposite(AlphaComposite.SrcOver);
        } else {
          g.setColor(palette.surface());
          g.fillRect(0, 0, getWidth(), getHeight());
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(
            RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        paintNotch(g);
      } finally {
        g.dispose();
      }
    }

    private void paintNotch(Graphics2D g) {
      double height = Math.min(BODY_HEIGHT + OVERSHOOT, BODY_HEIGHT * easeOutBack(t));
      if (height < 1) {
        return;
      }
      double width = BODY_WIDTH * (0.38 + 0.62 * easeOutCubic(t));
      double centerX = getWidth() / 2.0;

      for (int layer = SHADOW / 2; layer >= 1; layer--) {
        g.setColor(withAlpha(palette.shadow(), t));
        g.fill(notchShape(centerX, width + layer * 2.0, height + layer * 1.6));
      }
      Path2D shape = notchShape(centerX, width, height);
      g.setColor(palette.surface());
      g.fill(shape);
      g.setColor(palette.border());
      g.setStroke(new BasicStroke(1f));
      g.draw(shape);

      double alpha = smoothstep((t - 0.55) / 0.45);
      if (alpha <= 0) {
        return;
      }
      Graphics2D content = (Graphics2D) g.create();
      try {
        content.clip(shape);
        content.setComposite(AlphaComposite.SrcOver.derive((float) alpha));
        content.translate(0, (1 - alpha) * -6);
        paintContent(content);
      } finally {
        content.dispose();
      }
    }

    private void paintContent(Graphics2D g) {
      Status current = status.get();
      boolean alwaysOn = current.active() && current.until().isEmpty();
      for (int i = 0; i < adjusters.length; i++) {
        boolean minus = i < 2;
        adjusters[i].enabled = current.adjustable() && (!minus || current.active());
        adjusters[i].tooltip =
            alwaysOn ? "Switch the mode to Schedule or Off to use timers" : "";
      }

      double left = bodyLeft() + PADDING;
      double right = bodyLeft() + BODY_WIDTH - PADDING;

      Color accent = current.active() ? palette.active() : palette.idle();
      g.setColor(accent);
      g.fill(new Ellipse2D.Double(left, 18, 8, 8));
      g.setFont(new Font(FONT, Font.BOLD, 11));
      g.setColor(palette.secondary());
      String state = alwaysOn ? "ALWAYS ON" : current.active() ? "AWAKE" : "IDLE";
      g.drawString(state, (float) (left + 14), 26f);

      String big =
          alwaysOn
              ? "∞"
              : current
                  .until()
                  .map(until -> formatRemaining(Duration.between(LocalDateTime.now(), until)))
                  .orElse("00:00:00");
      g.setFont(new Font(FONT, Font.BOLD, alwaysOn ? 40 : 34));
      g.setColor(current.active() ? palette.text() : palette.secondary());
      g.drawString(big, (float) left, 68f);

      g.setFont(new Font(FONT, Font.PLAIN, 12));
      g.setColor(palette.secondary());
      g.drawString(ellipsize(subtitle(current), g.getFontMetrics(), right - left), (float) left, 87f);

      for (Control control : controls) {
        paintControl(g, control);
      }
    }

    private void paintControl(Graphics2D g, Control control) {
      Rectangle2D.Double b = control.bounds;
      Color fill =
          control == pressed
              ? palette.buttonPressed()
              : control == hovered ? palette.buttonHover() : null;
      if (control.kind == Control.Kind.PILL && fill == null) {
        fill = palette.button();
      }
      double fade = control.enabled ? 1 : 0.4;
      if (fill != null) {
        g.setColor(withAlpha(fill, fade));
        g.fill(new RoundRectangle2D.Double(b.x, b.y, b.width, b.height, b.height, b.height));
      }
      g.setColor(withAlpha(palette.text(), fade));
      switch (control.kind) {
        case PILL -> {
          g.setFont(new Font(FONT, Font.BOLD, 12));
          FontMetrics metrics = g.getFontMetrics();
          float x = (float) (b.getCenterX() - metrics.stringWidth(control.label) / 2.0);
          float y = (float) (b.getCenterY() + (metrics.getAscent() - metrics.getDescent()) / 2.0);
          g.drawString(control.label, x, y);
        }
        case CLOSE -> {
          g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          double cx = b.getCenterX();
          double cy = b.getCenterY();
          double r = 4.5;
          g.draw(new Line2D.Double(cx - r, cy - r, cx + r, cy + r));
          g.draw(new Line2D.Double(cx - r, cy + r, cx + r, cy - r));
        }
        case GEAR -> paintGear(g, new Point2D.Double(b.getCenterX(), b.getCenterY()));
      }
    }

    private void paintGear(Graphics2D g, Point2D center) {
      g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      double cx = center.getX();
      double cy = center.getY();
      g.draw(new Ellipse2D.Double(cx - 4.2, cy - 4.2, 8.4, 8.4));
      g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      for (int i = 0; i < 8; i++) {
        double angle = Math.PI / 4 * i;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        g.draw(new Line2D.Double(cx + cos * 6.2, cy + sin * 6.2, cx + cos * 7.6, cy + sin * 7.6));
      }
    }
  }
}
