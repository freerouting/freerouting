package app.freerouting.board;

import app.freerouting.datastructures.Signum;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Vector;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/** Class for moving a group of items on the board */
public class MoveComponent {

  private static final int PULL_TIGHT_TIME_LIMIT = 1000;
  private static final int CHECK_TIME_LIMIT = 3000;
  private final Vector translateVector;
  private final int maxRecursionDepth;
  private final int maxViaRecursionDepth;
  private final RoutingBoard board;
  private final SortedItem[] itemGroupArr;
  private boolean allItemsMovable = true;
  private Component component;

  /** Creates a new instance of MoveItemGroup */
  public MoveComponent(
      Item p_item,
      Vector p_translate_vector,
      int p_max_recursion_depth,
      int p_max_via_recursion_depth) {
    translateVector = p_translate_vector;
    maxRecursionDepth = p_max_recursion_depth;
    maxViaRecursionDepth = p_max_via_recursion_depth;
    if (p_item.board instanceof RoutingBoard routingBoard) {
      board = routingBoard;
    } else {
      board = null;
      allItemsMovable = false;
    }

    Collection<Item> itemGroupList;
    int componentNo = p_item.get_component_no();
    if (componentNo > 0) {
      itemGroupList = board.get_component_items(componentNo);
      this.component = board.components.get(componentNo);
    } else {
      itemGroupList = new LinkedList<>();
      itemGroupList.add(p_item);
    }
    Collection<FloatPoint> itemCenters = new LinkedList<>();
    for (Item currItem : itemGroupList) {
      boolean currItemMovable =
          !currItem.is_user_fixed()
              && ((currItem instanceof DrillItem)
                  || (currItem instanceof ObstacleArea)
                  || (currItem instanceof ComponentOutline));
      if (!currItemMovable) {
        // MoveItemGroup currently only implemented for DrillItems
        allItemsMovable = false;
        itemGroupArr = new SortedItem[0];
        return;
      }
      if (currItem instanceof DrillItem item) {
        itemCenters.add(item.get_center().to_float());
      }
    }
    // calculate the gravity point of all item centers
    double gravityX = 0;
    double gravityY = 0;
    for (FloatPoint currCenter : itemCenters) {
      gravityX += currCenter.x;
      gravityY += currCenter.y;
    }
    gravityX /= itemCenters.size();
    gravityY /= itemCenters.size();
    Point gravityPoint = new IntPoint((int) Math.round(gravityX), (int) Math.round(gravityY));
    itemGroupArr = new SortedItem[itemGroupList.size()];
    Iterator<Item> it = itemGroupList.iterator();
    for (int i = 0; i < itemGroupArr.length; i++) {
      Item currItem = it.next();
      Point itemCenter;
      if (currItem instanceof DrillItem item) {
        itemCenter = item.get_center();
      } else {
        itemCenter = currItem.bounding_box().centre_of_gravity().round();
      }
      Vector compareVector = gravityPoint.difference_by(itemCenter);
      double currProjection = compareVector.scalar_product(translateVector);
      itemGroupArr[i] = new SortedItem(currItem, currProjection);
    }
    // sort the items, in the direction of p_translate_vector, so that
    // the items in front come first.
    Arrays.sort(itemGroupArr);
  }

  /**
   * Checks, if all items in the group can be moved by shoving obstacle trace aside without creating
   * clearance violations.
   */
  public boolean check() {
    if (!allItemsMovable) {
      return false;
    }
    TimeLimit timeLimit = new TimeLimit(CHECK_TIME_LIMIT);
    Collection<Item> ignoreItems = new LinkedList<>();
    for (int i = 0; i < itemGroupArr.length; i++) {
      boolean moveOk;
      if (itemGroupArr[i].item instanceof DrillItem curr_drill_item) {
        if (this.translateVector.length_approx() >= curr_drill_item.min_width()) {
          // a clearance violation with a connecting trace may occur
          moveOk = false;
        } else {
          moveOk =
              MoveDrillItemAlgo.check(
                  curr_drill_item,
                  this.translateVector,
                  this.maxRecursionDepth,
                  this.maxViaRecursionDepth,
                  ignoreItems,
                  board,
                  timeLimit);
        }
      } else {
        moveOk = board.check_move_item(itemGroupArr[i].item, this.translateVector, ignoreItems);
      }
      if (!moveOk) {
        return false;
      }
    }
    return true;
  }

  /**
   * Moves all items in the group by this.translateVector and shoves aside obstacle traces. Returns
   * false, if that was not possible without creating clearance violations. In this case an undo may
   * be necessary.
   */
  public boolean insert(int p_tidy_width, int p_pull_tight_accuracy) {
    if (!allItemsMovable) {
      return false;
    }
    if (this.component != null) {
      // component must be moved first, so that the new pin shapes are calculated correctly
      board.components.move(this.component.no, translateVector);
      // let the observers synchronize the moving
      board.communication.observers.notify_moved(this.component);
    }
    for (int i = 0; i < itemGroupArr.length; i++) {
      if (itemGroupArr[i].item instanceof DrillItem curr_drill_item) {
        boolean moveOk =
            board.move_drill_item(
                curr_drill_item,
                this.translateVector,
                this.maxRecursionDepth,
                this.maxViaRecursionDepth,
                p_tidy_width,
                p_pull_tight_accuracy,
                PULL_TIGHT_TIME_LIMIT);
        if (!moveOk) {
          if (this.component != null) {
            this.component.translate_by(translateVector.negate());
            // Otherwise the component outline is not restored correctly by the undo algorithm.
          }
          return false;
        }
      } else {
        itemGroupArr[i].item.move_by(this.translateVector);
      }
    }
    return true;
  }

  /**
   * used to sort the group items in the direction of translateVector, so that the front items can
   * be moved first.
   */
  private record SortedItem(Item item, double projection) implements Comparable<SortedItem> {

    @Override
    public int compareTo(SortedItem p_other) {
      return Signum.as_int(this.projection - p_other.projection);
    }
  }
}
