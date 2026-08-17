package app.freerouting.gui.workspace;

import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Unit;
import app.freerouting.gui.rendering.GraphicsContext;
import java.util.Locale;

/**
 * Narrow session-owned port used by background routing code.
 *
 * <p>Implementations own all presentation scheduling and reject events from stale runs or loads.
 */
public interface WorkspacePort
    extends BoardLoadPort,
        BoardReplacementPort,
        RouteControlPort,
        RouteProgressPort,
        SettingsSnapshotPort {

  /** Returns domain state needed by the routing worker and renderer. */
  RoutingBoard routingBoard();

  /** Returns the current rendering context for the EDT paint callback. */
  GraphicsContext graphicsContext();

  /** Returns the current display locale. */
  Locale locale();

  /** Returns the active display unit. */
  Unit displayUnit();

  /** Requests an EDT repaint from a non-worker callback. */
  void repaint();

  /** Publishes the current log counters on the EDT. */
  void publishLogCounts(int errorsCount, int warningsCount);

  /** Shows the profile dialog through the EDT adapter. */
  void showProfileDialog();
}
