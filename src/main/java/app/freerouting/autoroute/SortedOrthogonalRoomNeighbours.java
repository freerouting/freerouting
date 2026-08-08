package app.freerouting.autoroute;

import app.freerouting.board.Item;
import app.freerouting.board.SearchTreeObject;
import app.freerouting.board.ShapeSearchTree;
import app.freerouting.datastructures.ShapeTree;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Limits;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.LinkedList;
import java.util.SortedSet;
import java.util.TreeSet;

public final class SortedOrthogonalRoomNeighbours {

  public final CompleteExpansionRoom completedRoom;
  public final SortedSet<SortedRoomNeighbour> sortedNeighbours;
  private final ExpansionRoom fromRoom;
  private final boolean isObstacleExpansionRoom;
  private final IntBox roomShape;
  private final boolean[] edgeInteriorTouchesObstacle;

  /** Creates a new instance of SortedOrthogonalRoomNeighbours */
  private SortedOrthogonalRoomNeighbours(
      ExpansionRoom pFromRoom, CompleteExpansionRoom pCompletedRoom) {
    fromRoom = pFromRoom;
    completedRoom = pCompletedRoom;
    isObstacleExpansionRoom = pFromRoom instanceof ObstacleExpansionRoom;
    roomShape = (IntBox) pCompletedRoom.getShape();
    sortedNeighbours = new TreeSet<>();
    edgeInteriorTouchesObstacle = new boolean[4];
    for (int i = 0; i < 4; i++) {
      edgeInteriorTouchesObstacle[i] = false;
    }
  }

  public static CompleteExpansionRoom calculate(
      ExpansionRoom pRoom, AutorouteEngine pAutorouteEngine) {
    int netNo = pAutorouteEngine.getNetNo();
    SortedOrthogonalRoomNeighbours roomNeighbours =
        SortedOrthogonalRoomNeighbours.calculateNeighbours(
            pRoom,
            netNo,
            pAutorouteEngine.autorouteSearchTree,
            pAutorouteEngine.generateRoomIdNo());
    if (roomNeighbours == null) {
      return null;
    }

    // Check, that each side of the room shape has at least one touching neighbour.
    // Otherwise, improve the room shape by enlarging.
    boolean edgeRemoved = roomNeighbours.tryRemoveEdge(netNo, pAutorouteEngine.autorouteSearchTree);
    CompleteExpansionRoom result = roomNeighbours.completedRoom;
    if (edgeRemoved) {
      pAutorouteEngine.removeAllDoors(result);
      return calculate(pRoom, pAutorouteEngine);
    }

    // Now calculate the new incomplete rooms together with the doors
    // between this room and the sorted neighbours.

    if (roomNeighbours.sortedNeighbours.isEmpty()) {
      if (result instanceof ObstacleExpansionRoom) {
        calculateIncompleteRoomsWithEmptyNeighbours(
            (ObstacleExpansionRoom) pRoom, pAutorouteEngine);
      }
    } else {
      roomNeighbours.calculateNewIncompleteRooms(pAutorouteEngine);
    }
    return result;
  }

  private static void calculateIncompleteRoomsWithEmptyNeighbours(
      ObstacleExpansionRoom pRoom, AutorouteEngine pAutorouteEngine) {
    TileShape roomShape = pRoom.getShape();
    if (!(roomShape instanceof IntBox room_box)) {
      FRLogger.warn(
          "SortedOrthoganelRoomNeighbours.calculate_incomplete_rooms_with_empty_neighbours: IntBox expected for roomShape");
      return;
    }
    IntBox boundingBox = pAutorouteEngine.board.getBoundingBox();
    for (int i = 0; i < 4; i++) {
      IntBox newRoomBox =
          switch (i) {
            case 0 ->
                new IntBox(boundingBox.ll.x, boundingBox.ll.y, boundingBox.ur.x, room_box.ll.y);
            case 1 ->
                new IntBox(room_box.ur.x, boundingBox.ll.y, boundingBox.ur.x, boundingBox.ur.y);
            case 2 ->
                new IntBox(boundingBox.ll.x, room_box.ur.y, boundingBox.ur.x, boundingBox.ur.y);
            default -> // i == 3
                new IntBox(boundingBox.ll.x, boundingBox.ll.y, room_box.ll.x, boundingBox.ur.y);
          };
      IntBox newContainedBox = room_box.intersection(newRoomBox);
      FreeSpaceExpansionRoom newRoom =
          pAutorouteEngine.addIncompleteExpansionRoom(
              newRoomBox, pRoom.getLayer(), newContainedBox);
      ExpansionDoor newDoor = new ExpansionDoor(pRoom, newRoom, 1);
      pRoom.addDoor(newDoor);
      newRoom.addDoor(newDoor);
    }
  }

  /**
   * Calculates all touching neighbours of p_room and sorts them in counterclock sense around the
   * boundary of the room shape.
   */
  private static SortedOrthogonalRoomNeighbours calculateNeighbours(
      ExpansionRoom pRoom, int pNetNo, ShapeSearchTree pAutorouteSearchTree, int pRoomIdNo) {
    TileShape roomShape = pRoom.getShape();
    if (!(roomShape instanceof IntBox room_box)) {
      FRLogger.warn("SortedOrthogonalRoomNeighbours.calculate: IntBox expected for roomShape");
      return null;
    }
    CompleteExpansionRoom completedRoom;
    if (pRoom instanceof IncompleteFreeSpaceExpansionRoom) {
      completedRoom = new CompleteFreeSpaceExpansionRoom(roomShape, pRoom.getLayer(), pRoomIdNo);
    } else if (pRoom instanceof ObstacleExpansionRoom room) {
      completedRoom = room;
    } else {
      FRLogger.warn("SortedOrthogonalRoomNeighbours.calculate: unexpected expansion room type");
      return null;
    }
    SortedOrthogonalRoomNeighbours result =
        new SortedOrthogonalRoomNeighbours(pRoom, completedRoom);
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
      if (!(currShape instanceof IntBox currBox)) {
        FRLogger.warn(
            "OrthogonalAutorouteEngine:calculate_sorted_neighbours: IntBox expected for currShape");
        return null;
      }
      IntBox intersection = room_box.intersection(currBox);
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

        FRLogger.warn("AutorouteEngine.calculate_doors: dimension >= 0 expected");
        continue;
      }
      result.addSortedNeighbour(currObject, currBox, intersection);
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

  private static IntBox removeBorderLine(IntBox pRoomBox, int pRemoveEdgeNo) {
    return switch (pRemoveEdgeNo) {
      case 0 -> new IntBox(pRoomBox.ll.x, -Limits.CRIT_INT, pRoomBox.ur.x, pRoomBox.ur.y);
      case 1 -> new IntBox(pRoomBox.ll.x, pRoomBox.ll.y, Limits.CRIT_INT, pRoomBox.ur.y);
      case 2 -> new IntBox(pRoomBox.ll.x, pRoomBox.ll.y, pRoomBox.ur.x, Limits.CRIT_INT);
      case 3 -> new IntBox(-Limits.CRIT_INT, pRoomBox.ll.y, pRoomBox.ur.x, pRoomBox.ur.y);
      default -> {
        FRLogger.warn(
            "SortedOrthogonalRoomNeighbours.remove_border_line: illegal p_remove_edge_no");
        yield null;
      }
    };
  }

  private void calculateNewIncompleteRooms(AutorouteEngine pAutorouteEngine) {
    IntBox boardBounds = pAutorouteEngine.board.boundingBox;
    SortedRoomNeighbour prevNeighbour = this.sortedNeighbours.getLast();

    for (SortedRoomNeighbour nextNeighbour : this.sortedNeighbours) {
      if (!nextNeighbour.intersection.intersects(prevNeighbour.intersection)) {
        // create a door to a new incomplete expansion room between
        // the last corner of the previous neighbour and the first corner of the
        // current neighbour.
        switch (nextNeighbour.firstTouchingSide) {
          case 0 -> {
            if (prevNeighbour.lastTouchingSide == 0) {
              if (prevNeighbour.intersection.ur.x < nextNeighbour.intersection.ll.x) {
                insertIncompleteRoom(
                    pAutorouteEngine,
                    prevNeighbour.intersection.ur.x,
                    boardBounds.ll.y,
                    nextNeighbour.intersection.ll.x,
                    this.roomShape.ll.y);
              }
            } else {
              if (prevNeighbour.intersection.ll.y > this.roomShape.ll.y
                  || nextNeighbour.intersection.ll.x > this.roomShape.ll.x) {
                if (isObstacleExpansionRoom) {
                  // no 2-dim doors between obstacle_expansion_rooms and free space rooms allowed.
                  if (prevNeighbour.lastTouchingSide == 3) {
                    insertIncompleteRoom(
                        pAutorouteEngine,
                        boardBounds.ll.x,
                        roomShape.ll.y,
                        roomShape.ll.x,
                        prevNeighbour.intersection.ll.y);
                  }
                  insertIncompleteRoom(
                      pAutorouteEngine,
                      roomShape.ll.x,
                      boardBounds.ll.y,
                      nextNeighbour.intersection.ll.x,
                      roomShape.ll.y);
                } else {
                  insertIncompleteRoom(
                      pAutorouteEngine,
                      boardBounds.ll.x,
                      boardBounds.ll.y,
                      nextNeighbour.intersection.ll.x,
                      prevNeighbour.intersection.ll.y);
                }
              }
            }
          }
          case 1 -> {
            if (prevNeighbour.lastTouchingSide == 1) {
              if (prevNeighbour.intersection.ur.y < nextNeighbour.intersection.ll.y) {
                insertIncompleteRoom(
                    pAutorouteEngine,
                    this.roomShape.ur.x,
                    prevNeighbour.intersection.ur.y,
                    boardBounds.ur.x,
                    nextNeighbour.intersection.ll.y);
              }
            } else {
              if (prevNeighbour.intersection.ur.x < this.roomShape.ur.x
                  || nextNeighbour.intersection.ll.y > this.roomShape.ll.y) {
                if (isObstacleExpansionRoom) {
                  // no 2-dim doors between obstacle_expansion_rooms and free space rooms allowed.
                  if (prevNeighbour.lastTouchingSide == 0) {
                    insertIncompleteRoom(
                        pAutorouteEngine,
                        prevNeighbour.intersection.ur.x,
                        boardBounds.ll.y,
                        roomShape.ur.x,
                        roomShape.ll.y);
                  }
                  insertIncompleteRoom(
                      pAutorouteEngine,
                      roomShape.ur.x,
                      roomShape.ll.y,
                      roomShape.ur.x,
                      nextNeighbour.intersection.ll.y);
                } else {
                  insertIncompleteRoom(
                      pAutorouteEngine,
                      prevNeighbour.intersection.ur.x,
                      boardBounds.ll.y,
                      boardBounds.ur.x,
                      nextNeighbour.intersection.ll.y);
                }
              }
            }
          }
          case 2 -> {
            if (prevNeighbour.lastTouchingSide == 2) {
              if (prevNeighbour.intersection.ll.x > nextNeighbour.intersection.ur.x) {
                insertIncompleteRoom(
                    pAutorouteEngine,
                    nextNeighbour.intersection.ur.x,
                    this.roomShape.ur.y,
                    prevNeighbour.intersection.ll.x,
                    boardBounds.ur.y);
              }
            } else {
              if (prevNeighbour.intersection.ur.y < this.roomShape.ur.y
                  || nextNeighbour.intersection.ur.x < this.roomShape.ur.x) {
                if (isObstacleExpansionRoom) {
                  // no 2-dim doors between obstacle_expansion_rooms and free space rooms allowed.
                  if (prevNeighbour.lastTouchingSide == 1) {
                    insertIncompleteRoom(
                        pAutorouteEngine,
                        roomShape.ur.x,
                        prevNeighbour.intersection.ur.y,
                        boardBounds.ur.x,
                        roomShape.ur.y);
                  }
                  insertIncompleteRoom(
                      pAutorouteEngine,
                      nextNeighbour.intersection.ur.x,
                      roomShape.ur.y,
                      roomShape.ur.x,
                      boardBounds.ur.y);
                } else {
                  insertIncompleteRoom(
                      pAutorouteEngine,
                      nextNeighbour.intersection.ur.x,
                      prevNeighbour.intersection.ur.y,
                      boardBounds.ur.x,
                      boardBounds.ur.y);
                }
              }
            }
          }
          case 3 -> {
            if (prevNeighbour.lastTouchingSide == 3) {
              if (prevNeighbour.intersection.ll.y > nextNeighbour.intersection.ur.y) {
                insertIncompleteRoom(
                    pAutorouteEngine,
                    boardBounds.ll.x,
                    nextNeighbour.intersection.ur.y,
                    this.roomShape.ll.x,
                    prevNeighbour.intersection.ll.y);
              }
            } else {
              if (nextNeighbour.intersection.ur.y < this.roomShape.ur.y
                  || prevNeighbour.intersection.ll.x > this.roomShape.ll.x) {
                if (isObstacleExpansionRoom) {
                  // no 2-dim doors between obstacle_expansion_rooms and free space rooms allowed.
                  if (prevNeighbour.lastTouchingSide == 2) {
                    insertIncompleteRoom(
                        pAutorouteEngine,
                        roomShape.ll.x,
                        roomShape.ur.y,
                        prevNeighbour.intersection.ll.x,
                        boardBounds.ur.y);
                  }
                  insertIncompleteRoom(
                      pAutorouteEngine,
                      boardBounds.ll.x,
                      nextNeighbour.intersection.ur.y,
                      roomShape.ll.x,
                      roomShape.ur.y);
                } else {
                  insertIncompleteRoom(
                      pAutorouteEngine,
                      boardBounds.ll.x,
                      nextNeighbour.intersection.ur.y,
                      prevNeighbour.intersection.ll.x,
                      boardBounds.ur.y);
                }
              }
            }
          }
          default ->
              FRLogger.warn(
                  "SortedOrthogonalRoomNeighbour.calculate_new_incomplete: illegal touching side");
        }
      }
      prevNeighbour = nextNeighbour;
    }
  }

  private void insertIncompleteRoom(
      AutorouteEngine pAutorouteEngine, int pLlX, int pLlY, int pUrX, int pUrY) {
    IntBox newIncompleteRoomShape = new IntBox(pLlX, pLlY, pUrX, pUrY);
    if (newIncompleteRoomShape.dimension() == 2) {
      IntBox newContainedShape = this.roomShape.intersection(newIncompleteRoomShape);
      if (!newContainedShape.isEmpty()) {
        int doorDimension = newIncompleteRoomShape.intersection(this.roomShape).dimension();
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

  /**
   * Check, that each side of the room shape has at least one touching neighbour. Otherwise, the
   * room shape will be improved the by enlarging. Returns true, if the room shape was changed.
   */
  private boolean tryRemoveEdge(int pNetNo, ShapeSearchTree pAutorouteSearchTree) {
    if (!(this.fromRoom instanceof IncompleteFreeSpaceExpansionRoom curr_incomplete_room)) {
      return false;
    }
    if (!(curr_incomplete_room.getShape() instanceof IntBox room_box)) {
      FRLogger.warn(
          "SortedOrthogonalRoomNeighbours.try_remove_edge: IntBox expected for roomShape type");
      return false;
    }
    double roomArea = room_box.area();

    int removeEdgeNo = -1;
    for (int i = 0; i < 4; i++) {
      if (!this.edgeInteriorTouchesObstacle[i]) {
        removeEdgeNo = i;
        break;
      }
    }

    if (removeEdgeNo >= 0) {
      // Touching neighbour missing at the edge side with index removeEdgeNo
      // Remove the edge line and restart the algorithm.
      FRLogger.trace(
          "ROOM_EDGE_REMOVE start"
              + ", net="
              + pNetNo
              + ", layer="
              + curr_incomplete_room.getLayer()
              + ", removeEdge="
              + removeEdgeNo
              + ", room_bounds="
              + room_box);
      IntBox enlargedBox = removeBorderLine(room_box, removeEdgeNo);
      Collection<ExpansionDoor> doorList = this.completedRoom.getDoors();
      TileShape ignoreShape = null;
      SearchTreeObject ignoreObject = null;
      double maxDoorArea = 0;
      int ignoreCandidateCount = 0;
      int equalAreaTieCount = 0;
      for (ExpansionDoor currDoor : doorList) {
        // insert the overlapping doors with CompleteFreeSpaceExpansionRooms
        // for the information in complete_shape about the objects to ignore.
        if (currDoor.dimension == 2) {
          CompleteExpansionRoom otherRoom = currDoor.otherRoom(this.completedRoom);
          {
            if (otherRoom instanceof CompleteFreeSpaceExpansionRoom room) {
              TileShape currDoorShape = currDoor.getShape();
              double currDoorArea = currDoorShape.area();
              ++ignoreCandidateCount;
              FRLogger.trace(
                  "ROOM_EDGE_REMOVE ignore_candidate"
                      + ", net="
                      + pNetNo
                      + ", layer="
                      + curr_incomplete_room.getLayer()
                      + ", removeEdge="
                      + removeEdgeNo
                      + ", candidate_no="
                      + ignoreCandidateCount
                      + ", candidate_bounds="
                      + currDoorShape.boundingBox()
                      + ", candidate_area="
                      + currDoorArea);
              if (currDoorArea > maxDoorArea) {
                maxDoorArea = currDoorArea;
                ignoreShape = currDoorShape;
                ignoreObject = room;
                FRLogger.trace(
                    "ROOM_EDGE_REMOVE ignore_selected"
                        + ", net="
                        + pNetNo
                        + ", layer="
                        + curr_incomplete_room.getLayer()
                        + ", removeEdge="
                        + removeEdgeNo
                        + ", reason=larger_area"
                        + ", selected_bounds="
                        + currDoorShape.boundingBox()
                        + ", selected_area="
                        + currDoorArea);
              } else if (Double.compare(currDoorArea, maxDoorArea) == 0) {
                ++equalAreaTieCount;
                FRLogger.trace(
                    "ROOM_EDGE_REMOVE ignore_tie"
                        + ", net="
                        + pNetNo
                        + ", layer="
                        + curr_incomplete_room.getLayer()
                        + ", removeEdge="
                        + removeEdgeNo
                        + ", tie_no="
                        + equalAreaTieCount
                        + ", tie_bounds="
                        + currDoorShape.boundingBox()
                        + ", tie_area="
                        + currDoorArea);
              }
            }
          }
        }
      }
      FRLogger.trace(
          "ROOM_EDGE_REMOVE ignore_summary"
              + ", net="
              + pNetNo
              + ", layer="
              + curr_incomplete_room.getLayer()
              + ", removeEdge="
              + removeEdgeNo
              + ", candidate_count="
              + ignoreCandidateCount
              + ", tie_count="
              + equalAreaTieCount
              + ", selected_bounds="
              + (ignoreShape == null ? "null" : ignoreShape.boundingBox())
              + ", selected_area="
              + maxDoorArea);
      IncompleteFreeSpaceExpansionRoom enlargedRoom =
          new IncompleteFreeSpaceExpansionRoom(
              enlargedBox,
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
              + ", removeEdge="
              + removeEdgeNo
              + ", enlarged_bounds="
              + enlargedBox
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
                  + ", removeEdge="
                  + removeEdgeNo
                  + ", old_bounds="
                  + room_box
                  + ", newBounds="
                  + newRoom.getShape().boundingBox());
          curr_incomplete_room.setShape(newRoom.getShape());
          curr_incomplete_room.setContainedShape(newRoom.getContainedShape());
          return true;
        }
      }
    }
    return false;
  }

  private void addSortedNeighbour(
      SearchTreeObject pSearchTreeObject, IntBox pNeighbourShape, IntBox pIntersection) {
    SortedRoomNeighbour newNeighbour =
        new SortedRoomNeighbour(pSearchTreeObject, pNeighbourShape, pIntersection);
    sortedNeighbours.add(newNeighbour);
  }

  /**
   * Helper class to sort the doors of an expansion room counterclockwise around the border of the
   * room shape.
   */
  private class SortedRoomNeighbour implements Comparable<SortedRoomNeighbour> {

    /** The search tree object of the neighbour room */
    public final SearchTreeObject searchTreeObject;

    /** The shape of the neighbour room */
    public final IntBox shape;

    /** The intersection of this ExpansionRoom shape with the neighbourShape */
    public final IntBox intersection;

    /** The first side of the room shape, where the neighbourShape touches */
    public final int firstTouchingSide;

    /** The last side of the room shape, where the neighbourShape touches */
    public final int lastTouchingSide;

    public SortedRoomNeighbour(
        SearchTreeObject pSearchTreeObject, IntBox pNeighbourShape, IntBox pIntersection) {
      searchTreeObject = pSearchTreeObject;
      shape = pNeighbourShape;
      intersection = pIntersection;

      if (pIntersection.ll.y == roomShape.ll.y
          && pIntersection.ur.x > roomShape.ll.x
          && pIntersection.ll.x < roomShape.ur.x) {
        edgeInteriorTouchesObstacle[0] = true;
      }
      if (pIntersection.ur.x == roomShape.ur.x
          && pIntersection.ur.y > roomShape.ll.y
          && pIntersection.ll.y < roomShape.ur.y) {
        edgeInteriorTouchesObstacle[1] = true;
      }
      if (pIntersection.ur.y == roomShape.ur.y
          && pIntersection.ur.x > roomShape.ll.x
          && pIntersection.ll.x < roomShape.ur.x) {
        edgeInteriorTouchesObstacle[2] = true;
      }
      if (pIntersection.ll.x == roomShape.ll.x
          && pIntersection.ur.y > roomShape.ll.y
          && pIntersection.ll.y < roomShape.ur.y) {
        edgeInteriorTouchesObstacle[3] = true;
      }

      if (pIntersection.ll.y == roomShape.ll.y && pIntersection.ll.x > roomShape.ll.x) {
        this.firstTouchingSide = 0;
      } else if (pIntersection.ur.x == roomShape.ur.x && pIntersection.ll.y > roomShape.ll.y) {
        this.firstTouchingSide = 1;
      } else if (pIntersection.ur.y == roomShape.ur.y) {
        this.firstTouchingSide = 2;
      } else if (pIntersection.ll.x == roomShape.ll.x) {
        this.firstTouchingSide = 3;
      } else {
        FRLogger.warn("SortedRoomNeighbour: case not expected");
        this.firstTouchingSide = -1;
      }

      if (pIntersection.ll.x == roomShape.ll.x && pIntersection.ll.y > roomShape.ll.y) {
        this.lastTouchingSide = 3;
      } else if (pIntersection.ur.y == roomShape.ur.y && pIntersection.ll.x > roomShape.ll.x) {
        this.lastTouchingSide = 2;
      } else if (pIntersection.ur.x == roomShape.ur.x) {
        this.lastTouchingSide = 1;
      } else if (pIntersection.ll.y == roomShape.ll.y) {
        this.lastTouchingSide = 0;
      } else {
        FRLogger.warn("SortedRoomNeighbour: case not expected");
        this.lastTouchingSide = -1;
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
      IntBox is1 = this.intersection;
      IntBox is2 = pOther.intersection;
      int cmpValue;

      switch (firstTouchingSide) {
        case 0 -> cmpValue = is1.ll.x - is2.ll.x;
        case 1 -> cmpValue = is1.ll.y - is2.ll.y;
        case 2 -> cmpValue = is2.ur.x - is1.ur.x;
        case 3 -> cmpValue = is2.ur.y - is1.ur.y;
        default -> {
          FRLogger.warn("SortedRoomNeighbour.compareTo: firstTouchingSide out of range ");
          return 0;
        }
      }
      if (cmpValue == 0) {
        // The first touching points of this neighbour and p_other with the room shape are equal.
        // Compare the last touching points.
        int thisTouchingSideDiff = (this.lastTouchingSide - this.firstTouchingSide + 4) % 4;
        int otherTouchingSideDiff = (pOther.lastTouchingSide - pOther.firstTouchingSide + 4) % 4;
        if (thisTouchingSideDiff > otherTouchingSideDiff) {
          return 1;
        }
        if (thisTouchingSideDiff < otherTouchingSideDiff) {
          return -1;
        }

        // now the last touch of this and p_other is at the same side
        switch (lastTouchingSide) {
          case 0 -> cmpValue = is1.ur.x - is2.ur.x;
          case 1 -> cmpValue = is1.ur.y - is2.ur.y;
          case 2 -> cmpValue = is2.ll.x - is1.ll.x;
          case 3 -> cmpValue = is2.ll.y - is1.ll.y;
          default -> {
            FRLogger.warn("SortedRoomNeighbour.compareTo: firstTouchingSide out of range ");
            return 0;
          }
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
