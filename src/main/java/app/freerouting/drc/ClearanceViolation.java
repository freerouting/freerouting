package app.freerouting.drc;

import app.freerouting.board.Item;
import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.management.TextManager;

import java.util.Locale;

/**
 * Information of a clearance violation between 2 items.
 */
public class ClearanceViolation implements ObjectInfoPanel.Printable
{

  /**
   * The first item of the clearance violation
   */
  public final Item first_item;
  /**
   * The second item of the clearance violation
   */
  public final Item second_item;
  /**
   * The shape of the clearance violation
   */
  public final ConvexShape shape;
  /**
   * The layer of the clearance violation
   */
  public final int layer;
  public final double expected_clearance;
  public final double actual_clearance;

  /**
   * Creates a new instance of ClearanceViolation
   */
  public ClearanceViolation(Item p_first_item, Item p_second_item, ConvexShape p_shape, int p_layer, double p_expected_clearance, double p_actual_clearance)
  {
    first_item = p_first_item;
    second_item = p_second_item;
    shape = p_shape;
    layer = p_layer;
    expected_clearance = p_expected_clearance;
    actual_clearance = p_actual_clearance;
  }

  @Override
  public void print_info(ObjectInfoPanel p_window, Locale p_locale)
  {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.append_bold(tm.getText("clearance_violation_2"));
    p_window.append(" " + tm.getText("at") + " ");
    p_window.append(shape.centre_of_gravity());
    p_window.append(", " + tm.getText("width") + " ");
    p_window.append(2 * this.shape.smallest_radius());
    p_window.append(", " + tm.getText("layer") + " ");
    p_window.append(first_item.board.layer_structure.arr[this.layer].name);
    p_window.append(", " + tm.getText("between"));
    p_window.newline();
    p_window.indent();
    first_item.print_info(p_window, p_locale);
    p_window.indent();
    second_item.print_info(p_window, p_locale);
    p_window.newline();
    p_window.indent();
    String clearance_violation_info_expected_clearance = String.format(tm.getText("clearance_violation_info_expected_clearance"), (this.expected_clearance / 10000.0), (this.actual_clearance / 10000.0));
    p_window.append(clearance_violation_info_expected_clearance);
  }
}