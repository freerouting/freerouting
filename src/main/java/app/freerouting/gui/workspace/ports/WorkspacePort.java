package app.freerouting.gui.workspace.ports;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.gui.rendering.GraphicsContext;
import app.freerouting.gui.workspace.progress.RoutingSummaryData;
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
  public RoutingBoard routingBoard();

  /** Returns the current rendering context for the EDT paint callback. */
  public GraphicsContext graphicsContext();

  /** Returns the current display locale. */
  public Locale locale();

  /** Returns the active display unit. */
  public Unit displayUnit();

  /** Requests an EDT repaint from a non-worker callback. */
  public void repaint();

  /** Publishes the current log counters on the EDT. */
  public void publishLogCounts(int errorsCount, int warningsCount);

  /** Shows the profile dialog through the EDT adapter. */
  public void showProfileDialog();

  /** Shows the routing summary dialog through the EDT adapter. */
  public void showRoutingSummary(RoutingSummaryData summaryData);
}
