package app.freerouting.gui.workspace.progress;

import java.awt.EventQueue;
import javax.swing.SwingUtilities;

/** Schedules session-owned presentation mutations on the Swing event dispatch thread. */
@FunctionalInterface
public interface EdtExecutor {

  /** Returns the production Swing executor. */
  public static EdtExecutor swing() {
    return SwingUtilities::invokeLater;
  }

  /** Returns whether the current thread is the EDT. */
  public static boolean isEdt() {
    return EventQueue.isDispatchThread();
  }

  /** Schedules an action on the EDT. */
  public void execute(Runnable action);
}
