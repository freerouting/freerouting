package app.freerouting.autoroute.expansion;

import app.freerouting.autoroute.ItemAutorouteInfo;
import app.freerouting.autoroute.maze.AutorouteEngine;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.datastructures.ShapeTree;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Limits;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.LinkedList;
import java.util.SortedSet;
import java.util.TreeSet;

/** SortedOrthogonalRoomNeighbours. */
public final class SortedOrthogonalRoomNeighbours {

  public final CompleteExpansionRoom completedRoom;
  public final SortedSet<SortedRoomNeighbour> sortedNeighbours;
  private final ExpansionRoom fromRoom;
  private final boolean isObstacleExpansionRoom;
  private final IntBox roomShape;
  private final boolean[] edgeInteriorTouchesObstacle;

  /** Creates a new instance of SortedOrthogonalRoomNeighbours. */
  private SortedOrthogonalRoomNeighbours(
      ExpansionRoom fromRoom, CompleteExpansionRoom completedRoom) {
    this.fromRoom = fromRoom;
    this.completedRoom = completedRoom;
    isObstacleExpansionRoom = fromRoom instanceof ObstacleExpansionRoom;
    roomShape = (IntBox) completedRoom.getShape();
    sortedNeighbours = new TreeSet<>();
    edgeInteriorTouchesObstacle = new boolean[4];
    for (int i = 0; i < 4; i++) {
      edgeInteriorTouchesObstacle[i] = false;
    }
  }

  /** Calculates the completed expansion room for orthogonal routing. */
  public static CompleteExpansionRoom calculate(
      ExpansionRoom room, AutorouteEngine autorouteEngine) {
    int netNumber = autorouteEngine.getNetNumber();
    SortedOrthogonalRoomNeighbours roomNeighbours =
        SortedOrthogonalRoomNeighbours.calculateNeighbours(
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
        roomNeighbours.tryRemoveEdge(netNumber, autorouteEngine.autorouteSearchTree);
    CompleteExpansionRoom result = roomNeighbours.completedRoom;
    if (edgeRemoved) {
      autorouteEngine.removeAllDoors(result);
      return calculate(room, autorouteEngine);
    }

    // Now calculate the new incomplete rooms together with the doors
    // between this room and the sorted neighbours.

    if (roomNeighbours.sortedNeighbours.isEmpty()) {
      if (result instanceof ObstacleExpansionRoom obstacleRoom) {
        calculateIncompleteRoomsWithEmptyNeighbours(obstacleRoom, autorouteEngine);
      }
    } else {
      roomNeighbours.calculateNewIncompleteRooms(autorouteEngine);
    }
    return result;
  }

  private static void calculateIncompleteRoomsWithEmptyNeighbours(
      ObstacleExpansionRoom room, AutorouteEngine autorouteEngine) {
    TileShape roomShape = room.getShape();
    if (!(roomShape instanceof IntBox roomBox)) {
      FRLogger.warn(
          "SortedOrthoganelRoomNeighbours.calculate_incomplete_rooms_with_empty_neighbours: "
              + "IntBox expected for roomShape");
      return;
    }
    IntBox boundingBox = autorouteEngine.board.getBoundingBox();
    for (int i = 0; i < 4; i++) {
      IntBox newRoomBox =
          switch (i) {
            case 0 ->
                new IntBox(boundingBox.ll.x, boundingBox.ll.y, boundingBox.ur.x, roomBox.ll.y);
            case 1 ->
                new IntBox(roomBox.ur.x, boundingBox.ll.y, boundingBox.ur.x, boundingBox.ur.y);
            case 2 ->
                new IntBox(boundingBox.ll.x, roomBox.ur.y, boundingBox.ur.x, boundingBox.ur.y);
            default -> // i == 3
                new IntBox(boundingBox.ll.x, boundingBox.ll.y, roomBox.ll.x, boundingBox.ur.y);
          };
      IntBox newContainedBox = roomBox.intersection(newRoomBox);
      FreeSpaceExpansionRoom newRoom =
          autorouteEngine.addIncompleteExpansionRoom(newRoomBox, room.getLayer(), newContainedBox);
      ExpansionDoor newDoor = new ExpansionDoor(room, newRoom, 1);
      room.addDoor(newDoor);
      newRoom.addDoor(newDoor);
    }
  }

  /**
   * Calculates all touching neighbours of room and sorts them in counterclock sense around the
   * boundary of the room shape.
   */
  private static SortedOrthogonalRoomNeighbours calculateNeighbours(
      ExpansionRoom room, int netNumber, ShapeSearchTree autorouteSearchTree, int roomIdNo) {
    TileShape roomShape = room.getShape();
    if (!(roomShape instanceof IntBox roomBox)) {
      FRLogger.warn("SortedOrthogonalRoomNeighbours.calculate: IntBox expected for roomShape");
      return null;
    }
    CompleteExpansionRoom completedRoom;
    if (room instanceof IncompleteFreeSpaceExpansionRoom) {
      completedRoom = new CompleteFreeSpaceExpansionRoom(roomShape, room.getLayer(), roomIdNo);
    } else if (room instanceof ObstacleExpansionRoom obstacleRoom) {
      completedRoom = obstacleRoom;
    } else {
      FRLogger.warn("SortedOrthogonalRoomNeighbours.calculate: unexpected expansion room type");
      return null;
    }
    SortedOrthogonalRoomNeighbours result = new SortedOrthogonalRoomNeighbours(room, completedRoom);
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
      if (!(currentShape instanceof IntBox currentBox)) {
        FRLogger.warn(
            "OrthogonalAutorouteEngine:calculate_sorted_neighbours: "
                + "IntBox expected for currentShape");
        return null;
      }
      IntBox intersection = roomBox.intersection(currentBox);
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

        FRLogger.warn("AutorouteEngine.calculate_doors: dimension >= 0 expected");
        continue;
      }
      result.addSortedNeighbour(currentObject, currentBox, intersection);
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

  private static IntBox removeBorderLine(IntBox roomBox, int removeEdgeNo) {
    return switch (removeEdgeNo) {
      case 0 -> new IntBox(roomBox.ll.x, -Limits.CRIT_INT, roomBox.ur.x, roomBox.ur.y);
      case 1 -> new IntBox(roomBox.ll.x, roomBox.ll.y, Limits.CRIT_INT, roomBox.ur.y);
      case 2 -> new IntBox(roomBox.ll.x, roomBox.ll.y, roomBox.ur.x, Limits.CRIT_INT);
      case 3 -> new IntBox(-Limits.CRIT_INT, roomBox.ll.y, roomBox.ur.x, roomBox.ur.y);
      default -> {
        FRLogger.warn("SortedOrthogonalRoomNeighbours.removeBorderLine: illegal removeEdgeNo");
        yield null;
      }
    };
  }

  private void calculateNewIncompleteRooms(AutorouteEngine autorouteEngine) {
    IntBox boardBounds = autorouteEngine.board.boundingBox;
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
                    autorouteEngine,
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
                        autorouteEngine,
                        boardBounds.ll.x,
                        roomShape.ll.y,
                        roomShape.ll.x,
                        prevNeighbour.intersection.ll.y);
                  }
                  insertIncompleteRoom(
                      autorouteEngine,
                      roomShape.ll.x,
                      boardBounds.ll.y,
                      nextNeighbour.intersection.ll.x,
                      roomShape.ll.y);
                } else {
                  insertIncompleteRoom(
                      autorouteEngine,
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
                    autorouteEngine,
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
                        autorouteEngine,
                        prevNeighbour.intersection.ur.x,
                        boardBounds.ll.y,
                        roomShape.ur.x,
                        roomShape.ll.y);
                  }
                  insertIncompleteRoom(
                      autorouteEngine,
                      roomShape.ur.x,
                      roomShape.ll.y,
                      roomShape.ur.x,
                      nextNeighbour.intersection.ll.y);
                } else {
                  insertIncompleteRoom(
                      autorouteEngine,
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
                    autorouteEngine,
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
                        autorouteEngine,
                        roomShape.ur.x,
                        prevNeighbour.intersection.ur.y,
                        boardBounds.ur.x,
                        roomShape.ur.y);
                  }
                  insertIncompleteRoom(
                      autorouteEngine,
                      nextNeighbour.intersection.ur.x,
                      roomShape.ur.y,
                      roomShape.ur.x,
                      boardBounds.ur.y);
                } else {
                  insertIncompleteRoom(
                      autorouteEngine,
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
                    autorouteEngine,
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
                        autorouteEngine,
                        roomShape.ll.x,
                        roomShape.ur.y,
                        prevNeighbour.intersection.ll.x,
                        boardBounds.ur.y);
                  }
                  insertIncompleteRoom(
                      autorouteEngine,
                      boardBounds.ll.x,
                      nextNeighbour.intersection.ur.y,
                      roomShape.ll.x,
                      roomShape.ur.y);
                } else {
                  insertIncompleteRoom(
                      autorouteEngine,
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
      AutorouteEngine autorouteEngine, int llX, int llY, int urX, int urY) {
    IntBox newIncompleteRoomShape = new IntBox(llX, llY, urX, urY);
    if (newIncompleteRoomShape.dimension() == 2) {
      IntBox newContainedShape = this.roomShape.intersection(newIncompleteRoomShape);
      if (!newContainedShape.isEmpty()) {
        int doorDimension = newIncompleteRoomShape.intersection(this.roomShape).dimension();
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

  /**
   * Checks that each side of the room shape has at least one touching neighbour. Otherwise, the
   * room shape will be improved by enlarging. Returns true if the room shape was changed.
   */
  private boolean tryRemoveEdge(int netNumber, ShapeSearchTree autorouteSearchTree) {
    if (!(this.fromRoom instanceof IncompleteFreeSpaceExpansionRoom currentIncompleteRoom)) {
      return false;
    }
    if (!(currentIncompleteRoom.getShape() instanceof IntBox roomBox)) {
      FRLogger.warn(
          "SortedOrthogonalRoomNeighbours.try_remove_edge: IntBox expected for roomShape type");
      return false;
    }
    double roomArea = roomBox.area();

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
              + netNumber
              + ", layer="
              + currentIncompleteRoom.getLayer()
              + ", removeEdge="
              + removeEdgeNo
              + ", room_bounds="
              + roomBox);
      IntBox enlargedBox = removeBorderLine(roomBox, removeEdgeNo);
      Collection<ExpansionDoor> doorList = this.completedRoom.getDoors();
      TileShape ignoreShape = null;
      SearchTreeObject ignoreObject = null;
      double maxDoorArea = 0;
      int ignoreCandidateCount = 0;
      int equalAreaTieCount = 0;
      for (ExpansionDoor currentDoor : doorList) {
        // insert the overlapping doors with CompleteFreeSpaceExpansionRooms
        // for the information in complete_shape about the objects to ignore.
        if (currentDoor.dimension == 2) {
          CompleteExpansionRoom otherRoom = currentDoor.otherRoom(this.completedRoom);
          {
            if (otherRoom instanceof CompleteFreeSpaceExpansionRoom freeSpaceRoom) {
              TileShape currentDoorShape = currentDoor.getShape();
              double currentDoorArea = currentDoorShape.area();
              ++ignoreCandidateCount;
              FRLogger.trace(
                  "ROOM_EDGE_REMOVE ignore_candidate"
                      + ", net="
                      + netNumber
                      + ", layer="
                      + currentIncompleteRoom.getLayer()
                      + ", removeEdge="
                      + removeEdgeNo
                      + ", candidate_no="
                      + ignoreCandidateCount
                      + ", candidate_bounds="
                      + currentDoorShape.boundingBox()
                      + ", candidate_area="
                      + currentDoorArea);
              if (currentDoorArea > maxDoorArea) {
                maxDoorArea = currentDoorArea;
                ignoreShape = currentDoorShape;
                ignoreObject = freeSpaceRoom;
                FRLogger.trace(
                    "ROOM_EDGE_REMOVE ignore_selected"
                        + ", net="
                        + netNumber
                        + ", layer="
                        + currentIncompleteRoom.getLayer()
                        + ", removeEdge="
                        + removeEdgeNo
                        + ", reason=larger_area"
                        + ", selected_bounds="
                        + currentDoorShape.boundingBox()
                        + ", selected_area="
                        + currentDoorArea);
              } else if (Double.compare(currentDoorArea, maxDoorArea) == 0) {
                ++equalAreaTieCount;
                FRLogger.trace(
                    "ROOM_EDGE_REMOVE ignore_tie"
                        + ", net="
                        + netNumber
                        + ", layer="
                        + currentIncompleteRoom.getLayer()
                        + ", removeEdge="
                        + removeEdgeNo
                        + ", tie_no="
                        + equalAreaTieCount
                        + ", tie_bounds="
                        + currentDoorShape.boundingBox()
                        + ", tie_area="
                        + currentDoorArea);
              }
            }
          }
        }
      }
      FRLogger.trace(
          "ROOM_EDGE_REMOVE ignore_summary"
              + ", net="
              + netNumber
              + ", layer="
              + currentIncompleteRoom.getLayer()
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
                  + netNumber
                  + ", layer="
                  + currentIncompleteRoom.getLayer()
                  + ", removeEdge="
                  + removeEdgeNo
                  + ", old_bounds="
                  + roomBox
                  + ", newBounds="
                  + newRoom.getShape().boundingBox());
          currentIncompleteRoom.setShape(newRoom.getShape());
          currentIncompleteRoom.setContainedShape(newRoom.getContainedShape());
          return true;
        }
      }
    }
    return false;
  }

  private void addSortedNeighbour(
      SearchTreeObject searchTreeObject, IntBox neighbourShape, IntBox intersection) {
    SortedRoomNeighbour newNeighbour =
        new SortedRoomNeighbour(searchTreeObject, neighbourShape, intersection);
    sortedNeighbours.add(newNeighbour);
  }

  /**
   * Helper class to sort the doors of an expansion room counterclockwise around the border of the
   * room shape.
   */
  private class SortedRoomNeighbour implements Comparable<SortedRoomNeighbour> {

    /** The search tree object of the neighbour room. */
    public final SearchTreeObject searchTreeObject;

    /** The shape of the neighbour room. */
    public final IntBox shape;

    /** The intersection of this ExpansionRoom shape with the neighbourShape. */
    public final IntBox intersection;

    /** The first side of the room shape, where the neighbourShape touches. */
    public final int firstTouchingSide;

    /** The last side of the room shape, where the neighbourShape touches. */
    public final int lastTouchingSide;

    public SortedRoomNeighbour(
        SearchTreeObject searchTreeObject, IntBox neighbourShape, IntBox intersection) {
      this.searchTreeObject = searchTreeObject;
      this.shape = neighbourShape;
      this.intersection = intersection;

      if (intersection.ll.y == roomShape.ll.y
          && intersection.ur.x > roomShape.ll.x
          && intersection.ll.x < roomShape.ur.x) {
        edgeInteriorTouchesObstacle[0] = true;
      }
      if (intersection.ur.x == roomShape.ur.x
          && intersection.ur.y > roomShape.ll.y
          && intersection.ll.y < roomShape.ur.y) {
        edgeInteriorTouchesObstacle[1] = true;
      }
      if (intersection.ur.y == roomShape.ur.y
          && intersection.ur.x > roomShape.ll.x
          && intersection.ll.x < roomShape.ur.x) {
        edgeInteriorTouchesObstacle[2] = true;
      }
      if (intersection.ll.x == roomShape.ll.x
          && intersection.ur.y > roomShape.ll.y
          && intersection.ll.y < roomShape.ur.y) {
        edgeInteriorTouchesObstacle[3] = true;
      }

      if (intersection.ll.y == roomShape.ll.y && intersection.ll.x > roomShape.ll.x) {
        this.firstTouchingSide = 0;
      } else if (intersection.ur.x == roomShape.ur.x && intersection.ll.y > roomShape.ll.y) {
        this.firstTouchingSide = 1;
      } else if (intersection.ur.y == roomShape.ur.y) {
        this.firstTouchingSide = 2;
      } else if (intersection.ll.x == roomShape.ll.x) {
        this.firstTouchingSide = 3;
      } else {
        FRLogger.warn("SortedRoomNeighbour: case not expected");
        this.firstTouchingSide = -1;
      }

      if (intersection.ll.x == roomShape.ll.x && intersection.ll.y > roomShape.ll.y) {
        this.lastTouchingSide = 3;
      } else if (intersection.ur.y == roomShape.ur.y && intersection.ll.x > roomShape.ll.x) {
        this.lastTouchingSide = 2;
      } else if (intersection.ur.x == roomShape.ur.x) {
        this.lastTouchingSide = 1;
      } else if (intersection.ll.y == roomShape.ll.y) {
        this.lastTouchingSide = 0;
      } else {
        FRLogger.warn("SortedRoomNeighbour: case not expected");
        this.lastTouchingSide = -1;
      }
    }

    /**
     * Compare function for sorting the neighbours in counterclock sense around the border of the
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
      IntBox is1 = this.intersection;
      IntBox is2 = other.intersection;
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
        // The first touching points of this neighbour and other with the room shape are equal.
        // Compare the last touching points.
        int thisTouchingSideDiff = (this.lastTouchingSide - this.firstTouchingSide + 4) % 4;
        int otherTouchingSideDiff = (other.lastTouchingSide - other.firstTouchingSide + 4) % 4;
        if (thisTouchingSideDiff > otherTouchingSideDiff) {
          return 1;
        }
        if (thisTouchingSideDiff < otherTouchingSideDiff) {
          return -1;
        }

        // now the last touch of this and other is at the same side
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
        cmpValue = this.searchTreeObject.getId() - other.searchTreeObject.getId();
      }
      return cmpValue;
    }
  }
}
