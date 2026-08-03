package app.freerouting.gui;

import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import app.freerouting.management.analytics.FRAnalytics;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class WindowBase extends JFrame {

  protected TextManager tm;
  private Instant gotFocusAt;
  private GraphicsConfiguration lastGraphicsConfig;

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

    // Track the initial graphics configuration for per-monitor DPI change detection
    this.lastGraphicsConfig = getGraphicsConfiguration();

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentMoved(ComponentEvent e) {
        checkGraphicsConfigurationChanged();
      }

      @Override
      public void componentResized(ComponentEvent e) {
        checkGraphicsConfigurationChanged();
      }
    });

    addWindowFocusListener(new WindowFocusListener() {
      @Override
      public void windowGainedFocus(WindowEvent e) {
        Window window = e.getWindow();
        String className = window.getClass().getName();
        String title = "";
        if (window instanceof Frame frame) {
          title = frame.getTitle();
        }
        if (window instanceof WindowBase base) {
          base.gotFocusAt = Instant.now();
        }
        FRLogger.trace("Window '" + className + "' with title of '" + title + "' gained focus.");

        if (!Objects.equals(title, "")) {
          FRAnalytics.setAppLocation(className, title);
        }
      }

      @Override
      public void windowLostFocus(WindowEvent e) {
        Window window = e.getWindow();
        String className = window.getClass().getName();
        String title = "";
        if (window instanceof Frame frame) {
          title = frame.getTitle();
        }
        if (window instanceof WindowBase base) {
          Instant gotFocusAt = base.gotFocusAt;
          if (gotFocusAt != null) {
            long gotFocusFor = Instant.now().getEpochSecond() - gotFocusAt.getEpochSecond();

            if (gotFocusFor > 1) {
              FRLogger.trace("Window '" + className + "' with title of '" + title + "' got the focus for " + gotFocusFor + " seconds.");
            }
          }
        }

        FRLogger.trace("Window '" + className + "' with title of '" + title + "' lost focus.");
      }
    });
  }

  /**
   * Checks whether the window has moved to a different display (GraphicsConfiguration).
   * If so, triggers a re-layout so that font metrics and component sizes are
   * recomputed for the new display's DPI scaling.
   */
  private void checkGraphicsConfigurationChanged() {
    GraphicsConfiguration current = getGraphicsConfiguration();
    if (current != lastGraphicsConfig) {
      lastGraphicsConfig = current;
      FRLogger.trace("Window '" + this.getClass().getName() + "' moved to a different display; re-laying out for new DPI scaling.");
      SwingUtilities.invokeLater(() -> onGraphicsConfigurationChanged());
    }
  }

  /**
   * Called when the window has been moved to a different display with a different
   * GraphicsConfiguration (e.g. different DPI scaling). Subclasses may override
   * to perform additional re-layout work such as pack() or refresh().
   * <p>
   * The default implementation revalidates and repaints the content pane so that
   * component sizes and font metrics are recomputed for the new display.
   */
  protected void onGraphicsConfigurationChanged() {
    if (getContentPane() != null) {
      getContentPane().revalidate();
      getContentPane().repaint();
    }
  }

  /**
   * Sets the language of the window and updates texts on it if needed.
   */
  public void setLanguage(Locale locale) {
    if (this.tm != null) {
      this.tm.setLocale(locale);
      this.updateTexts();
    } else {
      this.tm = new TextManager(this.getClass(), locale);
    }
  }

  /**
   * Updates the language-specific texts in the window. It must be overridden in the inherited class.
   */
  public void updateTexts() {
    // This method must be overridden in the inherited class if there is at least one language-specific text in the window.
  }
}