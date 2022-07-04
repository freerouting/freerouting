package app.freerouting.board;

import app.freerouting.datastructures.Signum;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Vector;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/** Class for moving a group of items on the board */
public class MoveComponent {
  private static final int PULL_TIGHT_TIME_LIMIT = 1000;
  private static final int CHECK_TIME_LIMIT = 3000;
  private final Vector translate_vector;
  private final int max_recursion_depth;
  private final int max_via_recursion_depth;
  private final RoutingBoard board;
  private boolean all_items_movable = true;
  private SortedItem[] item_group_arr;
  private Component component = null;
  /** Creates a new instance of MoveItemGroup */
  public MoveComponent(
      Item p_item,
      Vector p_translate_vector,
      int p_max_recursion_depth,
      int p_max_via_recursion_depth) {
    translate_vector = p_translate_vector;
    max_recursion_depth = p_max_recursion_depth;
    max_via_recursion_depth = p_max_via_recursion_depth;
    if (p_item.board instanceof RoutingBoard) {
      board = (RoutingBoard) p_item.board;
    } else {
      board = null;
      all_items_movable = false;
    }

    Collection<Item> item_group_list;
    int component_no = p_item.get_component_no();
    if (component_no > 0) {
      item_group_list = board.get_component_items(component_no);
      this.component = board.components.get(component_no);
    } else {
      item_group_list = new LinkedList<Item>();
      item_group_list.add(p_item);
    }
    Collection<FloatPoint> item_centers = new LinkedList<FloatPoint>();
    for (Item curr_item : item_group_list) {
      boolean curr_item_movable =
          !curr_item.is_user_fixed()
              && ((curr_item instanceof DrillItem)
                  || (curr_item instanceof ObstacleArea)
                  || (curr_item instanceof ComponentOutline));
      if (!curr_item_movable) {
        // MoveItemGroup currently only implemented for DrillItems
        all_items_movable = false;
        return;
      }
      if (curr_item instanceof DrillItem) {
        item_centers.add(((DrillItem) curr_item).get_center().to_float());
      }
    }
    // calculate the gravity point of all item centers
    double gravity_x = 0;
    double gravity_y = 0;
    for (FloatPoint curr_center : item_centers) {
      gravity_x += curr_center.x;
      gravity_y += curr_center.y;
    }
    gravity_x /= item_centers.size();
    gravity_y /= item_centers.size();
    Point gravity_point = new IntPoint((int) Math.round(gravity_x), (int) Math.round(gravity_y));
    item_group_arr = new SortedItem[item_group_list.size()];
    Iterator<Item> it = item_group_list.iterator();
    for (int i = 0; i < item_group_arr.length; ++i) {
      Item curr_item = it.next();
      Point item_center;
      if (curr_item instanceof DrillItem) {
        item_center = ((DrillItem) curr_item).get_center();
      } else {
        item_center = curr_item.bounding_box().centre_of_gravity().round();
      }
      Vector compare_vector = gravity_point.difference_by(item_center);
      double curr_projection = compare_vector.scalar_product(translate_vector);
      item_group_arr[i] = new SortedItem(curr_item, curr_projection);
    }
    // sort the items, in the direction of p_translate_vector, so that
    // the items in front come first.
    java.util.Arrays.sort(item_group_arr);
  }

  /**
   * Checks, if all items in the group can be moved by shoving obstacle trace aside without creating
   * clearance violations.
   */
  public boolean check() {
    if (!all_items_movable) {
      return false;
    }
    TimeLimit time_limit = new TimeLimit(CHECK_TIME_LIMIT);
    Collection<Item> ignore_items = new LinkedList<Item>();
    for (int i = 0; i < item_group_arr.length; ++i) {
      boolean move_ok;
      if (item_group_arr[i].item instanceof DrillItem) {
        DrillItem curr_drill_item = (DrillItem) item_group_arr[i].item;
        if (this.translate_vector.length_approx() >= curr_drill_item.min_width()) {
          // a clearance violation with a connecting trace may occur
          move_ok = false;
        } else {
          move_ok =
              MoveDrillItemAlgo.check(
                  curr_drill_item,
                  this.translate_vector,
                  this.max_recursion_depth,
                  this.max_via_recursion_depth,
                  ignore_items,
                  board,
                  time_limit);
        }
      } else {
        move_ok =
            board.check_move_item(item_group_arr[i].item, this.translate_vector, ignore_items);
      }
      if (!move_ok) {
        return false;
      }
    }
    return true;
  }

  /**
   * Moves all items in the group by this.translate_vector and shoves aside obstacle traces. Returns
   * false, if that was not possible without creating clearance violations. In this case an undo may
   * be necessary.
   */
  public boolean insert(int p_tidy_width, int p_pull_tight_accuracy) {
    if (!all_items_movable) {
      return false;
    }
    if (this.component != null) {
      // component must be moved first, so that the new pin shapes are calculeted correctly
      board.components.move(this.component.no, translate_vector);
      // let the observers syncronize the moving
      board.communication.observers.notify_moved(this.component);
    }
    for (int i = 0; i < item_group_arr.length; ++i) {
      if (item_group_arr[i].item instanceof DrillItem) {
        DrillItem curr_drill_item = (DrillItem) item_group_arr[i].item;
        boolean move_ok =
            board.move_drill_item(
                curr_drill_item,
                this.translate_vector,
                this.max_recursion_depth,
                this.max_via_recursion_depth,
                p_tidy_width,
                p_pull_tight_accuracy,
                PULL_TIGHT_TIME_LIMIT);
        if (!move_ok) {
          if (this.component != null) {
            this.component.translate_by(translate_vector.negate());
            // Otherwise the component outline is not restored correctly by the undo algorithm.
          }
          return false;
        }
      } else {
        item_group_arr[i].item.move_by(this.translate_vector);
      }
    }
    return true;
  }

  /**
   * used to sort the group items in the direction of translate_vector, so that the front items can
   * be moved first.
   */
  private static class SortedItem implements Comparable<SortedItem> {

    final Item item;
    final double projection;

    SortedItem(Item p_item, double p_projection) {
      item = p_item;
      projection = p_projection;
    }

    public int compareTo(SortedItem p_other) {
      return Signum.as_int(this.projection - p_other.projection);
    }
  }
}
