package app.freerouting.core;

import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.Direction;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Locale;

/**
 * Describes padstack masks for pins or vias located at the origin.
 */
public class Padstack implements Comparable<Padstack>, ObjectInfoPanel.Printable, Serializable {

  public final String name;
  public final int no;
  /**
   * true, if vias of the own net are allowed to overlap with this padstack
   */
  public final boolean attach_allowed;
  /**
   * If false, the layers of the padstack are mirrored, if it is placed on the back side. The default is false.
   */
  public final boolean placed_absolute;
  private final ConvexShape[] shapes;
  /**
   * Pointer to the pacdstack list containing this padstack
   */
  private final Padstacks padstack_list;
  /** Cached drill radius to avoid repeated regex parsing on every render call. */
  private Double cachedDrillRadius = null;

  /**
   * Creates a new Padstack with shape p_shapes[i] on layer i (0 <= i < p_shapes.length). p_is_drilllable indicates, if vias of the own net are allowed to overlap with this padstack If
   * p_placed_absolute is false, the layers of the padstack are mirrored, if it is placed on the back side. p_padstack_list is the list, where this padstack belongs to.
   */
  Padstack(String p_name, int p_no, ConvexShape[] p_shapes, boolean p_is_drilllable, boolean p_placed_absolute, Padstacks p_padstack_list) {
    shapes = p_shapes;
    name = p_name;
    no = p_no;
    attach_allowed = p_is_drilllable;
    placed_absolute = p_placed_absolute;
    padstack_list = p_padstack_list;
  }

  /**
   * Compares 2 padstacks by name. Useful for example to display padstacks in alphabetic order.
   */
  @Override
  public int compareTo(Padstack p_other) {
    return this.name.compareToIgnoreCase(p_other.name);
  }

  /**
   * Returns the drill radius of this padstack in board units.
   * The result is cached after the first computation to avoid repeated regex parsing.
   */
  public double get_drill_radius() {
    if (cachedDrillRadius != null) {
      return cachedDrillRadius;
    }
    double result;
    if (name != null) {
      int colonIndex = name.indexOf(':');
      if (colonIndex >= 0) {
        int underscoreIndex = name.indexOf('_', colonIndex);
        String drillStr;
        if (underscoreIndex > colonIndex) {
          drillStr = name.substring(colonIndex + 1, underscoreIndex);
        } else {
          drillStr = name.substring(colonIndex + 1);
        }
        try {
          drillStr = drillStr.replaceAll("[^0-9.]", "");
          double drillDia = Double.parseDouble(drillStr);
          int lastUnderscore = name.lastIndexOf('_', colonIndex);
          if (lastUnderscore >= 0) {
            String outerStr = name.substring(lastUnderscore + 1, colonIndex).replaceAll("[^0-9.]", "");
            double outerDia = Double.parseDouble(outerStr);
            if (outerDia > 0) {
              double actualOuterRadius = get_smallest_radius();
              if (actualOuterRadius > 0) {
                result = actualOuterRadius * (drillDia / outerDia);
                cachedDrillRadius = result;
                return cachedDrillRadius;
              }
            }
          }
        } catch (NumberFormatException e) {
          // Ignore
        }
      }
    }
    result = get_smallest_radius() * 0.45;
    cachedDrillRadius = result;
    return cachedDrillRadius;
  }

  private double get_smallest_radius() {
    double minRadius = Double.MAX_VALUE;
    for (ConvexShape shape : shapes) {
      if (shape != null) {
        double radius = Math.min(shape.bounding_box().width(), shape.bounding_box().height()) / 2.0;
        if (radius < minRadius) {
          minRadius = radius;
        }
      }
    }
    return minRadius == Double.MAX_VALUE ? 0.0 : minRadius;
  }

  /**
   * Gets the shape of this padstack on layer p_layer
   */
  public ConvexShape get_shape(int p_layer) {
    if (p_layer < 0 || p_layer >= shapes.length) {
      FRLogger.warn("Padstack.get_layer p_layer out of range");
      return null;
    }
    return shapes[p_layer];
  }

  /**
   * Returns the first layer of this padstack with a shape != null.
   */
  public int from_layer() {
    int result = 0;
    while (result < shapes.length && shapes[result] == null) {
      ++result;
    }
    return result;
  }

  /**
   * Returns the last layer of this padstack with a shape != null.
   */
  public int to_layer() {
    int result = shapes.length - 1;
    while (result >= 0 && shapes[result] == null) {
      --result;
    }
    return result;
  }

  /**
   * Returns the layer count of the board of this padstack.
   */
  public int board_layer_count() {
    return shapes.length;
  }

  @Override
  public String toString() {
    return this.name;
  }

  /**
   * Calculates the allowed trace exit directions of the shape of this padstack on layer p_layer. If the length of the pad is smaller than p_factor times the height of the pad, connection also to the
   * long side is allowed.
   */
  public Collection<Direction> get_trace_exit_directions(int p_layer, double p_factor) {
    Collection<Direction> result = new LinkedList<>();
    if (p_layer < 0 || p_layer >= shapes.length) {
      return result;
    }
    ConvexShape curr_shape = shapes[p_layer];
    if (curr_shape == null) {
      return result;
    }
    if (!(curr_shape instanceof IntBox || curr_shape instanceof IntOctagon)) {
      return result;
    }
    IntBox curr_box = curr_shape.bounding_box();

    boolean all_dirs = Math.max(curr_box.width(), curr_box.height()) < p_factor * Math.min(curr_box.width(), curr_box.height());

    if (all_dirs || curr_box.width() >= curr_box.height()) {
      result.add(Direction.RIGHT);
      result.add(Direction.LEFT);
    }
    if (all_dirs || curr_box.width() <= curr_box.height()) {
      result.add(Direction.UP);
      result.add(Direction.DOWN);
    }
    return result;
  }

  @Override
  public void print_info(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.append_bold(tm.getText("padstack") + " ");
    p_window.append_bold(this.name);
    for (int i = 0; i < shapes.length; i++) {
      if (shapes[i] != null) {
        p_window.newline();
        p_window.indent();
        p_window.append(shapes[i], p_locale);
        p_window.append(" " + tm.getText("on_layer") + " ");
        p_window.append(padstack_list.board_layer_structure.arr[i].name);
      }
    }
    p_window.newline();
  }
}
