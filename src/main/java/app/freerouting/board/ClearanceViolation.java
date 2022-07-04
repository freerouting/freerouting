package app.freerouting.board;

import app.freerouting.geometry.planar.ConvexShape;

/** Information of a clearance violation between 2 items. */
public class ClearanceViolation implements ObjectInfoPanel.Printable {

  /** The first item of the clearance violation */
  public final Item first_item;
  /** The second item of the clearance violation */
  public final Item second_item;
  /** The shape of the clearance violation */
  public final ConvexShape shape;
  /** The layer of the clearance violation */
  public final int layer;
  /** Creates a new instance of ClearanceViolation */
  public ClearanceViolation(
      Item p_first_item, Item p_second_item, ConvexShape p_shape, int p_layer) {
    first_item = p_first_item;
    second_item = p_second_item;
    shape = p_shape;
    layer = p_layer;
  }

  public void print_info(ObjectInfoPanel p_window, java.util.Locale p_locale) {
    java.util.ResourceBundle resources =
        java.util.ResourceBundle.getBundle("app.freerouting.board.ObjectInfoPanel", p_locale);
    p_window.append_bold(resources.getString("clearance_violation_2"));
    p_window.append(" " + resources.getString("at") + " ");
    p_window.append(shape.centre_of_gravity());
    p_window.append(", " + resources.getString("width") + " ");
    p_window.append(2 * this.shape.smallest_radius());
    p_window.append(", " + resources.getString("layer") + " ");
    p_window.append(first_item.board.layer_structure.arr[this.layer].name);
    p_window.append(", " + resources.getString("between"));
    p_window.newline();
    p_window.indent();
    first_item.print_info(p_window, p_locale);
    p_window.indent();
    second_item.print_info(p_window, p_locale);
  }
}
