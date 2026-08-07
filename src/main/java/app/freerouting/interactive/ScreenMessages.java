package app.freerouting.interactive;

import app.freerouting.board.Unit;
import app.freerouting.core.RouterCounters;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.text.NumberFormat;
import java.util.Locale;
import javax.swing.JLabel;

/** Generate language-specific texts for fields at the bottom of the screen, below the PCB frame. */
public class ScreenMessages {

  private static final String empty_string = "            ";
  final JLabel errorLabel;
  final JLabel warningLabel;
  private final String activeLayerString;
  private final String targetLayerString;

  /** The number format for displaying the trace length */
  private final NumberFormat numberFormat;

  private final JLabel addField;
  private final JLabel statusField;
  private final JLabel layerField;
  private final JLabel scoreField;
  private final JLabel mousePosition;
  private final JLabel unitLabel;
  private final TextManager tm;
  private String prevTargetLayerName = empty_string;
  private boolean writeProtected;

  /** Creates a new instance of ScreenMessages */
  public ScreenMessages(
      JLabel errorLabel,
      JLabel warningLabel,
      JLabel p_status_field,
      JLabel p_add_field,
      JLabel p_layer_field,
      JLabel p_score_field,
      JLabel p_mouse_position,
      JLabel p_unit_label,
      Locale p_locale) {

    tm = new TextManager(this.getClass(), p_locale);
    activeLayerString = tm.getText("currentLayer") + " ";
    targetLayerString = tm.getText("targetLayer") + " ";
    this.errorLabel = errorLabel;
    this.warningLabel = warningLabel;
    statusField = p_status_field;
    addField = p_add_field;
    layerField = p_layer_field;
    scoreField = p_score_field;
    mousePosition = p_mouse_position;
    unitLabel = p_unit_label;
    addField.setText(empty_string);

    this.numberFormat = NumberFormat.getInstance(p_locale);
    this.numberFormat.setMinimumFractionDigits(2);
    this.numberFormat.setMaximumFractionDigits(2);
  }

  public void set_error_and_warning_count(int errorsCount, int warningCount) {
    errorLabel.setText(Integer.toString(errorsCount));
    warningLabel.setText(Integer.toString(warningCount));
  }

  /** Sets the message in the status field. */
  public void set_status_message(String p_message) {
    if (!this.writeProtected) {
      statusField.setText(p_message);
    }
  }

  /** Displays the latest traced operation in the footer. */
  public void set_trace_message(String operation, String message, String impactedItems) {
    if (this.writeProtected) {
      return;
    }
    String statusText =
        operation == null || operation.isEmpty() ? message : operation + ": " + message;
    statusField.setText(statusText == null ? empty_string : statusText);
    String impactedText =
        impactedItems == null || impactedItems.isEmpty() ? empty_string : impactedItems;
    addField.setText(impactedText);
  }

  /** Sets the displayed layer number on the screen. */
  public void set_layer(String p_layer_name) {
    if (!this.writeProtected) {
      layerField.setText(activeLayerString + p_layer_name);
    }
  }

  public void set_interactive_autoroute_info(int p_found, int p_not_found, int p_items_to_go) {
    int found = p_found;
    int failed = p_not_found;
    int itemsToGo = p_items_to_go;

    addField.setText(tm.getText("interactive_autoroute_add", String.valueOf(itemsToGo)));
    layerField.setText(
        tm.getText("interactive_autoroute_layer", String.valueOf(found), String.valueOf(failed)));
  }

  public void set_batch_autoroute_info(RouterCounters routerCounters) {
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

  public void set_post_route_info(int p_via_count, double p_trace_length, Unit unit) {
    int viaCount = p_via_count;
    addField.setText(tm.getText("post_route_add", String.valueOf(viaCount)));
    layerField.setText(
        tm.getText("post_route_layer", this.numberFormat.format(p_trace_length), unit.toString()));
  }

  /** Sets the displayed layer of the nearest target item in interactive routing. */
  public void set_target_layer(String p_layer_name) {
    if (!(p_layer_name.equals(prevTargetLayerName) || this.writeProtected)) {
      addField.setText(targetLayerString + p_layer_name);
      prevTargetLayerName = p_layer_name;
    }
  }

  public void set_mouse_position(FloatPoint p_pos) {
    if (p_pos == null || this.mousePosition == null || this.writeProtected) {
      return;
    }
    this.mousePosition.setText(p_pos.to_string(this.tm.getLocale(), 2, 10));
  }

  public void set_unit_label(String p_unit) {
    this.unitLabel.setText(p_unit);
  }

  /**
   * Clears the additional field, which is among others used to display the layer of the nearest
   * target item.
   */
  public void clear_add_field() {
    if (!this.writeProtected) {
      addField.setText(empty_string);
      prevTargetLayerName = empty_string;
    }
  }

  /** Clears the status field and the additional field. */
  public void clear() {
    if (!this.writeProtected) {
      statusField.setText(empty_string);
      clear_add_field();
      layerField.setText(empty_string);
      scoreField.setText(empty_string);
    }
  }

  /** As long as writeProtected is set to true, the set functions in this class will do nothing. */
  public void set_write_protected(boolean p_value) {
    writeProtected = p_value;
  }

  public void set_board_score(float score, int unrouted_count, int violationCount) {
    scoreField.setText(
        tm.getText(
            "score",
            FRLogger.defaultFloatFormat.format(score),
            String.valueOf(unrouted_count),
            String.valueOf(violationCount)));
  }
}
