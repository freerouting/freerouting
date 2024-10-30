package app.freerouting.gui;

import app.freerouting.logger.FRLogger;
import app.freerouting.management.TextManager;
import app.freerouting.management.analytics.FRAnalytics;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public class WindowBase extends JFrame
{
  protected TextManager tm = null;
  private Instant gotFocusAt = null;

  WindowBase(int minWidth, int minHeight)
  {
    super();

    try
    {
      URL resource = this.getClass().getResource("/freerouting_icon_256x256_v3.png");
      BufferedImage image = ImageIO.read(resource);
      this.setIconImage(image);
    } catch (IOException e)
    {
      FRLogger.error("Couldn't load icon file 'freerouting_icon_256x256_v3.png'.", e);
    }
    this.setMinimumSize(new Dimension(minWidth, minHeight));

    addWindowFocusListener(new java.awt.event.WindowFocusListener()
    {
      @Override
      public void windowGainedFocus(java.awt.event.WindowEvent e)
      {
        Window window = e.getWindow();
        String className = window.getClass().getName();
        String title = "";
        if (window instanceof Frame)
        {
          title = ((Frame) window).getTitle();
        }
        if (window instanceof WindowBase)
        {
          ((WindowBase) window).gotFocusAt = Instant.now();
        }
        FRLogger.trace("Window '" + className + "' with title of '" + title + "' gained focus.");

        if (!Objects.equals(title, ""))
        {
          FRAnalytics.setAppLocation(className, title);
        }
      }

      @Override
      public void windowLostFocus(java.awt.event.WindowEvent e)
      {
        Window window = e.getWindow();
        String className = window.getClass().getName();
        String title = "";
        if (window instanceof Frame)
        {
          title = ((Frame) window).getTitle();
        }
        if (window instanceof WindowBase)
        {
          Instant gotFocusAt = ((WindowBase) window).gotFocusAt;
          if (gotFocusAt != null)
          {
            long gotFocusFor = Instant.now().getEpochSecond() - gotFocusAt.getEpochSecond();

            if (gotFocusFor > 1)
            {
              FRLogger.trace("Window '" + className + "' with title of '" + title + "' got the focus for " + gotFocusFor + " seconds.");
            }
          }
        }

        FRLogger.trace("Window '" + className + "' with title of '" + title + "' lost focus.");
      }
    });
  }

  /**
   * Sets the language of the window and updates texts on it if needed.
   */
  public void setLanguage(Locale locale)
  {
    if (this.tm != null)
    {
      this.tm.setLocale(locale);
      this.updateTexts();
    }
    else
    {
      this.tm = new TextManager(this.getClass(), locale);
    }
  }

  /**
   * Updates the language-specific texts in the window. It must be overridden in the inherited class.
   */
  public void updateTexts()
  {
    // This method must be overridden in the inherited class if there is at least one language-specific text in the window.
  }
}