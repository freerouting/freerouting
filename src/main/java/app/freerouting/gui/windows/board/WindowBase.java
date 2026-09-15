package app.freerouting.gui.windows.board;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
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
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Provides common behavior for Freerouting windows. */
public class WindowBase extends JFrame {

  public TextManager tm;
  private Instant gotFocusAt;
  private GraphicsConfiguration lastGraphicsConfig;

  public WindowBase(int minWidth, int minHeight) {
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

    addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentMoved(ComponentEvent e) {
            checkGraphicsConfigurationChanged();
          }

          @Override
          public void componentResized(ComponentEvent e) {
            checkGraphicsConfigurationChanged();
          }
        });

    addWindowFocusListener(
        new WindowFocusListener() {
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
            FRLogger.trace(
                "Window '" + className + "' with title of '" + title + "' gained focus.");

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
                  FRLogger.trace(
                      "Window '"
                          + className
                          + "' with title of '"
                          + title
                          + "' got the focus for "
                          + gotFocusFor
                          + " seconds.");
                }
              }
            }

            FRLogger.trace("Window '" + className + "' with title of '" + title + "' lost focus.");
          }
        });
  }

  /**
   * Checks whether the window has moved to a different display (GraphicsConfiguration). If so,
   * triggers a re-layout so that font metrics and component sizes are recomputed for the new
   * display's DPI scaling.
   */
  private void checkGraphicsConfigurationChanged() {
    GraphicsConfiguration current = getGraphicsConfiguration();
    if (current != lastGraphicsConfig) {
      lastGraphicsConfig = current;
      FRLogger.trace(
          "Window '"
              + this.getClass().getName()
              + "' moved to a different display; re-laying out for new DPI scaling.");
      SwingUtilities.invokeLater(this::onGraphicsConfigurationChanged);
    }
  }

  /**
   * Called when the window has been moved to a different display with a different
   * GraphicsConfiguration (e.g. different DPI scaling). Subclasses may override to perform
   * additional re-layout work such as pack() or refresh().
   *
   * <p>The default implementation revalidates and repaints the content pane so that component sizes
   * and font metrics are recomputed for the new display.
   */
  protected void onGraphicsConfigurationChanged() {
    if (getContentPane() != null) {
      getContentPane().revalidate();
      getContentPane().repaint();
    }
  }

  /** Sets the language of the window and updates texts on it if needed. */
  public void setLanguage(Locale locale) {
    if (this.tm != null) {
      this.tm.setLocale(locale);
      this.updateTexts();
    } else {
      this.tm = new TextManager(this.getClass(), locale);
    }
  }

  /**
   * Updates the language-specific texts in the window. It must be overridden in the inherited
   * class.
   */
  public void updateTexts() {
    // This method must be overridden in the inherited class if there is at least one
    // language-specific text in the window.
  }

  /**
   * Wraps the given content component in a borderless {@link JScrollPane} with smooth mouse-wheel
   * scrolling and adaptive scrollbars.
   *
   * @param content the content component to make scrollable
   * @return a configured {@link JScrollPane}
   */
  public static JScrollPane createScrollableContainer(JComponent content) {
    JScrollPane scrollPane = new JScrollPane(content);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollPane.getVerticalScrollBar().setUnitIncrement(16);
    scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
    if (content.getBackground() != null) {
      scrollPane.getViewport().setBackground(content.getBackground());
    }
    return scrollPane;
  }

  /**
   * Calculates clamped window bounds ensuring the height does not exceed 85% of the usable screen
   * work area, adjusts width if vertical scrolling was engaged, and guarantees the window stays
   * within screen bounds without pushing the title bar off-screen.
   *
   * @param currentBounds the current bounds of the window
   * @param screenWorkArea the usable screen work area (excluding taskbars/docks)
   * @param scrollBarWidth the width of the vertical scrollbar to compensate, or <= 0 for default
   * @return the clamped, safely positioned {@link Rectangle} bounds
   */
  public static Rectangle calculateClampedBounds(
      Rectangle currentBounds, Rectangle screenWorkArea, int scrollBarWidth) {
    int width = currentBounds.width;
    int height = currentBounds.height;
    int maxHeight = (int) (screenWorkArea.height * 0.85);

    if (height > maxHeight) {
      width += (scrollBarWidth > 0 ? scrollBarWidth : 18);
      height = maxHeight;
    }

    int x = currentBounds.x;
    int y = currentBounds.y;

    // Safety clamp vertical position
    y = Math.max(screenWorkArea.y, y);
    if (y + height > screenWorkArea.y + screenWorkArea.height) {
      y = Math.max(screenWorkArea.y, screenWorkArea.y + screenWorkArea.height - height);
    }

    // Safety clamp horizontal position
    x = Math.max(screenWorkArea.x, x);
    if (x + width > screenWorkArea.x + screenWorkArea.width) {
      x = Math.max(screenWorkArea.x, screenWorkArea.x + screenWorkArea.width - width);
    }

    return new Rectangle(x, y, width, height);
  }

  /**
   * Clamps a window's packed height to 85% of the usable screen work area (accounting for OS
   * taskbars and dock insets), adjusts width if vertical scrolling was engaged, and positions the
   * window safely on screen without letting the title bar render off-screen.
   *
   * @param window the window or dialog to clamp and position
   * @param parent the parent component to center relative to, or {@code null} to center on screen
   */
  public static void clampWindowHeight(Window window, Component parent) {
    if (GraphicsEnvironment.isHeadless() || window == null) {
      return;
    }

    // Pack to ensure layout and native peer insets are accurately settled if not yet sized
    if (window.getWidth() <= 0 || window.getHeight() <= 0) {
      window.pack();
    }

    if (parent != null && parent.isShowing()) {
      window.setLocationRelativeTo(parent);
    } else {
      window.setLocationRelativeTo(null);
    }

    GraphicsConfiguration gc =
        (parent != null && parent.getGraphicsConfiguration() != null)
            ? parent.getGraphicsConfiguration()
            : window.getGraphicsConfiguration();
    Rectangle maxBounds;
    if (gc != null) {
      Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
      Rectangle bounds = gc.getBounds();
      maxBounds =
          new Rectangle(
              bounds.x + insets.left,
              bounds.y + insets.top,
              bounds.width - insets.left - insets.right,
              bounds.height - insets.top - insets.bottom);
    } else {
      maxBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    }

    int scrollBarWidth = UIManager.getInt("ScrollBar.width");
    Rectangle clamped = calculateClampedBounds(window.getBounds(), maxBounds, scrollBarWidth);
    window.setBounds(clamped);
  }
}
