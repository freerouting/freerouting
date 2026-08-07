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
      ExpansionRoom p_from_room, CompleteExpansionRoom p_completed_room) {
    fromRoom = p_from_room;
    completedRoom = p_completed_room;
    isObstacleExpansionRoom = p_from_room instanceof ObstacleExpansionRoom;
    roomShape = (IntBox) p_completed_room.get_shape();
    sortedNeighbours = new TreeSet<>();
    edgeInteriorTouchesObstacle = new boolean[4];
    for (int i = 0; i < 4; i++) {
      edgeInteriorTouchesObstacle[i] = false;
    }
  }

  public static CompleteExpansionRoom calculate(
      ExpansionRoom p_room, AutorouteEngine p_autoroute_engine) {
    int netNo = p_autoroute_engine.get_net_no();
    SortedOrthogonalRoomNeighbours roomNeighbours =
        SortedOrthogonalRoomNeighbours.calculate_neighbours(
            p_room,
            netNo,
            p_autoroute_engine.autorouteSearchTree,
            p_autoroute_engine.generate_room_id_no());
    if (roomNeighbours == null) {
      return null;
    }

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
    }
    return result;
  }

  private static void calculate_incomplete_rooms_with_empty_neighbours(
      ObstacleExpansionRoom p_room, AutorouteEngine p_autoroute_engine) {
    TileShape roomShape = p_room.get_shape();
    if (!(roomShape instanceof IntBox room_box)) {
      FRLogger.warn(
          "SortedOrthoganelRoomNeighbours.calculate_incomplete_rooms_with_empty_neighbours: IntBox expected for roomShape");
      return;
    }
    IntBox boundingBox = p_autoroute_engine.board.get_bounding_box();
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
          p_autoroute_engine.add_incomplete_expansion_room(
              newRoomBox, p_room.get_layer(), newContainedBox);
      ExpansionDoor newDoor = new ExpansionDoor(p_room, newRoom, 1);
      p_room.add_door(newDoor);
      newRoom.add_door(newDoor);
    }
  }

  /**
   * Calculates all touching neighbours of p_room and sorts them in counterclock sense around the
   * boundary of the room shape.
   */
  private static SortedOrthogonalRoomNeighbours calculate_neighbours(
      ExpansionRoom p_room,
      int p_net_no,
      ShapeSearchTree p_autoroute_search_tree,
      int p_room_id_no) {
    TileShape roomShape = p_room.get_shape();
    if (!(roomShape instanceof IntBox room_box)) {
      FRLogger.warn("SortedOrthogonalRoomNeighbours.calculate: IntBox expected for roomShape");
      return null;
    }
    CompleteExpansionRoom completedRoom;
    if (p_room instanceof IncompleteFreeSpaceExpansionRoom) {
      completedRoom =
          new CompleteFreeSpaceExpansionRoom(roomShape, p_room.get_layer(), p_room_id_no);
    } else if (p_room instanceof ObstacleExpansionRoom room) {
      completedRoom = room;
    } else {
      FRLogger.warn("SortedOrthogonalRoomNeighbours.calculate: unexpected expansion room type");
      return null;
    }
    SortedOrthogonalRoomNeighbours result =
        new SortedOrthogonalRoomNeighbours(p_room, completedRoom);
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
      if ((completedRoom instanceof CompleteFreeSpaceExpansionRoom fs_room)
          && !currObject.is_trace_obstacle(p_net_no)) {
        fs_room.calculate_target_doors(currEntry, p_net_no, p_autoroute_search_tree);
        continue;
      }
      TileShape currShape =
          currObject.get_tree_shape(p_autoroute_search_tree, currEntry.shapeIndexInObject);
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
          if (currItem.is_routable()) {
            ItemAutorouteInfo itemInfo = currItem.get_autoroute_info();
            ObstacleExpansionRoom currOverlapRoom =
                itemInfo.get_expansion_room(currEntry.shapeIndexInObject, p_autoroute_search_tree);
            obs_room.create_overlap_door(currOverlapRoom);
          }
        }
        continue;
      }
      if (dimension < 0) {

        FRLogger.warn("AutorouteEngine.calculate_doors: dimension >= 0 expected");
        continue;
      }
      result.add_sorted_neighbour(currObject, currBox, intersection);
      if (dimension > 0) {
        // make  sure, that there is a door to the neighbour room.
        ExpansionRoom neighbourRoom = null;
        if (currObject instanceof ExpansionRoom ex_room) {
          neighbourRoom = ex_room;
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
            ExpansionDoor newDoor = new ExpansionDoor(completedRoom, neighbourRoom);
            neighbourRoom.add_door(newDoor);
            completedRoom.add_door(newDoor);
          }
        }
      }
    }
    return result;
  }

  private static IntBox remove_border_line(IntBox p_room_box, int p_remove_edge_no) {
    return switch (p_remove_edge_no) {
      case 0 -> new IntBox(p_room_box.ll.x, -Limits.CRIT_INT, p_room_box.ur.x, p_room_box.ur.y);
      case 1 -> new IntBox(p_room_box.ll.x, p_room_box.ll.y, Limits.CRIT_INT, p_room_box.ur.y);
      case 2 -> new IntBox(p_room_box.ll.x, p_room_box.ll.y, p_room_box.ur.x, Limits.CRIT_INT);
      case 3 -> new IntBox(-Limits.CRIT_INT, p_room_box.ll.y, p_room_box.ur.x, p_room_box.ur.y);
      default -> {
        FRLogger.warn(
            "SortedOrthogonalRoomNeighbours.remove_border_line: illegal p_remove_edge_no");
        yield null;
      }
    };
  }

  private void calculate_new_incomplete_rooms(AutorouteEngine p_autoroute_engine) {
    IntBox boardBounds = p_autoroute_engine.board.boundingBox;
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
                insert_incomplete_room(
                    p_autoroute_engine,
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
                    insert_incomplete_room(
                        p_autoroute_engine,
                        boardBounds.ll.x,
                        roomShape.ll.y,
                        roomShape.ll.x,
                        prevNeighbour.intersection.ll.y);
                  }
                  insert_incomplete_room(
                      p_autoroute_engine,
                      roomShape.ll.x,
                      boardBounds.ll.y,
                      nextNeighbour.intersection.ll.x,
                      roomShape.ll.y);
                } else {
                  insert_incomplete_room(
                      p_autoroute_engine,
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
                insert_incomplete_room(
                    p_autoroute_engine,
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
                    insert_incomplete_room(
                        p_autoroute_engine,
                        prevNeighbour.intersection.ur.x,
                        boardBounds.ll.y,
                        roomShape.ur.x,
                        roomShape.ll.y);
                  }
                  insert_incomplete_room(
                      p_autoroute_engine,
                      roomShape.ur.x,
                      roomShape.ll.y,
                      roomShape.ur.x,
                      nextNeighbour.intersection.ll.y);
                } else {
                  insert_incomplete_room(
                      p_autoroute_engine,
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
                insert_incomplete_room(
                    p_autoroute_engine,
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
                    insert_incomplete_room(
                        p_autoroute_engine,
                        roomShape.ur.x,
                        prevNeighbour.intersection.ur.y,
                        boardBounds.ur.x,
                        roomShape.ur.y);
                  }
                  insert_incomplete_room(
                      p_autoroute_engine,
                      nextNeighbour.intersection.ur.x,
                      roomShape.ur.y,
                      roomShape.ur.x,
                      boardBounds.ur.y);
                } else {
                  insert_incomplete_room(
                      p_autoroute_engine,
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
                insert_incomplete_room(
                    p_autoroute_engine,
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
                    insert_incomplete_room(
                        p_autoroute_engine,
                        roomShape.ll.x,
                        roomShape.ur.y,
                        prevNeighbour.intersection.ll.x,
                        boardBounds.ur.y);
                  }
                  insert_incomplete_room(
                      p_autoroute_engine,
                      boardBounds.ll.x,
                      nextNeighbour.intersection.ur.y,
                      roomShape.ll.x,
                      roomShape.ur.y);
                } else {
                  insert_incomplete_room(
                      p_autoroute_engine,
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

  private void insert_incomplete_room(
      AutorouteEngine p_autoroute_engine, int p_ll_x, int p_ll_y, int p_ur_x, int p_ur_y) {
    IntBox newIncompleteRoomShape = new IntBox(p_ll_x, p_ll_y, p_ur_x, p_ur_y);
    if (newIncompleteRoomShape.dimension() == 2) {
      IntBox newContainedShape = this.roomShape.intersection(newIncompleteRoomShape);
      if (!newContainedShape.is_empty()) {
        int doorDimension = newIncompleteRoomShape.intersection(this.roomShape).dimension();
        if (doorDimension > 0) {
          FreeSpaceExpansionRoom newRoom =
              p_autoroute_engine.add_incomplete_expansion_room(
                  newIncompleteRoomShape, this.fromRoom.get_layer(), newContainedShape);
          ExpansionDoor newDoor = new ExpansionDoor(this.completedRoom, newRoom, doorDimension);
          this.completedRoom.add_door(newDoor);
          newRoom.add_door(newDoor);
        }
      }
    }
  }

  /**
   * Check, that each side of the room shape has at least one touching neighbour. Otherwise, the
   * room shape will be improved the by enlarging. Returns true, if the room shape was changed.
   */
  private boolean try_remove_edge(int p_net_no, ShapeSearchTree p_autoroute_search_tree) {
    if (!(this.fromRoom instanceof IncompleteFreeSpaceExpansionRoom curr_incomplete_room)) {
      return false;
    }
    if (!(curr_incomplete_room.get_shape() instanceof IntBox room_box)) {
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
              + p_net_no
              + ", layer="
              + curr_incomplete_room.get_layer()
              + ", removeEdge="
              + removeEdgeNo
              + ", room_bounds="
              + room_box);
      IntBox enlargedBox = remove_border_line(room_box, removeEdgeNo);
      Collection<ExpansionDoor> doorList = this.completedRoom.get_doors();
      TileShape ignoreShape = null;
      SearchTreeObject ignoreObject = null;
      double maxDoorArea = 0;
      int ignoreCandidateCount = 0;
      int equalAreaTieCount = 0;
      for (ExpansionDoor currDoor : doorList) {
        // insert the overlapping doors with CompleteFreeSpaceExpansionRooms
        // for the information in complete_shape about the objects to ignore.
        if (currDoor.dimension == 2) {
          CompleteExpansionRoom otherRoom = currDoor.other_room(this.completedRoom);
          {
            if (otherRoom instanceof CompleteFreeSpaceExpansionRoom room) {
              TileShape currDoorShape = currDoor.get_shape();
              double currDoorArea = currDoorShape.area();
              ++ignoreCandidateCount;
              FRLogger.trace(
                  "ROOM_EDGE_REMOVE ignore_candidate"
                      + ", net="
                      + p_net_no
                      + ", layer="
                      + curr_incomplete_room.get_layer()
                      + ", removeEdge="
                      + removeEdgeNo
                      + ", candidate_no="
                      + ignoreCandidateCount
                      + ", candidate_bounds="
                      + currDoorShape.bounding_box()
                      + ", candidate_area="
                      + currDoorArea);
              if (currDoorArea > maxDoorArea) {
                maxDoorArea = currDoorArea;
                ignoreShape = currDoorShape;
                ignoreObject = room;
                FRLogger.trace(
                    "ROOM_EDGE_REMOVE ignore_selected"
                        + ", net="
                        + p_net_no
                        + ", layer="
                        + curr_incomplete_room.get_layer()
                        + ", removeEdge="
                        + removeEdgeNo
                        + ", reason=larger_area"
                        + ", selected_bounds="
                        + currDoorShape.bounding_box()
                        + ", selected_area="
                        + currDoorArea);
              } else if (Double.compare(currDoorArea, maxDoorArea) == 0) {
                ++equalAreaTieCount;
                FRLogger.trace(
                    "ROOM_EDGE_REMOVE ignore_tie"
                        + ", net="
                        + p_net_no
                        + ", layer="
                        + curr_incomplete_room.get_layer()
                        + ", removeEdge="
                        + removeEdgeNo
                        + ", tie_no="
                        + equalAreaTieCount
                        + ", tie_bounds="
                        + currDoorShape.bounding_box()
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
              + p_net_no
              + ", layer="
              + curr_incomplete_room.get_layer()
              + ", removeEdge="
              + removeEdgeNo
              + ", candidate_count="
              + ignoreCandidateCount
              + ", tie_count="
              + equalAreaTieCount
              + ", selected_bounds="
              + (ignoreShape == null ? "null" : ignoreShape.bounding_box())
              + ", selected_area="
              + maxDoorArea);
      IncompleteFreeSpaceExpansionRoom enlargedRoom =
          new IncompleteFreeSpaceExpansionRoom(
              enlargedBox,
              curr_incomplete_room.get_layer(),
              curr_incomplete_room.get_contained_shape());
      Collection<IncompleteFreeSpaceExpansionRoom> newRooms =
          p_autoroute_search_tree.complete_shape(enlargedRoom, p_net_no, ignoreObject, ignoreShape);
      FRLogger.trace(
          "ROOM_EDGE_REMOVE complete_shape"
              + ", net="
              + p_net_no
              + ", layer="
              + curr_incomplete_room.get_layer()
              + ", removeEdge="
              + removeEdgeNo
              + ", enlarged_bounds="
              + enlargedBox
              + ", candidate_count="
              + newRooms.size());
      if (newRooms.size() == 1) {
        // Check, that the area increases to prevent endless loop.
        IncompleteFreeSpaceExpansionRoom newRoom = newRooms.iterator().next();
        if (newRoom.get_shape().area() > roomArea) {
          FRLogger.trace(
              "ROOM_EDGE_REMOVE applied"
                  + ", net="
                  + p_net_no
                  + ", layer="
                  + curr_incomplete_room.get_layer()
                  + ", removeEdge="
                  + removeEdgeNo
                  + ", old_bounds="
                  + room_box
                  + ", newBounds="
                  + newRoom.get_shape().bounding_box());
          curr_incomplete_room.set_shape(newRoom.get_shape());
          curr_incomplete_room.set_contained_shape(newRoom.get_contained_shape());
          return true;
        }
      }
    }
    return false;
  }

  private void add_sorted_neighbour(
      SearchTreeObject p_search_tree_object, IntBox p_neighbour_shape, IntBox p_intersection) {
    SortedRoomNeighbour newNeighbour =
        new SortedRoomNeighbour(p_search_tree_object, p_neighbour_shape, p_intersection);
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
        SearchTreeObject p_search_tree_object, IntBox p_neighbour_shape, IntBox p_intersection) {
      searchTreeObject = p_search_tree_object;
      shape = p_neighbour_shape;
      intersection = p_intersection;

      if (p_intersection.ll.y == roomShape.ll.y
          && p_intersection.ur.x > roomShape.ll.x
          && p_intersection.ll.x < roomShape.ur.x) {
        edgeInteriorTouchesObstacle[0] = true;
      }
      if (p_intersection.ur.x == roomShape.ur.x
          && p_intersection.ur.y > roomShape.ll.y
          && p_intersection.ll.y < roomShape.ur.y) {
        edgeInteriorTouchesObstacle[1] = true;
      }
      if (p_intersection.ur.y == roomShape.ur.y
          && p_intersection.ur.x > roomShape.ll.x
          && p_intersection.ll.x < roomShape.ur.x) {
        edgeInteriorTouchesObstacle[2] = true;
      }
      if (p_intersection.ll.x == roomShape.ll.x
          && p_intersection.ur.y > roomShape.ll.y
          && p_intersection.ll.y < roomShape.ur.y) {
        edgeInteriorTouchesObstacle[3] = true;
      }

      if (p_intersection.ll.y == roomShape.ll.y && p_intersection.ll.x > roomShape.ll.x) {
        this.firstTouchingSide = 0;
      } else if (p_intersection.ur.x == roomShape.ur.x && p_intersection.ll.y > roomShape.ll.y) {
        this.firstTouchingSide = 1;
      } else if (p_intersection.ur.y == roomShape.ur.y) {
        this.firstTouchingSide = 2;
      } else if (p_intersection.ll.x == roomShape.ll.x) {
        this.firstTouchingSide = 3;
      } else {
        FRLogger.warn("SortedRoomNeighbour: case not expected");
        this.firstTouchingSide = -1;
      }

      if (p_intersection.ll.x == roomShape.ll.x && p_intersection.ll.y > roomShape.ll.y) {
        this.lastTouchingSide = 3;
      } else if (p_intersection.ur.y == roomShape.ur.y && p_intersection.ll.x > roomShape.ll.x) {
        this.lastTouchingSide = 2;
      } else if (p_intersection.ur.x == roomShape.ur.x) {
        this.lastTouchingSide = 1;
      } else if (p_intersection.ll.y == roomShape.ll.y) {
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
    public int compareTo(SortedRoomNeighbour p_other) {
      if (this.firstTouchingSide > p_other.firstTouchingSide) {
        return 1;
      }
      if (this.firstTouchingSide < p_other.firstTouchingSide) {
        return -1;
      }

      // now the first touch of this and p_other is at the same side
      IntBox is1 = this.intersection;
      IntBox is2 = p_other.intersection;
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
        int otherTouchingSideDiff = (p_other.lastTouchingSide - p_other.firstTouchingSide + 4) % 4;
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
        cmpValue = this.searchTreeObject.get_id_no() - p_other.searchTreeObject.get_id_no();
      }
      return cmpValue;
    }
  }
}
