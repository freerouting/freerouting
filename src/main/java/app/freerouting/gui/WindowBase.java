package app.freerouting.gui;

import app.freerouting.logger.FRLogger;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import javax.imageio.ImageIO;
import javax.swing.JFrame;

public class WindowBase extends JFrame {
  WindowBase(int minWidth, int minHeight) {
    super();
    try {
      URL resource = this.getClass().getResource("/freerouting_icon_256x256_v3.png");
      BufferedImage image = ImageIO.read(resource);
      this.setIconImage(image);
    } catch (IOException e) {
      FRLogger.error("Couldn't load icon file 'freerouting_icon_256x256_v3.png'.", e);
    }
    this.setMinimumSize(new Dimension(minWidth, minHeight));
  }
}
