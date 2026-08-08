package app.freerouting.autoroute;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.Connectable;
import app.freerouting.board.FixedState;
import app.freerouting.board.ForcedPadAlgo;
import app.freerouting.board.ForcedViaAlgo;
import app.freerouting.board.Item;
import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.Pin;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.SearchTreeObject;
import app.freerouting.board.ShapeSearchTree;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.core.Padstack;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.ViaInfo;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** Class for auto-routing an incomplete connection via a maze search algorithm. */
public class MazeSearchAlgo {

  private static final int ALREADY_RIPPED_COSTS = 1;

  /** The autoroute engine of this expansion algorithm. */
  public final AutorouteEngine autorouteEngine;

  final AutorouteControl ctrl;

  /** The queue of expanded elements used in this search algorithm. */
  final SortedSet<MazeListElement> mazeExpansionList;

  /**
   * Used for calculating of a good lower bound for the distance between a new MazeExpansionElement
   * and the destination set of the expansion.
   */
  final DestinationDistance destinationDistance;

  /** The search tree for expanding. It is the tree compensated for the current net. */
  private final ShapeSearchTree searchTree;

  private final Random randomGenerator = new Random();

  /** The destination door found by the expanding algorithm. */
  private ExpandableObject destinationDoor;

  private int sectionNoOfDestinationDoor;

  /** Creates a new instance of MazeSearchAlgo */
  MazeSearchAlgo(AutorouteEngine p_autoroute_engine, AutorouteControl p_ctrl) {
    autorouteEngine = p_autoroute_engine;
    ctrl = p_ctrl;
    randomGenerator.setSeed(
        p_ctrl.ripupCosts); // Keep v1.9 deterministic randomization across passes.
    this.searchTree = p_autoroute_engine.autorouteSearchTree;
    mazeExpansionList =
        new TreeSet<>() {
          @Override
          public boolean add(MazeListElement p_element) {
            if (ctrl.isFanout && ctrl.fanoutStartPinCenter != null) {
              app.freerouting.geometry.planar.FloatPoint pinCenterFloat =
                  ctrl.fanoutStartPinCenter.toFloat();
              boolean onStartLayer =
                  p_element.nextRoom != null
                      && p_element.nextRoom.getLayer() == ctrl.fanoutStartPinLayer;
              if (onStartLayer) {
                double maxLen =
                    ctrl.settings.fanout != null && ctrl.settings.fanout.maxEscapeLengthMm != null
                        ? ctrl.settings.fanout.maxEscapeLengthMm * 1000.0
                        : 3000.0;
                double resolution =
                    autorouteEngine.board.communication.getResolution(
                        app.freerouting.board.Unit.UM);
                app.freerouting.geometry.planar.FloatPoint entryPoint =
                    p_element.shapeEntry.a.middlePoint(p_element.shapeEntry.b);
                double dist = entryPoint.distance(pinCenterFloat);
                if (dist > maxLen * resolution) {
                  return false;
                }
              }
              if (p_element.door instanceof ExpansionDrill drill) {
                double minLen =
                    ctrl.settings.fanout != null && ctrl.settings.fanout.minEscapeLengthMm != null
                        ? ctrl.settings.fanout.minEscapeLengthMm * 1000.0
                        : 500.0;
                double resolution =
                    autorouteEngine.board.communication.getResolution(
                        app.freerouting.board.Unit.UM);
                double drillDist = drill.location.toFloat().distance(pinCenterFloat);
                if (drillDist < minLen * resolution) {
                  return false;
                }
              }
            }
            return super.add(p_element);
          }
        };
    destinationDistance =
        new DestinationDistance(
            ctrl.traceCosts, ctrl.layerActive, ctrl.minNormalViaCost, ctrl.minCheapViaCost);
  }

  /**
   * Initializes a new instance of MazeSearchAlgo for searching a connection between p_start_items
   * and p_destination_items. Returns null, if the initialisation failed.
   */
  public static MazeSearchAlgo getInstance(
      Set<Item> p_start_items,
      Set<Item> p_destination_items,
      AutorouteEngine p_autoroute_database,
      AutorouteControl p_ctrl) {
    MazeSearchAlgo newInstance = new MazeSearchAlgo(p_autoroute_database, p_ctrl);
    MazeSearchAlgo result;
    if (newInstance.init(p_start_items, p_destination_items)) {
      result = newInstance;
    } else {
      result = null;
    }
    return result;
  }

  /**
   * Looks for pins with more than 1 nets and reduces shapes of traces of foreign nets, which are
   * already connected to such a pin, so that the pin center is not blocked for connection.
   */
  private static void reduceTraceShapesAtTiePins(
      Collection<Item> p_item_list, int p_own_net_no, ShapeSearchTree p_autoroute_tree) {
    for (Item currItem : p_item_list) {
      if ((currItem instanceof Pin curr_tie_pin) && currItem.netCount() > 1) {
        Collection<Item> pinContacts = currItem.getNormalContacts();
        for (Item currContact : pinContacts) {
          if (!(currContact instanceof PolylineTrace) || currContact.containsNet(p_own_net_no)) {
            continue;
          }
          p_autoroute_tree.reduceTraceShapeAtTiePin(curr_tie_pin, (PolylineTrace) currContact);
        }
      }
    }
  }

  /**
   * Return the additional cost factor for ripping the trace, if it is connected to a fanout via or
   * 1, if no fanout via was found.
   */
  private static double calcFanoutViaRipupCostFactor(Trace p_trace) {
    final double FANOUT_COST_CONST = 20000;
    Collection<Item> currEndContacts;
    for (int i = 0; i < 2; i++) {
      if (i == 0) {
        currEndContacts = p_trace.getStartContacts();
      } else {
        currEndContacts = p_trace.getEndContacts();
      }
      if (currEndContacts.size() != 1) {
        continue;
      }
      Item currTraceContact = currEndContacts.iterator().next();
      boolean protectFanoutVia = false;
      if (currTraceContact instanceof Pin
          && currTraceContact.firstLayer() == currTraceContact.lastLayer()) {
        protectFanoutVia = true;
      } else if (currTraceContact instanceof PolylineTrace contactTrace
          && currTraceContact.getFixedState() == FixedState.SHOVE_FIXED) {
        // look for shove fixed exit traces of SMD-pins
        if (contactTrace.cornerCount() == 2) {
          protectFanoutVia = true;
        }
      }

      if (protectFanoutVia) {
        double fanoutViaCostFactor = p_trace.getHalfWidth() / p_trace.getLength();
        fanoutViaCostFactor *= fanoutViaCostFactor;
        fanoutViaCostFactor *= FANOUT_COST_CONST;
        return Math.max(fanoutViaCostFactor, 1);
      }
    }
    return 1;
  }

  /**
   * Returns the perpendicular projection of p_from_segment onto p_to_segment. Returns null, if the
   * projection is empty.
   */
  private static FloatLine segmentProjection(FloatLine p_from_segment, FloatLine p_to_segment) {
    FloatLine checkSegment = p_from_segment.adjustDirection(p_to_segment);
    FloatLine firstProjection = p_to_segment.segmentProjection(checkSegment);
    FloatLine secondProjection = p_to_segment.segmentProjection2(checkSegment);
    FloatLine result;
    if (firstProjection != null && secondProjection != null) {
      FloatPoint resultA;
      if (firstProjection.a == p_to_segment.a || secondProjection.a == p_to_segment.a) {
        resultA = p_to_segment.a;
      } else if (firstProjection.a.distanceSquare(p_to_segment.a)
          <= secondProjection.a.distanceSquare(p_to_segment.a)) {
        resultA = firstProjection.a;
      } else {
        resultA = secondProjection.a;
      }
      FloatPoint resultB;
      if (firstProjection.b == p_to_segment.b || secondProjection.b == p_to_segment.b) {
        resultB = p_to_segment.b;
      } else if (firstProjection.b.distanceSquare(p_to_segment.b)
          <= secondProjection.b.distanceSquare(p_to_segment.b)) {
        resultB = firstProjection.b;
      } else {
        resultB = secondProjection.b;
      }
      result = new FloatLine(resultA, resultB);
    } else if (firstProjection != null) {
      result = firstProjection;
    } else {
      result = secondProjection;
    }
    return result;
  }

  /**
   * Does a maze search to find a connection route between the start and the destination items. If
   * the algorithm succeeds, the ExpansionDoor and its section number of the found destination is
   * returned, from where the whole found connection can be backtracked. Otherwise, the return value
   * will be null.
   */
  public Result findConnection() {
    while (occupyNextElement()) {
      continue;
    }
    if (this.destinationDoor == null) {
      return null;
    }
    return new Result(this.destinationDoor, this.sectionNoOfDestinationDoor);
  }

  /**
   * Expands the next element in the maze expansion list. Returns false, if the expansion list is
   * exhausted or the destination is reached.
   */
  public boolean occupyNextElement() {
    if (this.destinationDoor != null) {
      return false; // destination already reached
    }
    MazeListElement listElement = null;
    MazeSearchElement currDoorSection = null;
    // Search the next element, which is not yet expanded.
    boolean nextElementFound = false;
    while (!mazeExpansionList.isEmpty()) {
      if (this.autorouteEngine.isStopRequested()) {
        return false;
      }

      Iterator<MazeListElement> it = mazeExpansionList.iterator();
      listElement = it.next();
      it.remove();

      int currSectionNo = listElement.sectionNoOfDoor;
      currDoorSection = listElement.door.getMazeSearchElement(currSectionNo);

      if (!currDoorSection.isOccupied) {
        nextElementFound = true;
        break;
      }
    }
    if (!nextElementFound) {
      return false;
    }
    currDoorSection.backtrackDoor = listElement.backtrackDoor;
    currDoorSection.sectionNoOfBacktrackDoor = listElement.sectionNoOfBacktrackDoor;
    currDoorSection.roomRipped = listElement.roomRipped;
    currDoorSection.ripupCost = listElement.ripupCost;
    currDoorSection.adjustment = listElement.adjustment;

    if (listElement.door instanceof DrillPage) {
      expandToDrillsOfPage(listElement);
      return true;
    }

    if (listElement.door instanceof TargetItemExpansionDoor currDoor) {
      if (currDoor.isDestinationDoor()) {
        // The destination is reached.
        this.destinationDoor = currDoor;
        this.sectionNoOfDestinationDoor = listElement.sectionNoOfDoor;
        return false;
      }
    }
    if (ctrl.isFanout
        && listElement.door instanceof ExpansionDrill
        && listElement.backtrackDoor instanceof ExpansionDrill) {
      // algorithm completed after the first drill;
      this.destinationDoor = listElement.door;
      this.sectionNoOfDestinationDoor = listElement.sectionNoOfDoor;
      return false;
    }
    if (ctrl.viasAllowed
        && listElement.door instanceof ExpansionDrill
        && !(listElement.backtrackDoor instanceof ExpansionDrill)) {
      expandToOtherLayers(listElement);
    }

    if (listElement.nextRoom != null) {
      if (!expandToRoomDoors(listElement)) {
        return true; // occupation by ripup is delayed or nothing was expanded
        // In case nothing was expanded allow the section to be occupied from
        // somewhere else, if the next room is thin.
      }
    }
    currDoorSection.isOccupied = true;
    return true;
  }

  /**
   * Expands the other door section of the room. Returns true, if the from door section has to be
   * occupied, and false, if the occupation for is delayed.
   */
  private boolean expandToRoomDoors(MazeListElement p_list_element) {

    // Complete the neighbour rooms to make sure, that the
    // doors of this room will not change later on.
    int layerNo = p_list_element.nextRoom.getLayer();

    boolean layerActive = ctrl.layerActive[layerNo];
    if (!layerActive) {
      if (autorouteEngine.board.layerStructure.arr[layerNo].isSignal) {
        return true;
      }
    }

    double halfWidth = ctrl.compensatedTraceHalfWidth[layerNo];
    boolean currDoorIsSmall = false;
    if (p_list_element.door instanceof ExpansionDoor currDoor) {
      double halfWidthAdd = halfWidth + AutorouteEngine.TRACE_WIDTH_TOLERANCE;
      if (this.ctrl.withNeckdown) {
        // try evtl. neckdown at a destination pin
        double neckDownHalfWidth = checkNeckDownAtDestPin(p_list_element.nextRoom);
        if (neckDownHalfWidth > 0) {
          halfWidthAdd = Math.min(halfWidthAdd, neckDownHalfWidth);
          halfWidth = halfWidthAdd;
        }
      }
      currDoorIsSmall = doorIsSmall(currDoor, 2 * halfWidthAdd);
    }

    int doorCountBeforeCompletion = p_list_element.nextRoom.getDoors().size();
    this.autorouteEngine.completeNeighbourRooms(p_list_element.nextRoom);
    int doorCountAfterCompletion = p_list_element.nextRoom.getDoors().size();
    FRLogger.trace(
        "ROOM_COMPLETE_SYNC"
            + ", net="
            + ctrl.netNo
            + ", layer="
            + layerNo
            + ", from_section="
            + p_list_element.sectionNoOfDoor
            + ", backtrack_section="
            + p_list_element.sectionNoOfBacktrackDoor
            + ", from_door="
            + describeExpandable(p_list_element.door)
            + ", nextRoom="
            + describeRoom(p_list_element.nextRoom)
            + ", door_count_before="
            + doorCountBeforeCompletion
            + ", door_count_after="
            + doorCountAfterCompletion);

    FloatPoint shapeEntryMiddle =
        p_list_element.shapeEntry.a.middlePoint(p_list_element.shapeEntry.b);

    if (this.ctrl.withNeckdown && p_list_element.door instanceof TargetItemExpansionDoor door) {
      // try evtl. neckdown at a start pin
      Item startItem = door.item;
      if (startItem instanceof Pin pin) {
        double neckdownHalfWidth = pin.getTraceNeckdownHalfwidth(layerNo);
        if (neckdownHalfWidth > 0) {
          halfWidth = Math.min(halfWidth, neckdownHalfWidth);
        }
      }
    }

    boolean nextRoomIsThick = true;
    if (p_list_element.nextRoom instanceof ObstacleExpansionRoom room) {
      nextRoomIsThick = roomShapeIsThick(room);
    } else {
      TileShape nextRoomShape = p_list_element.nextRoom.getShape();
      if (nextRoomShape.minWidth() < 2 * halfWidth) {
        nextRoomIsThick = false; // to prevent problems with the opposite side
      } else if (!p_list_element.alreadyChecked
          && p_list_element.door.getDimension() == 1
          && !currDoorIsSmall) {
        // The algorithm below works only, if p_location is on the border of p_room_shape.
        // That is only the case for 1 dimensional doors.
        // For small doors the check is done in check_leaving_via below.

        FloatPoint[] nearestPoints =
            nextRoomShape.nearestBorderPointsApprox(shapeEntryMiddle, 2);
        if (nearestPoints.length < 2) {
          FRLogger.warn("MazeSearchAlgo.expand_to_room_doors: nearestPoints.length == 2 expected");
          nextRoomIsThick = false;
        } else {
          double currDist = nearestPoints[1].distance(shapeEntryMiddle);
          nextRoomIsThick = currDist > halfWidth + 1;
        }
      }
    }
    if (!layerActive && p_list_element.door instanceof ExpansionDrill drill) {
      // check for drill to a foreign conduction area on split plane.
      Point drillLocation = drill.location;
      ItemSelectionFilter filter =
          new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.CONDUCTION);
      Set<Item> pickedItems = autorouteEngine.board.pickItems(drillLocation, layerNo, filter);
      for (Item currItem : pickedItems) {
        if (!currItem.containsNet(ctrl.netNo)) {
          return true;
        }
      }
    }
    boolean somethingExpanded =
        expandToTargetDoors(p_list_element, nextRoomIsThick, currDoorIsSmall, shapeEntryMiddle);

    if (!layerActive) {
      return true;
    }

    int ripupCosts = 0;

    if (p_list_element.nextRoom instanceof FreeSpaceExpansionRoom) {
      if (!p_list_element.alreadyChecked) {
        if (currDoorIsSmall) {
          boolean enterThroughSmallDoor = false;
          if (nextRoomIsThick) {
            // check to enter the thick room from a ripped item through a small door (after
            // ripup)
            enterThroughSmallDoor = checkLeavingRippedItem(p_list_element);
          }
          if (!enterThroughSmallDoor) {
            return somethingExpanded;
          }
        }
      }
    } else if (p_list_element.nextRoom instanceof ObstacleExpansionRoom obstacle_room) {

      if (!p_list_element.alreadyChecked) {
        boolean roomRippable = false;
        if (this.ctrl.ripupAllowed) {
          ripupCosts = checkRipup(p_list_element, obstacle_room.getItem(), currDoorIsSmall);
          roomRippable = ripupCosts >= 0;
        }

        if (ripupCosts != ALREADY_RIPPED_COSTS && nextRoomIsThick) {
          Item obstacleItem = obstacle_room.getItem();
          if (!currDoorIsSmall
              && this.ctrl.maxShoveTraceRecursionDepth > 0
              && obstacleItem instanceof PolylineTrace) {
            boolean shoved = shoveTraceRoom(p_list_element, obstacle_room);
            if (!shoved) {
              if (ripupCosts > 0) {
                // delay the occupation by ripup to allow shoving the room by another door
                // sections.
                MazeListElement newElement =
                    new MazeListElement(
                        p_list_element.door,
                        p_list_element.sectionNoOfDoor,
                        p_list_element.backtrackDoor,
                        p_list_element.sectionNoOfBacktrackDoor,
                        p_list_element.expansionValue + ripupCosts,
                        p_list_element.sortingValue + ripupCosts,
                        p_list_element.nextRoom,
                        p_list_element.shapeEntry,
                        true,
                        p_list_element.adjustment,
                        true);
                newElement.ripupCost = (int) ripupCosts;
                this.mazeExpansionList.add(newElement);
              }
              return somethingExpanded;
            }
          }
        }
        if (!roomRippable) {
          return true;
        }
      }
    }

    List<ExpansionDoor> roomDoorsSnapshot = new LinkedList<>(p_list_element.nextRoom.getDoors());
    FRLogger.trace(
        "ROOM_DOOR context from_section="
            + p_list_element.sectionNoOfDoor
            + ", backtrack_section="
            + p_list_element.sectionNoOfBacktrackDoor
            + ", from_door="
            + describeExpandable(p_list_element.door)
            + ", nextRoom="
            + describeRoom(p_list_element.nextRoom)
            + ", net="
            + ctrl.netNo);
    for (int door_index = 0; door_index < roomDoorsSnapshot.size(); door_index++) {
      ExpansionDoor candidateDoor = roomDoorsSnapshot.get(door_index);
      FRLogger.trace(
          "ROOM_DOOR candidate index="
              + door_index
              + ", from_section="
              + p_list_element.sectionNoOfDoor
              + ", backtrack_section="
              + p_list_element.sectionNoOfBacktrackDoor
              + ", from_door="
              + describeExpandable(p_list_element.door)
              + ", nextRoom="
              + describeRoom(p_list_element.nextRoom)
              + ", candidate="
              + describeExpandable(candidateDoor)
              + ", net="
              + ctrl.netNo);
    }

    for (ExpansionDoor to_door : roomDoorsSnapshot) {
      if (to_door == p_list_element.door) {
        continue;
      }
      if (expandToDoor(
          to_door,
          p_list_element,
          ripupCosts,
          nextRoomIsThick,
          MazeSearchElement.Adjustment.NONE)) {
        somethingExpanded = true;
      }
    }

    // Expand also the drill pages intersecting the room.
    if (ctrl.viasAllowed && !(p_list_element.door instanceof ExpansionDrill)) {
      if ((somethingExpanded || nextRoomIsThick)
          && p_list_element.nextRoom instanceof CompleteFreeSpaceExpansionRoom) {
        // avoid setting somethingExpanded to true when nextRoom is thin to allow
        // occupying by
        // different sections of the door
        Collection<DrillPage> overlappingDrillPages =
            this.autorouteEngine.drillPageArray.overlappingPages(
                p_list_element.nextRoom.getShape());
        {
          for (DrillPage to_drill_page : overlappingDrillPages) {
            expandToDrillPage(to_drill_page, p_list_element);
            somethingExpanded = true;
          }
        }
      } else if (p_list_element.nextRoom instanceof ObstacleExpansionRoom room) {
        Item currObstacleItem = room.getItem();
        if (currObstacleItem instanceof Via currVia) {
          ExpansionDrill viaDrillInfo =
              currVia.getAutorouteDrillInfo(this.autorouteEngine.autorouteSearchTree);
          expandToDrill(viaDrillInfo, p_list_element, ripupCosts);
        }
      }
    }

    return somethingExpanded;
  }

  /** Expand the target doors of the room. Returns true, if at least 1 target door was expanded */
  private boolean expandToTargetDoors(
      MazeListElement p_list_element,
      boolean p_next_room_is_thick,
      boolean p_curr_door_is_small,
      FloatPoint p_shape_entry_middle) {
    if (p_curr_door_is_small) {
      boolean enterThroughSmallDoor = false;
      if (p_list_element.door instanceof ExpansionDoor) {
        CompleteExpansionRoom fromRoom = p_list_element.door.otherRoom(p_list_element.nextRoom);
        if (fromRoom instanceof ObstacleExpansionRoom) {
          // otherwise entering through the small door may fail, because it was not
          // checked.
          enterThroughSmallDoor = true;
        }
      }
      if (!enterThroughSmallDoor) {
        return false;
      }
    }
    boolean result = false;
    for (TargetItemExpansionDoor to_door : p_list_element.nextRoom.getTargetDoors()) {
      if (to_door == p_list_element.door) {
        continue;
      }
      // Validate index before calling - prevents warning when indices become stale
      // during routing
      int treeShapeCount = to_door.item.treeShapeCount(this.autorouteEngine.autorouteSearchTree);
      if (to_door.treeEntryNo < 0 || to_door.treeEntryNo >= treeShapeCount) {
        // Index out of range (trace was modified during routing)
        continue;
      }
      TileShape targetShape =
          ((Connectable) to_door.item)
              .getTraceConnectionShape(
                  this.autorouteEngine.autorouteSearchTree, to_door.treeEntryNo);
      if (targetShape == null) {
        // Item's tree shape index out of range (can happen when traces are modified
        // during routing)
        continue;
      }
      FloatPoint connectionPoint = targetShape.nearestPointApprox(p_shape_entry_middle);
      if (!p_next_room_is_thick) {
        // check the line from p_shape_entry_middle to the nearest point.
        int[] currNetNoArr = new int[1];
        currNetNoArr[0] = this.ctrl.netNo;
        int currLayer = p_list_element.nextRoom.getLayer();
        IntPoint[] checkPoints = new IntPoint[2];
        checkPoints[0] = p_shape_entry_middle.round();
        checkPoints[1] = connectionPoint.round();
        if (!checkPoints[0].equals(checkPoints[1])) {
          Polyline checkPolyline = new Polyline(checkPoints);
          boolean checkOk =
              autorouteEngine.board.checkForcedTracePolyline(
                  checkPolyline,
                  ctrl.traceHalfWidth[currLayer],
                  currLayer,
                  currNetNoArr,
                  ctrl.traceClearanceClassNo,
                  ctrl.maxShoveTraceRecursionDepth,
                  ctrl.maxShoveViaRecursionDepth,
                  ctrl.maxSpringOverRecursionDepth);
          if (!checkOk) {
            continue;
          }
        }
      }

      FloatLine newShapeEntry = new FloatLine(connectionPoint, connectionPoint);

      if (expandToDoorSection(
          to_door, 0, newShapeEntry, p_list_element, 0, MazeSearchElement.Adjustment.NONE)) {
        result = true;
      }
    }
    return result;
  }

  /** Return true, if at least 1 door ection was expanded. */
  private boolean expandToDoor(
      ExpansionDoor p_to_door,
      MazeListElement p_list_element,
      int p_add_costs,
      boolean p_next_room_is_thick,
      MazeSearchElement.Adjustment p_adjustment) {
    double halfWidth = ctrl.compensatedTraceHalfWidth[p_list_element.nextRoom.getLayer()];
    boolean somethingExpanded = false;
    FloatLine[] lineSections = p_to_door.getSectionSegments(halfWidth);

    for (int i = 0; i < lineSections.length; i++) {
      if (p_to_door.sectionArr[i].isOccupied) {
        continue;
      }
      FloatLine newShapeEntry;
      if (p_next_room_is_thick) {
        newShapeEntry = lineSections[i];
        if (p_to_door.dimension == 1
            && lineSections.length == 1
            && p_to_door.firstRoom instanceof CompleteFreeSpaceExpansionRoom
            && p_to_door.secondRoom instanceof CompleteFreeSpaceExpansionRoom) {
          // check entering the p_to_door at an acute corner of the shape of
          // p_list_element.nextRoom
          FloatPoint shapeEntryMiddle = newShapeEntry.a.middlePoint(newShapeEntry.b);
          TileShape roomShape = p_list_element.nextRoom.getShape();
          if (roomShape.minWidth() < 2 * halfWidth) {
            return false;
          }
          FloatPoint[] nearestPoints = roomShape.nearestBorderPointsApprox(shapeEntryMiddle, 2);
          if (nearestPoints.length < 2
              || nearestPoints[1].distance(shapeEntryMiddle) <= halfWidth + 1) {
            return false;
          }
        }
      } else {
        // expand only doors on the opposite side of the room from the shapeEntry.
        if (p_to_door.dimension == 1
            && i == 0
            && lineSections[0].b.distanceSquare(lineSections[0].a) < 1) {
          // p_to_door is small belonging to a via or thin room
          continue;
        }
        newShapeEntry = segmentProjection(p_list_element.shapeEntry, lineSections[i]);
        if (newShapeEntry == null) {
          continue;
        }
      }

      if (expandToDoorSection(
          p_to_door, i, newShapeEntry, p_list_element, p_add_costs, p_adjustment)) {
        somethingExpanded = true;
      }
    }
    return somethingExpanded;
  }

  /** Checks, if the width p_door is big enough for a trace with width p_trace_width. */
  private boolean doorIsSmall(ExpansionDoor p_door, double p_trace_width) {
    if (p_door.dimension == 1
        || p_door.firstRoom instanceof CompleteFreeSpaceExpansionRoom
            && p_door.secondRoom instanceof CompleteFreeSpaceExpansionRoom) {
      TileShape doorShape = p_door.getShape();
      if (doorShape.isEmpty()) {
        FRLogger.trace("MazeSearchAlgo:check_door_width doorShape is empty");
        return true;
      }

      double doorLength;
      AngleRestriction angleRestriction = autorouteEngine.board.rules.getTraceAngleRestriction();
      if (angleRestriction == AngleRestriction.NINETY_DEGREE) {
        IntBox doorBox = doorShape.boundingBox();
        doorLength = doorBox.maxWidth();
      } else if (angleRestriction == AngleRestriction.FORTYFIVE_DEGREE) {
        IntOctagon doorOct = doorShape.boundingOctagon();
        doorLength = doorOct.maxWidth();
      } else {
        FloatLine doorLineSegment = doorShape.diagonalCornerSegment();
        doorLength = doorLineSegment.b.distance(doorLineSegment.a);
      }
      return doorLength < p_trace_width;
    }
    return false;
  }

  /** Return true, if the door section was successfully expanded. */
  private boolean expandToDoorSection(
      ExpandableObject p_door,
      int p_section_no,
      FloatLine p_shape_entry,
      MazeListElement p_from_element,
      int p_add_costs,
      MazeSearchElement.Adjustment p_adjustment) {
    boolean doorSectionOccupied = p_door.getMazeSearchElement(p_section_no).isOccupied;
    if (doorSectionOccupied || p_shape_entry == null) {
      FRLogger.trace(
          "RAW_SECTION skip selected_section="
              + p_section_no
              + ", from_section="
              + p_from_element.sectionNoOfDoor
              + ", backtrack_section="
              + p_from_element.sectionNoOfBacktrackDoor
              + ", occupied="
              + doorSectionOccupied
              + ", shape_entry_null="
              + (p_shape_entry == null)
              + ", adjustment="
              + p_adjustment
              + ", door="
              + describeExpandable(p_door)
              + ", door_bounds="
              + describeExpandableBounds(p_door)
              + ", from_door="
              + describeExpandable(p_from_element.door)
              + ", from_door_bounds="
              + describeExpandableBounds(p_from_element.door)
              + ", net="
              + ctrl.netNo);
      FRLogger.trace(
          "MazeSearchAlgo.expand_to_door_section",
          "skip_assign_raw",
          "selected_section="
              + p_section_no
              + ", from_section="
              + p_from_element.sectionNoOfDoor
              + ", backtrack_section="
              + p_from_element.sectionNoOfBacktrackDoor
              + ", occupied="
              + doorSectionOccupied
              + ", shape_entry_null="
              + (p_shape_entry == null)
              + ", adjustment="
              + p_adjustment,
          "Net #"
              + ctrl.netNo
              + ", door="
              + describeExpandable(p_door)
              + ", door_bounds="
              + describeExpandableBounds(p_door)
              + ", from_door="
              + describeExpandable(p_from_element.door)
              + ", from_door_bounds="
              + describeExpandableBounds(p_from_element.door),
          toImpactedPoints(p_shape_entry));
      return false;
    }
    CompleteExpansionRoom nextRoom = p_door.otherRoom(p_from_element.nextRoom);
    int layer = p_from_element.nextRoom.getLayer();
    FloatPoint shapeEntryMiddle = p_shape_entry.a.middlePoint(p_shape_entry.b);

    double bendCostPenalty = 0.0;
    if (ctrl.bendCosts[layer] > 0.0 && p_from_element.backtrackDoor != null) {
      FloatPoint fromMid = p_from_element.shapeEntry.a.middlePoint(p_from_element.shapeEntry.b);
      // Build vectors prev→curr and curr→next to detect a direction change.
      FloatPoint backtrackCog = p_from_element.backtrackDoor.getShape().centreOfGravity();
      double prevDx = fromMid.x - backtrackCog.x;
      double prevDy = fromMid.y - backtrackCog.y;
      double nextDx = shapeEntryMiddle.x - fromMid.x;
      double nextDy = shapeEntryMiddle.y - fromMid.y;
      double crossProduct = prevDx * nextDy - prevDy * nextDx;
      double sqLenPrev = prevDx * prevDx + prevDy * prevDy;
      double sqLenNext = nextDx * nextDx + nextDy * nextDy;
      // Use a normalized threshold (sin² of angle > 0.01, approx. 5.7°) to be scale-independent.
      if (sqLenPrev > 0.0
          && sqLenNext > 0.0
          && (crossProduct * crossProduct) > 0.01 * sqLenPrev * sqLenNext) {
        bendCostPenalty = ctrl.bendCosts[layer];
      }
    }

    double expansionValue =
        p_from_element.expansionValue
            + p_add_costs
            + bendCostPenalty
            + shapeEntryMiddle.weightedDistance(
                p_from_element.shapeEntry.a.middlePoint(p_from_element.shapeEntry.b),
                ctrl.traceCosts[layer].horizontal,
                ctrl.traceCosts[layer].vertical);
    double sortingValue =
        expansionValue + this.destinationDistance.calculate(shapeEntryMiddle, layer);
    boolean roomRipped =
        p_add_costs > 0 && p_adjustment == MazeSearchElement.Adjustment.NONE
            || p_from_element.alreadyChecked && p_from_element.roomRipped;

    MazeListElement newElement =
        new MazeListElement(
            p_door,
            p_section_no,
            p_from_element.door,
            p_from_element.sectionNoOfDoor,
            expansionValue,
            sortingValue,
            nextRoom,
            p_shape_entry,
            roomRipped,
            p_adjustment,
            false);
    // Store the direct ripup cost on this element (non-zero only when this specific door
    // caused a ripup; propagated roomRipped from a parent keeps ripupCost=0).
    if (p_add_costs > 0 && p_adjustment == MazeSearchElement.Adjustment.NONE) {
      newElement.ripupCost = (int) p_add_costs;
    }
    FRLogger.trace(
        "RAW_SECTION assign selected_section="
            + p_section_no
            + ", from_section="
            + p_from_element.sectionNoOfDoor
            + ", backtrack_section="
            + p_from_element.sectionNoOfBacktrackDoor
            + ", add_costs="
            + p_add_costs
            + ", adjustment="
            + p_adjustment
            + ", roomRipped="
            + roomRipped
            + ", expansionValue="
            + expansionValue
            + ", sortingValue="
            + sortingValue
            + ", door="
            + describeExpandable(p_door)
            + ", door_bounds="
            + describeExpandableBounds(p_door)
            + ", from_door="
            + describeExpandable(p_from_element.door)
            + ", from_door_bounds="
            + describeExpandableBounds(p_from_element.door)
            + ", net="
            + ctrl.netNo);
    FRLogger.trace(
        "MazeSearchAlgo.expand_to_door_section",
        "assign_raw",
        "selected_section="
            + p_section_no
            + ", from_section="
            + p_from_element.sectionNoOfDoor
            + ", backtrack_section="
            + p_from_element.sectionNoOfBacktrackDoor
            + ", add_costs="
            + p_add_costs
            + ", adjustment="
            + p_adjustment
            + ", roomRipped="
            + roomRipped
            + ", expansionValue="
            + expansionValue
            + ", sortingValue="
            + sortingValue,
        "Net #"
            + ctrl.netNo
            + ", door="
            + describeExpandable(p_door)
            + ", door_bounds="
            + describeExpandableBounds(p_door)
            + ", from_door="
            + describeExpandable(p_from_element.door)
            + ", from_door_bounds="
            + describeExpandableBounds(p_from_element.door),
        toImpactedPoints(p_shape_entry));
    this.mazeExpansionList.add(newElement);
    return true;
  }

  private static String describeExpandable(ExpandableObject p_door) {
    if (p_door == null) {
      return "null";
    }
    String sectionCount = safeMazeSectionCount(p_door);
    if (p_door instanceof TargetItemExpansionDoor targetDoor) {
      return "TargetItemExpansionDoor"
          + "/item="
          + targetDoor.item.getIdNo()
          + "/tree_entry="
          + targetDoor.treeEntryNo
          + "/dim="
          + p_door.getDimension()
          + "/sections="
          + sectionCount;
    }
    if (p_door instanceof ExpansionDrill drill) {
      return "ExpansionDrill"
          + "/location="
          + drill.location
          + "/layers="
          + drill.firstLayer
          + "-"
          + drill.lastLayer
          + "/dim="
          + p_door.getDimension()
          + "/sections="
          + sectionCount;
    }
    IntBox bounds = p_door.getShape().boundingBox();
    return p_door.getClass().getSimpleName()
        + "/bounds=[("
        + bounds.ll.x
        + ","
        + bounds.ll.y
        + ")..("
        + bounds.ur.x
        + ","
        + bounds.ur.y
        + ")]"
        + "/dim="
        + p_door.getDimension()
        + "/sections="
        + sectionCount;
  }

  private static String safeMazeSectionCount(ExpandableObject p_door) {
    try {
      return Integer.toString(p_door.mazeSearchElementCount());
    } catch (RuntimeException e) {
      return "uninitialized";
    }
  }

  private static String describeExpandableBounds(ExpandableObject p_door) {
    if (p_door == null) {
      return "null";
    }
    IntBox bounds = p_door.getShape().boundingBox();
    return "[(" + bounds.ll.x + "," + bounds.ll.y + ")..(" + bounds.ur.x + "," + bounds.ur.y + ")]";
  }

  private static String describeRoom(CompleteExpansionRoom p_room) {
    if (p_room == null) {
      return "null";
    }
    IntBox bounds = p_room.getShape().boundingBox();
    return p_room.getClass().getSimpleName()
        + "/layer="
        + p_room.getLayer()
        + "/bounds=[("
        + bounds.ll.x
        + ","
        + bounds.ll.y
        + ")..("
        + bounds.ur.x
        + ","
        + bounds.ur.y
        + ")]";
  }

  private static Point[] toImpactedPoints(FloatLine p_shape_entry) {
    if (p_shape_entry == null) {
      return null;
    }
    return new Point[] {p_shape_entry.a.round(), p_shape_entry.b.round()};
  }

  private boolean shouldTraceFanoutDiagnostics() {
    return ctrl.isFanout
        && ctrl.fanoutStartPinName != null
        && ctrl.fanoutStartPinName.startsWith("U27-");
  }

  private String fanoutDiagnosticLabel() {
    return ctrl.fanoutStartPinName == null
        ? "fanout-pin(net=" + ctrl.netNo + ")"
        : ctrl.fanoutStartPinName;
  }

  private void traceFanoutDiagnostic(String event, String message) {
    if (!shouldTraceFanoutDiagnostics()) {
      return;
    }
    FRLogger.trace(
        "FANOUT_DIAG event="
            + event
            + ", pin="
            + fanoutDiagnosticLabel()
            + ", net="
            + ctrl.netNo
            + ", "
            + message);
  }

  private void expandToDrill(
      ExpansionDrill p_drill, MazeListElement p_from_element, int p_add_costs) {
    int layer = p_from_element.nextRoom.getLayer();
    int traceHalfWidth = this.ctrl.compensatedTraceHalfWidth[layer];
    boolean roomShapeIsThin = p_from_element.nextRoom.getShape().minWidth() < 2 * traceHalfWidth;

    if (roomShapeIsThin) {
      // expand only drills intersecting the backtrack door
      if (p_from_element.backtrackDoor == null
          || !p_drill.getShape().intersects(p_from_element.backtrackDoor.getShape())) {
        traceFanoutDiagnostic(
            "drill_rejected_thin_room_no_backtrack_intersection",
            "drill="
                + describeExpandable(p_drill)
                + ", from_door="
                + describeExpandable(p_from_element.door)
                + ", backtrack="
                + describeExpandable(p_from_element.backtrackDoor)
                + ", room="
                + describeRoom(p_from_element.nextRoom));
        return;
      }
    }

    double viaRadius = ctrl.viaRadiusArr[layer];
    ConvexShape shrinkedDrillShape = p_drill.getShape().shrink(viaRadius);
    FloatPoint compareCorner =
        p_from_element.shapeEntry.a.middlePoint(p_from_element.shapeEntry.b);
    if (p_from_element.door instanceof DrillPage
        && p_from_element.backtrackDoor instanceof TargetItemExpansionDoor door) {
      // If expansion comes from a pin with trace exit directions the expansionValue
      // is calculated
      // from the nearest trace exit point instead from the center olf the pin.
      Item fromItem = door.item;
      if (fromItem instanceof Pin pin) {
        FloatPoint nearestExitCorner =
            pin.nearestTraceExitCorner(p_drill.location.toFloat(), traceHalfWidth, layer);
        if (nearestExitCorner != null) {
          compareCorner = nearestExitCorner;
        }
      }
    }
    FloatPoint nearestPoint = shrinkedDrillShape.nearestPointApprox(compareCorner);
    FloatLine shapeEntry = new FloatLine(nearestPoint, nearestPoint);
    int sectionNo = layer - p_drill.firstLayer;
    double expansionValue =
        p_from_element.expansionValue
            + p_add_costs
            + nearestPoint.weightedDistance(
                compareCorner, ctrl.traceCosts[layer].horizontal, ctrl.traceCosts[layer].vertical);
    ExpandableObject newBacktrackDoor;
    int newSectionNoOfBacktrackDoor;
    if (p_from_element.door instanceof DrillPage) {
      newBacktrackDoor = p_from_element.backtrackDoor;
      newSectionNoOfBacktrackDoor = p_from_element.sectionNoOfBacktrackDoor;
    } else {
      // Expanded directly through already existing via
      // The step expand_to_drill_page is skipped
      newBacktrackDoor = p_from_element.door;
      newSectionNoOfBacktrackDoor = p_from_element.sectionNoOfDoor;
      expansionValue += ctrl.minNormalViaCost;
    }
    double sortingValue = expansionValue + this.destinationDistance.calculate(nearestPoint, layer);
    MazeListElement newElement =
        new MazeListElement(
            p_drill,
            sectionNo,
            newBacktrackDoor,
            newSectionNoOfBacktrackDoor,
            expansionValue,
            sortingValue,
            null,
            shapeEntry,
            p_from_element.roomRipped,
            MazeSearchElement.Adjustment.NONE,
            false);
    this.mazeExpansionList.add(newElement);
    traceFanoutDiagnostic(
        "drill_accepted",
        "drill="
            + describeExpandable(p_drill)
            + ", room="
            + describeRoom(p_from_element.nextRoom)
            + ", nearestPoint="
            + nearestPoint
            + ", expansionValue="
            + expansionValue);
  }

  /**
   * A drill page is inserted between an expansion room and the drill to expand in order to prevent
   * performance problems with rooms with big shapes containing many drills.
   */
  private void expandToDrillPage(DrillPage p_drill_page, MazeListElement p_from_element) {

    int layer = p_from_element.nextRoom.getLayer();
    FloatPoint fromElementShapeEntryMiddle =
        p_from_element.shapeEntry.a.middlePoint(p_from_element.shapeEntry.b);
    FloatPoint nearestPoint = p_drill_page.shape.nearestPoint(fromElementShapeEntryMiddle);
    double expansionValue = p_from_element.expansionValue + ctrl.minNormalViaCost;
    double sortingValue =
        expansionValue
            + nearestPoint.weightedDistance(
                fromElementShapeEntryMiddle,
                ctrl.traceCosts[layer].horizontal,
                ctrl.traceCosts[layer].vertical)
            + this.destinationDistance.calculate(nearestPoint, layer);
    MazeListElement newElement =
        new MazeListElement(
            p_drill_page,
            layer,
            p_from_element.door,
            p_from_element.sectionNoOfDoor,
            expansionValue,
            sortingValue,
            p_from_element.nextRoom,
            p_from_element.shapeEntry,
            p_from_element.roomRipped,
            MazeSearchElement.Adjustment.NONE,
            false);
    this.mazeExpansionList.add(newElement);
  }

  private void expandToDrillsOfPage(MazeListElement p_from_element) {
    int fromRoomLayer = p_from_element.sectionNoOfDoor;
    DrillPage drillPage = (DrillPage) p_from_element.door;
    Collection<ExpansionDrill> drillList =
        drillPage.getDrills(this.autorouteEngine, this.ctrl.attachSmdAllowed);
    if (shouldTraceFanoutDiagnostics()) {
      traceFanoutDiagnostic(
          "drill_page_scan",
          "candidate_count="
              + drillList.size()
              + ", attachSmdAllowed="
              + this.ctrl.attachSmdAllowed
              + ", room="
              + describeRoom(p_from_element.nextRoom)
              + ", from_door="
              + describeExpandable(p_from_element.door));
      if (drillList.isEmpty()) {
        traceFanoutDiagnostic("drill_page_empty", "no_candidates=true");
      }
    }
    // Track the first room-mismatch per fanout attempt for first-mismatch investigation.
    boolean firstMismatchLogged = false;
    for (ExpansionDrill currDrill : drillList) {
      int sectionNo = fromRoomLayer - currDrill.firstLayer;
      if (sectionNo < 0 || sectionNo >= currDrill.roomArr.length) {
        traceFanoutDiagnostic(
            "drill_rejected_section_out_of_range",
            "drill="
                + describeExpandable(currDrill)
                + ", section="
                + sectionNo
                + ", room_arr_len="
                + currDrill.roomArr.length);
        continue;
      }
      if (currDrill.roomArr[sectionNo] != p_from_element.nextRoom) {
        traceFanoutDiagnostic(
            "drill_rejected_room_mismatch",
            "drill="
                + describeExpandable(currDrill)
                + ", expected_room="
                + describeRoom(p_from_element.nextRoom)
                + ", drill_room="
                + describeRoom(currDrill.roomArr[sectionNo]));
        // Log the first mismatch per page-scan with extra geometric context for investigation.
        if (!firstMismatchLogged && shouldTraceFanoutDiagnostics()) {
          firstMismatchLogged = true;
          CompleteExpansionRoom expRoom = p_from_element.nextRoom;
          CompleteExpansionRoom drillRoom = currDrill.roomArr[sectionNo];
          FRLogger.trace(
              "FANOUT_DIAG event=first_room_mismatch_detail"
                  + ", pin="
                  + fanoutDiagnosticLabel()
                  + ", net="
                  + ctrl.netNo
                  + ", drillLocation="
                  + currDrill.location
                  + ", expansion_room_id="
                  + System.identityHashCode(expRoom)
                  + ", expansion_room_bounds="
                  + (expRoom != null ? expRoom.getShape() : "null")
                  + ", drill_room_id="
                  + System.identityHashCode(drillRoom)
                  + ", drill_room_bounds="
                  + (drillRoom != null ? drillRoom.getShape() : "null")
                  + ", from_door_type="
                  + (p_from_element.door != null
                      ? p_from_element.door.getClass().getSimpleName()
                      : "null")
                  + ", backtrack_door_type="
                  + (p_from_element.backtrackDoor != null
                      ? p_from_element.backtrackDoor.getClass().getSimpleName()
                      : "null")
                  + ", sectionNo="
                  + sectionNo
                  + ", layer="
                  + fromRoomLayer);
        }
        continue;
      }
      if (currDrill.getMazeSearchElement(sectionNo).isOccupied) {
        traceFanoutDiagnostic(
            "drill_rejected_section_occupied",
            "drill=" + describeExpandable(currDrill) + ", section=" + sectionNo);
        continue;
      }
      expandToDrill(currDrill, p_from_element, 0);
    }
  }

  /** Tries to expand other layers by inserting a via. */
  private void expandToOtherLayers(MazeListElement p_list_element) {
    int viaLowerBound = 0;
    int viaUpperBound = -1;
    ExpansionDrill currDrill = (ExpansionDrill) p_list_element.door;
    int fromLayer = currDrill.firstLayer + p_list_element.sectionNoOfDoor;
    boolean smdAttachedOnComponentSide = false;
    boolean smdAttachedOnSolderSide = false;
    boolean roomRipped;
    if (currDrill.roomArr[p_list_element.sectionNoOfDoor] instanceof ObstacleExpansionRoom room) {
      // check ripup of an existing via
      if (!this.ctrl.ripupAllowed) {
        return;
      }
      Item currObstacleItem = room.getItem();
      if (!(currObstacleItem instanceof Via)) {
        return;
      }
      Padstack currObstaclePadstack = ((Via) currObstacleItem).getPadstack();
      if (!this.ctrl.viaRule.containsPadstack(currObstaclePadstack)
          || currObstacleItem.clearanceClassNo() != this.ctrl.viaClearanceClass) {
        return;
      }
      viaLowerBound = currObstaclePadstack.fromLayer();
      viaUpperBound = currObstaclePadstack.toLayer();
      roomRipped = true;
    } else {
      int[] netNoArr = new int[1];
      netNoArr[0] = ctrl.netNo;

      roomRipped = false;
      int viaLowerLimit = Math.max(currDrill.firstLayer, ctrl.viaLowerBound);
      int viaUpperLimit = Math.min(currDrill.lastLayer, ctrl.viaUpperBound);
      // Calculate the lower bound of possible vias.
      int currLayer = fromLayer;
      for (; ; ) {
        TileShape currRoomShape = currDrill.roomArr[currLayer - currDrill.firstLayer].getShape();
        ForcedPadAlgo.CheckDrillResult drillResult =
            checkLayerWithAnyMatchingVia(currDrill, currLayer, currRoomShape, netNoArr);
        if (drillResult == ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE) {
          viaLowerBound = currLayer + 1;
          break;
        } else if (drillResult == ForcedPadAlgo.CheckDrillResult.DRILLABLE_WITH_ATTACH_SMD) {
          if (currLayer == 0) {
            smdAttachedOnComponentSide = true;
          } else if (currLayer == ctrl.layerCount - 1) {
            smdAttachedOnSolderSide = true;
          }
        }
        if (currLayer <= viaLowerLimit) {
          viaLowerBound = viaLowerLimit;
          break;
        }
        --currLayer;
      }
      if (viaLowerBound > currDrill.firstLayer) {
        return;
      }
      currLayer = fromLayer + 1;
      for (; ; ) {
        if (currLayer > viaUpperLimit) {
          viaUpperBound = viaUpperLimit;
          break;
        }
        TileShape currRoomShape = currDrill.roomArr[currLayer - currDrill.firstLayer].getShape();
        ForcedPadAlgo.CheckDrillResult drillResult =
            checkLayerWithAnyMatchingVia(currDrill, currLayer, currRoomShape, netNoArr);
        if (drillResult == ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE) {
          viaUpperBound = currLayer - 1;
          break;
        } else if (drillResult == ForcedPadAlgo.CheckDrillResult.DRILLABLE_WITH_ATTACH_SMD) {
          if (currLayer == ctrl.layerCount - 1) {
            smdAttachedOnSolderSide = true;
          }
        }
        ++currLayer;
      }
      if (viaUpperBound < currDrill.lastLayer) {
        return;
      }
    }

    for (int toLayer = viaLowerBound; toLayer <= viaUpperBound; toLayer++) {
      if (toLayer == fromLayer) {
        continue;
      }
      // check, there is a fitting via mask.
      int currFirstLayer;
      int currLastLayer;
      if (toLayer < fromLayer) {
        currFirstLayer = toLayer;
        currLastLayer = fromLayer;
      } else {
        currFirstLayer = fromLayer;
        currLastLayer = toLayer;
      }
      boolean maskFound = false;
      for (int i = 0; i < ctrl.viaInfoArr.length; i++) {
        AutorouteControl.ViaMask currViaInfo = ctrl.viaInfoArr[i];
        if (currFirstLayer >= currViaInfo.fromLayer
            && currLastLayer <= currViaInfo.toLayer
            && currViaInfo.fromLayer >= viaLowerBound
            && currViaInfo.toLayer <= viaUpperBound) {
          boolean maskOk = true;
          if (currViaInfo.fromLayer == 0 && smdAttachedOnComponentSide
              || currViaInfo.toLayer == ctrl.layerCount - 1 && smdAttachedOnSolderSide) {
            maskOk = currViaInfo.attachSmdAllowed;
          }
          if (maskOk) {
            maskFound = true;
            break;
          }
        }
      }
      if (!maskFound) {
        continue;
      }
      MazeSearchElement currDrillLayerInfo =
          currDrill.getMazeSearchElement(toLayer - currDrill.firstLayer);
      if (currDrillLayerInfo.isOccupied) {
        continue;
      }
      double expansionValue =
          p_list_element.expansionValue + ctrl.addViaCosts[fromLayer].toLayer[toLayer];
      FloatPoint shapeEntryMiddle =
          p_list_element.shapeEntry.a.middlePoint(p_list_element.shapeEntry.b);
      double sortingValue =
          expansionValue + this.destinationDistance.calculate(shapeEntryMiddle, toLayer);
      int currRoomIndex = toLayer - currDrill.firstLayer;
      MazeListElement newElement =
          new MazeListElement(
              currDrill,
              currRoomIndex,
              currDrill,
              p_list_element.sectionNoOfDoor,
              expansionValue,
              sortingValue,
              currDrill.roomArr[currRoomIndex],
              p_list_element.shapeEntry,
              roomRipped,
              MazeSearchElement.Adjustment.NONE,
              false);
      this.mazeExpansionList.add(newElement);
    }
  }

  private ForcedPadAlgo.CheckDrillResult checkLayerWithAnyMatchingVia(
      ExpansionDrill p_drill, int p_layer, TileShape p_room_shape, int[] p_net_no_arr) {
    boolean drillableWithAttachSmd = false;
    for (int i = 0; i < this.ctrl.viaRule.viaCount(); i++) {
      ViaInfo viaInfo = this.ctrl.viaRule.getVia(i);
      Padstack viaPadstack = viaInfo.getPadstack();
      if (p_layer < viaPadstack.fromLayer() || p_layer > viaPadstack.toLayer()) {
        continue;
      }
      ConvexShape viaShape = viaPadstack.getShape(p_layer);
      double viaRadius = viaShape == null ? 0 : 0.5 * viaShape.maxWidth();
      double requiredRadius = Math.max(viaRadius, this.ctrl.traceHalfWidth[p_layer]);
      ForcedPadAlgo.CheckDrillResult result =
          ForcedViaAlgo.checkLayer(
              requiredRadius,
              viaInfo.getClearanceClass(),
              viaInfo.attachSmdAllowed(),
              p_room_shape,
              p_drill.location,
              p_layer,
              p_net_no_arr,
              this.ctrl.maxShoveTraceRecursionDepth,
              0,
              this.autorouteEngine.board,
              this.ctrl.traceHalfWidth[p_layer],
              this.ctrl.traceClearanceClassNo);
      if (result == ForcedPadAlgo.CheckDrillResult.DRILLABLE) {
        return result;
      }
      if (result == ForcedPadAlgo.CheckDrillResult.DRILLABLE_WITH_ATTACH_SMD) {
        drillableWithAttachSmd = true;
      }
    }
    return drillableWithAttachSmd
        ? ForcedPadAlgo.CheckDrillResult.DRILLABLE_WITH_ATTACH_SMD
        : ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE;
  }

  /** Initializes the maze search algorithm. Returns false if the initialisation failed. */
  private boolean init(Set<Item> p_start_items, Set<Item> p_destination_items) {
    reduceTraceShapesAtTiePins(p_start_items, this.ctrl.netNo, this.searchTree);
    reduceTraceShapesAtTiePins(p_destination_items, this.ctrl.netNo, this.searchTree);
    // process the destination items
    boolean destinationOk = false;
    for (Item currItem : p_destination_items) {
      if (this.autorouteEngine.isStopRequested()) {
        return false;
      }
      ItemAutorouteInfo currInfo = currItem.getAutorouteInfo();
      currInfo.setStartInfo(false);
      for (int i = 0; i < currItem.treeShapeCount(this.searchTree); i++) {
        TileShape currTreeShape = currItem.getTreeShape(this.searchTree, i);
        if (currTreeShape != null) {
          destinationDistance.join(currTreeShape.boundingBox(), currItem.shapeLayer(i));
        }
      }
      destinationOk = true;
    }
    if (!destinationOk && this.ctrl.isFanout) {
      // destination set is not needed for fanout
      IntBox boardBoundingBox = this.autorouteEngine.board.boundingBox;
      destinationDistance.join(boardBoundingBox, 0);
      destinationDistance.join(boardBoundingBox, this.ctrl.layerCount - 1);
      destinationOk = true;
    }

    if (!destinationOk) {
      FRLogger.debug(
          "MazeSearchAlgo.init: Failed - no valid destination items found"
              + " (dest set size: "
              + p_destination_items.size()
              + ", isFanout: "
              + this.ctrl.isFanout
              + ")");
      return false;
    }
    // process the start items
    Collection<IncompleteFreeSpaceExpansionRoom> startRooms = new LinkedList<>();
    for (Item currItem : p_start_items) {
      if (this.autorouteEngine.isStopRequested()) {
        return false;
      }
      ItemAutorouteInfo currInfo = currItem.getAutorouteInfo();
      currInfo.setStartInfo(true);
      if (currItem instanceof Connectable connectable) {
        for (int i = 0; i < currItem.treeShapeCount(searchTree); i++) {
          TileShape containedShape = connectable.getTraceConnectionShape(searchTree, i);
          IncompleteFreeSpaceExpansionRoom newStartRoom =
              autorouteEngine.addIncompleteExpansionRoom(
                  null, currItem.shapeLayer(i), containedShape);
          startRooms.add(newStartRoom);
        }
      }
    }

    // complete the start rooms
    Collection<CompleteFreeSpaceExpansionRoom> completedStartRooms = new LinkedList<>();

    if (this.autorouteEngine.maintainDatabase) {
      // add the completed start rooms carried over from the last autoroute to the
      // start rooms.
      completedStartRooms.addAll(this.autorouteEngine.getRoomsWithTargetItems(p_start_items));
    }

    for (IncompleteFreeSpaceExpansionRoom currRoom : startRooms) {
      if (this.autorouteEngine.isStopRequested()) {
        return false;
      }
      Collection<CompleteFreeSpaceExpansionRoom> currCompletedRooms =
          autorouteEngine.completeExpansionRoom(currRoom);
      completedStartRooms.addAll(currCompletedRooms);
    }

    // Put the ItemExpansionDoors of the completed start rooms into
    // the mazeExpansionList.
    boolean startOk = false;
    int expansionDoorsFound = 0;
    int expansionDoorsDestination = 0;
    for (CompleteFreeSpaceExpansionRoom currRoom : completedStartRooms) {
      for (TargetItemExpansionDoor currDoor : currRoom.getTargetDoors()) {
        expansionDoorsFound++;
        if (this.autorouteEngine.isStopRequested()) {
          return false;
        }
        if (currDoor.isDestinationDoor()) {
          expansionDoorsDestination++;
          continue;
        }
        TileShape connectionShape =
            ((Connectable) currDoor.item)
                .getTraceConnectionShape(searchTree, currDoor.treeEntryNo);
        connectionShape = connectionShape.intersection(currDoor.room.getShape());
        FloatPoint currCenter = connectionShape.centreOfGravity();
        FloatLine shapeEntry = new FloatLine(currCenter, currCenter);
        double sortingValue = this.destinationDistance.calculate(currCenter, currRoom.getLayer());
        MazeListElement newListElement =
            new MazeListElement(
                currDoor,
                0,
                null,
                0,
                0,
                sortingValue,
                currRoom,
                shapeEntry,
                false,
                MazeSearchElement.Adjustment.NONE,
                false);
        mazeExpansionList.add(newListElement);
        startOk = true;
      }
    }
    if (!startOk) {
      FRLogger.debug(
          "MazeSearchAlgo.init: Failed - no accessible expansion doors found"
              + " (start items: "
              + p_start_items.size()
              + ", start rooms: "
              + startRooms.size()
              + ", completed start rooms: "
              + completedStartRooms.size()
              + ", expansion doors found: "
              + expansionDoorsFound
              + ", destination doors: "
              + expansionDoorsDestination
              + ", ripupAllowed: "
              + this.ctrl.ripupAllowed
              + ", ripupCosts: "
              + this.ctrl.ripupCosts
              + ")");
    }
    return startOk;
  }

  private boolean roomShapeIsThick(ObstacleExpansionRoom p_obstacle_room) {
    Item obstacleItem = p_obstacle_room.getItem();
    int layer = p_obstacle_room.getLayer();
    double obstacleHalfWidth;
    if (obstacleItem instanceof Trace trace) {
      obstacleHalfWidth =
          trace.getHalfWidth()
              + this.searchTree.clearanceCompensationValue(
                  obstacleItem.clearanceClassNo(), layer);

    } else if (obstacleItem instanceof Via via) {
      TileShape viaShape = via.getTreeShapeOnLayer(this.searchTree, layer);
      obstacleHalfWidth = 0.5 * viaShape.maxWidth();
    } else {
      FRLogger.warn("MazeSearchAlgo. room_shape_is_thick: unexpected obstacle item");
      obstacleHalfWidth = 0;
    }
    return obstacleHalfWidth >= this.ctrl.compensatedTraceHalfWidth[layer];
  }

  /**
   * Checks, if the room can be ripped and returns the rip up costs, which are > 0, if the room is
   * ripped and -1, if no ripup was possible. If the previous room was also ripped and contained the
   * same item or an item of the same connection, the result will be equal to ALREADY_RIPPED_COSTS
   */
  private int checkRipup(
      MazeListElement p_list_element, Item p_obstacle_item, boolean p_door_is_small) {
    if (!p_obstacle_item.isRoutable()) {
      return -1;
    }
    if (p_door_is_small) {
      // allow entering a via or trace, if its corresponding border segment is smaller
      // than the
      // current trace width

      if (!enterThroughSmallDoor(p_list_element, p_obstacle_item)) {
        return -1;
      }
    }
    CompleteExpansionRoom previousRoom = p_list_element.door.otherRoom(p_list_element.nextRoom);
    boolean roomWasShoved = p_list_element.adjustment != MazeSearchElement.Adjustment.NONE;
    Item previousItem = null;
    if (previousRoom instanceof ObstacleExpansionRoom room) {
      previousItem = room.getItem();
    }
    if (roomWasShoved) {
      if (previousItem != null
          && previousItem != p_obstacle_item
          && previousItem.sharesNet(p_obstacle_item)) {
        // The ripped trace may start at a fork.
        return -1;
      }
    } else if (previousItem == p_obstacle_item) {
      return ALREADY_RIPPED_COSTS;
    }

    double fanoutViaCostFactor = 1.0;
    double costFactor = 1;
    boolean preserveFanoutProtection =
        !this.ctrl.removeUnconnectedVias
            && this.ctrl.ripupCosts <= (this.ctrl.settings.getStartRipupCosts() * 2);
    if (p_obstacle_item instanceof Trace obstacle_trace) {
      costFactor = obstacle_trace.getHalfWidth();
      if (preserveFanoutProtection) {
        // protect traces between SMD-pins and fanout vias
        fanoutViaCostFactor = calcFanoutViaRipupCostFactor(obstacle_trace);
      }
    } else if (p_obstacle_item instanceof Via) {
      boolean lookIfFanoutVia = preserveFanoutProtection;
      Collection<Item> contactList = p_obstacle_item.getNormalContacts();
      int contactCount = 0;
      for (Item currContact : contactList) {
        if (!(currContact instanceof Trace obstacle_trace) || currContact.isUserFixed()) {
          return -1;
        }
        ++contactCount;
        costFactor = Math.max(costFactor, obstacle_trace.getHalfWidth());
        if (lookIfFanoutVia && !this.ctrl.isFanout) {
          double currFanoutViaCostFactor = calcFanoutViaRipupCostFactor(obstacle_trace);
          if (currFanoutViaCostFactor > 1) {
            fanoutViaCostFactor = currFanoutViaCostFactor;
            lookIfFanoutVia = false;
          }
        }
      }
      if (fanoutViaCostFactor <= 1) {
        // not a fanout via
        costFactor *= 0.5 * Math.max(contactCount - 1, 0);
      }
    }

    double ripupCost = this.ctrl.ripupCosts * costFactor;
    double detour = 1;
    double traceLength = 0;
    double minTraceLength = 0;
    int itemCount = 0;
    String connectionItemIds = "[]";
    if (fanoutViaCostFactor <= 1
        && !this.ctrl
            .isFanout) // p_obstacle_item does not belong to a fanout, and not during fanout pass
    {
      Connection obstacleConnection = Connection.get(p_obstacle_item);
      if (obstacleConnection != null) {
        detour = obstacleConnection.getDetour();
        traceLength = obstacleConnection.traceLength();
        itemCount = obstacleConnection.itemList.size();
        if (obstacleConnection.startPoint != null && obstacleConnection.endPoint != null) {
          minTraceLength =
              obstacleConnection
                  .startPoint
                  .toFloat()
                  .distance(obstacleConnection.endPoint.toFloat());
        }
        StringBuilder sb = new StringBuilder("[");
        for (app.freerouting.board.Item ci : obstacleConnection.itemList) {
          if (sb.length() > 1) {
            sb.append(",");
          }
          sb.append(ci.getIdNo());
        }
        sb.append("]");
        connectionItemIds = sb.toString();
      }
    }
    boolean randomize = this.ctrl.ripupPassNo >= 4 && this.ctrl.ripupPassNo % 3 != 0;
    if (randomize) {
      // shuffle the result to avoid repetitive loops
      double randomNumber = this.randomGenerator.nextDouble();
      double randomFactor = 0.5 + randomNumber * randomNumber;
      detour *= randomFactor;
    }
    ripupCost /= detour;

    ripupCost *= fanoutViaCostFactor;
    int result = Math.max((int) ripupCost, 1);
    final int MAX_RIPUP_COSTS = Integer.MAX_VALUE / 100;
    result = Math.min(result, MAX_RIPUP_COSTS);
    String obstacleNets = "[]";
    if (p_obstacle_item instanceof app.freerouting.board.Item obstacleItem) {
      int[] nets = new int[obstacleItem.netCount()];
      for (int i = 0; i < nets.length; i++) {
        nets[i] = obstacleItem.getNetNo(i);
      }
      obstacleNets = java.util.Arrays.toString(nets);
    }
    FRLogger.trace(
        "CHECK_RIPUP net="
            + ctrl.netNo
            + ", obstacle_id="
            + (p_obstacle_item instanceof app.freerouting.board.Item obstItem
                ? obstItem.getIdNo()
                : -1)
            + ", obstacle_nets="
            + obstacleNets
            + ", connectionItems="
            + connectionItemIds
            + ", halfWidth="
            + costFactor
            + ", ripupCosts="
            + this.ctrl.ripupCosts
            + ", traceLength="
            + traceLength
            + ", minTraceLength="
            + minTraceLength
            + ", itemCount="
            + itemCount
            + ", detour="
            + detour
            + ", result="
            + result);
    return result;
  }

  /**
   * Shoves a trace room and expands the corresponding doors. Return false, if no door was expanded.
   * In this case occupation of the door_section by ripup can be delayed to allow shoving the room
   * from a different door section
   */
  private boolean shoveTraceRoom(
      MazeListElement p_list_element, ObstacleExpansionRoom p_obstacle_room) {
    if (p_list_element.sectionNoOfDoor != 0
        && p_list_element.sectionNoOfDoor != p_list_element.door.mazeSearchElementCount() - 1) {
      // No delay of occupation necessary because inner sections of a door are
      // currently not
      // shoved.
      return true;
    }
    boolean result = false;
    if (p_list_element.adjustment != MazeSearchElement.Adjustment.RIGHT) {
      Collection<MazeShoveTraceAlgo.DoorSection> leftToDoorSectionList = new LinkedList<>();

      if (MazeShoveTraceAlgo.checkShoveTraceLine(
          p_list_element,
          p_obstacle_room,
          this.autorouteEngine.board,
          this.ctrl,
          false,
          leftToDoorSectionList)) {
        result = true;
      }

      for (MazeShoveTraceAlgo.DoorSection curr_left_door_section : leftToDoorSectionList) {
        MazeSearchElement.Adjustment currAdjustment;
        if (curr_left_door_section.door.dimension == 2) {
          // the door is the link door to the next room
          currAdjustment = MazeSearchElement.Adjustment.LEFT;
        } else {
          currAdjustment = MazeSearchElement.Adjustment.NONE;
        }

        expandToDoorSection(
            curr_left_door_section.door,
            curr_left_door_section.sectionNo,
            curr_left_door_section.sectionLine,
            p_list_element,
            0,
            currAdjustment);
      }
    }

    if (p_list_element.adjustment != MazeSearchElement.Adjustment.LEFT) {
      Collection<MazeShoveTraceAlgo.DoorSection> rightToDoorSectionList = new LinkedList<>();

      if (MazeShoveTraceAlgo.checkShoveTraceLine(
          p_list_element,
          p_obstacle_room,
          this.autorouteEngine.board,
          this.ctrl,
          true,
          rightToDoorSectionList)) {
        result = true;
      }
      for (MazeShoveTraceAlgo.DoorSection curr_right_door_section : rightToDoorSectionList) {
        MazeSearchElement.Adjustment currAdjustment;
        if (curr_right_door_section.door.dimension == 2) {
          // the door is the link door to the next room
          currAdjustment = MazeSearchElement.Adjustment.RIGHT;
        } else {
          currAdjustment = MazeSearchElement.Adjustment.NONE;
        }
        expandToDoorSection(
            curr_right_door_section.door,
            curr_right_door_section.sectionNo,
            curr_right_door_section.sectionLine,
            p_list_element,
            0,
            currAdjustment);
      }
    }
    return result;
  }

  /**
   * Checks, if the next room contains a destination pin, where evtl. neckdown is necessary. Return
   * the neck down width in this case, or 0, if no such pin was found,
   */
  private double checkNeckDownAtDestPin(CompleteExpansionRoom p_room) {
    Collection<TargetItemExpansionDoor> targetDoors = p_room.getTargetDoors();
    for (TargetItemExpansionDoor curr_target_door : targetDoors) {
      if (curr_target_door.item instanceof Pin pin) {
        return pin.getTraceNeckdownHalfwidth(p_room.getLayer());
      }
    }
    return 0;
  }

  /**
   * Checks, if the next room can be entered if the door of p_list_element is small. If
   * p_ignore_item != null, p_ignore_item and all other items directly connected to p_ignore_item
   * are ignored in the check.
   */
  private boolean enterThroughSmallDoor(MazeListElement p_list_element, Item p_ignore_item) {
    if (p_list_element.door.getDimension() != 1) {
      return false;
    }
    TileShape doorShape = p_list_element.door.getShape();

    // Get the line of the 1 dimensional door.
    Line doorLine = null;
    FloatPoint prevCorner = doorShape.cornerApprox(0);
    int cornerCount = doorShape.borderLineCount();
    for (int i = 1; i < cornerCount; i++) {
      // skip lines of length 0
      FloatPoint nextCorner = doorShape.cornerApprox(i);
      if (nextCorner.distanceSquare(prevCorner) > 1) {
        doorLine = doorShape.borderLine(i - 1);
        break;
      }
      prevCorner = nextCorner;
    }
    if (doorLine == null) {
      return false;
    }

    IntPoint doorCenter = doorShape.centreOfGravity().round();
    int currLayer = p_list_element.nextRoom.getLayer();
    int checkRadius =
        this.ctrl.compensatedTraceHalfWidth[currLayer] + AutorouteEngine.TRACE_WIDTH_TOLERANCE;
    // create a perpendicular line segment of length 2 * checkRadius through the
    // door center
    Line[] lineArr = new Line[3];
    lineArr[0] = doorLine.translate(checkRadius);
    lineArr[1] = new Line(doorCenter, doorLine.direction().turn45Degree(2));
    lineArr[2] = doorLine.translate(-checkRadius);

    Polyline checkPolyline = new Polyline(lineArr);
    TileShape checkShape = checkPolyline.offsetShape(checkRadius, 0);
    int[] ignoreNetNos = new int[1];
    ignoreNetNos[0] = this.ctrl.netNo;
    Set<SearchTreeObject> overlappingObjects = new TreeSet<>();
    this.autorouteEngine.autorouteSearchTree.overlappingObjects(
        checkShape, currLayer, ignoreNetNos, overlappingObjects);

    for (SearchTreeObject currObject : overlappingObjects) {
      if (!(currObject instanceof Item currItem) || currObject == p_ignore_item) {
        continue;
      }
      if (!currItem.sharesNet(p_ignore_item)) {
        return false;
      }
      Set<Item> currContacts = currItem.getNormalContacts();
      if (!currContacts.contains(p_ignore_item)) {
        return false;
      }
    }
    return true;
  }

  /** Checks entering a thick room from a via or trace through a small door (after ripup) */
  private boolean checkLeavingRippedItem(MazeListElement p_list_element) {
    if (!(p_list_element.door instanceof ExpansionDoor currDoor)) {
      return false;
    }
    CompleteExpansionRoom fromRoom = currDoor.otherRoom(p_list_element.nextRoom);
    if (!(fromRoom instanceof ObstacleExpansionRoom)) {
      return false;
    }
    Item currItem = ((ObstacleExpansionRoom) fromRoom).getItem();
    if (!currItem.isRoutable()) {
      return false;
    }
    return enterThroughSmallDoor(p_list_element, currItem);
  }

  /** The result type of MazeSearchAlgo.find_connection */
  public static class Result {

    public final ExpandableObject destinationDoor;
    public final int sectionNoOfDoor;

    Result(ExpandableObject p_destination_door, int p_section_no_of_door) {
      destinationDoor = p_destination_door;
      sectionNoOfDoor = p_section_no_of_door;
    }
  }

  /**
   * Used for the result of MazeShoveViaAlgo.check_shove_via and
   * MazeShoveThinRoomAlgo.check_shove_thin_room.
   */
  static class ShoveResult {

    /** The opposite door to be expanded */
    final ExpansionDoor oppositeDoor;

    /** The doors at the adjusted edge of the room shape to be expanded. */
    final Collection<ExpansionDoor> sideDoors;

    /** The passing point of a trace through the from_door after adjustment. */
    final FloatPoint fromDoorPassingPoint;

    /** The passing point of a trace through the opposite door after adjustment. */
    final FloatPoint oppositeDoorPassingPoint;

    ShoveResult(
        ExpansionDoor p_opposite_door,
        Collection<ExpansionDoor> p_side_doors,
        FloatPoint p_from_door_passing_point,
        FloatPoint p_opposite_door_passing_point) {
      oppositeDoor = p_opposite_door;
      sideDoors = p_side_doors;
      fromDoorPassingPoint = p_from_door_passing_point;
      oppositeDoorPassingPoint = p_opposite_door_passing_point;
    }
  }
}
