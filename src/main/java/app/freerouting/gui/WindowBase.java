package app.freerouting.gui;

import app.freerouting.logger.FRLogger;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import javax.imageio.ImageIO;

public class WindowBase extends javax.swing.JFrame {
  WindowBase(int minWidth, int minHeight) {
    super();
    try {
      URL resource = this.getClass().getResource("/freerouting_icon_256x256_v2.png");
      BufferedImage image = ImageIO.read(resource);
      this.setIconImage(image);
    } catch (IOException e) {
      FRLogger.error("Couldn't load icon file 'freerouting_icon_256x256_v2.png'.", e);
    }
    this.setMinimumSize(new java.awt.Dimension(minWidth, minHeight));
  }
}
