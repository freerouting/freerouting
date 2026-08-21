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
    Frame activeFrame = findActiveFrame();
    if (activeFrame != null) {
      JOptionPane.showMessageDialog(activeFrame, e.toString(), "Exception Occurred", OK_OPTION);
    }
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
