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

  /** Creates a new instance of SortedRoomNeighbours. */
  private SortedRoomNeighbours(ExpansionRoom fromRoom, CompleteExpansionRoom completedRoom) {
    this.fromRoom = fromRoom;
    this.completedRoom = completedRoom;
    roomShape = completedRoom.getShape();
    sortedNeighbours = new TreeSet<>();
    ownNetObjects = new LinkedList<>();
  }

  /**
   * Calculates the neighbour rooms of an expansion room. The neighbour rooms will be sorted in
   * counterclock sense around the border of the shape of room. Overlapping neighbours containing
   * an item may be stored in an unordered list.
   */
  public static CompleteExpansionRoom calculate(
      ExpansionRoom room, AutorouteEngine autorouteEngine) {
    int netNo = autorouteEngine.getNetNo();

    SortedRoomNeighbours roomNeighbours =
        calculateNeighbours(
            room,
            netNo,
            autorouteEngine.autorouteSearchTree,
            autorouteEngine.generateRoomIdNo());

    // Check, that each side of the room shape has at least one touching neighbour.
    // Otherwise, improve the room shape by enlarging.

    boolean edgeRemoved = roomNeighbours.tryRemoveEdge(netNo, autorouteEngine.autorouteSearchTree);
    CompleteExpansionRoom result = roomNeighbours.completedRoom;
    if (edgeRemoved) {
      autorouteEngine.removeAllDoors(result);
      return calculate(room, autorouteEngine);
    }

    // Now calculate the new incomplete rooms together with the doors
    // between this room and the sorted neighbours.
    if (roomNeighbours.sortedNeighbours.isEmpty()) {
      if (result instanceof ObstacleExpansionRoom) {
        calculateIncompleteRoomsWithEmptyNeighbours(
            (ObstacleExpansionRoom) room, autorouteEngine);
      }
    } else {
      roomNeighbours.calculateNewIncompleteRooms(autorouteEngine);
      if (result.getShape().dimension() < 2) {
        FRLogger.trace(
            "AutorouteEngine.calculate_new_incomplete_rooms_with_more_than_1_neighbour: "
                + "unexpected dimension for smoothened_shape");
      }
    }

    if (result instanceof CompleteFreeSpaceExpansionRoom freeRoom) {
      calculateTargetDoors(freeRoom, roomNeighbours.ownNetObjects, autorouteEngine);
    }
    return result;
  }

  private static void calculateIncompleteRoomsWithEmptyNeighbours(
      ObstacleExpansionRoom room, AutorouteEngine autorouteEngine) {
    TileShape roomShape = room.getShape();
    for (int i = 0; i < roomShape.borderLineCount(); i++) {
      Line currLine = roomShape.borderLine(i);
      if (SortedRoomNeighbours.insertDoorOk(room, currLine)) {
        Line[] shapeLine = new Line[1];
        shapeLine[0] = currLine.opposite();
        TileShape newRoomShape = new Simplex(shapeLine);
        TileShape newContainedShape = roomShape.intersection(newRoomShape);
        FreeSpaceExpansionRoom newRoom =
            autorouteEngine.addIncompleteExpansionRoom(
                newRoomShape, room.getLayer(), newContainedShape);
        ExpansionDoor newDoor = new ExpansionDoor(room, newRoom, 1);
        room.addDoor(newDoor);
        newRoom.addDoor(newDoor);
      }
    }
  }

  private static void calculateTargetDoors(
      CompleteFreeSpaceExpansionRoom room,
      Collection<ShapeTree.TreeEntry> ownNetObjects,
      AutorouteEngine autorouteEngine) {
    if (!ownNetObjects.isEmpty()) {
      room.setNetDependent();
    }
    for (ShapeTree.TreeEntry currentEntry : ownNetObjects) {
      if (currentEntry.object instanceof Connectable currentObject) {
        if (currentObject.containsNet(autorouteEngine.getNetNo())) {
          TileShape currentConnectionShape =
              currentObject.getTraceConnectionShape(
                  autorouteEngine.autorouteSearchTree, currentEntry.shapeIndexInObject);
          if (currentConnectionShape != null
              && room.getShape().intersects(currentConnectionShape)) {
            Item currentItem = (Item) currentObject;
            TargetItemExpansionDoor newTargetDoor =
                new TargetItemExpansionDoor(
                    currentItem,
                    currentEntry.shapeIndexInObject,
                    room,
                    autorouteEngine.autorouteSearchTree);
            room.addTargetDoor(newTargetDoor);
          }
        }
      }
    }
  }

  private static SortedRoomNeighbours calculateNeighbours(
      ExpansionRoom room, int netNo, ShapeSearchTree autorouteSearchTree, int roomIdNo) {
    TileShape roomShape = room.getShape();
    CompleteExpansionRoom completedRoom;
    if (room instanceof IncompleteFreeSpaceExpansionRoom) {
      completedRoom = new CompleteFreeSpaceExpansionRoom(roomShape, room.getLayer(), roomIdNo);
    } else if (room instanceof ObstacleExpansionRoom obstacleRoom) {
      completedRoom = obstacleRoom;
    } else {
      FRLogger.warn("SortedRoomNeighbours.calculate: unexpected expansion room type");
      return null;
    }
    SortedRoomNeighbours result = new SortedRoomNeighbours(room, completedRoom);
    Collection<ShapeTree.TreeEntry> overlappingObjects = new LinkedList<>();
    autorouteSearchTree.overlappingTreeEntries(roomShape, room.getLayer(), overlappingObjects);

    // Sort the overlapping objects deterministically to ensure parity with v1.9.
    ((LinkedList<ShapeTree.TreeEntry>) overlappingObjects)
        .sort(
            (e1, e2) -> {
              int idDiff =
                  ((SearchTreeObject) e1.object).getIdNo()
                      - ((SearchTreeObject) e2.object).getIdNo();
              if (idDiff != 0) {
                return idDiff;
              }
              return e1.shapeIndexInObject - e2.shapeIndexInObject;
            });

    // Calculate the touching neighbour objects and sort them in counterclock sense
    // around the border of the room shape.
    for (ShapeTree.TreeEntry currentEntry : overlappingObjects) {
      SearchTreeObject currentObject = (SearchTreeObject) currentEntry.object;
      if (currentObject == room) {
        continue;
      }
      if ((room instanceof IncompleteFreeSpaceExpansionRoom)
          && !currentObject.isTraceObstacle(netNo)) {
        // delay processing the target doors until the room shape will not change anymore
        result.ownNetObjects.add(currentEntry);
        continue;
      }
      TileShape currentShape =
          currentObject.getTreeShape(autorouteSearchTree, currentEntry.shapeIndexInObject);
      TileShape intersection = roomShape.intersection(currentShape);
      int dimension = intersection.dimension();
      if (dimension > 1) {
        if (completedRoom instanceof ObstacleExpansionRoom obstacleRoom
            && currentObject instanceof Item currentItem) {
          // only Obstacle expansion room may have a 2-dim overlap
          if (currentItem.isRoutable()) {
            ItemAutorouteInfo itemInfo = currentItem.getAutorouteInfo();
            ObstacleExpansionRoom currentOverlapRoom =
                itemInfo.getExpansionRoom(currentEntry.shapeIndexInObject, autorouteSearchTree);
            obstacleRoom.createOverlapDoor(currentOverlapRoom);
          }
        } else {
          FRLogger.trace(
              "SortedRoomNeighbours.calculate: "
                  + "unexpected area overlap of free space expansion room");
        }
        continue;
      }
      if (dimension < 0) {
        FRLogger.debug("SortedRoomNeighbours.calculate: dimension >= 0 expected");
        continue;
      }
      if (dimension == 1) {
        int[] touchingSides = roomShape.touchingSides(currentShape);
        if (touchingSides.length != 2) {
          FRLogger.debug("SortedRoomNeighbours.calculate: touchingSides length 2 expected");
          continue;
        }
        result.addSortedNeighbour(
            currentObject,
            currentShape,
            intersection,
            touchingSides[0],
            touchingSides[1],
            false,
            false);
        // make  sure, that there is a door to the neighbour room.
        ExpansionRoom neighbourRoom = null;
        if (currentObject instanceof ExpansionRoom targetExpansionRoom) {
          neighbourRoom = targetExpansionRoom;
        } else if (currentObject instanceof Item currentItem) {
          if (currentItem.isRoutable()) {
            // expand the item for ripup and pushing purposes
            ItemAutorouteInfo itemInfo = currentItem.getAutorouteInfo();
            neighbourRoom =
                itemInfo.getExpansionRoom(currentEntry.shapeIndexInObject, autorouteSearchTree);
          }
        }
        if (neighbourRoom != null) {
          if (SortedRoomNeighbours.insertDoorOk(completedRoom, neighbourRoom, intersection)) {
            ExpansionDoor newDoor = new ExpansionDoor(completedRoom, neighbourRoom, 1);
            neighbourRoom.addDoor(newDoor);
            completedRoom.addDoor(newDoor);
          }
        }
      } else {
        // dimension = 0
        Point touchingPoint = intersection.corner(0);
        int roomCornerNo = roomShape.equalsCorner(touchingPoint);
        boolean roomTouchIsCorner;
        int touchingSideNoOfRoom;
        if (roomCornerNo >= 0) {
          roomTouchIsCorner = true;
          touchingSideNoOfRoom = roomCornerNo;
        } else {
          roomTouchIsCorner = false;
          touchingSideNoOfRoom = roomShape.containsOnBorderLineNo(touchingPoint);
          if (touchingSideNoOfRoom < 0) {
            FRLogger.debug("SortedRoomNeighbours.calculate: touchingSideNoOfRoom >= 0 expected");
          }
        }
        int neighbourRoomCornerNo = currentShape.equalsCorner(touchingPoint);
        boolean neighbourRoomTouchIsCorner;
        int touchingSideNoOfNeighbourRoom;
        if (neighbourRoomCornerNo >= 0) {
          neighbourRoomTouchIsCorner = true;
          // The previous border line is preferred to make the shape of the incomplete room as big
          // as possible
          touchingSideNoOfNeighbourRoom = currentShape.prevNo(neighbourRoomCornerNo);
        } else {
          neighbourRoomTouchIsCorner = false;
          touchingSideNoOfNeighbourRoom = currentShape.containsOnBorderLineNo(touchingPoint);
          if (touchingSideNoOfNeighbourRoom < 0) {
            FRLogger.debug(
                "SortedRoomNeighbours.calculate: touchingSideNoOfNeighbourRoom >= 0 expected");
          }
        }
        result.addSortedNeighbour(
            currentObject,
            currentShape,
            intersection,
            touchingSideNoOfRoom,
            touchingSideNoOfNeighbourRoom,
            roomTouchIsCorner,
            neighbourRoomTouchIsCorner);
      }
    }
    return result;
  }

  /** Door shape is expected to have dimension 1. */
  static boolean insertDoorOk(ExpansionRoom room1, ExpansionRoom room2, TileShape doorShape) {
    if (room1.doorExists(room2)) {
      return false;
    }
    if (room1 instanceof ObstacleExpansionRoom obsRoom1
        && room2 instanceof ObstacleExpansionRoom obsRoom2) {
      Item firstItem = obsRoom1.getItem();
      Item secondItem = obsRoom2.getItem();
      // insert only overlap_doors between items of the same net for performance reasons.
      return firstItem.sharesNet(secondItem);
    }
    if (!(room1 instanceof ObstacleExpansionRoom) && !(room2 instanceof ObstacleExpansionRoom)) {
      return true;
    }
    // Insert 1 dimensional doors of trace rooms only, if they are parallel to the trace line.
    // Otherwise, there may be check ripup problems with entering at the wrong side at a fork.
    Line doorLine = null;
    Point prevCorner = doorShape.corner(0);
    int cornerCount = doorShape.borderLineCount();
    for (int i = 1; i < cornerCount; i++) {
      Point currCorner = doorShape.corner(i);
      if (!currCorner.equals(prevCorner)) {
        doorLine = doorShape.borderLine(i - 1);
        break;
      }
      prevCorner = currCorner;
    }
    if (room1 instanceof ObstacleExpansionRoom obsRoom) {
      if (!insertDoorOk(obsRoom, doorLine)) {
        return false;
      }
    }
    if (room2 instanceof ObstacleExpansionRoom obsRoom) {
      return insertDoorOk(obsRoom, doorLine);
    }
    return true;
  }

  /**
   * Insert 1 dimensional doors for the first and the last room of a trace rooms only, if they are
   * parallel to the trace line. Otherwise, there may be check ripup problems with entering at the
   * wrong side at a fork.
   */
  private static boolean insertDoorOk(ObstacleExpansionRoom room, Line doorLine) {
    if (doorLine == null) {
      FRLogger.warn("SortedRoomNeighbours.insert_door_ok: doorLine is null");
      return false;
    }
    Item currItem = room.getItem();
    if (currItem instanceof PolylineTrace currTrace) {
      int roomIndex = room.getIndexInItem();
      if (roomIndex == 0 || roomIndex == currTrace.tileShapeCount() - 1) {
        Line currTraceLine = currTrace.polyline().arr[roomIndex + 1];
        return currTraceLine.isParallel(doorLine);
      }
    }
    return true;
  }

  private void addSortedNeighbour(
      SearchTreeObject searchTreeObject,
      TileShape neighbourShape,
      TileShape intersection,
      int touchingSideNoOfRoom,
      int touchingSideNoOfNeighbourRoom,
      boolean roomTouchIsCorner,
      boolean neighbourRoomTouchIsCorner) {
    SortedRoomNeighbour newNeighbour =
        new SortedRoomNeighbour(
            searchTreeObject,
            neighbourShape,
            intersection,
            touchingSideNoOfRoom,
            touchingSideNoOfNeighbourRoom,
            roomTouchIsCorner,
            neighbourRoomTouchIsCorner);
    sortedNeighbours.add(newNeighbour);
  }

  /**
   * Checks that each side of the room shape has at least one touching neighbour. Otherwise, the
   * room shape will be improved by enlarging. Returns true if the room shape was changed.
   */
  private boolean tryRemoveEdge(int netNo, ShapeSearchTree autorouteSearchTree) {
    if (!(this.fromRoom instanceof IncompleteFreeSpaceExpansionRoom currIncompleteRoom)) {
      return false;
    }
    int removeEdgeNo = -1;
    Simplex roomSimplex = currIncompleteRoom.getShape().toSimplex();
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

    if (removeEdgeNo < 0 && currEdgeNo < roomSimplex.borderLineCount()) {
      // missing touching neighbour at the last edge side.
      removeEdgeNo = currEdgeNo;
    }

    if (removeEdgeNo >= 0) {
      // Touching neighbour missing at the edge side with index removeEdgeNo
      // Remove the edge line and restart the algorithm.
      FRLogger.trace(
          "ROOM_EDGE_REMOVE start"
              + ", net="
              + netNo
              + ", layer="
              + currIncompleteRoom.getLayer()
              + ", removeEdge="
              + removeEdgeNo
              + ", room_bounds="
              + currIncompleteRoom.getShape().boundingBox());
      Simplex enlargedShape = roomSimplex.removeBorderLine(removeEdgeNo);
      IncompleteFreeSpaceExpansionRoom enlargedRoom =
          new IncompleteFreeSpaceExpansionRoom(
              enlargedShape,
              currIncompleteRoom.getLayer(),
              currIncompleteRoom.getContainedShape());
      Collection<IncompleteFreeSpaceExpansionRoom> newRooms =
          autorouteSearchTree.completeShape(enlargedRoom, netNo, null, null);
      FRLogger.trace(
          "ROOM_EDGE_REMOVE complete_shape"
              + ", net="
              + netNo
              + ", layer="
              + currIncompleteRoom.getLayer()
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
      if (newShape.getShape().area() > roomShapeArea) {
        removeEdge = true;
      }
      if (removeEdge) {
        Iterator<IncompleteFreeSpaceExpansionRoom> it2 = newRooms.iterator();
        IncompleteFreeSpaceExpansionRoom newRoom = it2.next();
        FRLogger.trace(
            "ROOM_EDGE_REMOVE applied"
                + ", net="
                + netNo
                + ", layer="
                + currIncompleteRoom.getLayer()
                + ", removeEdge="
                + removeEdgeNo
                + ", old_bounds="
                + currIncompleteRoom.getShape().boundingBox()
                + ", newBounds="
                + newRoom.getShape().boundingBox());
        currIncompleteRoom.setShape(newRoom.getShape());
        currIncompleteRoom.setContainedShape(newRoom.getContainedShape());
        return true;
      }
    }
    return false;
  }

  /**
   * Called from calculate_doors(). The shape of the room result may change inside this function.
   */
  public void calculateNewIncompleteRooms(AutorouteEngine autorouteEngine) {
    SortedRoomNeighbour prevNeighbour = this.sortedNeighbours.getLast();
    Simplex roomSimplex = this.fromRoom.getShape().toSimplex();
    for (SortedRoomNeighbour nextNeighbour : this.sortedNeighbours) {
      int firstTouchingSideNo = prevNeighbour.touchingSideNoOfRoom;
      int lastTouchingSideNo = nextNeighbour.touchingSideNoOfRoom;

      int currNextNo = roomSimplex.nextNo(firstTouchingSideNo);
      boolean intersectionWithPrevNeighbourEndsAtCorner =
          (firstTouchingSideNo != lastTouchingSideNo
                  || prevNeighbour == this.sortedNeighbours.getLast())
              && prevNeighbour.lastCorner().equals(roomSimplex.corner(currNextNo));
      boolean intersectionWithNextNeighbourStartsAtCorner =
          (firstTouchingSideNo != lastTouchingSideNo
                  || prevNeighbour == this.sortedNeighbours.getLast())
              && nextNeighbour.firstCorner().equals(roomSimplex.corner(lastTouchingSideNo));

      if (intersectionWithPrevNeighbourEndsAtCorner) {
        firstTouchingSideNo = currNextNo;
      }

      if (intersectionWithNextNeighbourStartsAtCorner) {
        lastTouchingSideNo = roomSimplex.prevNo(lastTouchingSideNo);
      }
      boolean neighboursTouch = false;

      if (this.sortedNeighbours.size() > 1) {
        neighboursTouch = prevNeighbour.lastCorner().equals(nextNeighbour.firstCorner());
      }

      if (!neighboursTouch) {
        // create a door to a new incomplete expansion room between
        // the last corner of the previous neighbour and the first corner of the
        // current neighbour.
        int lastBoundingLineNo = prevNeighbour.touchingSideNoOfNeighbourRoom;
        if (!(intersectionWithPrevNeighbourEndsAtCorner || prevNeighbour.roomTouchIsCorner)) {
          lastBoundingLineNo = prevNeighbour.neighbourShape.prevNo(lastBoundingLineNo);
        }

        int firstBoundingLineNo = nextNeighbour.touchingSideNoOfNeighbourRoom;
        if (!(intersectionWithNextNeighbourStartsAtCorner
            || nextNeighbour.neighbourRoomTouchIsCorner)) {
          firstBoundingLineNo = nextNeighbour.neighbourShape.nextNo(firstBoundingLineNo);
        }
        Line startEdgeLine =
            nextNeighbour.neighbourShape.borderLine(firstBoundingLineNo).opposite();
        // startEdgeLine is only used for the first new incomplete room.
        Line middleEdgeLine = null;
        int currTouchingSideNo = lastTouchingSideNo;
        boolean firstTime = true;
        // The loop goes backwards from the edge line of nextNeighbour to the edge line of
        // prevNeighbour.
        for (; ; ) {
          boolean cornerCutOff = false;
          if (this.fromRoom instanceof IncompleteFreeSpaceExpansionRoom incompleteRoom) {
            if (currTouchingSideNo == lastTouchingSideNo
                && firstTouchingSideNo != lastTouchingSideNo) {
              // Create a new line approximately from the last corner of the previous
              // neighbour to the first corner of the next neighbour to cut off
              // the outstanding corners of the room shape in the empty space.
              // That is only tried in the first pass of the loop.
              IntPoint cutLineStart = prevNeighbour.lastCorner().toFloat().round();
              IntPoint cutLineEnd = nextNeighbour.firstCorner().toFloat().round();
              Line cutLine = new Line(cutLineStart, cutLineEnd);
              TileShape cutHalfPlane = TileShape.getInstance(cutLine);
              ((CompleteFreeSpaceExpansionRoom) this.completedRoom)
                  .setShape(this.completedRoom.getShape().intersection(cutHalfPlane));
              // Otherwise p_room.containedShape would no longer be contained
              // in the shape after cutting of the corner.
              cornerCutOff =
                  incompleteRoom.getContainedShape().sideOf(cutLine) == Side.ON_THE_LEFT;
              if (cornerCutOff) {
                middleEdgeLine = cutLine.opposite();
              }
            }
          }
          final int nextTouchingSideNo = roomSimplex.prevNo(currTouchingSideNo);

          if (!cornerCutOff) {
            middleEdgeLine = roomSimplex.borderLine(currTouchingSideNo).opposite();
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
            endEdgeLine = prevNeighbour.neighbourShape.borderLine(lastBoundingLineNo).opposite();
            if (endEdgeLine.direction().sideOf(middleLineDir) != Side.ON_THE_LEFT) {
              // Concave corner between the middle and the last line.
              // Maybe there is a 1 point touch.
              endEdgeLine = null;
            }
          } else {
            endEdgeLine = null;
          }

          if (startEdgeLine != null
              && middleLineDir.sideOf(startEdgeLine.direction()) != Side.ON_THE_LEFT) {
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
          Simplex newRoomShape = Simplex.getInstance(newEdgeLines);
          if (!newRoomShape.isEmpty()) {

            TileShape newContainedShape = this.completedRoom.getShape().intersection(newRoomShape);
            if (!newContainedShape.isEmpty()) {
              FreeSpaceExpansionRoom newRoom =
                  autorouteEngine.addIncompleteExpansionRoom(
                      newRoomShape, this.fromRoom.getLayer(), newContainedShape);
              ExpansionDoor newDoor = new ExpansionDoor(this.completedRoom, newRoom, 1);
              this.completedRoom.addDoor(newDoor);
              newRoom.addDoor(newDoor);
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

    /** The search tree object of the neighbour room. */
    public final SearchTreeObject searchTreeObject;

    /** The shape of the neighbour room. */
    public final TileShape neighbourShape;

    /** The intersection of this ExpansionRoom shape with the neighbourShape. */
    public final TileShape intersection;

    /** The side number of this room, where it touches the neighbour. */
    public final int touchingSideNoOfRoom;

    /** The side number of the neighbour room, where it touches this room. */
    public final int touchingSideNoOfNeighbourRoom;

    /**
     * True, if the intersection of this room and the neighbour is equal to a corner of this room.
     */
    public final boolean roomTouchIsCorner;

    /**
     * True, if the intersection of this room and the neighbour is equal to a corner of the
     * neighbour room.
     */
    public final boolean neighbourRoomTouchIsCorner;

    private Point precalculatedFirstCorner;
    private Point precalculatedLastCorner;

    public SortedRoomNeighbour(
        SearchTreeObject searchTreeObject,
        TileShape neighbourShape,
        TileShape intersection,
        int touchingSideNoOfRoom,
        int touchingSideNoOfNeighbourRoom,
        boolean roomTouchIsCorner,
        boolean neighbourRoomTouchIsCorner) {
      this.searchTreeObject = searchTreeObject;
      this.neighbourShape = neighbourShape;
      this.intersection = intersection;
      this.touchingSideNoOfRoom = touchingSideNoOfRoom;
      this.touchingSideNoOfNeighbourRoom = touchingSideNoOfNeighbourRoom;
      this.roomTouchIsCorner = roomTouchIsCorner;
      this.neighbourRoomTouchIsCorner = neighbourRoomTouchIsCorner;
    }

    /**
     * Compare function for sorting the neighbours in counterclock sense around the border of the
     * room shape in ascending order.
     */
    @Override
    public int compareTo(SortedRoomNeighbour other) {
      int compareValue = this.touchingSideNoOfRoom - other.touchingSideNoOfRoom;
      if (compareValue != 0) {
        return compareValue;
      }
      FloatPoint compareCorner = roomShape.cornerApprox(touchingSideNoOfRoom);
      double thisDistance = this.firstCorner().toFloat().distance(compareCorner);
      double otherDistance = other.firstCorner().toFloat().distance(compareCorner);
      double deltaDistance = thisDistance - otherDistance;
      if (Math.abs(deltaDistance) <= c_dist_tolerance) {
        // check corners for equality
        if (this.firstCorner().equals(other.firstCorner())) {
          // in this case compare the last corners
          double thisDistance2 = this.lastCorner().toFloat().distance(compareCorner);
          double otherDistance2 = other.lastCorner().toFloat().distance(compareCorner);
          deltaDistance = thisDistance2 - otherDistance2;
          if (Math.abs(deltaDistance) <= c_dist_tolerance) {
            if (this.neighbourRoomTouchIsCorner && other.neighbourRoomTouchIsCorner) {
              // Otherwise there may be a short 1 dim. touch at a link between 2 trace lines.
              // In this case equality is ok, because the 2 intersection pieces with
              // the expansion room are identical, so that only 1 obstacle is needed.
              int compareLineNo = touchingSideNoOfRoom;
              if (roomTouchIsCorner) {
                compareLineNo = roomShape.prevNo(compareLineNo);
              }
              Direction compareDir = roomShape.borderLine(compareLineNo).direction().opposite();
              Line thisCompareLine =
                  this.neighbourShape.borderLine(this.touchingSideNoOfNeighbourRoom);
              Line otherCompareLine =
                  other.neighbourShape.borderLine(other.touchingSideNoOfNeighbourRoom);
              deltaDistance =
                  compareDir.compareFrom(thisCompareLine.direction(), otherCompareLine.direction());
            }
          }
        }
      }
      int res = Signum.asInt(deltaDistance);
      if (res == 0) {
        // Deterministic tie-breaker for identical geometry
        res = this.searchTreeObject.getIdNo() - other.searchTreeObject.getIdNo();
      }
      return res;
    }

    /** Returns the first corner of the intersection shape with the neighbour. */
    public Point firstCorner() {
      if (precalculatedFirstCorner == null) {
        if (roomTouchIsCorner) {
          precalculatedFirstCorner = roomShape.corner(touchingSideNoOfRoom);
        } else if (neighbourRoomTouchIsCorner) {
          precalculatedFirstCorner = neighbourShape.corner(touchingSideNoOfNeighbourRoom);
        } else {
          Point currFirstCorner =
              neighbourShape.corner(neighbourShape.nextNo(touchingSideNoOfNeighbourRoom));
          Line prevLine = roomShape.borderLine(roomShape.prevNo(touchingSideNoOfRoom));
          if (prevLine.sideOf(currFirstCorner) == Side.ON_THE_RIGHT) {
            precalculatedFirstCorner = currFirstCorner;
          } else {
            // currFirstCorner is outside the door shape
            precalculatedFirstCorner = roomShape.corner(touchingSideNoOfRoom);
          }
        }
      }
      return precalculatedFirstCorner;
    }

    /** Returns the last corner of the intersection shape with the neighbour. */
    public Point lastCorner() {
      if (precalculatedLastCorner == null) {
        if (roomTouchIsCorner) {
          precalculatedLastCorner = roomShape.corner(touchingSideNoOfRoom);
        } else if (neighbourRoomTouchIsCorner) {
          precalculatedLastCorner = neighbourShape.corner(touchingSideNoOfNeighbourRoom);
        } else {
          Point currLastCorner = neighbourShape.corner(touchingSideNoOfNeighbourRoom);
          Line nextLine = roomShape.borderLine(roomShape.nextNo(touchingSideNoOfRoom));
          if (nextLine.sideOf(currLastCorner) == Side.ON_THE_RIGHT) {
            precalculatedLastCorner = currLastCorner;
          } else {
            // currLastCorner is outside the door shape
            precalculatedLastCorner = roomShape.corner(roomShape.nextNo(touchingSideNoOfRoom));
          }
        }
      }
      return precalculatedLastCorner;
    }
  }
}
