package moogl;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

final class StatusIcons {

  static final Color ACTIVE = new Color(0x2E, 0xA0, 0x43);
  static final Color IDLE = new Color(0x8A, 0x8F, 0x98);

  private StatusIcons() {}

  static Image dot(boolean active, int size) {
    BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int inset = Math.max(1, size / 8);
      int diameter = size - 2 * inset;
      g.setColor(active ? ACTIVE : IDLE);
      g.fillOval(inset, inset, diameter, diameter);
      g.setColor(Color.WHITE);
      g.setStroke(new BasicStroke(Math.max(1f, size / 16f)));
      g.drawOval(inset, inset, diameter, diameter);
    } finally {
      g.dispose();
    }
    return image;
  }
}
