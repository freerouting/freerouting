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

/** Class for moving a group of items on the board. */
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

  /** Creates a new instance of MoveItemGroup. */
  public MoveComponent(
      Item item, Vector translateVector, int maxRecursionDepth, int maxViaRecursionDepth) {
    this.translateVector = translateVector;
    this.maxRecursionDepth = maxRecursionDepth;
    this.maxViaRecursionDepth = maxViaRecursionDepth;
    if (item.board instanceof RoutingBoard routingBoard) {
      board = routingBoard;
    } else {
      board = null;
      allItemsMovable = false;
    }

    Collection<Item> itemGroupList;
    int componentNo = item.getComponentId();
    if (componentNo > 0) {
      itemGroupList = board.getComponentItems(componentNo);
      this.component = board.components.get(componentNo);
    } else {
      itemGroupList = new LinkedList<>();
      itemGroupList.add(item);
    }
    Collection<FloatPoint> itemCenters = new LinkedList<>();
    for (Item currentItem : itemGroupList) {
      boolean currentItemMovable =
          !currentItem.isUserFixed()
              && ((currentItem instanceof DrillItem)
                  || (currentItem instanceof ObstacleArea)
                  || (currentItem instanceof ComponentOutline));
      if (!currentItemMovable) {
        // MoveItemGroup currently only implemented for DrillItems
        allItemsMovable = false;
        itemGroupArr = new SortedItem[0];
        return;
      }
      if (currentItem instanceof DrillItem drillItem) {
        itemCenters.add(drillItem.getCenter().toFloat());
      }
    }
    // calculate the gravity point of all item centers
    double gravityX = 0;
    double gravityY = 0;
    for (FloatPoint currentCenter : itemCenters) {
      gravityX += currentCenter.x;
      gravityY += currentCenter.y;
    }
    gravityX /= itemCenters.size();
    gravityY /= itemCenters.size();
    Point gravityPoint = new IntPoint((int) Math.round(gravityX), (int) Math.round(gravityY));
    itemGroupArr = new SortedItem[itemGroupList.size()];
    Iterator<Item> it = itemGroupList.iterator();
    for (int i = 0; i < itemGroupArr.length; i++) {
      Item currentItem = it.next();
      Point itemCenter;
      if (currentItem instanceof DrillItem drillItem) {
        itemCenter = drillItem.getCenter();
      } else {
        itemCenter = currentItem.boundingBox().centreOfGravity().round();
      }
      Vector compareVector = gravityPoint.differenceBy(itemCenter);
      double currentProjection = compareVector.scalarProduct(translateVector);
      itemGroupArr[i] = new SortedItem(currentItem, currentProjection);
    }
    // sort the items, in the direction of translateVector, so that
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
      if (itemGroupArr[i].item instanceof DrillItem currentDrillItem) {
        if (this.translateVector.lengthApprox() >= currentDrillItem.minWidth()) {
          // a clearance violation with a connecting trace may occur
          moveOk = false;
        } else {
          moveOk =
              DrillItemMover.check(
                  currentDrillItem,
                  this.translateVector,
                  this.maxRecursionDepth,
                  this.maxViaRecursionDepth,
                  ignoreItems,
                  board,
                  timeLimit);
        }
      } else {
        moveOk = board.checkMoveItem(itemGroupArr[i].item, this.translateVector, ignoreItems);
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
  public boolean insert(int tidyWidth, int pullTightAccuracy) {
    if (!allItemsMovable) {
      return false;
    }
    if (this.component != null) {
      // component must be moved first, so that the new pin shapes are calculated correctly
      board.components.move(this.component.id, translateVector);
      // let the observers synchronize the moving
      board.communication.observers.notifyMoved(this.component);
    }
    for (int i = 0; i < itemGroupArr.length; i++) {
      if (itemGroupArr[i].item instanceof DrillItem currentDrillItem) {
        boolean moveOk =
            board.moveDrillItem(
                currentDrillItem,
                this.translateVector,
                this.maxRecursionDepth,
                this.maxViaRecursionDepth,
                tidyWidth,
                pullTightAccuracy,
                PULL_TIGHT_TIME_LIMIT);
        if (!moveOk) {
          if (this.component != null) {
            this.component.translateBy(translateVector.negate());
            // Otherwise the component outline is not restored correctly by the undo algorithm.
          }
          return false;
        }
      } else {
        itemGroupArr[i].item.moveBy(this.translateVector);
      }
    }
    return true;
  }

  /**
   * Sorts group items in the direction of translateVector.
   *
   * <p>Front items are moved first.
   */
  private record SortedItem(Item item, double projection) implements Comparable<SortedItem> {

    @Override
    public int compareTo(SortedItem other) {
      return Signum.asInt(this.projection - other.projection);
    }
  }
}
