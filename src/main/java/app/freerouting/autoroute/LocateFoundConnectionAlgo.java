package app.freerouting.autoroute;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.Connectable;
import app.freerouting.board.Item;
import app.freerouting.board.ShapeSearchTree;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.awt.Graphics;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.SortedSet;

public abstract class LocateFoundConnectionAlgo {

  /** The new items implementing the found connection */
  public final Collection<ResultItem> connectionItems;

  /** The start item of the new routed connection */
  public final Item startItem;

  /** The layer of the connection to the start item */
  public final int startLayer;

  /** The destination item of the new routed connection */
  public final Item targetItem;

  /** The layer of the connection to the target item */
  public final int targetLayer;

  /**
   * The array of backtrack doors from the destination to the start of a found connection of the
   * maze search algorithm.
   */
  protected final BacktrackElement[] backtrackArray;

  protected final AutorouteControl ctrl;
  protected final AngleRestriction angleRestriction;
  protected final TargetItemExpansionDoor startDoor;
  protected FloatPoint currentFromPoint;
  protected FloatPoint previousFromPoint;
  protected int currentTraceLayer;
  protected int currentFromDoorIndex;
  protected int currentToDoorIndex;
  protected int currentTargetDoorIndex;
  protected TileShape currentTargetShape;

  /** Creates a new instance of LocateFoundConnectionAlgo */
  protected LocateFoundConnectionAlgo(
      MazeSearchAlgo.Result p_maze_search_result,
      AutorouteControl p_ctrl,
      ShapeSearchTree p_search_tree,
      AngleRestriction p_angle_restriction,
      SortedSet<Item> p_ripped_item_list,
      Map<Item, Integer> p_ripup_costs) {
    this.ctrl = p_ctrl;
    this.angleRestriction = p_angle_restriction;
    Collection<BacktrackElement> backtrackList =
        backtrack(p_maze_search_result, p_ripped_item_list, p_ripup_costs, p_ctrl.netNo);
    this.backtrackArray = new BacktrackElement[backtrackList.size()];
    Iterator<BacktrackElement> it = backtrackList.iterator();
    for (int i = 0; i < backtrackArray.length; i++) {
      this.backtrackArray[i] = it.next();
    }
    if (this.ctrl.netNo == 33 || this.ctrl.netNo == 66 || this.ctrl.netNo == 67) {
      FRLogger.trace(
          "compare_trace_backtrack_raw net="
              + this.ctrl.netNo
              + ", size="
              + this.backtrackArray.length);
      for (int i = 0; i < this.backtrackArray.length; i++) {
        BacktrackElement element = this.backtrackArray[i];
        String nextRoomType =
            element.nextRoom != null ? element.nextRoom.getClass().getSimpleName() : "null";
        FRLogger.trace(
            "compare_trace_backtrack_raw net="
                + this.ctrl.netNo
                + ", idx="
                + i
                + ", door_type="
                + element.door.getClass().getSimpleName()
                + ", section="
                + element.sectionNoOfDoor
                + ", next_room_type="
                + nextRoomType);
      }
    }
    this.connectionItems = new LinkedList<>();
    BacktrackElement startInfo = this.backtrackArray[backtrackArray.length - 1];
    if (!(startInfo.door instanceof TargetItemExpansionDoor)) {
      FRLogger.warn("LocateFoundConnectionAlgo: ItemExpansionDoor expected for startInfo.door");
      this.startItem = null;
      this.startLayer = 0;
      this.targetItem = null;
      this.targetLayer = 0;
      this.startDoor = null;
      return;
    }
    this.startDoor = (TargetItemExpansionDoor) startInfo.door;
    this.startItem = startDoor.item;
    this.startLayer = startDoor.room.get_layer();

    this.currentFromDoorIndex = 0;
    boolean atFanoutEnd = false;
    if (p_maze_search_result.destinationDoor
        instanceof TargetItemExpansionDoor curr_destination_door) {
      this.targetItem = curr_destination_door.item;
      this.targetLayer = curr_destination_door.room.get_layer();

      this.currentFromPoint = calculate_starting_point(curr_destination_door, p_search_tree);
    } else if (p_maze_search_result.destinationDoor instanceof ExpansionDrill currDrill) {
      // may happen only in case of fanout
      this.targetItem = null;
      this.currentFromPoint = currDrill.location.to_float();
      this.targetLayer = currDrill.firstLayer + p_maze_search_result.sectionNoOfDoor;
      atFanoutEnd = true;
    } else {
      FRLogger.warn("LocateFoundConnectionAlgo: unexpected type of destinationDoor");
      this.targetItem = null;
      this.targetLayer = 0;
      return;
    }
    this.currentTraceLayer = this.targetLayer;
    this.previousFromPoint = this.currentFromPoint;

    boolean connectionDone = false;
    while (!connectionDone) {
      boolean layerChanged = false;
      if (atFanoutEnd) {
        // do not increase this.currentTargetDoorIndex
        layerChanged = true;
      } else {
        this.currentTargetDoorIndex = this.currentFromDoorIndex + 1;
        while (currentTargetDoorIndex < this.backtrackArray.length && !layerChanged) {
          if (this.backtrackArray[this.currentTargetDoorIndex].door instanceof ExpansionDrill) {
            layerChanged = true;
          } else {
            ++this.currentTargetDoorIndex;
          }
        }
      }
      if (layerChanged) {
        // the next trace leads to a via
        ExpansionDrill currentTargetDrill =
            (ExpansionDrill) this.backtrackArray[this.currentTargetDoorIndex].door;
        this.currentTargetShape = TileShape.get_instance(currentTargetDrill.location);
      } else {
        // the next trace leads to the final target
        connectionDone = true;
        this.currentTargetDoorIndex = this.backtrackArray.length - 1;
        TileShape targetShape =
            ((Connectable) startItem)
                .get_trace_connection_shape(p_search_tree, startDoor.treeEntryNo);
        this.currentTargetShape = targetShape.intersection(startDoor.room.get_shape());
        if (this.currentTargetShape.dimension() >= 2) {
          // the target is a conduction area, make a save connection
          // by shrinking the shape by the trace halfwidth.
          double traceHalfWidth = this.ctrl.compensatedTraceHalfWidth[startDoor.room.get_layer()];
          TileShape shrinkedShape = (TileShape) this.currentTargetShape.offset(-traceHalfWidth);
          if (!shrinkedShape.is_empty()) {
            this.currentTargetShape = shrinkedShape;
          }
        }
      }
      this.currentToDoorIndex = this.currentFromDoorIndex + 1;
      ResultItem nextTrace = this.calculate_next_trace(layerChanged, atFanoutEnd);
      atFanoutEnd = false;
      this.connectionItems.add(nextTrace);
    }
  }

  /** Returns a new Instance of LocateFoundConnectionAlgo or null, if p_destination_door is null. */
  public static LocateFoundConnectionAlgo get_instance(
      MazeSearchAlgo.Result p_maze_search_result,
      AutorouteControl p_ctrl,
      ShapeSearchTree p_search_tree,
      AngleRestriction p_angle_restriction,
      SortedSet<Item> p_ripped_item_list,
      Map<Item, Integer> p_ripup_costs) {
    if (p_maze_search_result == null) {
      return null;
    }
    LocateFoundConnectionAlgo result;
    if (p_angle_restriction == AngleRestriction.NINETY_DEGREE
        || p_angle_restriction == AngleRestriction.FORTYFIVE_DEGREE) {
      result =
          new LocateFoundConnectionAlgo45Degree(
              p_maze_search_result,
              p_ctrl,
              p_search_tree,
              p_angle_restriction,
              p_ripped_item_list,
              p_ripup_costs);
    } else {
      result =
          new LocateFoundConnectionAlgoAnyAngle(
              p_maze_search_result,
              p_ctrl,
              p_search_tree,
              p_angle_restriction,
              p_ripped_item_list,
              p_ripup_costs);
    }
    return result;
  }

  /**
   * Calculates the starting point of the next trace on p_from_door.item. The implementation is not
   * yet optimal for starting points on traces or areas.
   */
  private static FloatPoint calculate_starting_point(
      TargetItemExpansionDoor p_from_door, ShapeSearchTree p_search_tree) {
    TileShape connectionShape =
        ((Connectable) p_from_door.item)
            .get_trace_connection_shape(p_search_tree, p_from_door.treeEntryNo);
    connectionShape = connectionShape.intersection(p_from_door.room.get_shape());
    return connectionShape.centre_of_gravity().round().to_float();
  }

  /**
   * Creates a list of doors by backtracking from p_destination_door to the start door. Returns
   * null, if p_destination_door is null.
   */
  private static Collection<BacktrackElement> backtrack(
      MazeSearchAlgo.Result p_maze_search_result,
      SortedSet<Item> p_ripped_item_list,
      Map<Item, Integer> p_ripup_costs,
      int p_net_no) {
    if (p_maze_search_result == null) {
      return null;
    }
    Collection<BacktrackElement> result = new LinkedList<>();
    CompleteExpansionRoom currNextRoom = null;
    ExpandableObject currBacktrackDoor = p_maze_search_result.destinationDoor;
    MazeSearchElement currMazeSearchElement =
        currBacktrackDoor.get_maze_search_element(p_maze_search_result.sectionNoOfDoor);
    boolean debugBacktrack = p_net_no == 98;
    if (debugBacktrack) {
      String destType = currBacktrackDoor.getClass().getSimpleName();
      FRLogger.trace(
          "BACKTRACK_START net="
              + p_net_no
              + ", dest_type="
              + destType
              + ", dest_section="
              + p_maze_search_result.sectionNoOfDoor
              + ", dest_room_ripped="
              + currMazeSearchElement.roomRipped);
    }
    if (currBacktrackDoor instanceof TargetItemExpansionDoor door) {
      currNextRoom = door.room;
    } else if (currBacktrackDoor instanceof ExpansionDrill currDrill) {
      currNextRoom = currDrill.roomArr[currDrill.firstLayer + p_maze_search_result.sectionNoOfDoor];
      if (currMazeSearchElement.roomRipped) {
        for (CompleteExpansionRoom tmp_room : currDrill.roomArr) {
          if (tmp_room instanceof ObstacleExpansionRoom room) {
            p_ripped_item_list.add(room.get_item());
            if (p_ripup_costs != null) {
              p_ripup_costs.put(room.get_item(), currMazeSearchElement.ripupCost);
            }
          }
        }
      }
    }
    BacktrackElement currBacktrackElement =
        new BacktrackElement(currBacktrackDoor, p_maze_search_result.sectionNoOfDoor, currNextRoom);
    int step = 0;
    for (; ; ) {
      result.add(currBacktrackElement);
      currBacktrackDoor = currMazeSearchElement.backtrackDoor;
      if (currBacktrackDoor == null) {
        break;
      }
      int currSectionNo = currMazeSearchElement.sectionNoOfBacktrackDoor;
      if (currSectionNo >= currBacktrackDoor.maze_search_element_count()) {
        FRLogger.warn("LocateFoundConnectionAlgo: currSectionNo to big");
        currSectionNo = currBacktrackDoor.maze_search_element_count() - 1;
      }
      if (currBacktrackDoor instanceof ExpansionDrill currDrill) {
        currNextRoom = currDrill.roomArr[currSectionNo];
      } else {
        currNextRoom = currBacktrackDoor.other_room(currNextRoom);
      }
      currMazeSearchElement = currBacktrackDoor.get_maze_search_element(currSectionNo);
      currBacktrackElement = new BacktrackElement(currBacktrackDoor, currSectionNo, currNextRoom);
      if (debugBacktrack) {
        String doorType = currBacktrackDoor.getClass().getSimpleName();
        String nextRoomType =
            currNextRoom != null ? currNextRoom.getClass().getSimpleName() : "null";
        int obstacleId = -1;
        if (currNextRoom instanceof ObstacleExpansionRoom obst) {
          obstacleId = obst.get_item().get_id_no();
        }
        FRLogger.trace(
            "BACKTRACK_STEP net="
                + p_net_no
                + ", step="
                + step
                + ", door_type="
                + doorType
                + ", section="
                + currSectionNo
                + ", roomRipped="
                + currMazeSearchElement.roomRipped
                + ", ripupCost="
                + currMazeSearchElement.ripupCost
                + ", next_room_type="
                + nextRoomType
                + ", obstacle_id="
                + obstacleId);
      }
      if (currMazeSearchElement.roomRipped) {
        if (currNextRoom instanceof ObstacleExpansionRoom room) {
          p_ripped_item_list.add(room.get_item());
          if (p_ripup_costs != null) {
            p_ripup_costs.put(room.get_item(), currMazeSearchElement.ripupCost);
          }
        }
      }
      step++;
    }
    return result;
  }

  private static FloatPoint ninety_degree_corner(
      FloatPoint p_from_point, FloatPoint p_to_point, boolean p_horizontal_first) {
    double x;
    double y;
    if (p_horizontal_first) {
      x = p_to_point.x;
      y = p_from_point.y;
    } else {
      x = p_from_point.x;
      y = p_to_point.y;
    }
    return new FloatPoint(x, y);
  }

  private static FloatPoint fortyfive_degree_corner(
      FloatPoint p_from_point, FloatPoint p_to_point, boolean p_horizontal_first) {
    double absDx = Math.abs(p_to_point.x - p_from_point.x);
    double absDy = Math.abs(p_to_point.y - p_from_point.y);
    double x;
    double y;

    if (absDx <= absDy) {
      if (p_horizontal_first) {
        x = p_to_point.x;
        if (p_to_point.y >= p_from_point.y) {
          y = p_from_point.y + absDx;
        } else {
          y = p_from_point.y - absDx;
        }
      } else {
        x = p_from_point.x;
        if (p_to_point.y > p_from_point.y) {
          y = p_to_point.y - absDx;
        } else {
          y = p_to_point.y + absDx;
        }
      }
    } else {
      if (p_horizontal_first) {
        y = p_from_point.y;
        if (p_to_point.x > p_from_point.x) {
          x = p_to_point.x - absDy;
        } else {
          x = p_to_point.x + absDy;
        }
      } else {
        y = p_to_point.y;
        if (p_to_point.x > p_from_point.x) {
          x = p_from_point.x + absDy;
        } else {
          x = p_from_point.x - absDy;
        }
      }
    }
    return new FloatPoint(x, y);
  }

  /**
   * Calculates an additional corner, so that for the lines from p_from_point to the result corner
   * and from the result corner to p_to_point p_angle_restriction is fulfilled.
   */
  static FloatPoint calculate_additional_corner(
      FloatPoint p_from_point,
      FloatPoint p_to_point,
      boolean p_horizontal_first,
      AngleRestriction p_angle_restriction) {
    FloatPoint result;
    if (p_angle_restriction == AngleRestriction.NINETY_DEGREE) {
      result = ninety_degree_corner(p_from_point, p_to_point, p_horizontal_first);
    } else if (p_angle_restriction == AngleRestriction.FORTYFIVE_DEGREE) {
      result = fortyfive_degree_corner(p_from_point, p_to_point, p_horizontal_first);
    } else {
      result = p_to_point;
    }
    return result;
  }

  /**
   * Calculates the next trace of the connection under construction. Returns null, if all traces are
   * returned.
   */
  private ResultItem calculate_next_trace(boolean p_layer_changed, boolean p_at_fanout_end) {
    Collection<FloatPoint> cornerList = new LinkedList<>();
    cornerList.add(this.currentFromPoint);
    if (!p_at_fanout_end) {
      FloatPoint adjustedStartCorner = this.adjust_start_corner();
      if (adjustedStartCorner != this.currentFromPoint) {
        FloatPoint addCorner =
            calculate_additional_corner(
                this.currentFromPoint, adjustedStartCorner, true, this.angleRestriction);
        cornerList.add(addCorner);
        cornerList.add(adjustedStartCorner);
        this.previousFromPoint = this.currentFromPoint;
        this.currentFromPoint = adjustedStartCorner;
      }
    }
    FloatPoint prevCorner = this.currentFromPoint;
    for (; ; ) {
      Collection<FloatPoint> nextCorners = calculate_next_trace_corners();
      if (nextCorners.isEmpty()) {
        break;
      }
      for (FloatPoint currNextCorner : nextCorners) {
        if (currNextCorner != prevCorner) {
          cornerList.add(currNextCorner);
          this.previousFromPoint = this.currentFromPoint;
          this.currentFromPoint = currNextCorner;
          prevCorner = currNextCorner;
        }
      }
    }

    int nextLayer = this.currentTraceLayer;
    if (p_layer_changed) {
      this.currentFromDoorIndex = this.currentTargetDoorIndex + 1;
      CompleteExpansionRoom nextRoom = this.backtrackArray[this.currentFromDoorIndex].nextRoom;
      if (nextRoom != null) {
        nextLayer = nextRoom.get_layer();
      }
    }

    // Round the new trace corners to Integer.
    Collection<IntPoint> roundedCornerList = new LinkedList<>();
    IntPoint prevPoint = null;
    for (FloatPoint corner : cornerList) {
      IntPoint currPoint = corner.round();
      if (!currPoint.equals(prevPoint)) {
        roundedCornerList.add(currPoint);
        prevPoint = currPoint;
      }
    }

    // Construct the result item
    IntPoint[] cornerArr = new IntPoint[roundedCornerList.size()];
    Iterator<IntPoint> it2 = roundedCornerList.iterator();
    for (int i = 0; i < cornerArr.length; i++) {
      cornerArr[i] = it2.next();
    }
    ResultItem result = new ResultItem(cornerArr, this.currentTraceLayer);
    if (this.ctrl.netNo == 33 || this.ctrl.netNo == 66 || this.ctrl.netNo == 67) {
      IntPoint first = cornerArr.length > 0 ? cornerArr[0] : null;
      IntPoint last = cornerArr.length > 0 ? cornerArr[cornerArr.length - 1] : null;
      FRLogger.trace(
          "compare_trace_next_trace_raw net="
              + this.ctrl.netNo
              + ", traceLayer="
              + this.currentTraceLayer
              + ", nextLayer="
              + nextLayer
              + ", cornerCount="
              + cornerArr.length
              + ", first="
              + first
              + ", last="
              + last
              + ", from_door="
              + this.currentFromDoorIndex
              + ", to_door="
              + this.currentToDoorIndex
              + ", target_door="
              + this.currentTargetDoorIndex);
    }
    this.currentTraceLayer = nextLayer;
    return result;
  }

  /**
   * Returns the next list of corners for the construction of the trace in calculate_next_trace. If
   * the result is empty, the trace is already completed.
   */
  protected abstract Collection<FloatPoint> calculate_next_trace_corners();

  /** Test display of the baktrack rooms. */
  public void draw(Graphics p_graphics, GraphicsContext p_graphics_context) {
    for (int i = 0; i < backtrackArray.length; i++) {
      CompleteExpansionRoom nextRoom = backtrackArray[i].nextRoom;
      if (nextRoom != null) {
        nextRoom.draw(p_graphics, p_graphics_context, 0.2);
      }
      ExpandableObject nextDoor = backtrackArray[i].door;
      if (nextDoor instanceof ExpansionDrill drill) {
        drill.draw(p_graphics, p_graphics_context, 0.2);
      }
    }
  }

  /**
   * Adjusts the start corner, so that a trace starting at this corner is completely contained in
   * the start room.
   */
  private FloatPoint adjust_start_corner() {
    if (this.currentFromDoorIndex < 0) {
      return this.currentFromPoint;
    }
    BacktrackElement currFromInfo = this.backtrackArray[this.currentFromDoorIndex];
    if (currFromInfo.nextRoom == null) {
      return this.currentFromPoint;
    }
    double traceHalfWidth = this.ctrl.compensatedTraceHalfWidth[this.currentTraceLayer];
    TileShape shrinkedRoomShape =
        (TileShape) currFromInfo.nextRoom.get_shape().offset(-traceHalfWidth);
    if (shrinkedRoomShape.is_empty() || shrinkedRoomShape.contains(this.currentFromPoint)) {
      return this.currentFromPoint;
    }
    return shrinkedRoomShape.nearest_point_approx(this.currentFromPoint).round().to_float();
  }

  /**
   * Type of a single item in the result list connectionItems. Used to create a new PolylineTrace.
   */
  protected static class ResultItem {

    public final IntPoint[] corners;
    public final int layer;

    public ResultItem(IntPoint[] p_corners, int p_layer) {
      corners = p_corners;
      layer = p_layer;
    }
  }

  /**
   * Type of the elements of the list returned by this.backtrack(). Next_room is the common room of
   * the current door and the next door in the backtrack list.
   */
  protected static final class BacktrackElement {

    public final ExpandableObject door;
    public final int sectionNoOfDoor;
    public final CompleteExpansionRoom nextRoom;

    private BacktrackElement(
        ExpandableObject p_door, int p_section_no_of_door, CompleteExpansionRoom p_room) {
      door = p_door;
      sectionNoOfDoor = p_section_no_of_door;
      nextRoom = p_room;
    }
  }
}
