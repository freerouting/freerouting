package app.freerouting.gui.session;

import java.awt.EventQueue;
import javax.swing.SwingUtilities;

/** Schedules session-owned presentation mutations on the Swing event dispatch thread. */
@FunctionalInterface
public interface EdtExecutor {

  /** Schedules an action on the EDT. */
  void execute(Runnable action);

  /** Returns the production Swing executor. */
  static EdtExecutor swing() {
    return SwingUtilities::invokeLater;
  }

  /** Returns whether the current thread is the EDT. */
  static boolean isEdt() {
    return EventQueue.isDispatchThread();
  }
}
