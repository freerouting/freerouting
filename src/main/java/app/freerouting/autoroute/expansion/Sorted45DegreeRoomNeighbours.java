package app.freerouting.autoroute.expansion;

import app.freerouting.autoroute.ItemAutorouteInfo;
import app.freerouting.autoroute.maze.AutorouteEngine;
import app.freerouting.board.Item;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.datastructures.ShapeTree;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Limits;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.LinkedList;
import java.util.SortedSet;
import java.util.TreeSet;

/** Sorted45DegreeRoomNeighbours. */
public final class Sorted45DegreeRoomNeighbours {

  public final CompleteExpansionRoom completedRoom;
  public final SortedSet<SortedRoomNeighbour> sortedNeighbours;
  private final ExpansionRoom fromRoom;
  private final IntOctagon roomShape;
  private final boolean[] edgeInteriorTouchesObstacle;

  /** Creates a new instance of Sorted45DegreeRoomNeighbours. */
  private Sorted45DegreeRoomNeighbours(
      ExpansionRoom fromRoom, CompleteExpansionRoom completedRoom) {
    this.fromRoom = fromRoom;
    this.completedRoom = completedRoom;
    roomShape = completedRoom.getShape().boundingOctagon();
    sortedNeighbours = new TreeSet<>();

    edgeInteriorTouchesObstacle = new boolean[8];
    for (int i = 0; i < 8; i++) {
      edgeInteriorTouchesObstacle[i] = false;
    }
  }

  /** Calculates room neighbours. */
  public static CompleteExpansionRoom calculate(
      ExpansionRoom room, AutorouteEngine autorouteEngine) {
    int netNumber = autorouteEngine.getNetNumber();
    Sorted45DegreeRoomNeighbours roomNeighbours =
        Sorted45DegreeRoomNeighbours.calculateNeighbours(
            room,
            netNumber,
            autorouteEngine.autorouteSearchTree,
            autorouteEngine.generateRoomIdNo());
    if (roomNeighbours == null) {
      return null;
    }

    // Check, that each side of the room shape has at least one touching neighbour.
    // Otherwise, improve the room shape by enlarging.
    boolean edgeRemoved =
        roomNeighbours.tryRemoveEdgeLine(netNumber, autorouteEngine.autorouteSearchTree);
    CompleteExpansionRoom result = roomNeighbours.completedRoom;
    if (edgeRemoved) {
      autorouteEngine.removeAllDoors(result);
      return calculate(room, autorouteEngine);
    }

    // Now calculate the new incomplete rooms together with the doors
    // between this room and the sorted neighbours.

    if (roomNeighbours.sortedNeighbours.isEmpty()) {
      if (result instanceof ObstacleExpansionRoom) {
        roomNeighbours.calculateEdgeIncompleteRoomsOfObstacleExpansionRoom(0, 7, autorouteEngine);
      }
    } else {
      roomNeighbours.calculateNewIncompleteRooms(autorouteEngine);
    }
    return result;
  }

  /**
   * Calculates all touching neighbours of room and sorts them in counterclock sense around the
   * boundary of the room shape.
   */
  private static Sorted45DegreeRoomNeighbours calculateNeighbours(
      ExpansionRoom room, int netNumber, ShapeSearchTree autorouteSearchTree, int roomIdNo) {
    TileShape roomShape = room.getShape();
    CompleteExpansionRoom completedRoom;
    if (room instanceof IncompleteFreeSpaceExpansionRoom) {
      completedRoom = new CompleteFreeSpaceExpansionRoom(roomShape, room.getLayer(), roomIdNo);
    } else if (room instanceof ObstacleExpansionRoom obstacleRoom) {
      completedRoom = obstacleRoom;
    } else {
      FRLogger.warn(
          "Sorted45DegreeRoomNeighbours.calculate_neighbours: unexpected expansion room type");
      return null;
    }
    IntOctagon roomOct = roomShape.boundingOctagon();
    Sorted45DegreeRoomNeighbours result = new Sorted45DegreeRoomNeighbours(room, completedRoom);
    Collection<ShapeTree.TreeEntry> overlappingObjects = new LinkedList<>();
    autorouteSearchTree.overlappingTreeEntries(roomShape, room.getLayer(), overlappingObjects);

    // Sort the overlapping objects deterministically to ensure parity with v1.9.
    ((LinkedList<ShapeTree.TreeEntry>) overlappingObjects)
        .sort(
            (e1, e2) -> {
              int idDiff =
                  ((SearchTreeObject) e1.object).getId() - ((SearchTreeObject) e2.object).getId();
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
      if ((completedRoom instanceof CompleteFreeSpaceExpansionRoom fsRoom)
          && !currentObject.isTraceObstacle(netNumber)) {
        fsRoom.calculateTargetDoors(currentEntry, netNumber, autorouteSearchTree);
        continue;
      }
      TileShape currentShape =
          currentObject.getTreeShape(autorouteSearchTree, currentEntry.shapeIndexInObject);
      IntOctagon currentOct = currentShape.boundingOctagon();
      IntOctagon intersection = roomOct.intersection(currentOct);
      int dimension = intersection.dimension();
      if (dimension > 1 && completedRoom instanceof ObstacleExpansionRoom obsRoom) {
        if (currentObject instanceof Item currentItem) {
          // only Obstacle expansion room may have a 2-dim overlap
          if (currentItem.isRoutable()) {
            ItemAutorouteInfo itemInfo = currentItem.getAutorouteInfo();
            ObstacleExpansionRoom currentOverlapRoom =
                itemInfo.getExpansionRoom(currentEntry.shapeIndexInObject, autorouteSearchTree);
            obsRoom.createOverlapDoor(currentOverlapRoom);
          }
        }
        continue;
      }
      if (dimension < 0) {
        // may happen at a corner from 2 diagonal lines with non integer  coordinates (--.5, ---.5).
        continue;
      }
      result.addSortedNeighbour(currentObject, currentOct, intersection);
      if (dimension > 0) {
        // make  sure, that there is a door to the neighbour room.
        ExpansionRoom neighbourRoom = null;
        if (currentObject instanceof ExpansionRoom exRoom) {
          neighbourRoom = exRoom;
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
            ExpansionDoor newDoor = new ExpansionDoor(completedRoom, neighbourRoom);
            neighbourRoom.addDoor(newDoor);
            completedRoom.addDoor(newDoor);
          }
        }
      }
    }
    return result;
  }

  private static IntOctagon removeNotTouchingBorderLines(
      IntOctagon roomOct, boolean[] edgeInteriorTouchesObstacle) {
    int leftX;
    if (edgeInteriorTouchesObstacle[6]) {
      leftX = roomOct.leftX;
    } else {
      leftX = -Limits.CRIT_INT;
    }

    int bottomY;
    if (edgeInteriorTouchesObstacle[0]) {
      bottomY = roomOct.bottomY;
    } else {
      bottomY = -Limits.CRIT_INT;
    }

    int rightX;
    if (edgeInteriorTouchesObstacle[2]) {
      rightX = roomOct.rightX;
    } else {
      rightX = Limits.CRIT_INT;
    }

    int topY;
    if (edgeInteriorTouchesObstacle[4]) {
      topY = roomOct.topY;
    } else {
      topY = Limits.CRIT_INT;
    }

    int upperLeftDiagonalX;
    if (edgeInteriorTouchesObstacle[5]) {
      upperLeftDiagonalX = roomOct.upperLeftDiagonalX;
    } else {
      upperLeftDiagonalX = -Limits.CRIT_INT;
    }

    int lowerRightDiagonalX;
    if (edgeInteriorTouchesObstacle[1]) {
      lowerRightDiagonalX = roomOct.lowerRightDiagonalX;
    } else {
      lowerRightDiagonalX = Limits.CRIT_INT;
    }

    int lowerLeftDiagonalX;
    if (edgeInteriorTouchesObstacle[7]) {
      lowerLeftDiagonalX = roomOct.lowerLeftDiagonalX;
    } else {
      lowerLeftDiagonalX = -Limits.CRIT_INT;
    }

    int upperRightDiagonalX;
    if (edgeInteriorTouchesObstacle[3]) {
      upperRightDiagonalX = roomOct.upperRightDiagonalX;
    } else {
      upperRightDiagonalX = Limits.CRIT_INT;
    }

    IntOctagon result =
        new IntOctagon(
            leftX,
            bottomY,
            rightX,
            topY,
            upperLeftDiagonalX,
            lowerRightDiagonalX,
            lowerLeftDiagonalX,
            upperRightDiagonalX);
    return result.normalize();
  }

  private void addSortedNeighbour(
      SearchTreeObject searchTreeObject, IntOctagon neighbourShape, IntOctagon intersection) {
    SortedRoomNeighbour newNeighbour =
        new SortedRoomNeighbour(searchTreeObject, neighbourShape, intersection);
    if (newNeighbour.lastTouchingSide >= 0) {
      sortedNeighbours.add(newNeighbour);
    }
  }

  /** Calculates an incomplete room for each edge side from fromSideIndex to toSideIndex. */
  private void calculateEdgeIncompleteRoomsOfObstacleExpansionRoom(
      int fromSideIndex, int toSideIndex, AutorouteEngine autorouteEngine) {
    if (!(this.fromRoom instanceof ObstacleExpansionRoom)) {
      FRLogger.warn(
          "Sorted45DegreeRoomNeighbours.calculate_side_incomplete_rooms_of_obstacle_expansion_room:"
              + " ObstacleExpansionRoom expected for this.fromRoom");
      return;
    }
    IntOctagon boardBoundingOct = autorouteEngine.board.getBoundingBox().boundingOctagon();
    IntPoint currentCorner = this.roomShape.corner(fromSideIndex);
    int currentSideIndex = fromSideIndex;
    for (; ; ) {
      int nextSideNo = (currentSideIndex + 1) % 8;
      IntPoint nextCorner = this.roomShape.corner(nextSideNo);
      if (!currentCorner.equals(nextCorner)) {
        int leftX = boardBoundingOct.leftX;
        int bottomY = boardBoundingOct.bottomY;
        int rightX = boardBoundingOct.rightX;
        int topY = boardBoundingOct.topY;
        int upperLeftDiagonalX = boardBoundingOct.upperLeftDiagonalX;
        int lowerRightDiagonalX = boardBoundingOct.lowerRightDiagonalX;
        int lowerLeftDiagonalX = boardBoundingOct.lowerLeftDiagonalX;
        int upperRightDiagonalX = boardBoundingOct.upperRightDiagonalX;
        switch (currentSideIndex) {
          case 0 -> topY = this.roomShape.bottomY;
          case 1 -> upperLeftDiagonalX = this.roomShape.lowerRightDiagonalX;
          case 2 -> leftX = this.roomShape.rightX;
          case 3 -> lowerLeftDiagonalX = this.roomShape.upperRightDiagonalX;
          case 4 -> bottomY = this.roomShape.topY;
          case 5 -> lowerRightDiagonalX = this.roomShape.upperLeftDiagonalX;
          case 6 -> rightX = this.roomShape.leftX;
          case 7 -> upperRightDiagonalX = this.roomShape.lowerLeftDiagonalX;
          default -> {
            FRLogger.warn(
                "SortedOrthoganelRoomNeighbours.calculate_edge_incomplete_rooms_of_obstacle_"
                    + "expansion_room: currentSideIndex illegal");
            return;
          }
        }
        insertIncompleteRoom(
            autorouteEngine,
            leftX,
            bottomY,
            rightX,
            topY,
            upperLeftDiagonalX,
            lowerRightDiagonalX,
            lowerLeftDiagonalX,
            upperRightDiagonalX);
      }
      if (currentSideIndex == toSideIndex) {
        break;
      }
      currentSideIndex = nextSideNo;
    }
  }

  /**
   * Check, that each side of the room shape has at least one touching neighbour. Otherwise, the
   * room shape will be improved the by enlarging. Returns true, if the room shape was changed.
   */
  private boolean tryRemoveEdgeLine(int netNumber, ShapeSearchTree autorouteSearchTree) {
    if (!(this.fromRoom instanceof IncompleteFreeSpaceExpansionRoom currentIncompleteRoom)) {
      return false;
    }
    if (!(currentIncompleteRoom.getShape() instanceof IntOctagon roomOct)) {
      FRLogger.warn(
          "Sorted45DegreeRoomNeighbours.tryRemoveEdgeLine: "
              + "IntOctagon expected for roomShape type");
      return false;
    }
    double roomArea = roomOct.area();

    boolean tryRemoveEdgeLines = false;
    for (int i = 0; i < 8; i++) {
      if (!this.edgeInteriorTouchesObstacle[i]) {
        FloatPoint prevCorner = this.roomShape.cornerApprox(i);
        FloatPoint nextCorner = this.roomShape.cornerApprox(this.roomShape.nextNo(i));
        if (prevCorner.distanceSquare(nextCorner) > 1) {
          tryRemoveEdgeLines = true;
          break;
        }
      }
    }

    if (tryRemoveEdgeLines) {
      // Touching neighbour missing at the edge side with index removeEdgeNo
      // Remove the edge line and restart the algorithm.
      FRLogger.trace(
          "ROOM_EDGE_REMOVE start"
              + ", net="
              + netNumber
              + ", layer="
              + currentIncompleteRoom.getLayer()
              + ", room_bounds="
              + describeBounds(roomOct.boundingBox()));

      IntOctagon enlargedOct =
          removeNotTouchingBorderLines(roomOct, this.edgeInteriorTouchesObstacle);
      FRLogger.trace(
          "ROOM_EDGE_REMOVE enlarged"
              + ", net="
              + netNumber
              + ", layer="
              + currentIncompleteRoom.getLayer()
              + ", enlarged_bounds="
              + describeBounds(enlargedOct.boundingBox()));
      FRLogger.trace(
          "ROOM_EDGE_REMOVE contained"
              + ", net="
              + netNumber
              + ", layer="
              + currentIncompleteRoom.getLayer()
              + ", type="
              + currentIncompleteRoom.getContainedShape().getClass().getSimpleName()
              + ", bounds="
              + describeBounds(currentIncompleteRoom.getContainedShape().boundingBox()));

      Collection<ExpansionDoor> doorList = this.completedRoom.getDoors();
      TileShape ignoreShape = null;
      SearchTreeObject ignoreObject = null;
      double maxDoorArea = 0;
      for (ExpansionDoor currentDoor : doorList) {
        // insert the overlapping doors with CompleteFreeSpaceExpansionRooms
        // for the information in complete_shape about the objects to ignore.
        if (currentDoor.dimension == 2) {
          CompleteExpansionRoom otherRoom = currentDoor.otherRoom(this.completedRoom);
          {
            if (otherRoom instanceof CompleteFreeSpaceExpansionRoom room) {
              TileShape currentDoorShape = currentDoor.getShape();
              double currentDoorArea = currentDoorShape.area();
              if (currentDoorArea > maxDoorArea) {
                maxDoorArea = currentDoorArea;
                ignoreShape = currentDoorShape;
                ignoreObject = room;
              }
            }
          }
        }
      }
      IncompleteFreeSpaceExpansionRoom enlargedRoom =
          new IncompleteFreeSpaceExpansionRoom(
              enlargedOct,
              currentIncompleteRoom.getLayer(),
              currentIncompleteRoom.getContainedShape());
      Collection<IncompleteFreeSpaceExpansionRoom> newRooms =
          autorouteSearchTree.completeShape(enlargedRoom, netNumber, ignoreObject, ignoreShape);
      FRLogger.trace(
          "ROOM_EDGE_REMOVE complete_shape"
              + ", net="
              + netNumber
              + ", layer="
              + currentIncompleteRoom.getLayer()
              + ", candidate_count="
              + newRooms.size());
      if (newRooms.size() == 1) {
        // Check, that the area increases to prevent endless loop.
        IncompleteFreeSpaceExpansionRoom newRoom = newRooms.iterator().next();
        if (newRoom.getShape().area() > roomArea) {
          FRLogger.trace(
              "ROOM_EDGE_REMOVE applied"
                  + ", net="
                  + netNumber
                  + ", layer="
                  + currentIncompleteRoom.getLayer()
                  + ", old_bounds="
                  + describeBounds(roomOct.boundingBox())
                  + ", newBounds="
                  + describeBounds(newRoom.getShape().boundingBox()));
          currentIncompleteRoom.setShape(newRoom.getShape());
          currentIncompleteRoom.setContainedShape(newRoom.getContainedShape());
          return true;
        }
      }
    }
    return false;
  }

  private static String describeBounds(IntBox bounds) {
    return "[(" + bounds.ll.x + "," + bounds.ll.y + ")..(" + bounds.ur.x + "," + bounds.ur.y + ")]";
  }

  /** Inserts a new incomplete room with an octagon shape. */
  private void insertIncompleteRoom(
      AutorouteEngine autorouteEngine,
      int leftX,
      int bottomY,
      int rightX,
      int topY,
      int upperLeftDiagonalX,
      int lowerRightDiagonalX,
      int lowerLeftDiagonalX,
      int upperRightDiagonalX) {
    IntOctagon newIncompleteRoomShape =
        new IntOctagon(
            leftX,
            bottomY,
            rightX,
            topY,
            upperLeftDiagonalX,
            lowerRightDiagonalX,
            lowerLeftDiagonalX,
            upperRightDiagonalX);
    newIncompleteRoomShape = newIncompleteRoomShape.normalize();
    if (newIncompleteRoomShape.dimension() == 2) {
      IntOctagon newContainedShape = this.roomShape.intersection(newIncompleteRoomShape);
      if (!newContainedShape.isEmpty()) {
        int doorDimension = newContainedShape.dimension();
        if (doorDimension > 0) {
          FreeSpaceExpansionRoom newRoom =
              autorouteEngine.addIncompleteExpansionRoom(
                  newIncompleteRoomShape, this.fromRoom.getLayer(), newContainedShape);
          ExpansionDoor newDoor = new ExpansionDoor(this.completedRoom, newRoom, doorDimension);
          this.completedRoom.addDoor(newDoor);
          newRoom.addDoor(newDoor);
        }
      }
    }
  }

  private void calculateNewIncompleteRoomsForObstacleExpansionRoom(
      SortedRoomNeighbour prevNeighbour,
      SortedRoomNeighbour nextNeighbour,
      AutorouteEngine autorouteEngine) {
    int fromSideIndex = prevNeighbour.lastTouchingSide;
    int toSideIndex = nextNeighbour.firstTouchingSide;
    if (fromSideIndex == toSideIndex && prevNeighbour != nextNeighbour) {
      // no return in case of only 1 neighbour.
      return;
    }
    IntOctagon boardBoundingOct = autorouteEngine.board.boundingBox.boundingOctagon();

    // insert the new incomplete room from prevNeighbour to the next corner of the room shape.

    int leftX = boardBoundingOct.leftX;
    int bottomY = boardBoundingOct.bottomY;
    int rightX = boardBoundingOct.rightX;
    int topY = boardBoundingOct.topY;
    int upperLeftDiagonalX = boardBoundingOct.upperLeftDiagonalX;
    int lowerRightDiagonalX = boardBoundingOct.lowerRightDiagonalX;
    int lowerLeftDiagonalX = boardBoundingOct.lowerLeftDiagonalX;
    int upperRightDiagonalX = boardBoundingOct.upperRightDiagonalX;
    switch (fromSideIndex) {
      case 0 -> {
        topY = this.roomShape.bottomY;
        upperLeftDiagonalX = prevNeighbour.intersection.lowerRightDiagonalX;
      }
      case 1 -> {
        upperLeftDiagonalX = this.roomShape.lowerRightDiagonalX;
        leftX = prevNeighbour.intersection.rightX;
      }
      case 2 -> {
        leftX = this.roomShape.rightX;
        lowerLeftDiagonalX = prevNeighbour.intersection.upperRightDiagonalX;
      }
      case 3 -> {
        lowerLeftDiagonalX = this.roomShape.upperRightDiagonalX;
        bottomY = prevNeighbour.intersection.topY;
      }
      case 4 -> {
        bottomY = this.roomShape.topY;
        lowerRightDiagonalX = prevNeighbour.intersection.upperLeftDiagonalX;
      }
      case 5 -> {
        lowerRightDiagonalX = this.roomShape.upperLeftDiagonalX;
        rightX = prevNeighbour.intersection.leftX;
      }
      case 6 -> {
        rightX = this.roomShape.leftX;
        upperRightDiagonalX = prevNeighbour.intersection.lowerLeftDiagonalX;
      }
      case 7 -> {
        upperRightDiagonalX = this.roomShape.lowerLeftDiagonalX;
        topY = prevNeighbour.intersection.bottomY;
      }
      default -> {}
    }
    insertIncompleteRoom(
        autorouteEngine,
        leftX,
        bottomY,
        rightX,
        topY,
        upperLeftDiagonalX,
        lowerRightDiagonalX,
        lowerLeftDiagonalX,
        upperRightDiagonalX);

    // insert the new incomplete room from prevNeighbour to the next corner of the room shape.

    leftX = boardBoundingOct.leftX;
    bottomY = boardBoundingOct.bottomY;
    rightX = boardBoundingOct.rightX;
    topY = boardBoundingOct.topY;
    upperLeftDiagonalX = boardBoundingOct.upperLeftDiagonalX;
    lowerRightDiagonalX = boardBoundingOct.lowerRightDiagonalX;
    lowerLeftDiagonalX = boardBoundingOct.lowerLeftDiagonalX;
    upperRightDiagonalX = boardBoundingOct.upperRightDiagonalX;

    switch (toSideIndex) {
      case 0 -> {
        topY = this.roomShape.bottomY;
        upperRightDiagonalX = nextNeighbour.intersection.lowerLeftDiagonalX;
      }
      case 1 -> {
        upperLeftDiagonalX = this.roomShape.lowerRightDiagonalX;
        topY = nextNeighbour.intersection.bottomY;
      }
      case 2 -> {
        leftX = this.roomShape.rightX;
        upperLeftDiagonalX = nextNeighbour.intersection.lowerRightDiagonalX;
      }
      case 3 -> {
        lowerLeftDiagonalX = this.roomShape.upperRightDiagonalX;
        leftX = nextNeighbour.intersection.rightX;
      }
      case 4 -> {
        bottomY = this.roomShape.topY;
        lowerLeftDiagonalX = nextNeighbour.intersection.upperRightDiagonalX;
      }
      case 5 -> {
        lowerRightDiagonalX = this.roomShape.upperLeftDiagonalX;
        bottomY = nextNeighbour.intersection.topY;
      }
      case 6 -> {
        rightX = this.roomShape.leftX;
        lowerRightDiagonalX = nextNeighbour.intersection.upperLeftDiagonalX;
      }
      case 7 -> {
        upperRightDiagonalX = this.roomShape.lowerLeftDiagonalX;
        rightX = nextNeighbour.intersection.leftX;
      }
      default -> {}
    }
    insertIncompleteRoom(
        autorouteEngine,
        leftX,
        bottomY,
        rightX,
        topY,
        upperLeftDiagonalX,
        lowerRightDiagonalX,
        lowerLeftDiagonalX,
        upperRightDiagonalX);

    // Insert the new incomplete rooms on the intermediate free sides of the obstacle expansion
    // room.
    int currentFromSideNo = (fromSideIndex + 1) % 8;
    if (currentFromSideNo == toSideIndex) {
      return;
    }
    int currentToSideNo = (toSideIndex + 7) % 8;
    this.calculateEdgeIncompleteRoomsOfObstacleExpansionRoom(
        currentFromSideNo, currentToSideNo, autorouteEngine);
  }

  private void calculateNewIncompleteRooms(AutorouteEngine autorouteEngine) {
    IntOctagon boardBoundingOct = autorouteEngine.board.boundingBox.boundingOctagon();
    SortedRoomNeighbour prevNeighbour = this.sortedNeighbours.getLast();
    if (this.fromRoom instanceof ObstacleExpansionRoom && this.sortedNeighbours.size() == 1) {
      // ObstacleExpansionRoom has only 1 neighbour
      calculateNewIncompleteRoomsForObstacleExpansionRoom(
          prevNeighbour, prevNeighbour, autorouteEngine);
      return;
    }

    for (SortedRoomNeighbour nextNeighbour : this.sortedNeighbours) {
      boolean insertIncompleteRoom;

      if (this.completedRoom instanceof ObstacleExpansionRoom
          && this.sortedNeighbours.size() == 2) {
        // check, if this site is touching or open.
        TileShape intersection =
            nextNeighbour.intersection.intersection(prevNeighbour.intersection);
        if (intersection.isEmpty()) {
          insertIncompleteRoom = true;
        } else if (intersection.dimension() >= 1) {
          insertIncompleteRoom = false;
        } else { // dimension = 1
          // touch at a corner of the room shape
          if (prevNeighbour.lastTouchingSide == nextNeighbour.firstTouchingSide) {
            // touch along the side of the room shape
            insertIncompleteRoom = false;
          } else {
            insertIncompleteRoom =
                prevNeighbour.lastTouchingSide != (nextNeighbour.firstTouchingSide + 1) % 8;
          }
        }
      } else {
        // the 2 neighbours do not touch
        insertIncompleteRoom = !nextNeighbour.intersection.intersects(prevNeighbour.intersection);
      }

      if (insertIncompleteRoom) {
        // create a door to a new incomplete expansion room between
        // the last corner of the previous neighbour and the first corner of the
        // current neighbour

        if (this.fromRoom instanceof ObstacleExpansionRoom
            && nextNeighbour.firstTouchingSide != prevNeighbour.lastTouchingSide) {
          calculateNewIncompleteRoomsForObstacleExpansionRoom(
              prevNeighbour, nextNeighbour, autorouteEngine);
        } else {
          int lx = boardBoundingOct.leftX;
          int ly = boardBoundingOct.bottomY;
          int rx = boardBoundingOct.rightX;
          int uy = boardBoundingOct.topY;
          int ulx = boardBoundingOct.upperLeftDiagonalX;
          int lrx = boardBoundingOct.lowerRightDiagonalX;
          int llx = boardBoundingOct.lowerLeftDiagonalX;
          int urx = boardBoundingOct.upperRightDiagonalX;

          switch (nextNeighbour.firstTouchingSide) {
            case 0 -> {
              if (prevNeighbour.intersection.lowerLeftDiagonalX
                  < nextNeighbour.intersection.lowerLeftDiagonalX) {
                urx = nextNeighbour.intersection.lowerLeftDiagonalX;
                uy = prevNeighbour.intersection.bottomY;
                if (prevNeighbour.lastTouchingSide == 0) {
                  ulx = prevNeighbour.intersection.lowerRightDiagonalX;
                }
              } else if (prevNeighbour.intersection.lowerLeftDiagonalX
                  > nextNeighbour.intersection.lowerLeftDiagonalX) {
                rx = nextNeighbour.intersection.leftX;
                urx = prevNeighbour.intersection.lowerLeftDiagonalX;
              } else { // prevNeighbour.intersection.llx == nextNeighbour.intersection.llx
                urx = nextNeighbour.intersection.lowerLeftDiagonalX;
              }
            }
            case 1 -> {
              if (prevNeighbour.intersection.bottomY < nextNeighbour.intersection.bottomY) {
                uy = nextNeighbour.intersection.bottomY;
                ulx = prevNeighbour.intersection.lowerRightDiagonalX;
                if (prevNeighbour.lastTouchingSide == 1) {
                  lx = prevNeighbour.intersection.rightX;
                }
              } else if (prevNeighbour.intersection.bottomY > nextNeighbour.intersection.bottomY) {
                uy = prevNeighbour.intersection.bottomY;
                urx = nextNeighbour.intersection.lowerLeftDiagonalX;
              } else { // prevNeighbour.intersection.ly == nextNeighbour.intersection.ly
                uy = nextNeighbour.intersection.bottomY;
              }
            }
            case 2 -> {
              if (prevNeighbour.intersection.lowerRightDiagonalX
                  > nextNeighbour.intersection.lowerRightDiagonalX) {
                ulx = nextNeighbour.intersection.lowerRightDiagonalX;
                lx = prevNeighbour.intersection.rightX;
                if (prevNeighbour.lastTouchingSide == 2) {
                  llx = prevNeighbour.intersection.upperRightDiagonalX;
                }
              } else if (prevNeighbour.intersection.lowerRightDiagonalX
                  < nextNeighbour.intersection.lowerRightDiagonalX) {
                uy = nextNeighbour.intersection.bottomY;
                ulx = prevNeighbour.intersection.lowerRightDiagonalX;
              } else { // prevNeighbour.intersection.lrx == nextNeighbour.intersection.lrx
                ulx = nextNeighbour.intersection.lowerRightDiagonalX;
              }
            }
            case 3 -> {
              if (prevNeighbour.intersection.rightX > nextNeighbour.intersection.rightX) {
                lx = nextNeighbour.intersection.rightX;
                llx = prevNeighbour.intersection.upperRightDiagonalX;
                if (prevNeighbour.lastTouchingSide == 3) {
                  ly = prevNeighbour.intersection.topY;
                }
              } else if (prevNeighbour.intersection.rightX < nextNeighbour.intersection.rightX) {
                lx = prevNeighbour.intersection.rightX;
                ulx = nextNeighbour.intersection.lowerRightDiagonalX;
              } else { // prevNeighbour.intersection.ry == nextNeighbour.intersection.ry
                lx = nextNeighbour.intersection.rightX;
              }
            }
            case 4 -> {
              if (prevNeighbour.intersection.upperRightDiagonalX
                  > nextNeighbour.intersection.upperRightDiagonalX) {
                llx = nextNeighbour.intersection.upperRightDiagonalX;
                ly = prevNeighbour.intersection.topY;
                if (prevNeighbour.lastTouchingSide == 4) {
                  lrx = prevNeighbour.intersection.upperLeftDiagonalX;
                }
              } else if (prevNeighbour.intersection.upperRightDiagonalX
                  < nextNeighbour.intersection.upperRightDiagonalX) {
                lx = nextNeighbour.intersection.rightX;
                llx = prevNeighbour.intersection.upperRightDiagonalX;
              } else { // prevNeighbour.intersection.urx == nextNeighbour.intersection.urx
                llx = nextNeighbour.intersection.upperRightDiagonalX;
              }
            }
            case 5 -> {
              if (prevNeighbour.intersection.topY > nextNeighbour.intersection.topY) {
                ly = nextNeighbour.intersection.topY;
                lrx = prevNeighbour.intersection.upperLeftDiagonalX;
                if (prevNeighbour.lastTouchingSide == 5) {
                  rx = prevNeighbour.intersection.leftX;
                }
              } else if (prevNeighbour.intersection.topY < nextNeighbour.intersection.topY) {
                ly = prevNeighbour.intersection.topY;
                llx = nextNeighbour.intersection.upperRightDiagonalX;
              } else { // prevNeighbour.intersection.uy == nextNeighbour.intersection.uy
                ly = nextNeighbour.intersection.topY;
              }
            }
            case 6 -> {
              if (prevNeighbour.intersection.upperLeftDiagonalX
                  < nextNeighbour.intersection.upperLeftDiagonalX) {
                lrx = nextNeighbour.intersection.upperLeftDiagonalX;
                rx = prevNeighbour.intersection.leftX;
                if (prevNeighbour.lastTouchingSide == 6) {
                  urx = prevNeighbour.intersection.lowerLeftDiagonalX;
                }
              } else if (prevNeighbour.intersection.upperLeftDiagonalX
                  > nextNeighbour.intersection.upperLeftDiagonalX) {
                ly = nextNeighbour.intersection.topY;
                lrx = prevNeighbour.intersection.upperLeftDiagonalX;
              } else { // prevNeighbour.intersection.ulx == nextNeighbour.intersection.ulx
                lrx = nextNeighbour.intersection.upperLeftDiagonalX;
              }
            }
            case 7 -> {
              if (prevNeighbour.intersection.leftX < nextNeighbour.intersection.leftX) {
                rx = nextNeighbour.intersection.leftX;
                urx = prevNeighbour.intersection.lowerLeftDiagonalX;
                if (prevNeighbour.lastTouchingSide == 7) {
                  uy = prevNeighbour.intersection.bottomY;
                }
              } else if (prevNeighbour.intersection.leftX > nextNeighbour.intersection.leftX) {
                rx = prevNeighbour.intersection.leftX;
                lrx = nextNeighbour.intersection.upperLeftDiagonalX;
              } else { // prevNeighbour.intersection.lx == nextNeighbour.intersection.lx
                rx = nextNeighbour.intersection.leftX;
              }
            }
            default ->
                FRLogger.warn(
                    "Sorted45DegreeRoomNeighbour.calculate_new_incomplete: illegal touching side");
          }
          insertIncompleteRoom(autorouteEngine, lx, ly, rx, uy, ulx, lrx, llx, urx);
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

    /** The search tree object of the neighbour room. */
    public final SearchTreeObject searchTreeObject;

    /** The shape of the neighbour room. */
    public final IntOctagon shape;

    /** The intersection of this ExpansionRoom shape with the neighbourShape. */
    public final IntOctagon intersection;

    /** The first side of the room shape, where the neighbourShape touches. */
    public final int firstTouchingSide;

    /** The last side of the room shape, where the neighbourShape touches. */
    public final int lastTouchingSide;

    /**
     * Creates a new instance of SortedRoomNeighbour and calculates the first and last touching.
     * sides with the room shape. this.lastTouchingSide will be -1, if sorting did not work because
     * the roomShape is contained in the neighbour shape.
     */
    public SortedRoomNeighbour(
        SearchTreeObject searchTreeObject, IntOctagon neighbourShape, IntOctagon intersection) {
      this.searchTreeObject = searchTreeObject;
      this.shape = neighbourShape;
      this.intersection = intersection;

      if (intersection.bottomY == roomShape.bottomY
          && intersection.lowerLeftDiagonalX > roomShape.lowerLeftDiagonalX) {
        this.firstTouchingSide = 0;
      } else if (intersection.lowerRightDiagonalX == roomShape.lowerRightDiagonalX
          && intersection.bottomY > roomShape.bottomY) {
        this.firstTouchingSide = 1;
      } else if (intersection.rightX == roomShape.rightX
          && intersection.lowerRightDiagonalX < roomShape.lowerRightDiagonalX) {
        this.firstTouchingSide = 2;
      } else if (intersection.upperRightDiagonalX == roomShape.upperRightDiagonalX
          && intersection.rightX < roomShape.rightX) {
        this.firstTouchingSide = 3;
      } else if (intersection.topY == roomShape.topY
          && intersection.upperRightDiagonalX < roomShape.upperRightDiagonalX) {
        this.firstTouchingSide = 4;
      } else if (intersection.upperLeftDiagonalX == roomShape.upperLeftDiagonalX
          && intersection.topY < roomShape.topY) {
        this.firstTouchingSide = 5;
      } else if (intersection.leftX == roomShape.leftX
          && intersection.upperLeftDiagonalX > roomShape.upperLeftDiagonalX) {
        this.firstTouchingSide = 6;
      } else if (intersection.lowerLeftDiagonalX == roomShape.lowerLeftDiagonalX
          && intersection.leftX > roomShape.leftX) {
        this.firstTouchingSide = 7;
      } else {
        // the roomShape may be contained in the neighbourShape
        this.firstTouchingSide = -1;
        this.lastTouchingSide = -1;
        return;
      }

      if (intersection.lowerLeftDiagonalX == roomShape.lowerLeftDiagonalX
          && intersection.bottomY > roomShape.bottomY) {
        this.lastTouchingSide = 7;
      } else if (intersection.leftX == roomShape.leftX
          && intersection.lowerLeftDiagonalX > roomShape.lowerLeftDiagonalX) {
        this.lastTouchingSide = 6;
      } else if (intersection.upperLeftDiagonalX == roomShape.upperLeftDiagonalX
          && intersection.leftX > roomShape.leftX) {
        this.lastTouchingSide = 5;
      } else if (intersection.topY == roomShape.topY
          && intersection.upperLeftDiagonalX > roomShape.upperLeftDiagonalX) {
        this.lastTouchingSide = 4;
      } else if (intersection.upperRightDiagonalX == roomShape.upperRightDiagonalX
          && intersection.topY < roomShape.topY) {
        this.lastTouchingSide = 3;
      } else if (intersection.rightX == roomShape.rightX
          && intersection.upperRightDiagonalX < roomShape.upperRightDiagonalX) {
        this.lastTouchingSide = 2;
      } else if (intersection.lowerRightDiagonalX == roomShape.lowerRightDiagonalX
          && intersection.rightX < roomShape.rightX) {
        this.lastTouchingSide = 1;
      } else if (intersection.bottomY == roomShape.bottomY
          && intersection.lowerRightDiagonalX < roomShape.lowerRightDiagonalX) {
        this.lastTouchingSide = 0;
      } else {
        // the roomShape may be contained in the neighbourShape
        this.lastTouchingSide = -1;
        return;
      }

      int nextSideNo = this.firstTouchingSide;
      for (; ; ) {
        int currentSideIndex = nextSideNo;
        nextSideNo = (nextSideNo + 1) % 8;
        if (!edgeInteriorTouchesObstacle[currentSideIndex]) {
          boolean touchOnlyAtCorner = false;
          if (currentSideIndex == this.firstTouchingSide) {
            if (intersection.corner(currentSideIndex).equals(roomShape.corner(nextSideNo))) {
              touchOnlyAtCorner = true;
            }
          }
          if (currentSideIndex == this.lastTouchingSide) {
            if (intersection.corner(nextSideNo).equals(roomShape.corner(currentSideIndex))) {
              touchOnlyAtCorner = true;
            }
          }
          if (!touchOnlyAtCorner) {
            edgeInteriorTouchesObstacle[currentSideIndex] = true;
          }
        }
        if (currentSideIndex == this.lastTouchingSide) {
          break;
        }
      }
    }

    /**
     * Compare function for or sorting the neighbours in counterclock sense around the border of the
     * room shape in ascending order.
     */
    @Override
    public int compareTo(SortedRoomNeighbour other) {
      if (this.firstTouchingSide > other.firstTouchingSide) {
        return 1;
      }
      if (this.firstTouchingSide < other.firstTouchingSide) {
        return -1;
      }

      // now the first touch of this and other is at the same side
      IntOctagon is1 = this.intersection;
      IntOctagon is2 = other.intersection;
      int cmpValue;

      switch (firstTouchingSide) {
        case 0 -> cmpValue = is1.corner(0).x - is2.corner(0).x;
        case 1 -> cmpValue = is1.corner(1).x - is2.corner(1).x;
        case 2 -> cmpValue = is1.corner(2).y - is2.corner(2).y;
        case 3 -> cmpValue = is1.corner(3).y - is2.corner(3).y;
        case 4 -> cmpValue = is2.corner(4).x - is1.corner(4).x;
        case 5 -> cmpValue = is2.corner(5).x - is1.corner(5).x;
        case 6 -> cmpValue = is2.corner(6).y - is1.corner(6).y;
        case 7 -> cmpValue = is2.corner(7).y - is1.corner(7).y;
        default -> {
          FRLogger.warn("SortedRoomNeighbour.compareTo: firstTouchingSide out of range ");
          return 0;
        }
      }

      if (cmpValue == 0) {
        // The first touching points of this neighbour and other with the room shape are equal.
        // Compare the last touching points.
        int thisTouchingSideDiff = (this.lastTouchingSide - this.firstTouchingSide + 8) % 8;
        int otherTouchingSideDiff = (other.lastTouchingSide - other.firstTouchingSide + 8) % 8;
        if (thisTouchingSideDiff > otherTouchingSideDiff) {
          return 1;
        }
        if (thisTouchingSideDiff < otherTouchingSideDiff) {
          return -1;
        }
        // now the last touch of this and other is at the same side
        switch (lastTouchingSide) {
          case 0 -> cmpValue = is1.corner(1).x - is2.corner(1).x;
          case 1 -> cmpValue = is1.corner(2).x - is2.corner(2).x;
          case 2 -> cmpValue = is1.corner(3).y - is2.corner(3).y;
          case 3 -> cmpValue = is1.corner(4).y - is2.corner(4).y;
          case 4 -> cmpValue = is2.corner(5).x - is1.corner(5).x;
          case 5 -> cmpValue = is2.corner(6).x - is1.corner(6).x;
          case 6 -> cmpValue = is2.corner(7).y - is1.corner(7).y;
          case 7 -> cmpValue = is2.corner(0).y - is1.corner(0).y;
          default -> {}
        }
      }
      if (cmpValue == 0) {
        // Deterministic tie-breaker for identical geometry
        cmpValue = this.searchTreeObject.getId() - other.searchTreeObject.getId();
      }
      return cmpValue;
    }
  }
}
