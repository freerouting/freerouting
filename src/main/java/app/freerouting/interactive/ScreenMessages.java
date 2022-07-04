package app.freerouting.interactive;

import javax.swing.JLabel;

/** Text fields to display messages on the screen. */
public class ScreenMessages {

  private static final String empty_string = "            ";
  private final java.util.ResourceBundle resources;
  private final java.util.Locale locale;
  private final String active_layer_string;
  private final String target_layer_string;
  /** The number format for displaying the trace lengtht */
  private final java.text.NumberFormat number_format;
  private final JLabel add_field;
  private final JLabel status_field;
  private final JLabel layer_field;
  private final JLabel mouse_position;
  private String prev_target_layer_name = empty_string;
  private boolean write_protected = false;

  /** Creates a new instance of ScreenMessageFields */
  public ScreenMessages(
      JLabel p_status_field,
      JLabel p_add_field,
      JLabel p_layer_field,
      JLabel p_mouse_position,
      java.util.Locale p_locale) {
    resources =
        java.util.ResourceBundle.getBundle("app.freerouting.interactive.ScreenMessages", p_locale);
    locale = p_locale;
    active_layer_string = resources.getString("current_layer") + " ";
    target_layer_string = resources.getString("target_layer") + " ";
    status_field = p_status_field;
    add_field = p_add_field;
    layer_field = p_layer_field;
    mouse_position = p_mouse_position;
    add_field.setText(empty_string);

    this.number_format = java.text.NumberFormat.getInstance(p_locale);
    this.number_format.setMaximumFractionDigits(4);
  }

  /** Sets the message in the status field. */
  public void set_status_message(String p_message) {
    if (!this.write_protected) {
      status_field.setText(p_message);
    }
  }

  /** Sets the displayed layer number on the screen. */
  public void set_layer(String p_layer_name) {
    if (!this.write_protected) {
      layer_field.setText(active_layer_string + p_layer_name);
    }
  }

  public void set_interactive_autoroute_info(int p_found, int p_not_found, int p_items_to_go) {
    Integer found = p_found;
    Integer failed = p_not_found;
    Integer items_to_go = p_items_to_go;
    add_field.setText(resources.getString("to_route") + " " + items_to_go);
    layer_field.setText(
        resources.getString("found")
            + " "
            + found
            + ", "
            + resources.getString("failed")
            + " "
            + failed);
  }

  public void set_batch_autoroute_info(
      int p_items_to_go, int p_routed, int p_ripped, int p_failed) {
    Integer ripped = p_ripped;
    Integer routed = p_routed;
    Integer items_to_go = p_items_to_go;
    Integer failed = p_failed;
    add_field.setText(
        resources.getString("to_route")
            + " "
            + items_to_go
            + ", "
            + resources.getString("routed")
            + " "
            + routed
            + ", ");
    layer_field.setText(
        resources.getString("ripped")
            + " "
            + ripped
            + ", "
            + resources.getString("failed")
            + " "
            + failed);
  }

  public void set_batch_fanout_info(int p_pass_no, int p_components_to_go) {
    Integer components_to_go = p_components_to_go;
    Integer pass_no = Integer.valueOf(p_pass_no);
    add_field.setText(resources.getString("fanout_pass") + " " + pass_no + ": ");
    layer_field.setText(
        resources.getString("still")
            + " "
            + components_to_go
            + " "
            + resources.getString("components"));
  }

  public void set_post_route_info(int p_via_count, double p_trace_length) {
    Integer via_count = p_via_count;
    add_field.setText(resources.getString("via_count") + " " + via_count);
    layer_field.setText(
        resources.getString("trace_length") + " " + this.number_format.format(p_trace_length));
  }

  /** Sets the displayed layer of the nearest target item in interactive routing. */
  public void set_target_layer(String p_layer_name) {
    if (!(p_layer_name.equals(prev_target_layer_name) || this.write_protected)) {
      add_field.setText(target_layer_string + p_layer_name);
      prev_target_layer_name = p_layer_name;
    }
  }

  public void set_mouse_position(app.freerouting.geometry.planar.FloatPoint p_pos) {
    if (p_pos == null || this.mouse_position == null || this.write_protected) {
      return;
    }
    this.mouse_position.setText(p_pos.to_string(this.locale));
  }

  /**
   * Clears the additional field, which is among others used to display the layer of the nearest
   * target item.
   */
  public void clear_add_field() {
    if (!this.write_protected) {
      add_field.setText(empty_string);
      prev_target_layer_name = empty_string;
    }
  }

  /** Clears the status field and the additional field. */
  public void clear() {
    if (!this.write_protected) {
      status_field.setText(empty_string);
      clear_add_field();
      layer_field.setText(empty_string);
    }
  }

  /** As long as write_protected is set to true, the set functions in this class will do nothing. */
  public void set_write_protected(boolean p_value) {
    write_protected = p_value;
  }
}
