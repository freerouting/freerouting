package app.freerouting.autoroute;

import app.freerouting.board.Item;
import app.freerouting.board.SearchTreeObject;
import app.freerouting.board.ShapeSearchTree;
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

public final class Sorted45DegreeRoomNeighbours {

  public final CompleteExpansionRoom completedRoom;
  public final SortedSet<SortedRoomNeighbour> sortedNeighbours;
  private final ExpansionRoom fromRoom;
  private final IntOctagon roomShape;
  private final boolean[] edgeInteriorTouchesObstacle;

  /** Creates a new instance of Sorted45DegreeRoomNeighbours */
  private Sorted45DegreeRoomNeighbours(
      ExpansionRoom pFromRoom, CompleteExpansionRoom pCompletedRoom) {
    fromRoom = pFromRoom;
    completedRoom = pCompletedRoom;
    roomShape = pCompletedRoom.getShape().boundingOctagon();
    sortedNeighbours = new TreeSet<>();

    edgeInteriorTouchesObstacle = new boolean[8];
    for (int i = 0; i < 8; i++) {
      edgeInteriorTouchesObstacle[i] = false;
    }
  }

  public static CompleteExpansionRoom calculate(
      ExpansionRoom pRoom, AutorouteEngine pAutorouteEngine) {
    int netNo = pAutorouteEngine.getNetNo();
    Sorted45DegreeRoomNeighbours roomNeighbours =
        Sorted45DegreeRoomNeighbours.calculateNeighbours(
            pRoom,
            netNo,
            pAutorouteEngine.autorouteSearchTree,
            pAutorouteEngine.generateRoomIdNo());
    if (roomNeighbours == null) {
      return null;
    }

    // Check, that each side of the room shape has at least one touching neighbour.
    // Otherwise, improve the room shape by enlarging.
    boolean edgeRemoved =
        roomNeighbours.tryRemoveEdgeLine(netNo, pAutorouteEngine.autorouteSearchTree);
    CompleteExpansionRoom result = roomNeighbours.completedRoom;
    if (edgeRemoved) {
      pAutorouteEngine.removeAllDoors(result);
      return calculate(pRoom, pAutorouteEngine);
    }

    // Now calculate the new incomplete rooms together with the doors
    // between this room and the sorted neighbours.

    if (roomNeighbours.sortedNeighbours.isEmpty()) {
      if (result instanceof ObstacleExpansionRoom) {
        roomNeighbours.calculateEdgeIncompleteRoomsOfObstacleExpansionRoom(0, 7, pAutorouteEngine);
      }
    } else {
      roomNeighbours.calculateNewIncompleteRooms(pAutorouteEngine);
    }
    return result;
  }

  /**
   * Calculates all touching neighbours of p_room and sorts them in counterclock sense around the
   * boundary of the room shape.
   */
  private static Sorted45DegreeRoomNeighbours calculateNeighbours(
      ExpansionRoom pRoom, int pNetNo, ShapeSearchTree pAutorouteSearchTree, int pRoomIdNo) {
    TileShape roomShape = pRoom.getShape();
    CompleteExpansionRoom completedRoom;
    if (pRoom instanceof IncompleteFreeSpaceExpansionRoom) {
      completedRoom = new CompleteFreeSpaceExpansionRoom(roomShape, pRoom.getLayer(), pRoomIdNo);
    } else if (pRoom instanceof ObstacleExpansionRoom room) {
      completedRoom = room;
    } else {
      FRLogger.warn(
          "Sorted45DegreeRoomNeighbours.calculate_neighbours: unexpected expansion room type");
      return null;
    }
    IntOctagon roomOct = roomShape.boundingOctagon();
    Sorted45DegreeRoomNeighbours result = new Sorted45DegreeRoomNeighbours(pRoom, completedRoom);
    Collection<ShapeTree.TreeEntry> overlappingObjects = new LinkedList<>();
    pAutorouteSearchTree.overlappingTreeEntries(roomShape, pRoom.getLayer(), overlappingObjects);

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
    for (ShapeTree.TreeEntry currEntry : overlappingObjects) {
      SearchTreeObject currObject = (SearchTreeObject) currEntry.object;
      if (currObject == pRoom) {
        continue;
      }
      if ((completedRoom instanceof CompleteFreeSpaceExpansionRoom fs_room)
          && !currObject.isTraceObstacle(pNetNo)) {
        fs_room.calculateTargetDoors(currEntry, pNetNo, pAutorouteSearchTree);
        continue;
      }
      TileShape currShape =
          currObject.getTreeShape(pAutorouteSearchTree, currEntry.shapeIndexInObject);
      IntOctagon currOct = currShape.boundingOctagon();
      IntOctagon intersection = roomOct.intersection(currOct);
      int dimension = intersection.dimension();
      if (dimension > 1 && completedRoom instanceof ObstacleExpansionRoom obs_room) {
        if (currObject instanceof Item currItem) {
          // only Obstacle expansion room may have a 2-dim overlap
          if (currItem.isRoutable()) {
            ItemAutorouteInfo itemInfo = currItem.getAutorouteInfo();
            ObstacleExpansionRoom currOverlapRoom =
                itemInfo.getExpansionRoom(currEntry.shapeIndexInObject, pAutorouteSearchTree);
            obs_room.createOverlapDoor(currOverlapRoom);
          }
        }
        continue;
      }
      if (dimension < 0) {
        // may happen at a corner from 2 diagonal lines with non integer  coordinates (--.5, ---.5).
        continue;
      }
      result.addSortedNeighbour(currObject, currOct, intersection);
      if (dimension > 0) {
        // make  sure, that there is a door to the neighbour room.
        ExpansionRoom neighbourRoom = null;
        if (currObject instanceof ExpansionRoom ex_room) {
          neighbourRoom = ex_room;
        } else if (currObject instanceof Item currItem) {
          if (currItem.isRoutable()) {
            // expand the item for ripup and pushing purposes
            ItemAutorouteInfo itemInfo = currItem.getAutorouteInfo();
            neighbourRoom =
                itemInfo.getExpansionRoom(currEntry.shapeIndexInObject, pAutorouteSearchTree);
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
      IntOctagon pRoomOct, boolean[] pEdgeInteriorTouchesObstacle) {
    int lx;
    if (pEdgeInteriorTouchesObstacle[6]) {
      lx = pRoomOct.leftX;
    } else {
      lx = -Limits.CRIT_INT;
    }

    int ly;
    if (pEdgeInteriorTouchesObstacle[0]) {
      ly = pRoomOct.bottomY;
    } else {
      ly = -Limits.CRIT_INT;
    }

    int rx;
    if (pEdgeInteriorTouchesObstacle[2]) {
      rx = pRoomOct.rightX;
    } else {
      rx = Limits.CRIT_INT;
    }

    int uy;
    if (pEdgeInteriorTouchesObstacle[4]) {
      uy = pRoomOct.topY;
    } else {
      uy = Limits.CRIT_INT;
    }

    int ulx;
    if (pEdgeInteriorTouchesObstacle[5]) {
      ulx = pRoomOct.upperLeftDiagonalX;
    } else {
      ulx = -Limits.CRIT_INT;
    }

    int lrx;
    if (pEdgeInteriorTouchesObstacle[1]) {
      lrx = pRoomOct.lowerRightDiagonalX;
    } else {
      lrx = Limits.CRIT_INT;
    }

    int llx;
    if (pEdgeInteriorTouchesObstacle[7]) {
      llx = pRoomOct.lowerLeftDiagonalX;
    } else {
      llx = -Limits.CRIT_INT;
    }

    int urx;
    if (pEdgeInteriorTouchesObstacle[3]) {
      urx = pRoomOct.upperRightDiagonalX;
    } else {
      urx = Limits.CRIT_INT;
    }

    IntOctagon result = new IntOctagon(lx, ly, rx, uy, ulx, lrx, llx, urx);
    return result.normalize();
  }

  private void addSortedNeighbour(
      SearchTreeObject pSearchTreeObject, IntOctagon pNeighbourShape, IntOctagon pIntersection) {
    SortedRoomNeighbour newNeighbour =
        new SortedRoomNeighbour(pSearchTreeObject, pNeighbourShape, pIntersection);
    if (newNeighbour.lastTouchingSide >= 0) {
      sortedNeighbours.add(newNeighbour);
    }
  }

  /** Calculates an incomplete room for each edge side from p_from_side_no to p_to_side_no. */
  private void calculateEdgeIncompleteRoomsOfObstacleExpansionRoom(
      int pFromSideNo, int pToSideNo, AutorouteEngine pAutorouteEngine) {
    if (!(this.fromRoom instanceof ObstacleExpansionRoom)) {
      FRLogger.warn(
          "Sorted45DegreeRoomNeighbours.calculate_side_incomplete_rooms_of_obstacle_expansion_room: ObstacleExpansionRoom expected for this.fromRoom");
      return;
    }
    IntOctagon boardBoundingOct = pAutorouteEngine.board.getBoundingBox().boundingOctagon();
    IntPoint currCorner = this.roomShape.corner(pFromSideNo);
    int currSideNo = pFromSideNo;
    for (; ; ) {
      int nextSideNo = (currSideNo + 1) % 8;
      IntPoint nextCorner = this.roomShape.corner(nextSideNo);
      if (!currCorner.equals(nextCorner)) {
        int lx = boardBoundingOct.leftX;
        int ly = boardBoundingOct.bottomY;
        int rx = boardBoundingOct.rightX;
        int uy = boardBoundingOct.topY;
        int ulx = boardBoundingOct.upperLeftDiagonalX;
        int lrx = boardBoundingOct.lowerRightDiagonalX;
        int llx = boardBoundingOct.lowerLeftDiagonalX;
        int urx = boardBoundingOct.upperRightDiagonalX;
        switch (currSideNo) {
          case 0 -> uy = this.roomShape.bottomY;
          case 1 -> ulx = this.roomShape.lowerRightDiagonalX;
          case 2 -> lx = this.roomShape.rightX;
          case 3 -> llx = this.roomShape.upperRightDiagonalX;
          case 4 -> ly = this.roomShape.topY;
          case 5 -> lrx = this.roomShape.upperLeftDiagonalX;
          case 6 -> rx = this.roomShape.leftX;
          case 7 -> urx = this.roomShape.lowerLeftDiagonalX;
          default -> {
            FRLogger.warn(
                "SortedOrthoganelRoomNeighbours.calculate_edge_incomplete_rooms_of_obstacle_expansion_room: currSideNo illegal");
            return;
          }
        }
        insertIncompleteRoom(pAutorouteEngine, lx, ly, rx, uy, ulx, lrx, llx, urx);
      }
      if (currSideNo == pToSideNo) {
        break;
      }
      currSideNo = nextSideNo;
    }
  }

  /**
   * Check, that each side of the room shape has at least one touching neighbour. Otherwise, the
   * room shape will be improved the by enlarging. Returns true, if the room shape was changed.
   */
  private boolean tryRemoveEdgeLine(int pNetNo, ShapeSearchTree pAutorouteSearchTree) {
    if (!(this.fromRoom instanceof IncompleteFreeSpaceExpansionRoom curr_incomplete_room)) {
      return false;
    }
    if (!(curr_incomplete_room.getShape() instanceof IntOctagon roomOct)) {
      FRLogger.warn(
          "Sorted45DegreeRoomNeighbours.try_remove_edge_line: IntOctagon expected for roomShape type");
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
              + pNetNo
              + ", layer="
              + curr_incomplete_room.getLayer()
              + ", room_bounds="
              + describeBounds(roomOct.boundingBox()));

      IntOctagon enlargedOct =
          removeNotTouchingBorderLines(roomOct, this.edgeInteriorTouchesObstacle);
      FRLogger.trace(
          "ROOM_EDGE_REMOVE enlarged"
              + ", net="
              + pNetNo
              + ", layer="
              + curr_incomplete_room.getLayer()
              + ", enlarged_bounds="
              + describeBounds(enlargedOct.boundingBox()));
      FRLogger.trace(
          "ROOM_EDGE_REMOVE contained"
              + ", net="
              + pNetNo
              + ", layer="
              + curr_incomplete_room.getLayer()
              + ", type="
              + curr_incomplete_room.getContainedShape().getClass().getSimpleName()
              + ", bounds="
              + describeBounds(curr_incomplete_room.getContainedShape().boundingBox()));

      Collection<ExpansionDoor> doorList = this.completedRoom.getDoors();
      TileShape ignoreShape = null;
      SearchTreeObject ignoreObject = null;
      double maxDoorArea = 0;
      for (ExpansionDoor currDoor : doorList) {
        // insert the overlapping doors with CompleteFreeSpaceExpansionRooms
        // for the information in complete_shape about the objects to ignore.
        if (currDoor.dimension == 2) {
          CompleteExpansionRoom otherRoom = currDoor.otherRoom(this.completedRoom);
          {
            if (otherRoom instanceof CompleteFreeSpaceExpansionRoom room) {
              TileShape currDoorShape = currDoor.getShape();
              double currDoorArea = currDoorShape.area();
              if (currDoorArea > maxDoorArea) {
                maxDoorArea = currDoorArea;
                ignoreShape = currDoorShape;
                ignoreObject = room;
              }
            }
          }
        }
      }
      IncompleteFreeSpaceExpansionRoom enlargedRoom =
          new IncompleteFreeSpaceExpansionRoom(
              enlargedOct,
              curr_incomplete_room.getLayer(),
              curr_incomplete_room.getContainedShape());
      Collection<IncompleteFreeSpaceExpansionRoom> newRooms =
          pAutorouteSearchTree.completeShape(enlargedRoom, pNetNo, ignoreObject, ignoreShape);
      FRLogger.trace(
          "ROOM_EDGE_REMOVE complete_shape"
              + ", net="
              + pNetNo
              + ", layer="
              + curr_incomplete_room.getLayer()
              + ", candidate_count="
              + newRooms.size());
      if (newRooms.size() == 1) {
        // Check, that the area increases to prevent endless loop.
        IncompleteFreeSpaceExpansionRoom newRoom = newRooms.iterator().next();
        if (newRoom.getShape().area() > roomArea) {
          FRLogger.trace(
              "ROOM_EDGE_REMOVE applied"
                  + ", net="
                  + pNetNo
                  + ", layer="
                  + curr_incomplete_room.getLayer()
                  + ", old_bounds="
                  + describeBounds(roomOct.boundingBox())
                  + ", newBounds="
                  + describeBounds(newRoom.getShape().boundingBox()));
          curr_incomplete_room.setShape(newRoom.getShape());
          curr_incomplete_room.setContainedShape(newRoom.getContainedShape());
          return true;
        }
      }
    }
    return false;
  }

  private static String describeBounds(IntBox pBounds) {
    return "[("
        + pBounds.ll.x
        + ","
        + pBounds.ll.y
        + ")..("
        + pBounds.ur.x
        + ","
        + pBounds.ur.y
        + ")]";
  }

  /** Inserts a new incomplete room with an octagon shape. */
  private void insertIncompleteRoom(
      AutorouteEngine pAutorouteEngine,
      int pLx,
      int pLy,
      int pRx,
      int pUy,
      int pUlx,
      int pLrx,
      int pLlx,
      int pUrx) {
    IntOctagon newIncompleteRoomShape = new IntOctagon(pLx, pLy, pRx, pUy, pUlx, pLrx, pLlx, pUrx);
    newIncompleteRoomShape = newIncompleteRoomShape.normalize();
    if (newIncompleteRoomShape.dimension() == 2) {
      IntOctagon newContainedShape = this.roomShape.intersection(newIncompleteRoomShape);
      if (!newContainedShape.isEmpty()) {
        int doorDimension = newContainedShape.dimension();
        if (doorDimension > 0) {
          FreeSpaceExpansionRoom newRoom =
              pAutorouteEngine.addIncompleteExpansionRoom(
                  newIncompleteRoomShape, this.fromRoom.getLayer(), newContainedShape);
          ExpansionDoor newDoor = new ExpansionDoor(this.completedRoom, newRoom, doorDimension);
          this.completedRoom.addDoor(newDoor);
          newRoom.addDoor(newDoor);
        }
      }
    }
  }

  private void calculateNewIncompleteRoomsForObstacleExpansionRoom(
      SortedRoomNeighbour pPrevNeighbour,
      SortedRoomNeighbour pNextNeighbour,
      AutorouteEngine pAutorouteEngine) {
    int fromSideNo = pPrevNeighbour.lastTouchingSide;
    int toSideNo = pNextNeighbour.firstTouchingSide;
    if (fromSideNo == toSideNo && pPrevNeighbour != pNextNeighbour) {
      // no return in case of only 1 neighbour.
      return;
    }
    IntOctagon boardBoundingOct = pAutorouteEngine.board.boundingBox.boundingOctagon();

    // insert the new incomplete room from p_prev_neighbour to the next corner of the room shape.

    int lx = boardBoundingOct.leftX;
    int ly = boardBoundingOct.bottomY;
    int rx = boardBoundingOct.rightX;
    int uy = boardBoundingOct.topY;
    int ulx = boardBoundingOct.upperLeftDiagonalX;
    int lrx = boardBoundingOct.lowerRightDiagonalX;
    int llx = boardBoundingOct.lowerLeftDiagonalX;
    int urx = boardBoundingOct.upperRightDiagonalX;
    switch (fromSideNo) {
      case 0 -> {
        uy = this.roomShape.bottomY;
        ulx = pPrevNeighbour.intersection.lowerRightDiagonalX;
      }
      case 1 -> {
        ulx = this.roomShape.lowerRightDiagonalX;
        lx = pPrevNeighbour.intersection.rightX;
      }
      case 2 -> {
        lx = this.roomShape.rightX;
        llx = pPrevNeighbour.intersection.upperRightDiagonalX;
      }
      case 3 -> {
        llx = this.roomShape.upperRightDiagonalX;
        ly = pPrevNeighbour.intersection.topY;
      }
      case 4 -> {
        ly = this.roomShape.topY;
        lrx = pPrevNeighbour.intersection.upperLeftDiagonalX;
      }
      case 5 -> {
        lrx = this.roomShape.upperLeftDiagonalX;
        rx = pPrevNeighbour.intersection.leftX;
      }
      case 6 -> {
        rx = this.roomShape.leftX;
        urx = pPrevNeighbour.intersection.lowerLeftDiagonalX;
      }
      case 7 -> {
        urx = this.roomShape.lowerLeftDiagonalX;
        uy = pPrevNeighbour.intersection.bottomY;
      }
    }
    insertIncompleteRoom(pAutorouteEngine, lx, ly, rx, uy, ulx, lrx, llx, urx);

    // insert the new incomplete room from p_prev_neighbour to the next corner of the room shape.

    lx = boardBoundingOct.leftX;
    ly = boardBoundingOct.bottomY;
    rx = boardBoundingOct.rightX;
    uy = boardBoundingOct.topY;
    ulx = boardBoundingOct.upperLeftDiagonalX;
    lrx = boardBoundingOct.lowerRightDiagonalX;
    llx = boardBoundingOct.lowerLeftDiagonalX;
    urx = boardBoundingOct.upperRightDiagonalX;

    switch (toSideNo) {
      case 0 -> {
        uy = this.roomShape.bottomY;
        urx = pNextNeighbour.intersection.lowerLeftDiagonalX;
      }
      case 1 -> {
        ulx = this.roomShape.lowerRightDiagonalX;
        uy = pNextNeighbour.intersection.bottomY;
      }
      case 2 -> {
        lx = this.roomShape.rightX;
        ulx = pNextNeighbour.intersection.lowerRightDiagonalX;
      }
      case 3 -> {
        llx = this.roomShape.upperRightDiagonalX;
        lx = pNextNeighbour.intersection.rightX;
      }
      case 4 -> {
        ly = this.roomShape.topY;
        llx = pNextNeighbour.intersection.upperRightDiagonalX;
      }
      case 5 -> {
        lrx = this.roomShape.upperLeftDiagonalX;
        ly = pNextNeighbour.intersection.topY;
      }
      case 6 -> {
        rx = this.roomShape.leftX;
        lrx = pNextNeighbour.intersection.upperLeftDiagonalX;
      }
      case 7 -> {
        urx = this.roomShape.lowerLeftDiagonalX;
        rx = pNextNeighbour.intersection.leftX;
      }
    }
    insertIncompleteRoom(pAutorouteEngine, lx, ly, rx, uy, ulx, lrx, llx, urx);

    // Insert the new incomplete rooms on the intermediate free sides of the obstacle expansion
    // room.
    int currFromSideNo = (fromSideNo + 1) % 8;
    if (currFromSideNo == toSideNo) {
      return;
    }
    int currToSideNo = (toSideNo + 7) % 8;
    this.calculateEdgeIncompleteRoomsOfObstacleExpansionRoom(
        currFromSideNo, currToSideNo, pAutorouteEngine);
  }

  private void calculateNewIncompleteRooms(AutorouteEngine pAutorouteEngine) {
    IntOctagon boardBoundingOct = pAutorouteEngine.board.boundingBox.boundingOctagon();
    SortedRoomNeighbour prevNeighbour = this.sortedNeighbours.getLast();
    if (this.fromRoom instanceof ObstacleExpansionRoom && this.sortedNeighbours.size() == 1) {
      // ObstacleExpansionRoom has only 1 neighbour
      calculateNewIncompleteRoomsForObstacleExpansionRoom(
          prevNeighbour, prevNeighbour, pAutorouteEngine);
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
        } else // dimension = 1
        {
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
              prevNeighbour, nextNeighbour, pAutorouteEngine);
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
              } else // prevNeighbour.intersection.llx == nextNeighbour.intersection.llx
              {
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
              } else // prevNeighbour.intersection.ly == nextNeighbour.intersection.ly
              {
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
              } else // prevNeighbour.intersection.lrx == nextNeighbour.intersection.lrx
              {
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
              } else // prevNeighbour.intersection.ry == nextNeighbour.intersection.ry
              {
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
              } else // prevNeighbour.intersection.urx == nextNeighbour.intersection.urx
              {
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
              } else // prevNeighbour.intersection.uy == nextNeighbour.intersection.uy
              {
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
              } else // prevNeighbour.intersection.ulx == nextNeighbour.intersection.ulx
              {
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
              } else // prevNeighbour.intersection.lx == nextNeighbour.intersection.lx
              {
                rx = nextNeighbour.intersection.leftX;
              }
            }
            default ->
                FRLogger.warn(
                    "Sorted45DegreeRoomNeighbour.calculate_new_incomplete: illegal touching side");
          }
          insertIncompleteRoom(pAutorouteEngine, lx, ly, rx, uy, ulx, lrx, llx, urx);
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

    /** The search tree object of the neighbour room */
    public final SearchTreeObject searchTreeObject;

    /** The shape of the neighbour room */
    public final IntOctagon shape;

    /** The intersection of this ExpansionRoom shape with the neighbourShape */
    public final IntOctagon intersection;

    /** The first side of the room shape, where the neighbourShape touches */
    public final int firstTouchingSide;

    /** The last side of the room shape, where the neighbourShape touches */
    public final int lastTouchingSide;

    /**
     * Creates a new instance of SortedRoomNeighbour and calculates the first and last touching
     * sides with the room shape. this.lastTouchingSide will be -1, if sorting did not work because
     * the roomShape is contained in the neighbour shape.
     */
    public SortedRoomNeighbour(
        SearchTreeObject pSearchTreeObject, IntOctagon pNeighbourShape, IntOctagon pIntersection) {
      searchTreeObject = pSearchTreeObject;
      shape = pNeighbourShape;
      intersection = pIntersection;

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
        int currSideNo = nextSideNo;
        nextSideNo = (nextSideNo + 1) % 8;
        if (!edgeInteriorTouchesObstacle[currSideNo]) {
          boolean touchOnlyAtCorner = false;
          if (currSideNo == this.firstTouchingSide) {
            if (intersection.corner(currSideNo).equals(roomShape.corner(nextSideNo))) {
              touchOnlyAtCorner = true;
            }
          }
          if (currSideNo == this.lastTouchingSide) {
            if (intersection.corner(nextSideNo).equals(roomShape.corner(currSideNo))) {
              touchOnlyAtCorner = true;
            }
          }
          if (!touchOnlyAtCorner) {
            edgeInteriorTouchesObstacle[currSideNo] = true;
          }
        }
        if (currSideNo == this.lastTouchingSide) {
          break;
        }
      }
    }

    /**
     * Compare function for or sorting the neighbours in counterclock sense around the border of the
     * room shape in ascending order.
     */
    @Override
    public int compareTo(SortedRoomNeighbour pOther) {
      if (this.firstTouchingSide > pOther.firstTouchingSide) {
        return 1;
      }
      if (this.firstTouchingSide < pOther.firstTouchingSide) {
        return -1;
      }

      // now the first touch of this and p_other is at the same side
      IntOctagon is1 = this.intersection;
      IntOctagon is2 = pOther.intersection;
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
        // The first touching points of this neighbour and p_other with the room shape are equal.
        // Compare the last touching points.
        int thisTouchingSideDiff = (this.lastTouchingSide - this.firstTouchingSide + 8) % 8;
        int otherTouchingSideDiff = (pOther.lastTouchingSide - pOther.firstTouchingSide + 8) % 8;
        if (thisTouchingSideDiff > otherTouchingSideDiff) {
          return 1;
        }
        if (thisTouchingSideDiff < otherTouchingSideDiff) {
          return -1;
        }
        // now the last touch of this and p_other is at the same side
        switch (lastTouchingSide) {
          case 0 -> cmpValue = is1.corner(1).x - is2.corner(1).x;
          case 1 -> cmpValue = is1.corner(2).x - is2.corner(2).x;
          case 2 -> cmpValue = is1.corner(3).y - is2.corner(3).y;
          case 3 -> cmpValue = is1.corner(4).y - is2.corner(4).y;
          case 4 -> cmpValue = is2.corner(5).x - is1.corner(5).x;
          case 5 -> cmpValue = is2.corner(6).x - is1.corner(6).x;
          case 6 -> cmpValue = is2.corner(7).y - is1.corner(7).y;
          case 7 -> cmpValue = is2.corner(0).y - is1.corner(0).y;
        }
      }
      if (cmpValue == 0) {
        // Deterministic tie-breaker for identical geometry
        cmpValue = this.searchTreeObject.getIdNo() - pOther.searchTreeObject.getIdNo();
      }
      return cmpValue;
    }
  }
}
