package app.freerouting.gui.workspace.controllers;

import app.freerouting.geometry.planar.Point;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.logger.FRLogger;
import app.freerouting.logger.LogEntries;
import app.freerouting.logger.LogEntry;
import app.freerouting.logger.LogEntryType;
import app.freerouting.logger.TraceEvent;
import app.freerouting.logger.TraceEventListener;
import javax.swing.SwingUtilities;

/**
 * Bridges logger and trace diagnostics into the GUI session.
 *
 * <p>The bridge owns listener registration and disposal, while the manager remains the public
 * source of impacted points for presentation rendering.
 */
public final class GuiBoardEventBridge {

  private final GuiBoardManager manager;
  private final LogEntries.LogEntryAddedListener logEntryAddedListener;
  private final TraceEventListener traceEventListener;
  private Point[] impactedPoints;

  public GuiBoardEventBridge(GuiBoardManager manager) {
    this.manager = manager;
    this.logEntryAddedListener = this::logEntryAdded;
    this.traceEventListener = this::handleTraceEvent;
    FRLogger.getLogEntries().addLogEntryAddedListener(logEntryAddedListener);
    FRLogger.addTraceEventListener(traceEventListener);
  }

  public Point[] getImpactedPoints() {
    return impactedPoints;
  }

  public void dispose() {
    FRLogger.getLogEntries().removeLogEntryAddedListener(logEntryAddedListener);
    FRLogger.removeTraceEventListener(traceEventListener);
  }

  private void logEntryAdded(LogEntry logEntry) {
    if (logEntry.getType() == LogEntryType.Error || logEntry.getType() == LogEntryType.Warning) {
      LogEntries entries = FRLogger.getLogEntries();
      manager.getSessionPort().publishLogCounts(entries.getErrorCount(), entries.getWarningCount());
    }
  }

  private void handleTraceEvent(TraceEvent event) {
    if (event == null) {
      return;
    }
    SwingUtilities.invokeLater(
        () -> {
          manager.screenMessages.setTraceMessage(
              event.getOperation(), event.getMessage(), event.getImpactedItems());
          impactedPoints = event.getImpactedPoints();
          manager.getPanel().repaint();
        });
  }
}
