package app.freerouting.gui;

import static javax.swing.JOptionPane.OK_OPTION;

import app.freerouting.Freerouting;
import app.freerouting.analytics.FRAnalytics;
import app.freerouting.logger.FRLogger;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

/** Logs uncaught exceptions and shows them in the GUI when appropriate. */
public class DefaultExceptionHandler implements Thread.UncaughtExceptionHandler {

  /**
   * Handles an exception outside the normal application flow.
   *
   * @param e the exception to report
   */
  public static void handleException(Throwable e) {
    // Here you should have a more robust, permanent record of problems
    FRLogger.error(e.getLocalizedMessage(), e);
    FRAnalytics.exceptionThrown(e.getLocalizedMessage(), e);
    if (shouldShowDialog()) {
      JOptionPane.showMessageDialog(
          findActiveFrame(), e.toString(), "Exception Occurred", OK_OPTION);
    }
  }

  private static boolean shouldShowDialog() {
    if (GraphicsEnvironment.isHeadless()) {
      return false;
    }
    return !(Freerouting.globalSettings != null
        && Boolean.FALSE.equals(Freerouting.globalSettings.guiSettings.isEnabled));
  }

  private static Frame findActiveFrame() {
    Frame[] frames = JFrame.getFrames();
    for (Frame frame : frames) {
      if (frame.isVisible()) {
        return frame;
      }
    }
    return null;
  }

  @Override
  public void uncaughtException(Thread t, Throwable e) {
    handleException(e);
  }
}
