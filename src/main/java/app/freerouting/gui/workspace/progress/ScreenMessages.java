package app.freerouting.gui.workspace.progress;

import app.freerouting.board.model.structure.Unit;
import app.freerouting.core.RouterCounters;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.awt.EventQueue;
import java.text.NumberFormat;
import java.util.Locale;
import javax.swing.JLabel;

/** Generate language-specific texts for fields at the bottom of the screen, below the PCB frame. */
public class ScreenMessages {

  private static final String EMPTY_STRING = "            ";
  final JLabel errorLabel;
  final JLabel warningLabel;
  private final String activeLayerString;
  private final String targetLayerString;

  /** The number format for displaying the trace length. */
  private final NumberFormat numberFormat;

  private final JLabel addField;
  private final JLabel statusField;
  private final JLabel layerField;
  private final JLabel scoreField;
  private final JLabel mousePosition;
  private final JLabel unitLabel;
  private final TextManager tm;
  private String prevTargetLayerName = EMPTY_STRING;
  private boolean writeProtected;

  /** Creates a new instance of ScreenMessages. */
  public ScreenMessages(
      JLabel errorLabel,
      JLabel warningLabel,
      JLabel statusField,
      JLabel addField,
      JLabel layerField,
      JLabel scoreField,
      JLabel mousePosition,
      JLabel unitLabel,
      Locale locale) {

    tm = new TextManager(this.getClass(), locale);
    activeLayerString = tm.getText("currentLayer") + " ";
    targetLayerString = tm.getText("targetLayer") + " ";
    this.errorLabel = errorLabel;
    this.warningLabel = warningLabel;
    this.statusField = statusField;
    this.addField = addField;
    this.layerField = layerField;
    this.scoreField = scoreField;
    this.mousePosition = mousePosition;
    this.unitLabel = unitLabel;
    this.addField.setText(EMPTY_STRING);

    this.numberFormat = NumberFormat.getInstance(locale);
    this.numberFormat.setMinimumFractionDigits(2);
    this.numberFormat.setMaximumFractionDigits(2);
  }

  private static void requireEdt() {
    if (!EventQueue.isDispatchThread()) {
      throw new IllegalStateException("ScreenMessages must only be mutated on the EDT");
    }
  }

  /** Updates the displayed error and warning counts. */
  public void setErrorAndWarningCount(int errorsCount, int warningCount) {
    requireEdt();
    errorLabel.setText(Integer.toString(errorsCount));
    warningLabel.setText(Integer.toString(warningCount));
  }

  /** Sets the message in the status field. */
  public void setStatusMessage(String message) {
    requireEdt();
    if (!this.writeProtected) {
      statusField.setText(message);
    }
  }

  /** Displays the latest traced operation in the footer. */
  public void setTraceMessage(String operation, String message, String impactedItems) {
    requireEdt();
    if (this.writeProtected) {
      return;
    }
    String statusText =
        operation == null || operation.isEmpty() ? message : operation + ": " + message;
    statusField.setText(statusText == null ? EMPTY_STRING : statusText);
    String impactedText =
        impactedItems == null || impactedItems.isEmpty() ? EMPTY_STRING : impactedItems;
    addField.setText(impactedText);
  }

  /** Sets the displayed layer number on the screen. */
  public void setLayer(String layerName) {
    requireEdt();
    if (!this.writeProtected) {
      layerField.setText(activeLayerString + layerName);
    }
  }

  /** Updates the footer with interactive autorouting progress. */
  public void setInteractiveAutorouteInfo(int found, int failed, int itemsToGo) {
    requireEdt();
    addField.setText(tm.getText("interactive_autoroute_add", String.valueOf(itemsToGo)));
    layerField.setText(
        tm.getText("interactive_autoroute_layer", String.valueOf(found), String.valueOf(failed)));
  }

  /** Updates the footer with batch autorouting progress. */
  public void setBatchAutorouteInfo(RouterCounters routerCounters) {
    requireEdt();
    int itemsToGo = routerCounters.queuedToBeRoutedCount;
    int routed = routerCounters.routedCount;
    int failed = routerCounters.failedToBeRoutedCount;
    if ("fanout".equals(routerCounters.phase)) {
      int extraVias =
          routerCounters.fanoutExtraViasCount == null ? 0 : routerCounters.fanoutExtraViasCount;
      addField.setText(
          tm.getText("batch_autoroute_add", String.valueOf(itemsToGo), String.valueOf(routed)));
      layerField.setText(
          tm.getText("batch_fanout_layer", String.valueOf(failed), String.valueOf(extraVias)));
      return;
    }
    int ripped = routerCounters.rippedCount;
    addField.setText(
        tm.getText("batch_autoroute_add", String.valueOf(itemsToGo), String.valueOf(routed)));
    layerField.setText(
        tm.getText("batch_autoroute_layer", String.valueOf(ripped), String.valueOf(failed)));
  }

  /** Updates the footer with post-route statistics. */
  public void setPostRouteInfo(int viaCount, double traceLength, Unit unit) {
    requireEdt();
    addField.setText(tm.getText("post_route_add", String.valueOf(viaCount)));
    layerField.setText(
        tm.getText("post_route_layer", this.numberFormat.format(traceLength), unit.toString()));
  }

  /** Sets the displayed layer of the nearest target item in interactive routing. */
  public void setTargetLayer(String layerName) {
    requireEdt();
    if (!(layerName.equals(prevTargetLayerName) || this.writeProtected)) {
      addField.setText(targetLayerString + layerName);
      prevTargetLayerName = layerName;
    }
  }

  /** Updates the displayed mouse position. */
  public void setMousePosition(FloatPoint position) {
    requireEdt();
    if (position == null || this.mousePosition == null || this.writeProtected) {
      return;
    }
    this.mousePosition.setText(position.toString(this.tm.getLocale(), 2, 10));
  }

  /** Updates the displayed board unit label. */
  public void setUnitLabel(String unit) {
    requireEdt();
    this.unitLabel.setText(unit);
  }

  /**
   * Clears the additional field, which is among others used to display the layer of the nearest
   * target item.
   */
  public void clearAddField() {
    requireEdt();
    if (!this.writeProtected) {
      addField.setText(EMPTY_STRING);
      prevTargetLayerName = EMPTY_STRING;
    }
  }

  /** Clears the status field and the additional field. */
  public void clear() {
    requireEdt();
    if (!this.writeProtected) {
      statusField.setText(EMPTY_STRING);
      clearAddField();
      layerField.setText(EMPTY_STRING);
      scoreField.setText(EMPTY_STRING);
    }
  }

  /** As long as writeProtected is set to true, the set functions in this class will do nothing. */
  public void setWriteProtected(boolean value) {
    requireEdt();
    writeProtected = value;
  }

  /** Updates the displayed board score and routing counters. */
  public void setBoardScore(float score, int unroutedCount, int violationCount) {
    requireEdt();
    scoreField.setText(
        tm.getText(
            "score",
            FRLogger.defaultFloatFormat.format(score),
            String.valueOf(unroutedCount),
            String.valueOf(violationCount)));
  }
}
