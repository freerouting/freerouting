package app.freerouting.gui;

import static javax.swing.JOptionPane.OK_OPTION;

import app.freerouting.logger.FRLogger;
import app.freerouting.management.FRAnalytics;
import java.awt.Frame;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

public class DefaultExceptionHandler implements Thread.UncaughtExceptionHandler {
  public static void handleException(Throwable e) {
    // Here you should have a more robust, permanent record of problems
    FRLogger.error(e.getLocalizedMessage(), e);
    FRAnalytics.exceptionThrown(e.getLocalizedMessage(), e);
    JOptionPane.showMessageDialog(findActiveFrame(), e.toString(), "Exception Occurred", OK_OPTION);
  }

  private static Frame findActiveFrame() {
    Frame[] frames = JFrame.getFrames();
    for (Frame frame : frames) {
      if (frame.isVisible()) return frame;
    }
    return null;
  }

  @Override
  public void uncaughtException(Thread t, Throwable e) {
    handleException(e);
  }
}
