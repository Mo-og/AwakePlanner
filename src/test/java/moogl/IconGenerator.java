package moogl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public final class IconGenerator {

  private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

  private IconGenerator() {}

  public static void main(String[] args) throws IOException {
    System.setProperty("java.awt.headless", "true");
    Path target = Path.of(args.length > 0 ? args[0] : "src/main/resources/icon.ico");
    Files.write(target, ico());
    System.out.println("Wrote " + target.toAbsolutePath());
  }

  static byte[] ico() throws IOException {
    List<byte[]> images = new ArrayList<>();
    for (int size : SIZES) {
      ByteArrayOutputStream png = new ByteArrayOutputStream();
      ImageIO.write(StatusIcons.appIcon(true, size), "png", png);
      images.add(png.toByteArray());
    }
    int headerSize = 6 + 16 * SIZES.length;
    int total = headerSize + images.stream().mapToInt(image -> image.length).sum();
    ByteBuffer buffer = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putShort((short) 0).putShort((short) 1).putShort((short) SIZES.length);
    int offset = headerSize;
    for (int i = 0; i < SIZES.length; i++) {
      int dimension = SIZES[i] >= 256 ? 0 : SIZES[i];
      buffer.put((byte) dimension).put((byte) dimension).put((byte) 0).put((byte) 0);
      buffer.putShort((short) 1).putShort((short) 32);
      buffer.putInt(images.get(i).length).putInt(offset);
      offset += images.get(i).length;
    }
    images.forEach(buffer::put);
    return buffer.array();
  }
}
