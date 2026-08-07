package app.freerouting.autoroute;

import app.freerouting.board.Connectable;
import app.freerouting.board.Item;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.SearchTreeObject;
import app.freerouting.board.ShapeSearchTree;
import app.freerouting.datastructures.ShapeTree;
import app.freerouting.datastructures.Signum;
import app.freerouting.geometry.planar.Direction;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Side;
import app.freerouting.geometry.planar.Simplex;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * To calculate the neighbour rooms of an expansion room. The neighbour rooms will be sorted in
 * counterclock sense around the border of the shape of p_room. Overlapping neighbours containing an
 * item may be stored in an unordered list.
 */
public final class SortedRoomNeighbours {

  private final ExpansionRoom fromRoom;
  private final CompleteExpansionRoom completedRoom;
  private final TileShape roomShape;
  private final SortedSet<SortedRoomNeighbour> sortedNeighbours;
  private final Collection<ShapeTree.TreeEntry> ownNetObjects;

  /** Creates a new instance of SortedRoomNeighbours */
  private SortedRoomNeighbours(ExpansionRoom p_from_room, CompleteExpansionRoom p_completed_room) {
    fromRoom = p_from_room;
    completedRoom = p_completed_room;
    roomShape = p_completed_room.get_shape();
    sortedNeighbours = new TreeSet<>();
    ownNetObjects = new LinkedList<>();
  }

  /**
   * To calculate the neighbour rooms of an expansion room. The neighbour rooms will be sorted in
   * counterclock sense around the border of the shape of p_room. Overlapping neighbours containing
   * an item may be stored in an unordered list.
   */
  public static CompleteExpansionRoom calculate(
      ExpansionRoom p_room, AutorouteEngine p_autoroute_engine) {
    int netNo = p_autoroute_engine.get_net_no();

    SortedRoomNeighbours roomNeighbours =
        calculate_neighbours(
            p_room,
            netNo,
            p_autoroute_engine.autorouteSearchTree,
            p_autoroute_engine.generate_room_id_no());

    // Check, that each side of the room shape has at least one touching neighbour.
    // Otherwise, improve the room shape by enlarging.

    boolean edgeRemoved =
        roomNeighbours.try_remove_edge(netNo, p_autoroute_engine.autorouteSearchTree);
    CompleteExpansionRoom result = roomNeighbours.completedRoom;
    if (edgeRemoved) {
      p_autoroute_engine.remove_all_doors(result);
      return calculate(p_room, p_autoroute_engine);
    }

    // Now calculate the new incomplete rooms together with the doors
    // between this room and the sorted neighbours.
    if (roomNeighbours.sortedNeighbours.isEmpty()) {
      if (result instanceof ObstacleExpansionRoom) {
        calculate_incomplete_rooms_with_empty_neighbours(
            (ObstacleExpansionRoom) p_room, p_autoroute_engine);
      }
    } else {
      roomNeighbours.calculate_new_incomplete_rooms(p_autoroute_engine);
      if (result.get_shape().dimension() < 2) {
        FRLogger.trace(
            "AutorouteEngine.calculate_new_incomplete_rooms_with_more_than_1_neighbour: unexpected dimension for smoothened_shape");
      }
    }

    if (result instanceof CompleteFreeSpaceExpansionRoom room) {
      calculate_target_doors(room, roomNeighbours.ownNetObjects, p_autoroute_engine);
    }
    return result;
  }

  private static void calculate_incomplete_rooms_with_empty_neighbours(
      ObstacleExpansionRoom p_room, AutorouteEngine p_autoroute_engine) {
    TileShape roomShape = p_room.get_shape();
    for (int i = 0; i < roomShape.border_line_count(); i++) {
      Line currLine = roomShape.border_line(i);
      if (SortedRoomNeighbours.insert_door_ok(p_room, currLine)) {
        Line[] shapeLine = new Line[1];
        shapeLine[0] = currLine.opposite();
        TileShape newRoomShape = new Simplex(shapeLine);
        TileShape newContainedShape = roomShape.intersection(newRoomShape);
        FreeSpaceExpansionRoom newRoom =
            p_autoroute_engine.add_incomplete_expansion_room(
                newRoomShape, p_room.get_layer(), newContainedShape);
        ExpansionDoor newDoor = new ExpansionDoor(p_room, newRoom, 1);
        p_room.add_door(newDoor);
        newRoom.add_door(newDoor);
      }
    }
  }

  private static void calculate_target_doors(
      CompleteFreeSpaceExpansionRoom p_room,
      Collection<ShapeTree.TreeEntry> p_own_net_objects,
      AutorouteEngine p_autoroute_engine) {
    if (!p_own_net_objects.isEmpty()) {
      p_room.set_net_dependent();
    }
    for (ShapeTree.TreeEntry currEntry : p_own_net_objects) {
      if (currEntry.object instanceof Connectable currObject) {
        if (currObject.contains_net(p_autoroute_engine.get_net_no())) {
          TileShape currConnectionShape =
              currObject.get_trace_connection_shape(
                  p_autoroute_engine.autorouteSearchTree, currEntry.shapeIndexInObject);
          if (currConnectionShape != null && p_room.get_shape().intersects(currConnectionShape)) {
            Item currItem = (Item) currObject;
            TargetItemExpansionDoor newTargetDoor =
                new TargetItemExpansionDoor(
                    currItem,
                    currEntry.shapeIndexInObject,
                    p_room,
                    p_autoroute_engine.autorouteSearchTree);
            p_room.add_target_door(newTargetDoor);
          }
        }
      }
    }
  }

  private static SortedRoomNeighbours calculate_neighbours(
      ExpansionRoom p_room,
      int p_net_no,
      ShapeSearchTree p_autoroute_search_tree,
      int p_room_id_no) {
    TileShape roomShape = p_room.get_shape();
    CompleteExpansionRoom completedRoom;
    if (p_room instanceof IncompleteFreeSpaceExpansionRoom) {
      completedRoom =
          new CompleteFreeSpaceExpansionRoom(roomShape, p_room.get_layer(), p_room_id_no);
    } else if (p_room instanceof ObstacleExpansionRoom room) {
      completedRoom = room;
    } else {
      FRLogger.warn("SortedRoomNeighbours.calculate: unexpected expansion room type");
      return null;
    }
    SortedRoomNeighbours result = new SortedRoomNeighbours(p_room, completedRoom);
    Collection<ShapeTree.TreeEntry> overlappingObjects = new LinkedList<>();
    p_autoroute_search_tree.overlapping_tree_entries(
        roomShape, p_room.get_layer(), overlappingObjects);

    // Sort the overlapping objects deterministically to ensure parity with v1.9.
    ((LinkedList<ShapeTree.TreeEntry>) overlappingObjects)
        .sort(
            (e1, e2) -> {
              int idDiff =
                  ((SearchTreeObject) e1.object).get_id_no()
                      - ((SearchTreeObject) e2.object).get_id_no();
              if (idDiff != 0) {
                return idDiff;
              }
              return e1.shapeIndexInObject - e2.shapeIndexInObject;
            });

    // Calculate the touching neighbour objects and sort them in counterclock sense
    // around the border of the room shape.
    for (ShapeTree.TreeEntry currEntry : overlappingObjects) {
      SearchTreeObject currObject = (SearchTreeObject) currEntry.object;
      if (currObject == p_room) {
        continue;
      }
      if ((p_room instanceof IncompleteFreeSpaceExpansionRoom)
          && !currObject.is_trace_obstacle(p_net_no)) {
        // delay processing the target doors until the room shape will not change anymore
        result.ownNetObjects.add(currEntry);
        continue;
      }
      TileShape currShape =
          currObject.get_tree_shape(p_autoroute_search_tree, currEntry.shapeIndexInObject);
      TileShape intersection = roomShape.intersection(currShape);
      int dimension = intersection.dimension();
      if (dimension > 1) {
        if (completedRoom instanceof ObstacleExpansionRoom room
            && currObject instanceof Item currItem) {
          // only Obstacle expansion room may have a 2-dim overlap
          if (currItem.is_routable()) {
            ItemAutorouteInfo itemInfo = currItem.get_autoroute_info();
            ObstacleExpansionRoom currOverlapRoom =
                itemInfo.get_expansion_room(currEntry.shapeIndexInObject, p_autoroute_search_tree);
            room.create_overlap_door(currOverlapRoom);
          }
        } else {
          FRLogger.trace(
              "SortedRoomNeighbours.calculate: unexpected area overlap of free space expansion room");
        }
        continue;
      }
      if (dimension < 0) {
        FRLogger.debug("SortedRoomNeighbours.calculate: dimension >= 0 expected");
        continue;
      }
      if (dimension == 1) {
        int[] touchingSides = roomShape.touching_sides(currShape);
        if (touchingSides.length != 2) {
          FRLogger.debug("SortedRoomNeighbours.calculate: touchingSides length 2 expected");
          continue;
        }
        result.add_sorted_neighbour(
            currObject, currShape, intersection, touchingSides[0], touchingSides[1], false, false);
        // make  sure, that there is a door to the neighbour room.
        ExpansionRoom neighbourRoom = null;
        if (currObject instanceof ExpansionRoom room) {
          neighbourRoom = room;
        } else if (currObject instanceof Item currItem) {
          if (currItem.is_routable()) {
            // expand the item for ripup and pushing purposes
            ItemAutorouteInfo itemInfo = currItem.get_autoroute_info();
            neighbourRoom =
                itemInfo.get_expansion_room(currEntry.shapeIndexInObject, p_autoroute_search_tree);
          }
        }
        if (neighbourRoom != null) {
          if (SortedRoomNeighbours.insert_door_ok(completedRoom, neighbourRoom, intersection)) {
            ExpansionDoor newDoor = new ExpansionDoor(completedRoom, neighbourRoom, 1);
            neighbourRoom.add_door(newDoor);
            completedRoom.add_door(newDoor);
          }
        }
      } else // dimension = 0
      {
        Point touchingPoint = intersection.corner(0);
        int roomCornerNo = roomShape.equals_corner(touchingPoint);
        boolean roomTouchIsCorner;
        int touchingSideNoOfRoom;
        if (roomCornerNo >= 0) {
          roomTouchIsCorner = true;
          touchingSideNoOfRoom = roomCornerNo;
        } else {
          roomTouchIsCorner = false;
          touchingSideNoOfRoom = roomShape.contains_on_border_line_no(touchingPoint);
          if (touchingSideNoOfRoom < 0) {
            FRLogger.debug("SortedRoomNeighbours.calculate: touchingSideNoOfRoom >= 0 expected");
          }
        }
        int neighbourRoomCornerNo = currShape.equals_corner(touchingPoint);
        boolean neighbourRoomTouchIsCorner;
        int touchingSideNoOfNeighbourRoom;
        if (neighbourRoomCornerNo >= 0) {
          neighbourRoomTouchIsCorner = true;
          // The previous border line is preferred to make the shape of the incomplete room as big
          // as possible
          touchingSideNoOfNeighbourRoom = currShape.prev_no(neighbourRoomCornerNo);
        } else {
          neighbourRoomTouchIsCorner = false;
          touchingSideNoOfNeighbourRoom = currShape.contains_on_border_line_no(touchingPoint);
          if (touchingSideNoOfNeighbourRoom < 0) {
            FRLogger.debug(
                "AutorouteEngine.SortedRoomNeighbours.calculate: touchingSideNoOfNeighbourRoom >= 0 expected");
          }
        }
        result.add_sorted_neighbour(
            currObject,
            currShape,
            intersection,
            touchingSideNoOfRoom,
            touchingSideNoOfNeighbourRoom,
            roomTouchIsCorner,
            neighbourRoomTouchIsCorner);
      }
    }
    return result;
  }

  /** p_door_shape is expected to have dimension 1. */
  static boolean insert_door_ok(
      ExpansionRoom p_room_1, ExpansionRoom p_room_2, TileShape p_door_shape) {
    if (p_room_1.door_exists(p_room_2)) {
      return false;
    }
    if (p_room_1 instanceof ObstacleExpansionRoom room
        && p_room_2 instanceof ObstacleExpansionRoom room1) {
      Item firstItem = room.get_item();
      Item secondItem = room1.get_item();
      // insert only overlap_doors between items of the same net for performance reasons.
      return firstItem.shares_net(secondItem);
    }
    if (!(p_room_1 instanceof ObstacleExpansionRoom)
        && !(p_room_2 instanceof ObstacleExpansionRoom)) {
      return true;
    }
    // Insert 1 dimensional doors of trace rooms only, if they are parallel to the trace line.
    // Otherwise, there may be check ripup problems with entering at the wrong side at a fork.
    Line doorLine = null;
    Point prevCorner = p_door_shape.corner(0);
    int cornerCount = p_door_shape.border_line_count();
    for (int i = 1; i < cornerCount; i++) {
      Point currCorner = p_door_shape.corner(i);
      if (!currCorner.equals(prevCorner)) {
        doorLine = p_door_shape.border_line(i - 1);
        break;
      }
      prevCorner = currCorner;
    }
    if (p_room_1 instanceof ObstacleExpansionRoom room) {
      if (!insert_door_ok(room, doorLine)) {
        return false;
      }
    }
    if (p_room_2 instanceof ObstacleExpansionRoom room) {
      return insert_door_ok(room, doorLine);
    }
    return true;
  }

  /**
   * Insert 1 dimensional doors for the first and the last room of a trace rooms only, if they are
   * parallel to the trace line. Otherwise, there may be check ripup problems with entering at the
   * wrong side at a fork.
   */
  private static boolean insert_door_ok(ObstacleExpansionRoom p_room, Line p_door_line) {
    if (p_door_line == null) {
      FRLogger.warn("SortedRoomNeighbours.insert_door_ok: p_door_line is null");
      return false;
    }
    Item currItem = p_room.get_item();
    if (currItem instanceof PolylineTrace currTrace) {
      int roomIndex = p_room.get_index_in_item();
      if (roomIndex == 0 || roomIndex == currTrace.tile_shape_count() - 1) {
        Line currTraceLine = currTrace.polyline().arr[roomIndex + 1];
        return currTraceLine.is_parallel(p_door_line);
      }
    }
    return true;
  }

  private void add_sorted_neighbour(
      SearchTreeObject p_search_tree_object,
      TileShape p_neighbour_shape,
      TileShape p_intersection,
      int p_touching_side_no_of_room,
      int p_touching_side_no_of_neighbour_room,
      boolean p_room_touch_is_corner,
      boolean p_neighbour_room_touch_is_corner) {
    SortedRoomNeighbour newNeighbour =
        new SortedRoomNeighbour(
            p_search_tree_object,
            p_neighbour_shape,
            p_intersection,
            p_touching_side_no_of_room,
            p_touching_side_no_of_neighbour_room,
            p_room_touch_is_corner,
            p_neighbour_room_touch_is_corner);
    sortedNeighbours.add(newNeighbour);
  }

  /**
   * Check, that each side of the room shape has at least one touching neighbour. Otherwise, the
   * room shape will be improved the by enlarging. Returns true, if the room shape was changed.
   */
  private boolean try_remove_edge(int p_net_no, ShapeSearchTree p_autoroute_search_tree) {
    if (!(this.fromRoom instanceof IncompleteFreeSpaceExpansionRoom curr_incomplete_room)) {
      return false;
    }
    int removeEdgeNo = -1;
    Simplex roomSimplex = curr_incomplete_room.get_shape().to_Simplex();
    double roomShapeArea = roomSimplex.area();

    int prevEdgeNo = -1;
    int currEdgeNo = 0;
    for (SortedRoomNeighbour nextNeighbour : sortedNeighbours) {
      if (nextNeighbour.touchingSideNoOfRoom == prevEdgeNo) {
        continue;
      }
      if (nextNeighbour.touchingSideNoOfRoom == currEdgeNo) {
        prevEdgeNo = currEdgeNo;
        ++currEdgeNo;
      } else {
        // On the edge side with index currEdgeNo is no touching
        // neighbour.
        removeEdgeNo = currEdgeNo;
        break;
      }
    }

    if (removeEdgeNo < 0 && currEdgeNo < roomSimplex.border_line_count()) {
      // missing touching neighbour at the last edge side.
      removeEdgeNo = currEdgeNo;
    }

    if (removeEdgeNo >= 0) {
      // Touching neighbour missing at the edge side with index removeEdgeNo
      // Remove the edge line and restart the algorithm.
      FRLogger.trace(
          "ROOM_EDGE_REMOVE start"
              + ", net="
              + p_net_no
              + ", layer="
              + curr_incomplete_room.get_layer()
              + ", removeEdge="
              + removeEdgeNo
              + ", room_bounds="
              + curr_incomplete_room.get_shape().bounding_box());
      Simplex enlargedShape = roomSimplex.remove_border_line(removeEdgeNo);
      IncompleteFreeSpaceExpansionRoom enlargedRoom =
          new IncompleteFreeSpaceExpansionRoom(
              enlargedShape,
              curr_incomplete_room.get_layer(),
              curr_incomplete_room.get_contained_shape());
      Collection<IncompleteFreeSpaceExpansionRoom> newRooms =
          p_autoroute_search_tree.complete_shape(enlargedRoom, p_net_no, null, null);
      FRLogger.trace(
          "ROOM_EDGE_REMOVE complete_shape"
              + ", net="
              + p_net_no
              + ", layer="
              + curr_incomplete_room.get_layer()
              + ", removeEdge="
              + removeEdgeNo
              + ", candidate_count="
              + newRooms.size());
      if (newRooms.size() != 1) {
        FRLogger.trace("AutorouteEngine.calculate_doors: 1 completed shape expected");
        return false;
      }
      boolean removeEdge = false;
      // Check, that the area increases to prevent endless loop.
      IncompleteFreeSpaceExpansionRoom newShape = newRooms.iterator().next();
      if (newShape.get_shape().area() > roomShapeArea) {
        removeEdge = true;
      }
      if (removeEdge) {
        Iterator<IncompleteFreeSpaceExpansionRoom> it2 = newRooms.iterator();
        IncompleteFreeSpaceExpansionRoom newRoom = it2.next();
        FRLogger.trace(
            "ROOM_EDGE_REMOVE applied"
                + ", net="
                + p_net_no
                + ", layer="
                + curr_incomplete_room.get_layer()
                + ", removeEdge="
                + removeEdgeNo
                + ", old_bounds="
                + curr_incomplete_room.get_shape().bounding_box()
                + ", newBounds="
                + newRoom.get_shape().bounding_box());
        curr_incomplete_room.set_shape(newRoom.get_shape());
        curr_incomplete_room.set_contained_shape(newRoom.get_contained_shape());
        return true;
      }
    }
    return false;
  }

  /**
   * Called from calculate_doors(). The shape of the room p_result may change inside this function.
   */
  public void calculate_new_incomplete_rooms(AutorouteEngine p_autoroute_engine) {
    SortedRoomNeighbour prevNeighbour = this.sortedNeighbours.getLast();
    Simplex roomSimplex = this.fromRoom.get_shape().to_Simplex();
    for (SortedRoomNeighbour nextNeighbour : this.sortedNeighbours) {
      int firstTouchingSideNo = prevNeighbour.touchingSideNoOfRoom;
      int lastTouchingSideNo = nextNeighbour.touchingSideNoOfRoom;

      int currNextNo = roomSimplex.next_no(firstTouchingSideNo);
      boolean intersectionWithPrevNeighbourEndsAtCorner =
          (firstTouchingSideNo != lastTouchingSideNo
                  || prevNeighbour == this.sortedNeighbours.getLast())
              && prevNeighbour.last_corner().equals(roomSimplex.corner(currNextNo));
      boolean intersectionWithNextNeighbourStartsAtCorner =
          (firstTouchingSideNo != lastTouchingSideNo
                  || prevNeighbour == this.sortedNeighbours.getLast())
              && nextNeighbour.first_corner().equals(roomSimplex.corner(lastTouchingSideNo));

      if (intersectionWithPrevNeighbourEndsAtCorner) {
        firstTouchingSideNo = currNextNo;
      }

      if (intersectionWithNextNeighbourStartsAtCorner) {
        lastTouchingSideNo = roomSimplex.prev_no(lastTouchingSideNo);
      }
      boolean neighboursTouch = false;

      if (this.sortedNeighbours.size() > 1) {
        neighboursTouch = prevNeighbour.last_corner().equals(nextNeighbour.first_corner());
      }

      if (!neighboursTouch) {
        // create a door to a new incomplete expansion room between
        // the last corner of the previous neighbour and the first corner of the
        // current neighbour.
        int lastBoundingLineNo = prevNeighbour.touchingSideNoOfNeighbourRoom;
        if (!(intersectionWithPrevNeighbourEndsAtCorner || prevNeighbour.roomTouchIsCorner)) {
          lastBoundingLineNo = prevNeighbour.neighbourShape.prev_no(lastBoundingLineNo);
        }

        int firstBoundingLineNo = nextNeighbour.touchingSideNoOfNeighbourRoom;
        if (!(intersectionWithNextNeighbourStartsAtCorner
            || nextNeighbour.neighbourRoomTouchIsCorner)) {
          firstBoundingLineNo = nextNeighbour.neighbourShape.next_no(firstBoundingLineNo);
        }
        Line startEdgeLine =
            nextNeighbour.neighbourShape.border_line(firstBoundingLineNo).opposite();
        // startEdgeLine is only used for the first new incomplete room.
        Line middleEdgeLine = null;
        int currTouchingSideNo = lastTouchingSideNo;
        boolean firstTime = true;
        // The loop goes backwards from the edge line of nextNeighbour to the edge line of
        // prevNeighbour.
        for (; ; ) {
          boolean cornerCutOff = false;
          if (this.fromRoom instanceof IncompleteFreeSpaceExpansionRoom incomplete_room) {
            if (currTouchingSideNo == lastTouchingSideNo
                && firstTouchingSideNo != lastTouchingSideNo) {
              // Create a new line approximately from the last corner of the previous
              // neighbour to the first corner of the next neighbour to cut off
              // the outstanding corners of the room shape in the empty space.
              // That is only tried in the first pass of the loop.
              IntPoint cutLineStart = prevNeighbour.last_corner().to_float().round();
              IntPoint cutLineEnd = nextNeighbour.first_corner().to_float().round();
              Line cutLine = new Line(cutLineStart, cutLineEnd);
              TileShape cutHalfPlane = TileShape.get_instance(cutLine);
              ((CompleteFreeSpaceExpansionRoom) this.completedRoom)
                  .set_shape(this.completedRoom.get_shape().intersection(cutHalfPlane));
              // Otherwise p_room.containedShape would no longer be contained
              // in the shape after cutting of the corner.
              cornerCutOff =
                  incomplete_room.get_contained_shape().side_of(cutLine) == Side.ON_THE_LEFT;
              if (cornerCutOff) {
                middleEdgeLine = cutLine.opposite();
              }
            }
          }
          int nextTouchingSideNo = roomSimplex.prev_no(currTouchingSideNo);

          if (!cornerCutOff) {
            middleEdgeLine = roomSimplex.border_line(currTouchingSideNo).opposite();
          }

          Direction middleLineDir = middleEdgeLine.direction();

          boolean lastTime =
              currTouchingSideNo == firstTouchingSideNo
                      && !(prevNeighbour == this.sortedNeighbours.getLast() && firstTime)
                  // The expression above handles the case, when all neighbours are on 1 edge line.
                  || cornerCutOff;

          Line endEdgeLine;
          // endEdgeLine is only used for the last new incomplete room.
          if (lastTime) {
            endEdgeLine = prevNeighbour.neighbourShape.border_line(lastBoundingLineNo).opposite();
            if (endEdgeLine.direction().side_of(middleLineDir) != Side.ON_THE_LEFT) {
              // Concave corner between the middle and the last line.
              // Maybe there is a 1 point touch.
              endEdgeLine = null;
            }
          } else {
            endEdgeLine = null;
          }

          if (startEdgeLine != null
              && middleLineDir.side_of(startEdgeLine.direction()) != Side.ON_THE_LEFT) {
            // concave corner between the first and the middle line
            // May be there is a 1 point touch.
            startEdgeLine = null;
          }
          int newEdgeLineCount = 1;
          if (startEdgeLine != null) {
            ++newEdgeLineCount;
          }
          if (endEdgeLine != null) {
            ++newEdgeLineCount;
          }
          Line[] newEdgeLines = new Line[newEdgeLineCount];
          int currIndex = 0;
          if (startEdgeLine != null) {
            newEdgeLines[currIndex] = startEdgeLine;
            ++currIndex;
          }
          newEdgeLines[currIndex] = middleEdgeLine;
          if (endEdgeLine != null) {
            ++currIndex;
            newEdgeLines[currIndex] = endEdgeLine;
          }
          Simplex newRoomShape = Simplex.get_instance(newEdgeLines);
          if (!newRoomShape.is_empty()) {

            TileShape newContainedShape = this.completedRoom.get_shape().intersection(newRoomShape);
            if (!newContainedShape.is_empty()) {
              FreeSpaceExpansionRoom newRoom =
                  p_autoroute_engine.add_incomplete_expansion_room(
                      newRoomShape, this.fromRoom.get_layer(), newContainedShape);
              ExpansionDoor newDoor = new ExpansionDoor(this.completedRoom, newRoom, 1);
              this.completedRoom.add_door(newDoor);
              newRoom.add_door(newDoor);
            }
          }
          if (lastTime) {
            break;
          }
          currTouchingSideNo = nextTouchingSideNo;
          startEdgeLine = null;
          firstTime = false;
        }
      }
      prevNeighbour = nextNeighbour;
    }
  }

  /**
   * Helper class to sort the doors of an expansion room counterclockwise around the border of the
   * room shape.
   */
  private class SortedRoomNeighbour implements Comparable<SortedRoomNeighbour> {

    private static final double c_dist_tolerance = 1;

    /** The search tree object of the neighbour room */
    public final SearchTreeObject searchTreeObject;

    /** The shape of the neighbour room */
    public final TileShape neighbourShape;

    /** The intersection of this ExpansionRoom shape with the neighbourShape */
    public final TileShape intersection;

    /** The side number of this room, where it touches the neighbour */
    public final int touchingSideNoOfRoom;

    /** The side number of the neighbour room, where it touches this room */
    public final int touchingSideNoOfNeighbourRoom;

    /**
     * True, if the intersection of this room and the neighbour is equal to a corner of this room
     */
    public final boolean roomTouchIsCorner;

    /**
     * True, if the intersection of this room and the neighbour is equal to a corner of the
     * neighbour room
     */
    public final boolean neighbourRoomTouchIsCorner;

    private Point precalculatedFirstCorner;
    private Point precalculatedLastCorner;

    public SortedRoomNeighbour(
        SearchTreeObject p_search_tree_object,
        TileShape p_neighbour_shape,
        TileShape p_intersection,
        int p_touching_side_no_of_room,
        int p_touching_side_no_of_neighbour_room,
        boolean p_room_touch_is_corner,
        boolean p_neighbour_room_touch_is_corner) {
      searchTreeObject = p_search_tree_object;
      neighbourShape = p_neighbour_shape;
      intersection = p_intersection;
      touchingSideNoOfRoom = p_touching_side_no_of_room;
      touchingSideNoOfNeighbourRoom = p_touching_side_no_of_neighbour_room;
      roomTouchIsCorner = p_room_touch_is_corner;
      neighbourRoomTouchIsCorner = p_neighbour_room_touch_is_corner;
    }

    /**
     * Compare function for or sorting the neighbours in counterclock sense around the border of the
     * room shape in ascending order.
     */
    @Override
    public int compareTo(SortedRoomNeighbour p_other) {
      int compareValue = this.touchingSideNoOfRoom - p_other.touchingSideNoOfRoom;
      if (compareValue != 0) {
        return compareValue;
      }
      FloatPoint compareCorner = roomShape.corner_approx(touchingSideNoOfRoom);
      double thisDistance = this.first_corner().to_float().distance(compareCorner);
      double otherDistance = p_other.first_corner().to_float().distance(compareCorner);
      double deltaDistance = thisDistance - otherDistance;
      if (Math.abs(deltaDistance) <= c_dist_tolerance) {
        // check corners for equality
        if (this.first_corner().equals(p_other.first_corner())) {
          // in this case compare the last corners
          double thisDistance2 = this.last_corner().to_float().distance(compareCorner);
          double otherDistance2 = p_other.last_corner().to_float().distance(compareCorner);
          deltaDistance = thisDistance2 - otherDistance2;
          if (Math.abs(deltaDistance) <= c_dist_tolerance) {
            if (this.neighbourRoomTouchIsCorner && p_other.neighbourRoomTouchIsCorner)
            // Otherwise there may be a short 1 dim. touch at a link between 2 trace lines.
            // In this case equality is ok, because the 2 intersection pieces with
            // the expansion room are identical, so that only 1 obstacle is needed.
            {
              int compareLineNo = touchingSideNoOfRoom;
              if (roomTouchIsCorner) {
                compareLineNo = roomShape.prev_no(compareLineNo);
              }
              Direction compareDir = roomShape.border_line(compareLineNo).direction().opposite();
              Line thisCompareLine =
                  this.neighbourShape.border_line(this.touchingSideNoOfNeighbourRoom);
              Line otherCompareLine =
                  p_other.neighbourShape.border_line(p_other.touchingSideNoOfNeighbourRoom);
              deltaDistance =
                  compareDir.compare_from(
                      thisCompareLine.direction(), otherCompareLine.direction());
            }
          }
        }
      }
      int res = Signum.as_int(deltaDistance);
      if (res == 0) {
        // Deterministic tie-breaker for identical geometry
        res = this.searchTreeObject.get_id_no() - p_other.searchTreeObject.get_id_no();
      }
      return res;
    }

    /** Returns the first corner of the intersection shape with the neighbour. */
    public Point first_corner() {
      if (precalculatedFirstCorner == null) {
        if (roomTouchIsCorner) {
          precalculatedFirstCorner = roomShape.corner(touchingSideNoOfRoom);
        } else if (neighbourRoomTouchIsCorner) {
          precalculatedFirstCorner = neighbourShape.corner(touchingSideNoOfNeighbourRoom);
        } else {
          Point currFirstCorner =
              neighbourShape.corner(neighbourShape.next_no(touchingSideNoOfNeighbourRoom));
          Line prevLine = roomShape.border_line(roomShape.prev_no(touchingSideNoOfRoom));
          if (prevLine.side_of(currFirstCorner) == Side.ON_THE_RIGHT) {
            precalculatedFirstCorner = currFirstCorner;
          } else // currFirstCorner is outside the door shape
          {
            precalculatedFirstCorner = roomShape.corner(touchingSideNoOfRoom);
          }
        }
      }
      return precalculatedFirstCorner;
    }

    /** Returns the last corner of the intersection shape with the neighbour. */
    public Point last_corner() {
      if (precalculatedLastCorner == null) {
        if (roomTouchIsCorner) {
          precalculatedLastCorner = roomShape.corner(touchingSideNoOfRoom);
        } else if (neighbourRoomTouchIsCorner) {
          precalculatedLastCorner = neighbourShape.corner(touchingSideNoOfNeighbourRoom);
        } else {
          Point currLastCorner = neighbourShape.corner(touchingSideNoOfNeighbourRoom);
          Line nextLine = roomShape.border_line(roomShape.next_no(touchingSideNoOfRoom));
          if (nextLine.side_of(currLastCorner) == Side.ON_THE_RIGHT) {
            precalculatedLastCorner = currLastCorner;
          } else // currLastCorner is outside the door shape
          {
            precalculatedLastCorner = roomShape.corner(roomShape.next_no(touchingSideNoOfRoom));
          }
        }
      }
      return precalculatedLastCorner;
    }
  }
}
