package moogl;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

final class StatusIcons {

  private record Palette(Color sun, Color night, Color moon, Color outline) {}

  private static final Palette ACTIVE =
      new Palette(
          new Color(0xF5, 0xB3, 0x01),
          new Color(0x24, 0x31, 0x4F),
          new Color(0xF4, 0xF1, 0xE4),
          Color.WHITE);
  private static final Palette IDLE =
      new Palette(
          new Color(0xB5, 0xB8, 0xBE),
          new Color(0x5C, 0x61, 0x6B),
          new Color(0xE2, 0xE4, 0xE8),
          Color.WHITE);

  private StatusIcons() {}

  static BufferedImage appIcon(boolean active, int size) {
    Palette palette = active ? ACTIVE : IDLE;
    BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      boolean rays = size >= 24;
      double center = size / 2.0;
      double radius = size * (rays ? 0.34 : 0.44);
      Ellipse2D disc =
          new Ellipse2D.Double(center - radius, center - radius, 2 * radius, 2 * radius);

      if (rays) {
        g.setColor(palette.sun());
        g.setStroke(
            new BasicStroke((float) (size * 0.06), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 5; i++) {
          double angle = Math.toRadians(90 + i * 45);
          double inner = radius + size * 0.06;
          double outer = radius + size * 0.13;
          g.draw(
              new Line2D.Double(
                  center + inner * Math.cos(angle),
                  center - inner * Math.sin(angle),
                  center + outer * Math.cos(angle),
                  center - outer * Math.sin(angle)));
        }
      }

      Area sunHalf = new Area(disc);
      sunHalf.intersect(new Area(new Rectangle2D.Double(0, 0, center, size)));
      Area nightHalf = new Area(disc);
      nightHalf.subtract(sunHalf);
      g.setColor(palette.sun());
      g.fill(sunHalf);
      g.setColor(palette.night());
      g.fill(nightHalf);

      g.setColor(palette.moon());
      g.fill(crescent(center, radius));

      g.setColor(palette.outline());
      g.setStroke(new BasicStroke(Math.max(1f, size / 28f)));
      g.draw(disc);
    } finally {
      g.dispose();
    }
    return image;
  }

  private static Shape crescent(double center, double radius) {
    double moonRadius = radius * 0.42;
    double moonX = center + radius * 0.45;
    Area moon =
        new Area(
            new Ellipse2D.Double(
                moonX - moonRadius, center - moonRadius, 2 * moonRadius, 2 * moonRadius));
    double shift = moonRadius * 0.55;
    moon.subtract(
        new Area(
            new Ellipse2D.Double(
                moonX - moonRadius + shift,
                center - moonRadius - shift * 0.5,
                2 * moonRadius,
                2 * moonRadius)));
    return moon;
  }
}
