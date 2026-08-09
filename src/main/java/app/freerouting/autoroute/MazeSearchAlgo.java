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

  /** Creates a new instance of MazeSearchAlgo. */
  MazeSearchAlgo(AutorouteEngine autorouteEngine, AutorouteControl ctrl) {
    this.autorouteEngine = autorouteEngine;
    this.ctrl = ctrl;
    randomGenerator.setSeed(
        ctrl.ripupCosts); // Keep v1.9 deterministic randomization across passes.
    this.searchTree = autorouteEngine.autorouteSearchTree;
    mazeExpansionList =
        new TreeSet<>() {
          @Override
          public boolean add(MazeListElement element) {
            if (ctrl.isFanout && ctrl.fanoutStartPinCenter != null) {
              app.freerouting.geometry.planar.FloatPoint pinCenterFloat =
                  ctrl.fanoutStartPinCenter.toFloat();
              boolean onStartLayer =
                  element.nextRoom != null
                      && element.nextRoom.getLayer() == ctrl.fanoutStartPinLayer;
              if (onStartLayer) {
                double maxLen =
                    ctrl.settings.fanout != null && ctrl.settings.fanout.maxEscapeLengthMm != null
                        ? ctrl.settings.fanout.maxEscapeLengthMm * 1000.0
                        : 3000.0;
                double resolution =
                    autorouteEngine.board.communication.getResolution(
                        app.freerouting.board.Unit.UM);
                app.freerouting.geometry.planar.FloatPoint entryPoint =
                    element.shapeEntry.a.middlePoint(element.shapeEntry.b);
                double dist = entryPoint.distance(pinCenterFloat);
                if (dist > maxLen * resolution) {
                  return false;
                }
              }
              if (element.door instanceof ExpansionDrill drill) {
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
            return super.add(element);
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
      Set<Item> pStartItems,
      Set<Item> pDestinationItems,
      AutorouteEngine pAutorouteDatabase,
      AutorouteControl pCtrl) {
    MazeSearchAlgo newInstance = new MazeSearchAlgo(pAutorouteDatabase, pCtrl);
    MazeSearchAlgo result;
    if (newInstance.init(pStartItems, pDestinationItems)) {
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
      Collection<Item> pItemList, int pOwnNetNo, ShapeSearchTree pAutorouteTree) {
    for (Item currItem : pItemList) {
      if ((currItem instanceof Pin curr_tie_pin) && currItem.netCount() > 1) {
        Collection<Item> pinContacts = currItem.getNormalContacts();
        for (Item currContact : pinContacts) {
          if (!(currContact instanceof PolylineTrace) || currContact.containsNet(pOwnNetNo)) {
            continue;
          }
          pAutorouteTree.reduceTraceShapeAtTiePin(curr_tie_pin, (PolylineTrace) currContact);
        }
      }
    }
  }

  /**
   * Return the additional cost factor for ripping the trace, if it is connected to a fanout via or
   * 1, if no fanout via was found.
   */
  private static double calcFanoutViaRipupCostFactor(Trace pTrace) {
    final double fanoutCostConst = 20000;
    Collection<Item> currEndContacts;
    for (int i = 0; i < 2; i++) {
      if (i == 0) {
        currEndContacts = pTrace.getStartContacts();
      } else {
        currEndContacts = pTrace.getEndContacts();
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
        double fanoutViaCostFactor = pTrace.getHalfWidth() / pTrace.getLength();
        fanoutViaCostFactor *= fanoutViaCostFactor;
        fanoutViaCostFactor *= fanoutCostConst;
        return Math.max(fanoutViaCostFactor, 1);
      }
    }
    return 1;
  }

  /**
   * Returns the perpendicular projection of p_from_segment onto p_to_segment. Returns null, if the
   * projection is empty.
   */
  private static FloatLine segmentProjection(FloatLine pFromSegment, FloatLine pToSegment) {
    FloatLine checkSegment = pFromSegment.adjustDirection(pToSegment);
    FloatLine firstProjection = pToSegment.segmentProjection(checkSegment);
    FloatLine secondProjection = pToSegment.segmentProjection2(checkSegment);
    FloatLine result;
    if (firstProjection != null && secondProjection != null) {
      FloatPoint resultA;
      if (firstProjection.a == pToSegment.a || secondProjection.a == pToSegment.a) {
        resultA = pToSegment.a;
      } else if (firstProjection.a.distanceSquare(pToSegment.a)
          <= secondProjection.a.distanceSquare(pToSegment.a)) {
        resultA = firstProjection.a;
      } else {
        resultA = secondProjection.a;
      }
      FloatPoint resultB;
      if (firstProjection.b == pToSegment.b || secondProjection.b == pToSegment.b) {
        resultB = pToSegment.b;
      } else if (firstProjection.b.distanceSquare(pToSegment.b)
          <= secondProjection.b.distanceSquare(pToSegment.b)) {
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
  private boolean expandToRoomDoors(MazeListElement pListElement) {

    // Complete the neighbour rooms to make sure, that the
    // doors of this room will not change later on.
    int layerNo = pListElement.nextRoom.getLayer();

    boolean layerActive = ctrl.layerActive[layerNo];
    if (!layerActive) {
      if (autorouteEngine.board.layerStructure.arr[layerNo].isSignal) {
        return true;
      }
    }

    double halfWidth = ctrl.compensatedTraceHalfWidth[layerNo];
    boolean currDoorIsSmall = false;
    if (pListElement.door instanceof ExpansionDoor currDoor) {
      double halfWidthAdd = halfWidth + AutorouteEngine.TRACE_WIDTH_TOLERANCE;
      if (this.ctrl.withNeckdown) {
        // try evtl. neckdown at a destination pin
        double neckDownHalfWidth = checkNeckDownAtDestPin(pListElement.nextRoom);
        if (neckDownHalfWidth > 0) {
          halfWidthAdd = Math.min(halfWidthAdd, neckDownHalfWidth);
          halfWidth = halfWidthAdd;
        }
      }
      currDoorIsSmall = doorIsSmall(currDoor, 2 * halfWidthAdd);
    }

    int doorCountBeforeCompletion = pListElement.nextRoom.getDoors().size();
    this.autorouteEngine.completeNeighbourRooms(pListElement.nextRoom);
    int doorCountAfterCompletion = pListElement.nextRoom.getDoors().size();
    FRLogger.trace(
        "ROOM_COMPLETE_SYNC"
            + ", net="
            + ctrl.netNo
            + ", layer="
            + layerNo
            + ", from_section="
            + pListElement.sectionNoOfDoor
            + ", backtrack_section="
            + pListElement.sectionNoOfBacktrackDoor
            + ", from_door="
            + describeExpandable(pListElement.door)
            + ", nextRoom="
            + describeRoom(pListElement.nextRoom)
            + ", door_count_before="
            + doorCountBeforeCompletion
            + ", door_count_after="
            + doorCountAfterCompletion);

    FloatPoint shapeEntryMiddle = pListElement.shapeEntry.a.middlePoint(pListElement.shapeEntry.b);

    if (this.ctrl.withNeckdown && pListElement.door instanceof TargetItemExpansionDoor door) {
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
    if (pListElement.nextRoom instanceof ObstacleExpansionRoom room) {
      nextRoomIsThick = roomShapeIsThick(room);
    } else {
      TileShape nextRoomShape = pListElement.nextRoom.getShape();
      if (nextRoomShape.minWidth() < 2 * halfWidth) {
        nextRoomIsThick = false; // to prevent problems with the opposite side
      } else if (!pListElement.alreadyChecked
          && pListElement.door.getDimension() == 1
          && !currDoorIsSmall) {
        // The algorithm below works only, if p_location is on the border of p_room_shape.
        // That is only the case for 1 dimensional doors.
        // For small doors the check is done in check_leaving_via below.

        FloatPoint[] nearestPoints = nextRoomShape.nearestBorderPointsApprox(shapeEntryMiddle, 2);
        if (nearestPoints.length < 2) {
          FRLogger.warn("MazeSearchAlgo.expand_to_room_doors: nearestPoints.length == 2 expected");
          nextRoomIsThick = false;
        } else {
          double currDist = nearestPoints[1].distance(shapeEntryMiddle);
          nextRoomIsThick = currDist > halfWidth + 1;
        }
      }
    }
    if (!layerActive && pListElement.door instanceof ExpansionDrill drill) {
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
        expandToTargetDoors(pListElement, nextRoomIsThick, currDoorIsSmall, shapeEntryMiddle);

    if (!layerActive) {
      return true;
    }

    int ripupCosts = 0;

    if (pListElement.nextRoom instanceof FreeSpaceExpansionRoom) {
      if (!pListElement.alreadyChecked) {
        if (currDoorIsSmall) {
          boolean enterThroughSmallDoor = false;
          if (nextRoomIsThick) {
            // check to enter the thick room from a ripped item through a small door (after
            // ripup)
            enterThroughSmallDoor = checkLeavingRippedItem(pListElement);
          }
          if (!enterThroughSmallDoor) {
            return somethingExpanded;
          }
        }
      }
    } else if (pListElement.nextRoom instanceof ObstacleExpansionRoom obstacle_room) {

      if (!pListElement.alreadyChecked) {
        boolean roomRippable = false;
        if (this.ctrl.ripupAllowed) {
          ripupCosts = checkRipup(pListElement, obstacle_room.getItem(), currDoorIsSmall);
          roomRippable = ripupCosts >= 0;
        }

        if (ripupCosts != ALREADY_RIPPED_COSTS && nextRoomIsThick) {
          Item obstacleItem = obstacle_room.getItem();
          if (!currDoorIsSmall
              && this.ctrl.maxShoveTraceRecursionDepth > 0
              && obstacleItem instanceof PolylineTrace) {
            boolean shoved = shoveTraceRoom(pListElement, obstacle_room);
            if (!shoved) {
              if (ripupCosts > 0) {
                // delay the occupation by ripup to allow shoving the room by another door
                // sections.
                MazeListElement newElement =
                    new MazeListElement(
                        pListElement.door,
                        pListElement.sectionNoOfDoor,
                        pListElement.backtrackDoor,
                        pListElement.sectionNoOfBacktrackDoor,
                        pListElement.expansionValue + ripupCosts,
                        pListElement.sortingValue + ripupCosts,
                        pListElement.nextRoom,
                        pListElement.shapeEntry,
                        true,
                        pListElement.adjustment,
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

    List<ExpansionDoor> roomDoorsSnapshot = new LinkedList<>(pListElement.nextRoom.getDoors());
    FRLogger.trace(
        "ROOM_DOOR context from_section="
            + pListElement.sectionNoOfDoor
            + ", backtrack_section="
            + pListElement.sectionNoOfBacktrackDoor
            + ", from_door="
            + describeExpandable(pListElement.door)
            + ", nextRoom="
            + describeRoom(pListElement.nextRoom)
            + ", net="
            + ctrl.netNo);
    for (int door_index = 0; door_index < roomDoorsSnapshot.size(); door_index++) {
      ExpansionDoor candidateDoor = roomDoorsSnapshot.get(door_index);
      FRLogger.trace(
          "ROOM_DOOR candidate index="
              + door_index
              + ", from_section="
              + pListElement.sectionNoOfDoor
              + ", backtrack_section="
              + pListElement.sectionNoOfBacktrackDoor
              + ", from_door="
              + describeExpandable(pListElement.door)
              + ", nextRoom="
              + describeRoom(pListElement.nextRoom)
              + ", candidate="
              + describeExpandable(candidateDoor)
              + ", net="
              + ctrl.netNo);
    }

    for (ExpansionDoor toDoor : roomDoorsSnapshot) {
      if (toDoor == pListElement.door) {
        continue;
      }
      if (expandToDoor(
          toDoor, pListElement, ripupCosts, nextRoomIsThick, MazeSearchElement.Adjustment.NONE)) {
        somethingExpanded = true;
      }
    }

    // Expand also the drill pages intersecting the room.
    if (ctrl.viasAllowed && !(pListElement.door instanceof ExpansionDrill)) {
      if ((somethingExpanded || nextRoomIsThick)
          && pListElement.nextRoom instanceof CompleteFreeSpaceExpansionRoom) {
        // avoid setting somethingExpanded to true when nextRoom is thin to allow
        // occupying by
        // different sections of the door
        Collection<DrillPage> overlappingDrillPages =
            this.autorouteEngine.drillPageArray.overlappingPages(pListElement.nextRoom.getShape());
        {
          for (DrillPage toDrillPage : overlappingDrillPages) {
            expandToDrillPage(toDrillPage, pListElement);
            somethingExpanded = true;
          }
        }
      } else if (pListElement.nextRoom instanceof ObstacleExpansionRoom room) {
        Item currObstacleItem = room.getItem();
        if (currObstacleItem instanceof Via currVia) {
          ExpansionDrill viaDrillInfo =
              currVia.getAutorouteDrillInfo(this.autorouteEngine.autorouteSearchTree);
          expandToDrill(viaDrillInfo, pListElement, ripupCosts);
        }
      }
    }

    return somethingExpanded;
  }

  /** Expand the target doors of the room. Returns true, if at least 1 target door was expanded */
  private boolean expandToTargetDoors(
      MazeListElement pListElement,
      boolean pNextRoomIsThick,
      boolean pCurrDoorIsSmall,
      FloatPoint pShapeEntryMiddle) {
    if (pCurrDoorIsSmall) {
      boolean enterThroughSmallDoor = false;
      if (pListElement.door instanceof ExpansionDoor) {
        CompleteExpansionRoom fromRoom = pListElement.door.otherRoom(pListElement.nextRoom);
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
    for (TargetItemExpansionDoor toDoor : pListElement.nextRoom.getTargetDoors()) {
      if (toDoor == pListElement.door) {
        continue;
      }
      // Validate index before calling - prevents warning when indices become stale
      // during routing
      int treeShapeCount = toDoor.item.treeShapeCount(this.autorouteEngine.autorouteSearchTree);
      if (toDoor.treeEntryNo < 0 || toDoor.treeEntryNo >= treeShapeCount) {
        // Index out of range (trace was modified during routing)
        continue;
      }
      TileShape targetShape =
          ((Connectable) toDoor.item)
              .getTraceConnectionShape(
                  this.autorouteEngine.autorouteSearchTree, toDoor.treeEntryNo);
      if (targetShape == null) {
        // Item's tree shape index out of range (can happen when traces are modified
        // during routing)
        continue;
      }
      FloatPoint connectionPoint = targetShape.nearestPointApprox(pShapeEntryMiddle);
      if (!pNextRoomIsThick) {
        // check the line from p_shape_entry_middle to the nearest point.
        int[] currNetNoArr = new int[1];
        currNetNoArr[0] = this.ctrl.netNo;
        int currLayer = pListElement.nextRoom.getLayer();
        IntPoint[] checkPoints = new IntPoint[2];
        checkPoints[0] = pShapeEntryMiddle.round();
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
          toDoor, 0, newShapeEntry, pListElement, 0, MazeSearchElement.Adjustment.NONE)) {
        result = true;
      }
    }
    return result;
  }

  /** Return true, if at least 1 door ection was expanded. */
  private boolean expandToDoor(
      ExpansionDoor pToDoor,
      MazeListElement pListElement,
      int pAddCosts,
      boolean pNextRoomIsThick,
      MazeSearchElement.Adjustment pAdjustment) {
    double halfWidth = ctrl.compensatedTraceHalfWidth[pListElement.nextRoom.getLayer()];
    boolean somethingExpanded = false;
    FloatLine[] lineSections = pToDoor.getSectionSegments(halfWidth);

    for (int i = 0; i < lineSections.length; i++) {
      if (pToDoor.sectionArr[i].isOccupied) {
        continue;
      }
      FloatLine newShapeEntry;
      if (pNextRoomIsThick) {
        newShapeEntry = lineSections[i];
        if (pToDoor.dimension == 1
            && lineSections.length == 1
            && pToDoor.firstRoom instanceof CompleteFreeSpaceExpansionRoom
            && pToDoor.secondRoom instanceof CompleteFreeSpaceExpansionRoom) {
          // check entering the p_to_door at an acute corner of the shape of
          // p_list_element.nextRoom
          FloatPoint shapeEntryMiddle = newShapeEntry.a.middlePoint(newShapeEntry.b);
          TileShape roomShape = pListElement.nextRoom.getShape();
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
        if (pToDoor.dimension == 1
            && i == 0
            && lineSections[0].b.distanceSquare(lineSections[0].a) < 1) {
          // p_to_door is small belonging to a via or thin room
          continue;
        }
        newShapeEntry = segmentProjection(pListElement.shapeEntry, lineSections[i]);
        if (newShapeEntry == null) {
          continue;
        }
      }

      if (expandToDoorSection(pToDoor, i, newShapeEntry, pListElement, pAddCosts, pAdjustment)) {
        somethingExpanded = true;
      }
    }
    return somethingExpanded;
  }

  /** Checks, if the width p_door is big enough for a trace with width p_trace_width. */
  private boolean doorIsSmall(ExpansionDoor pDoor, double pTraceWidth) {
    if (pDoor.dimension == 1
        || pDoor.firstRoom instanceof CompleteFreeSpaceExpansionRoom
            && pDoor.secondRoom instanceof CompleteFreeSpaceExpansionRoom) {
      TileShape doorShape = pDoor.getShape();
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
      return doorLength < pTraceWidth;
    }
    return false;
  }

  /** Return true, if the door section was successfully expanded. */
  private boolean expandToDoorSection(
      ExpandableObject pDoor,
      int pSectionNo,
      FloatLine pShapeEntry,
      MazeListElement pFromElement,
      int pAddCosts,
      MazeSearchElement.Adjustment pAdjustment) {
    boolean doorSectionOccupied = pDoor.getMazeSearchElement(pSectionNo).isOccupied;
    if (doorSectionOccupied || pShapeEntry == null) {
      FRLogger.trace(
          "RAW_SECTION skip selected_section="
              + pSectionNo
              + ", from_section="
              + pFromElement.sectionNoOfDoor
              + ", backtrack_section="
              + pFromElement.sectionNoOfBacktrackDoor
              + ", occupied="
              + doorSectionOccupied
              + ", shape_entry_null="
              + (pShapeEntry == null)
              + ", adjustment="
              + pAdjustment
              + ", door="
              + describeExpandable(pDoor)
              + ", door_bounds="
              + describeExpandableBounds(pDoor)
              + ", from_door="
              + describeExpandable(pFromElement.door)
              + ", from_door_bounds="
              + describeExpandableBounds(pFromElement.door)
              + ", net="
              + ctrl.netNo);
      FRLogger.trace(
          "MazeSearchAlgo.expand_to_door_section",
          "skip_assign_raw",
          "selected_section="
              + pSectionNo
              + ", from_section="
              + pFromElement.sectionNoOfDoor
              + ", backtrack_section="
              + pFromElement.sectionNoOfBacktrackDoor
              + ", occupied="
              + doorSectionOccupied
              + ", shape_entry_null="
              + (pShapeEntry == null)
              + ", adjustment="
              + pAdjustment,
          "Net #"
              + ctrl.netNo
              + ", door="
              + describeExpandable(pDoor)
              + ", door_bounds="
              + describeExpandableBounds(pDoor)
              + ", from_door="
              + describeExpandable(pFromElement.door)
              + ", from_door_bounds="
              + describeExpandableBounds(pFromElement.door),
          toImpactedPoints(pShapeEntry));
      return false;
    }
    CompleteExpansionRoom nextRoom = pDoor.otherRoom(pFromElement.nextRoom);
    int layer = pFromElement.nextRoom.getLayer();
    FloatPoint shapeEntryMiddle = pShapeEntry.a.middlePoint(pShapeEntry.b);

    double bendCostPenalty = 0.0;
    if (ctrl.bendCosts[layer] > 0.0 && pFromElement.backtrackDoor != null) {
      FloatPoint fromMid = pFromElement.shapeEntry.a.middlePoint(pFromElement.shapeEntry.b);
      // Build vectors prev→curr and curr→next to detect a direction change.
      FloatPoint backtrackCog = pFromElement.backtrackDoor.getShape().centreOfGravity();
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
        pFromElement.expansionValue
            + pAddCosts
            + bendCostPenalty
            + shapeEntryMiddle.weightedDistance(
                pFromElement.shapeEntry.a.middlePoint(pFromElement.shapeEntry.b),
                ctrl.traceCosts[layer].horizontal,
                ctrl.traceCosts[layer].vertical);
    double sortingValue =
        expansionValue + this.destinationDistance.calculate(shapeEntryMiddle, layer);
    boolean roomRipped =
        pAddCosts > 0 && pAdjustment == MazeSearchElement.Adjustment.NONE
            || pFromElement.alreadyChecked && pFromElement.roomRipped;

    MazeListElement newElement =
        new MazeListElement(
            pDoor,
            pSectionNo,
            pFromElement.door,
            pFromElement.sectionNoOfDoor,
            expansionValue,
            sortingValue,
            nextRoom,
            pShapeEntry,
            roomRipped,
            pAdjustment,
            false);
    // Store the direct ripup cost on this element (non-zero only when this specific door
    // caused a ripup; propagated roomRipped from a parent keeps ripupCost=0).
    if (pAddCosts > 0 && pAdjustment == MazeSearchElement.Adjustment.NONE) {
      newElement.ripupCost = (int) pAddCosts;
    }
    FRLogger.trace(
        "RAW_SECTION assign selected_section="
            + pSectionNo
            + ", from_section="
            + pFromElement.sectionNoOfDoor
            + ", backtrack_section="
            + pFromElement.sectionNoOfBacktrackDoor
            + ", add_costs="
            + pAddCosts
            + ", adjustment="
            + pAdjustment
            + ", roomRipped="
            + roomRipped
            + ", expansionValue="
            + expansionValue
            + ", sortingValue="
            + sortingValue
            + ", door="
            + describeExpandable(pDoor)
            + ", door_bounds="
            + describeExpandableBounds(pDoor)
            + ", from_door="
            + describeExpandable(pFromElement.door)
            + ", from_door_bounds="
            + describeExpandableBounds(pFromElement.door)
            + ", net="
            + ctrl.netNo);
    FRLogger.trace(
        "MazeSearchAlgo.expand_to_door_section",
        "assign_raw",
        "selected_section="
            + pSectionNo
            + ", from_section="
            + pFromElement.sectionNoOfDoor
            + ", backtrack_section="
            + pFromElement.sectionNoOfBacktrackDoor
            + ", add_costs="
            + pAddCosts
            + ", adjustment="
            + pAdjustment
            + ", roomRipped="
            + roomRipped
            + ", expansionValue="
            + expansionValue
            + ", sortingValue="
            + sortingValue,
        "Net #"
            + ctrl.netNo
            + ", door="
            + describeExpandable(pDoor)
            + ", door_bounds="
            + describeExpandableBounds(pDoor)
            + ", from_door="
            + describeExpandable(pFromElement.door)
            + ", from_door_bounds="
            + describeExpandableBounds(pFromElement.door),
        toImpactedPoints(pShapeEntry));
    this.mazeExpansionList.add(newElement);
    return true;
  }

  private static String describeExpandable(ExpandableObject pDoor) {
    if (pDoor == null) {
      return "null";
    }
    String sectionCount = safeMazeSectionCount(pDoor);
    if (pDoor instanceof TargetItemExpansionDoor targetDoor) {
      return "TargetItemExpansionDoor"
          + "/item="
          + targetDoor.item.getIdNo()
          + "/tree_entry="
          + targetDoor.treeEntryNo
          + "/dim="
          + pDoor.getDimension()
          + "/sections="
          + sectionCount;
    }
    if (pDoor instanceof ExpansionDrill drill) {
      return "ExpansionDrill"
          + "/location="
          + drill.location
          + "/layers="
          + drill.firstLayer
          + "-"
          + drill.lastLayer
          + "/dim="
          + pDoor.getDimension()
          + "/sections="
          + sectionCount;
    }
    IntBox bounds = pDoor.getShape().boundingBox();
    return pDoor.getClass().getSimpleName()
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
        + pDoor.getDimension()
        + "/sections="
        + sectionCount;
  }

  private static String safeMazeSectionCount(ExpandableObject pDoor) {
    try {
      return Integer.toString(pDoor.mazeSearchElementCount());
    } catch (RuntimeException e) {
      return "uninitialized";
    }
  }

  private static String describeExpandableBounds(ExpandableObject pDoor) {
    if (pDoor == null) {
      return "null";
    }
    IntBox bounds = pDoor.getShape().boundingBox();
    return "[(" + bounds.ll.x + "," + bounds.ll.y + ")..(" + bounds.ur.x + "," + bounds.ur.y + ")]";
  }

  private static String describeRoom(CompleteExpansionRoom pRoom) {
    if (pRoom == null) {
      return "null";
    }
    IntBox bounds = pRoom.getShape().boundingBox();
    return pRoom.getClass().getSimpleName()
        + "/layer="
        + pRoom.getLayer()
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

  private static Point[] toImpactedPoints(FloatLine pShapeEntry) {
    if (pShapeEntry == null) {
      return null;
    }
    return new Point[] {pShapeEntry.a.round(), pShapeEntry.b.round()};
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

  private void expandToDrill(ExpansionDrill pDrill, MazeListElement pFromElement, int pAddCosts) {
    int layer = pFromElement.nextRoom.getLayer();
    int traceHalfWidth = this.ctrl.compensatedTraceHalfWidth[layer];
    boolean roomShapeIsThin = pFromElement.nextRoom.getShape().minWidth() < 2 * traceHalfWidth;

    if (roomShapeIsThin) {
      // expand only drills intersecting the backtrack door
      if (pFromElement.backtrackDoor == null
          || !pDrill.getShape().intersects(pFromElement.backtrackDoor.getShape())) {
        traceFanoutDiagnostic(
            "drill_rejected_thin_room_no_backtrack_intersection",
            "drill="
                + describeExpandable(pDrill)
                + ", from_door="
                + describeExpandable(pFromElement.door)
                + ", backtrack="
                + describeExpandable(pFromElement.backtrackDoor)
                + ", room="
                + describeRoom(pFromElement.nextRoom));
        return;
      }
    }

    double viaRadius = ctrl.viaRadiusArr[layer];
    ConvexShape shrinkedDrillShape = pDrill.getShape().shrink(viaRadius);
    FloatPoint compareCorner = pFromElement.shapeEntry.a.middlePoint(pFromElement.shapeEntry.b);
    if (pFromElement.door instanceof DrillPage
        && pFromElement.backtrackDoor instanceof TargetItemExpansionDoor door) {
      // If expansion comes from a pin with trace exit directions the expansionValue
      // is calculated
      // from the nearest trace exit point instead from the center olf the pin.
      Item fromItem = door.item;
      if (fromItem instanceof Pin pin) {
        FloatPoint nearestExitCorner =
            pin.nearestTraceExitCorner(pDrill.location.toFloat(), traceHalfWidth, layer);
        if (nearestExitCorner != null) {
          compareCorner = nearestExitCorner;
        }
      }
    }
    FloatPoint nearestPoint = shrinkedDrillShape.nearestPointApprox(compareCorner);
    FloatLine shapeEntry = new FloatLine(nearestPoint, nearestPoint);
    int sectionNo = layer - pDrill.firstLayer;
    double expansionValue =
        pFromElement.expansionValue
            + pAddCosts
            + nearestPoint.weightedDistance(
                compareCorner, ctrl.traceCosts[layer].horizontal, ctrl.traceCosts[layer].vertical);
    ExpandableObject newBacktrackDoor;
    int newSectionNoOfBacktrackDoor;
    if (pFromElement.door instanceof DrillPage) {
      newBacktrackDoor = pFromElement.backtrackDoor;
      newSectionNoOfBacktrackDoor = pFromElement.sectionNoOfBacktrackDoor;
    } else {
      // Expanded directly through already existing via
      // The step expand_to_drill_page is skipped
      newBacktrackDoor = pFromElement.door;
      newSectionNoOfBacktrackDoor = pFromElement.sectionNoOfDoor;
      expansionValue += ctrl.minNormalViaCost;
    }
    double sortingValue = expansionValue + this.destinationDistance.calculate(nearestPoint, layer);
    MazeListElement newElement =
        new MazeListElement(
            pDrill,
            sectionNo,
            newBacktrackDoor,
            newSectionNoOfBacktrackDoor,
            expansionValue,
            sortingValue,
            null,
            shapeEntry,
            pFromElement.roomRipped,
            MazeSearchElement.Adjustment.NONE,
            false);
    this.mazeExpansionList.add(newElement);
    traceFanoutDiagnostic(
        "drill_accepted",
        "drill="
            + describeExpandable(pDrill)
            + ", room="
            + describeRoom(pFromElement.nextRoom)
            + ", nearestPoint="
            + nearestPoint
            + ", expansionValue="
            + expansionValue);
  }

  /**
   * A drill page is inserted between an expansion room and the drill to expand in order to prevent
   * performance problems with rooms with big shapes containing many drills.
   */
  private void expandToDrillPage(DrillPage pDrillPage, MazeListElement pFromElement) {

    int layer = pFromElement.nextRoom.getLayer();
    FloatPoint fromElementShapeEntryMiddle =
        pFromElement.shapeEntry.a.middlePoint(pFromElement.shapeEntry.b);
    FloatPoint nearestPoint = pDrillPage.shape.nearestPoint(fromElementShapeEntryMiddle);
    double expansionValue = pFromElement.expansionValue + ctrl.minNormalViaCost;
    double sortingValue =
        expansionValue
            + nearestPoint.weightedDistance(
                fromElementShapeEntryMiddle,
                ctrl.traceCosts[layer].horizontal,
                ctrl.traceCosts[layer].vertical)
            + this.destinationDistance.calculate(nearestPoint, layer);
    MazeListElement newElement =
        new MazeListElement(
            pDrillPage,
            layer,
            pFromElement.door,
            pFromElement.sectionNoOfDoor,
            expansionValue,
            sortingValue,
            pFromElement.nextRoom,
            pFromElement.shapeEntry,
            pFromElement.roomRipped,
            MazeSearchElement.Adjustment.NONE,
            false);
    this.mazeExpansionList.add(newElement);
  }

  private void expandToDrillsOfPage(MazeListElement pFromElement) {
    int fromRoomLayer = pFromElement.sectionNoOfDoor;
    DrillPage drillPage = (DrillPage) pFromElement.door;
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
              + describeRoom(pFromElement.nextRoom)
              + ", from_door="
              + describeExpandable(pFromElement.door));
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
      if (currDrill.roomArr[sectionNo] != pFromElement.nextRoom) {
        traceFanoutDiagnostic(
            "drill_rejected_room_mismatch",
            "drill="
                + describeExpandable(currDrill)
                + ", expected_room="
                + describeRoom(pFromElement.nextRoom)
                + ", drill_room="
                + describeRoom(currDrill.roomArr[sectionNo]));
        // Log the first mismatch per page-scan with extra geometric context for investigation.
        if (!firstMismatchLogged && shouldTraceFanoutDiagnostics()) {
          firstMismatchLogged = true;
          CompleteExpansionRoom expRoom = pFromElement.nextRoom;
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
                  + (pFromElement.door != null
                      ? pFromElement.door.getClass().getSimpleName()
                      : "null")
                  + ", backtrack_door_type="
                  + (pFromElement.backtrackDoor != null
                      ? pFromElement.backtrackDoor.getClass().getSimpleName()
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
      expandToDrill(currDrill, pFromElement, 0);
    }
  }

  /** Tries to expand other layers by inserting a via. */
  private void expandToOtherLayers(MazeListElement pListElement) {
    int viaLowerBound = 0;
    int viaUpperBound = -1;
    ExpansionDrill currDrill = (ExpansionDrill) pListElement.door;
    int fromLayer = currDrill.firstLayer + pListElement.sectionNoOfDoor;
    boolean smdAttachedOnComponentSide = false;
    boolean smdAttachedOnSolderSide = false;
    boolean roomRipped;
    if (currDrill.roomArr[pListElement.sectionNoOfDoor] instanceof ObstacleExpansionRoom room) {
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
          pListElement.expansionValue + ctrl.addViaCosts[fromLayer].toLayer[toLayer];
      FloatPoint shapeEntryMiddle =
          pListElement.shapeEntry.a.middlePoint(pListElement.shapeEntry.b);
      double sortingValue =
          expansionValue + this.destinationDistance.calculate(shapeEntryMiddle, toLayer);
      int currRoomIndex = toLayer - currDrill.firstLayer;
      MazeListElement newElement =
          new MazeListElement(
              currDrill,
              currRoomIndex,
              currDrill,
              pListElement.sectionNoOfDoor,
              expansionValue,
              sortingValue,
              currDrill.roomArr[currRoomIndex],
              pListElement.shapeEntry,
              roomRipped,
              MazeSearchElement.Adjustment.NONE,
              false);
      this.mazeExpansionList.add(newElement);
    }
  }

  private ForcedPadAlgo.CheckDrillResult checkLayerWithAnyMatchingVia(
      ExpansionDrill pDrill, int pLayer, TileShape pRoomShape, int[] pNetNoArr) {
    boolean drillableWithAttachSmd = false;
    for (int i = 0; i < this.ctrl.viaRule.viaCount(); i++) {
      ViaInfo viaInfo = this.ctrl.viaRule.getVia(i);
      Padstack viaPadstack = viaInfo.getPadstack();
      if (pLayer < viaPadstack.fromLayer() || pLayer > viaPadstack.toLayer()) {
        continue;
      }
      ConvexShape viaShape = viaPadstack.getShape(pLayer);
      double viaRadius = viaShape == null ? 0 : 0.5 * viaShape.maxWidth();
      double requiredRadius = Math.max(viaRadius, this.ctrl.traceHalfWidth[pLayer]);
      ForcedPadAlgo.CheckDrillResult result =
          ForcedViaAlgo.checkLayer(
              requiredRadius,
              viaInfo.getClearanceClass(),
              viaInfo.attachSmdAllowed(),
              pRoomShape,
              pDrill.location,
              pLayer,
              pNetNoArr,
              this.ctrl.maxShoveTraceRecursionDepth,
              0,
              this.autorouteEngine.board,
              this.ctrl.traceHalfWidth[pLayer],
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
  private boolean init(Set<Item> pStartItems, Set<Item> pDestinationItems) {
    reduceTraceShapesAtTiePins(pStartItems, this.ctrl.netNo, this.searchTree);
    reduceTraceShapesAtTiePins(pDestinationItems, this.ctrl.netNo, this.searchTree);
    // process the destination items
    boolean destinationOk = false;
    for (Item currItem : pDestinationItems) {
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
              + pDestinationItems.size()
              + ", isFanout: "
              + this.ctrl.isFanout
              + ")");
      return false;
    }
    // process the start items
    Collection<IncompleteFreeSpaceExpansionRoom> startRooms = new LinkedList<>();
    for (Item currItem : pStartItems) {
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
      completedStartRooms.addAll(this.autorouteEngine.getRoomsWithTargetItems(pStartItems));
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
            ((Connectable) currDoor.item).getTraceConnectionShape(searchTree, currDoor.treeEntryNo);
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
              + pStartItems.size()
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

  private boolean roomShapeIsThick(ObstacleExpansionRoom pObstacleRoom) {
    Item obstacleItem = pObstacleRoom.getItem();
    int layer = pObstacleRoom.getLayer();
    double obstacleHalfWidth;
    if (obstacleItem instanceof Trace trace) {
      obstacleHalfWidth =
          trace.getHalfWidth()
              + this.searchTree.clearanceCompensationValue(obstacleItem.clearanceClassNo(), layer);

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
  private int checkRipup(MazeListElement pListElement, Item pObstacleItem, boolean pDoorIsSmall) {
    if (!pObstacleItem.isRoutable()) {
      return -1;
    }
    if (pDoorIsSmall) {
      // allow entering a via or trace, if its corresponding border segment is smaller
      // than the
      // current trace width

      if (!enterThroughSmallDoor(pListElement, pObstacleItem)) {
        return -1;
      }
    }
    CompleteExpansionRoom previousRoom = pListElement.door.otherRoom(pListElement.nextRoom);
    boolean roomWasShoved = pListElement.adjustment != MazeSearchElement.Adjustment.NONE;
    Item previousItem = null;
    if (previousRoom instanceof ObstacleExpansionRoom room) {
      previousItem = room.getItem();
    }
    if (roomWasShoved) {
      if (previousItem != null
          && previousItem != pObstacleItem
          && previousItem.sharesNet(pObstacleItem)) {
        // The ripped trace may start at a fork.
        return -1;
      }
    } else if (previousItem == pObstacleItem) {
      return ALREADY_RIPPED_COSTS;
    }

    double fanoutViaCostFactor = 1.0;
    double costFactor = 1;
    boolean preserveFanoutProtection =
        !this.ctrl.removeUnconnectedVias
            && this.ctrl.ripupCosts <= (this.ctrl.settings.getStartRipupCosts() * 2);
    if (pObstacleItem instanceof Trace obstacle_trace) {
      costFactor = obstacle_trace.getHalfWidth();
      if (preserveFanoutProtection) {
        // protect traces between SMD-pins and fanout vias
        fanoutViaCostFactor = calcFanoutViaRipupCostFactor(obstacle_trace);
      }
    } else if (pObstacleItem instanceof Via) {
      boolean lookIfFanoutVia = preserveFanoutProtection;
      Collection<Item> contactList = pObstacleItem.getNormalContacts();
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
      Connection obstacleConnection = Connection.get(pObstacleItem);
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
    final int maxRipupCosts = Integer.MAX_VALUE / 100;
    result = Math.min(result, maxRipupCosts);
    String obstacleNets = "[]";
    if (pObstacleItem instanceof app.freerouting.board.Item obstacleItem) {
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
            + (pObstacleItem instanceof app.freerouting.board.Item obstItem
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
      MazeListElement pListElement, ObstacleExpansionRoom pObstacleRoom) {
    if (pListElement.sectionNoOfDoor != 0
        && pListElement.sectionNoOfDoor != pListElement.door.mazeSearchElementCount() - 1) {
      // No delay of occupation necessary because inner sections of a door are
      // currently not
      // shoved.
      return true;
    }
    boolean result = false;
    if (pListElement.adjustment != MazeSearchElement.Adjustment.RIGHT) {
      Collection<MazeShoveTraceAlgo.DoorSection> leftToDoorSectionList = new LinkedList<>();

      if (MazeShoveTraceAlgo.checkShoveTraceLine(
          pListElement,
          pObstacleRoom,
          this.autorouteEngine.board,
          this.ctrl,
          false,
          leftToDoorSectionList)) {
        result = true;
      }

      for (MazeShoveTraceAlgo.DoorSection currLeftDoorSection : leftToDoorSectionList) {
        MazeSearchElement.Adjustment currAdjustment;
        if (currLeftDoorSection.door.dimension == 2) {
          // the door is the link door to the next room
          currAdjustment = MazeSearchElement.Adjustment.LEFT;
        } else {
          currAdjustment = MazeSearchElement.Adjustment.NONE;
        }

        expandToDoorSection(
            currLeftDoorSection.door,
            currLeftDoorSection.sectionNo,
            currLeftDoorSection.sectionLine,
            pListElement,
            0,
            currAdjustment);
      }
    }

    if (pListElement.adjustment != MazeSearchElement.Adjustment.LEFT) {
      Collection<MazeShoveTraceAlgo.DoorSection> rightToDoorSectionList = new LinkedList<>();

      if (MazeShoveTraceAlgo.checkShoveTraceLine(
          pListElement,
          pObstacleRoom,
          this.autorouteEngine.board,
          this.ctrl,
          true,
          rightToDoorSectionList)) {
        result = true;
      }
      for (MazeShoveTraceAlgo.DoorSection currRightDoorSection : rightToDoorSectionList) {
        MazeSearchElement.Adjustment currAdjustment;
        if (currRightDoorSection.door.dimension == 2) {
          // the door is the link door to the next room
          currAdjustment = MazeSearchElement.Adjustment.RIGHT;
        } else {
          currAdjustment = MazeSearchElement.Adjustment.NONE;
        }
        expandToDoorSection(
            currRightDoorSection.door,
            currRightDoorSection.sectionNo,
            currRightDoorSection.sectionLine,
            pListElement,
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
  private double checkNeckDownAtDestPin(CompleteExpansionRoom pRoom) {
    Collection<TargetItemExpansionDoor> targetDoors = pRoom.getTargetDoors();
    for (TargetItemExpansionDoor currTargetDoor : targetDoors) {
      if (currTargetDoor.item instanceof Pin pin) {
        return pin.getTraceNeckdownHalfwidth(pRoom.getLayer());
      }
    }
    return 0;
  }

  /**
   * Checks, if the next room can be entered if the door of p_list_element is small. If
   * p_ignore_item != null, p_ignore_item and all other items directly connected to p_ignore_item
   * are ignored in the check.
   */
  private boolean enterThroughSmallDoor(MazeListElement pListElement, Item pIgnoreItem) {
    if (pListElement.door.getDimension() != 1) {
      return false;
    }
    TileShape doorShape = pListElement.door.getShape();

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
    int currLayer = pListElement.nextRoom.getLayer();
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
      if (!(currObject instanceof Item currItem) || currObject == pIgnoreItem) {
        continue;
      }
      if (!currItem.sharesNet(pIgnoreItem)) {
        return false;
      }
      Set<Item> currContacts = currItem.getNormalContacts();
      if (!currContacts.contains(pIgnoreItem)) {
        return false;
      }
    }
    return true;
  }

  /** Checks entering a thick room from a via or trace through a small door (after ripup) */
  private boolean checkLeavingRippedItem(MazeListElement pListElement) {
    if (!(pListElement.door instanceof ExpansionDoor currDoor)) {
      return false;
    }
    CompleteExpansionRoom fromRoom = currDoor.otherRoom(pListElement.nextRoom);
    if (!(fromRoom instanceof ObstacleExpansionRoom)) {
      return false;
    }
    Item currItem = ((ObstacleExpansionRoom) fromRoom).getItem();
    if (!currItem.isRoutable()) {
      return false;
    }
    return enterThroughSmallDoor(pListElement, currItem);
  }

  /** The result type of MazeSearchAlgo.find_connection */
  public static class Result {

    public final ExpandableObject destinationDoor;
    public final int sectionNoOfDoor;

    Result(ExpandableObject pDestinationDoor, int pSectionNoOfDoor) {
      destinationDoor = pDestinationDoor;
      sectionNoOfDoor = pSectionNoOfDoor;
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
        ExpansionDoor pOppositeDoor,
        Collection<ExpansionDoor> pSideDoors,
        FloatPoint pFromDoorPassingPoint,
        FloatPoint pOppositeDoorPassingPoint) {
      oppositeDoor = pOppositeDoor;
      sideDoors = pSideDoors;
      fromDoorPassingPoint = pFromDoorPassingPoint;
      oppositeDoorPassingPoint = pOppositeDoorPassingPoint;
    }
  }
}
