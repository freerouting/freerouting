package app.freerouting.autoroute;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.Connectable;
import app.freerouting.board.FixedState;
import app.freerouting.board.ForcedPadRouter;
import app.freerouting.board.ForcedViaInserter;
import app.freerouting.board.Item;
import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.Pin;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.SearchTreeObject;
import app.freerouting.board.ShapeSearchTree;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.core.library.Padstack;
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
   * Initializes a new instance of MazeSearchAlgo for searching a connection between startItems and
   * destinationItems. Returns null, if the initialisation failed.
   */
  public static MazeSearchAlgo getInstance(
      Set<Item> startItems,
      Set<Item> destinationItems,
      AutorouteEngine autorouteDatabase,
      AutorouteControl ctrl) {
    MazeSearchAlgo newInstance = new MazeSearchAlgo(autorouteDatabase, ctrl);
    MazeSearchAlgo result;
    if (newInstance.init(startItems, destinationItems)) {
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
      Collection<Item> itemList, int ownNetNo, ShapeSearchTree autorouteTree) {
    for (Item currentItem : itemList) {
      if ((currentItem instanceof Pin currentTiePin) && currentItem.netCount() > 1) {
        Collection<Item> pinContacts = currentItem.getNormalContacts();
        for (Item currentContact : pinContacts) {
          if (!(currentContact instanceof PolylineTrace) || currentContact.containsNet(ownNetNo)) {
            continue;
          }
          autorouteTree.reduceTraceShapeAtTiePin(currentTiePin, (PolylineTrace) currentContact);
        }
      }
    }
  }

  /**
   * Return the additional cost factor for ripping the trace, if it is connected to a fanout via or
   * 1, if no fanout via was found.
   */
  private static double calcFanoutViaRipupCostFactor(Trace trace) {
    final double fanoutCostConst = 20000;
    Collection<Item> currentEndContacts;
    for (int i = 0; i < 2; i++) {
      if (i == 0) {
        currentEndContacts = trace.getStartContacts();
      } else {
        currentEndContacts = trace.getEndContacts();
      }
      if (currentEndContacts.size() != 1) {
        continue;
      }
      Item currentTraceContact = currentEndContacts.iterator().next();
      boolean protectFanoutVia = false;
      if (currentTraceContact instanceof Pin
          && currentTraceContact.firstLayer() == currentTraceContact.lastLayer()) {
        protectFanoutVia = true;
      } else if (currentTraceContact instanceof PolylineTrace contactTrace
          && currentTraceContact.getFixedState() == FixedState.SHOVE_FIXED) {
        // look for shove fixed exit traces of SMD-pins
        if (contactTrace.cornerCount() == 2) {
          protectFanoutVia = true;
        }
      }

      if (protectFanoutVia) {
        double fanoutViaCostFactor = trace.getHalfWidth() / trace.getLength();
        fanoutViaCostFactor *= fanoutViaCostFactor;
        fanoutViaCostFactor *= fanoutCostConst;
        return Math.max(fanoutViaCostFactor, 1);
      }
    }
    return 1;
  }

  /**
   * Returns the perpendicular projection of fromSegment onto toSegment. Returns null, if the
   * projection is empty.
   */
  private static FloatLine segmentProjection(FloatLine fromSegment, FloatLine toSegment) {
    FloatLine checkSegment = fromSegment.adjustDirection(toSegment);
    FloatLine firstProjection = toSegment.segmentProjection(checkSegment);
    FloatLine secondProjection = toSegment.segmentProjection2(checkSegment);
    FloatLine result;
    if (firstProjection != null && secondProjection != null) {
      FloatPoint resultA;
      if (firstProjection.a == toSegment.a || secondProjection.a == toSegment.a) {
        resultA = toSegment.a;
      } else if (firstProjection.a.distanceSquare(toSegment.a)
          <= secondProjection.a.distanceSquare(toSegment.a)) {
        resultA = firstProjection.a;
      } else {
        resultA = secondProjection.a;
      }
      FloatPoint resultB;
      if (firstProjection.b == toSegment.b || secondProjection.b == toSegment.b) {
        resultB = toSegment.b;
      } else if (firstProjection.b.distanceSquare(toSegment.b)
          <= secondProjection.b.distanceSquare(toSegment.b)) {
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

  private static String describeExpandable(ExpandableObject door) {
    if (door == null) {
      return "null";
    }
    String sectionCount = safeMazeSectionCount(door);
    if (door instanceof TargetItemExpansionDoor targetDoor) {
      return "TargetItemExpansionDoor"
          + "/item="
          + targetDoor.item.getIdNo()
          + "/tree_entry="
          + targetDoor.treeEntryNo
          + "/dim="
          + door.getDimension()
          + "/sections="
          + sectionCount;
    }
    if (door instanceof ExpansionDrill drill) {
      return "ExpansionDrill"
          + "/location="
          + drill.location
          + "/layers="
          + drill.firstLayer
          + "-"
          + drill.lastLayer
          + "/dim="
          + door.getDimension()
          + "/sections="
          + sectionCount;
    }
    IntBox bounds = door.getShape().boundingBox();
    return door.getClass().getSimpleName()
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
        + door.getDimension()
        + "/sections="
        + sectionCount;
  }

  private static String safeMazeSectionCount(ExpandableObject door) {
    try {
      return Integer.toString(door.mazeSearchElementCount());
    } catch (RuntimeException e) {
      return "uninitialized";
    }
  }

  private static String describeExpandableBounds(ExpandableObject door) {
    if (door == null) {
      return "null";
    }
    IntBox bounds = door.getShape().boundingBox();
    return "[(" + bounds.ll.x + "," + bounds.ll.y + ")..(" + bounds.ur.x + "," + bounds.ur.y + ")]";
  }

  private static String describeRoom(CompleteExpansionRoom room) {
    if (room == null) {
      return "null";
    }
    IntBox bounds = room.getShape().boundingBox();
    return room.getClass().getSimpleName()
        + "/layer="
        + room.getLayer()
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

  private static Point[] toImpactedPoints(FloatLine shapeEntry) {
    if (shapeEntry == null) {
      return null;
    }
    return new Point[] {shapeEntry.a.round(), shapeEntry.b.round()};
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
    MazeSearchElement currentDoorSection = null;
    // Search the next element, which is not yet expanded.
    boolean nextElementFound = false;
    while (!mazeExpansionList.isEmpty()) {
      if (this.autorouteEngine.isStopRequested()) {
        return false;
      }

      Iterator<MazeListElement> it = mazeExpansionList.iterator();
      listElement = it.next();
      it.remove();

      int currentSectionNo = listElement.sectionNoOfDoor;
      currentDoorSection = listElement.door.getMazeSearchElement(currentSectionNo);

      if (!currentDoorSection.isOccupied) {
        nextElementFound = true;
        break;
      }
    }
    if (!nextElementFound) {
      return false;
    }
    currentDoorSection.backtrackDoor = listElement.backtrackDoor;
    currentDoorSection.sectionNoOfBacktrackDoor = listElement.sectionNoOfBacktrackDoor;
    currentDoorSection.roomRipped = listElement.roomRipped;
    currentDoorSection.ripupCost = listElement.ripupCost;
    currentDoorSection.adjustment = listElement.adjustment;

    if (listElement.door instanceof DrillPage) {
      expandToDrillsOfPage(listElement);
      return true;
    }

    if (listElement.door instanceof TargetItemExpansionDoor currentDoor) {
      if (currentDoor.isDestinationDoor()) {
        // The destination is reached.
        this.destinationDoor = currentDoor;
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
    currentDoorSection.isOccupied = true;
    return true;
  }

  /**
   * Expands the other door section of the room. Returns true, if the from door section has to be
   * occupied, and false, if the occupation for is delayed.
   */
  private boolean expandToRoomDoors(MazeListElement listElement) {

    // Complete the neighbour rooms to make sure, that the
    // doors of this room will not change later on.
    int layerNo = listElement.nextRoom.getLayer();

    boolean layerActive = ctrl.layerActive[layerNo];
    if (!layerActive) {
      if (autorouteEngine.board.layerStructure.arr[layerNo].isSignal) {
        return true;
      }
    }

    double halfWidth = ctrl.compensatedTraceHalfWidth[layerNo];
    boolean currentDoorIsSmall = false;
    if (listElement.door instanceof ExpansionDoor currentDoor) {
      double halfWidthAdd = halfWidth + AutorouteEngine.TRACE_WIDTH_TOLERANCE;
      if (this.ctrl.withNeckdown) {
        // try evtl. neckdown at a destination pin
        double neckDownHalfWidth = checkNeckDownAtDestPin(listElement.nextRoom);
        if (neckDownHalfWidth > 0) {
          halfWidthAdd = Math.min(halfWidthAdd, neckDownHalfWidth);
          halfWidth = halfWidthAdd;
        }
      }
      currentDoorIsSmall = doorIsSmall(currentDoor, 2 * halfWidthAdd);
    }

    int doorCountBeforeCompletion = listElement.nextRoom.getDoors().size();
    this.autorouteEngine.completeNeighbourRooms(listElement.nextRoom);
    int doorCountAfterCompletion = listElement.nextRoom.getDoors().size();
    FRLogger.trace(
        "ROOM_COMPLETE_SYNC"
            + ", net="
            + ctrl.netNo
            + ", layer="
            + layerNo
            + ", from_section="
            + listElement.sectionNoOfDoor
            + ", backtrack_section="
            + listElement.sectionNoOfBacktrackDoor
            + ", from_door="
            + describeExpandable(listElement.door)
            + ", nextRoom="
            + describeRoom(listElement.nextRoom)
            + ", door_count_before="
            + doorCountBeforeCompletion
            + ", door_count_after="
            + doorCountAfterCompletion);

    FloatPoint shapeEntryMiddle = listElement.shapeEntry.a.middlePoint(listElement.shapeEntry.b);

    if (this.ctrl.withNeckdown && listElement.door instanceof TargetItemExpansionDoor door) {
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
    if (listElement.nextRoom instanceof ObstacleExpansionRoom room) {
      nextRoomIsThick = roomShapeIsThick(room);
    } else {
      TileShape nextRoomShape = listElement.nextRoom.getShape();
      if (nextRoomShape.minWidth() < 2 * halfWidth) {
        nextRoomIsThick = false; // to prevent problems with the opposite side
      } else if (!listElement.alreadyChecked
          && listElement.door.getDimension() == 1
          && !currentDoorIsSmall) {
        // The algorithm below works only, if location is on the border of roomShape.
        // That is only the case for 1 dimensional doors.
        // For small doors the check is done in check_leaving_via below.

        FloatPoint[] nearestPoints = nextRoomShape.nearestBorderPointsApprox(shapeEntryMiddle, 2);
        if (nearestPoints.length < 2) {
          FRLogger.warn("MazeSearchAlgo.expand_to_room_doors: nearestPoints.length == 2 expected");
          nextRoomIsThick = false;
        } else {
          double currentDistance = nearestPoints[1].distance(shapeEntryMiddle);
          nextRoomIsThick = currentDistance > halfWidth + 1;
        }
      }
    }
    if (!layerActive && listElement.door instanceof ExpansionDrill drill) {
      // check for drill to a foreign conduction area on split plane.
      Point drillLocation = drill.location;
      ItemSelectionFilter filter =
          new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.CONDUCTION);
      Set<Item> pickedItems = autorouteEngine.board.pickItems(drillLocation, layerNo, filter);
      for (Item currentItem : pickedItems) {
        if (!currentItem.containsNet(ctrl.netNo)) {
          return true;
        }
      }
    }
    boolean somethingExpanded =
        expandToTargetDoors(listElement, nextRoomIsThick, currentDoorIsSmall, shapeEntryMiddle);

    if (!layerActive) {
      return true;
    }

    int ripupCosts = 0;

    if (listElement.nextRoom instanceof FreeSpaceExpansionRoom) {
      if (!listElement.alreadyChecked) {
        if (currentDoorIsSmall) {
          boolean enterThroughSmallDoor = false;
          if (nextRoomIsThick) {
            // check to enter the thick room from a ripped item through a small door (after
            // ripup)
            enterThroughSmallDoor = checkLeavingRippedItem(listElement);
          }
          if (!enterThroughSmallDoor) {
            return somethingExpanded;
          }
        }
      }
    } else if (listElement.nextRoom instanceof ObstacleExpansionRoom obstacleRoom) {

      if (!listElement.alreadyChecked) {
        boolean roomRippable = false;
        if (this.ctrl.ripupAllowed) {
          ripupCosts = checkRipup(listElement, obstacleRoom.getItem(), currentDoorIsSmall);
          roomRippable = ripupCosts >= 0;
        }

        if (ripupCosts != ALREADY_RIPPED_COSTS && nextRoomIsThick) {
          Item obstacleItem = obstacleRoom.getItem();
          if (!currentDoorIsSmall
              && this.ctrl.maxShoveTraceRecursionDepth > 0
              && obstacleItem instanceof PolylineTrace) {
            boolean shoved = shoveTraceRoom(listElement, obstacleRoom);
            if (!shoved) {
              if (ripupCosts > 0) {
                // delay the occupation by ripup to allow shoving the room by another door
                // sections.
                MazeListElement newElement =
                    new MazeListElement(
                        listElement.door,
                        listElement.sectionNoOfDoor,
                        listElement.backtrackDoor,
                        listElement.sectionNoOfBacktrackDoor,
                        listElement.expansionValue + ripupCosts,
                        listElement.sortingValue + ripupCosts,
                        listElement.nextRoom,
                        listElement.shapeEntry,
                        true,
                        listElement.adjustment,
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

    List<ExpansionDoor> roomDoorsSnapshot = new LinkedList<>(listElement.nextRoom.getDoors());
    FRLogger.trace(
        "ROOM_DOOR context from_section="
            + listElement.sectionNoOfDoor
            + ", backtrack_section="
            + listElement.sectionNoOfBacktrackDoor
            + ", from_door="
            + describeExpandable(listElement.door)
            + ", nextRoom="
            + describeRoom(listElement.nextRoom)
            + ", net="
            + ctrl.netNo);
    for (int doorIndex = 0; doorIndex < roomDoorsSnapshot.size(); doorIndex++) {
      ExpansionDoor candidateDoor = roomDoorsSnapshot.get(doorIndex);
      FRLogger.trace(
          "ROOM_DOOR candidate index="
              + doorIndex
              + ", from_section="
              + listElement.sectionNoOfDoor
              + ", backtrack_section="
              + listElement.sectionNoOfBacktrackDoor
              + ", from_door="
              + describeExpandable(listElement.door)
              + ", nextRoom="
              + describeRoom(listElement.nextRoom)
              + ", candidate="
              + describeExpandable(candidateDoor)
              + ", net="
              + ctrl.netNo);
    }

    for (ExpansionDoor toDoor : roomDoorsSnapshot) {
      if (toDoor == listElement.door) {
        continue;
      }
      if (expandToDoor(
          toDoor, listElement, ripupCosts, nextRoomIsThick, MazeSearchElement.Adjustment.NONE)) {
        somethingExpanded = true;
      }
    }

    // Expand also the drill pages intersecting the room.
    if (ctrl.viasAllowed && !(listElement.door instanceof ExpansionDrill)) {
      if ((somethingExpanded || nextRoomIsThick)
          && listElement.nextRoom instanceof CompleteFreeSpaceExpansionRoom) {
        // avoid setting somethingExpanded to true when nextRoom is thin to allow
        // occupying by
        // different sections of the door
        Collection<DrillPage> overlappingDrillPages =
            this.autorouteEngine.drillPageArray.overlappingPages(listElement.nextRoom.getShape());
        {
          for (DrillPage toDrillPage : overlappingDrillPages) {
            expandToDrillPage(toDrillPage, listElement);
            somethingExpanded = true;
          }
        }
      } else if (listElement.nextRoom instanceof ObstacleExpansionRoom room) {
        Item currentObstacleItem = room.getItem();
        if (currentObstacleItem instanceof Via currentVia) {
          ExpansionDrill viaDrillInfo =
              currentVia.getAutorouteDrillInfo(this.autorouteEngine.autorouteSearchTree);
          expandToDrill(viaDrillInfo, listElement, ripupCosts);
        }
      }
    }

    return somethingExpanded;
  }

  /** Expand the target doors of the room. Returns true, if at least 1 target door was expanded */
  private boolean expandToTargetDoors(
      MazeListElement listElement,
      boolean nextRoomIsThick,
      boolean currentDoorIsSmall,
      FloatPoint shapeEntryMiddle) {
    if (currentDoorIsSmall) {
      boolean enterThroughSmallDoor = false;
      if (listElement.door instanceof ExpansionDoor) {
        CompleteExpansionRoom fromRoom = listElement.door.otherRoom(listElement.nextRoom);
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
    for (TargetItemExpansionDoor toDoor : listElement.nextRoom.getTargetDoors()) {
      if (toDoor == listElement.door) {
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
      FloatPoint connectionPoint = targetShape.nearestPointApprox(shapeEntryMiddle);
      if (!nextRoomIsThick) {
        // check the line from shapeEntryMiddle to the nearest point.
        int[] currentNetNoArr = new int[1];
        currentNetNoArr[0] = this.ctrl.netNo;
        int currentLayer = listElement.nextRoom.getLayer();
        IntPoint[] checkPoints = new IntPoint[2];
        checkPoints[0] = shapeEntryMiddle.round();
        checkPoints[1] = connectionPoint.round();
        if (!checkPoints[0].equals(checkPoints[1])) {
          Polyline checkPolyline = new Polyline(checkPoints);
          boolean checkOk =
              autorouteEngine.board.checkForcedTracePolyline(
                  checkPolyline,
                  ctrl.traceHalfWidth[currentLayer],
                  currentLayer,
                  currentNetNoArr,
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
          toDoor, 0, newShapeEntry, listElement, 0, MazeSearchElement.Adjustment.NONE)) {
        result = true;
      }
    }
    return result;
  }

  /** Return true, if at least 1 door ection was expanded. */
  private boolean expandToDoor(
      ExpansionDoor toDoor,
      MazeListElement listElement,
      int addCosts,
      boolean nextRoomIsThick,
      MazeSearchElement.Adjustment adjustment) {
    double halfWidth = ctrl.compensatedTraceHalfWidth[listElement.nextRoom.getLayer()];
    boolean somethingExpanded = false;
    FloatLine[] lineSections = toDoor.getSectionSegments(halfWidth);

    for (int i = 0; i < lineSections.length; i++) {
      if (toDoor.sectionArr[i].isOccupied) {
        continue;
      }
      FloatLine newShapeEntry;
      if (nextRoomIsThick) {
        newShapeEntry = lineSections[i];
        if (toDoor.dimension == 1
            && lineSections.length == 1
            && toDoor.firstRoom instanceof CompleteFreeSpaceExpansionRoom
            && toDoor.secondRoom instanceof CompleteFreeSpaceExpansionRoom) {
          // check entering the toDoor at an acute corner of the shape of
          // listElement.nextRoom
          FloatPoint shapeEntryMiddle = newShapeEntry.a.middlePoint(newShapeEntry.b);
          TileShape roomShape = listElement.nextRoom.getShape();
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
        if (toDoor.dimension == 1
            && i == 0
            && lineSections[0].b.distanceSquare(lineSections[0].a) < 1) {
          // toDoor is small belonging to a via or thin room
          continue;
        }
        newShapeEntry = segmentProjection(listElement.shapeEntry, lineSections[i]);
        if (newShapeEntry == null) {
          continue;
        }
      }

      if (expandToDoorSection(toDoor, i, newShapeEntry, listElement, addCosts, adjustment)) {
        somethingExpanded = true;
      }
    }
    return somethingExpanded;
  }

  /** Checks, if the width door is big enough for a trace with width traceWidth. */
  private boolean doorIsSmall(ExpansionDoor door, double traceWidth) {
    if (door.dimension == 1
        || door.firstRoom instanceof CompleteFreeSpaceExpansionRoom
            && door.secondRoom instanceof CompleteFreeSpaceExpansionRoom) {
      TileShape doorShape = door.getShape();
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
      return doorLength < traceWidth;
    }
    return false;
  }

  /** Return true, if the door section was successfully expanded. */
  private boolean expandToDoorSection(
      ExpandableObject door,
      int sectionNo,
      FloatLine shapeEntry,
      MazeListElement fromElement,
      int addCosts,
      MazeSearchElement.Adjustment adjustment) {
    boolean doorSectionOccupied = door.getMazeSearchElement(sectionNo).isOccupied;
    if (doorSectionOccupied || shapeEntry == null) {
      FRLogger.trace(
          "RAW_SECTION skip selected_section="
              + sectionNo
              + ", from_section="
              + fromElement.sectionNoOfDoor
              + ", backtrack_section="
              + fromElement.sectionNoOfBacktrackDoor
              + ", occupied="
              + doorSectionOccupied
              + ", shape_entry_null="
              + (shapeEntry == null)
              + ", adjustment="
              + adjustment
              + ", door="
              + describeExpandable(door)
              + ", door_bounds="
              + describeExpandableBounds(door)
              + ", from_door="
              + describeExpandable(fromElement.door)
              + ", from_door_bounds="
              + describeExpandableBounds(fromElement.door)
              + ", net="
              + ctrl.netNo);
      FRLogger.trace(
          "MazeSearchAlgo.expand_to_door_section",
          "skip_assign_raw",
          "selected_section="
              + sectionNo
              + ", from_section="
              + fromElement.sectionNoOfDoor
              + ", backtrack_section="
              + fromElement.sectionNoOfBacktrackDoor
              + ", occupied="
              + doorSectionOccupied
              + ", shape_entry_null="
              + (shapeEntry == null)
              + ", adjustment="
              + adjustment,
          "Net #"
              + ctrl.netNo
              + ", door="
              + describeExpandable(door)
              + ", door_bounds="
              + describeExpandableBounds(door)
              + ", from_door="
              + describeExpandable(fromElement.door)
              + ", from_door_bounds="
              + describeExpandableBounds(fromElement.door),
          toImpactedPoints(shapeEntry));
      return false;
    }
    CompleteExpansionRoom nextRoom = door.otherRoom(fromElement.nextRoom);
    int layer = fromElement.nextRoom.getLayer();
    FloatPoint shapeEntryMiddle = shapeEntry.a.middlePoint(shapeEntry.b);

    double bendCostPenalty = 0.0;
    if (ctrl.bendCosts[layer] > 0.0 && fromElement.backtrackDoor != null) {
      FloatPoint fromMid = fromElement.shapeEntry.a.middlePoint(fromElement.shapeEntry.b);
      // Build vectors prev→current and current→next to detect a direction change.
      FloatPoint backtrackCog = fromElement.backtrackDoor.getShape().centreOfGravity();
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
        fromElement.expansionValue
            + addCosts
            + bendCostPenalty
            + shapeEntryMiddle.weightedDistance(
                fromElement.shapeEntry.a.middlePoint(fromElement.shapeEntry.b),
                ctrl.traceCosts[layer].horizontal,
                ctrl.traceCosts[layer].vertical);
    double sortingValue =
        expansionValue + this.destinationDistance.calculate(shapeEntryMiddle, layer);
    boolean roomRipped =
        addCosts > 0 && adjustment == MazeSearchElement.Adjustment.NONE
            || fromElement.alreadyChecked && fromElement.roomRipped;

    MazeListElement newElement =
        new MazeListElement(
            door,
            sectionNo,
            fromElement.door,
            fromElement.sectionNoOfDoor,
            expansionValue,
            sortingValue,
            nextRoom,
            shapeEntry,
            roomRipped,
            adjustment,
            false);
    // Store the direct ripup cost on this element (non-zero only when this specific door
    // caused a ripup; propagated roomRipped from a parent keeps ripupCost=0).
    if (addCosts > 0 && adjustment == MazeSearchElement.Adjustment.NONE) {
      newElement.ripupCost = (int) addCosts;
    }
    FRLogger.trace(
        "RAW_SECTION assign selected_section="
            + sectionNo
            + ", from_section="
            + fromElement.sectionNoOfDoor
            + ", backtrack_section="
            + fromElement.sectionNoOfBacktrackDoor
            + ", add_costs="
            + addCosts
            + ", adjustment="
            + adjustment
            + ", roomRipped="
            + roomRipped
            + ", expansionValue="
            + expansionValue
            + ", sortingValue="
            + sortingValue
            + ", door="
            + describeExpandable(door)
            + ", door_bounds="
            + describeExpandableBounds(door)
            + ", from_door="
            + describeExpandable(fromElement.door)
            + ", from_door_bounds="
            + describeExpandableBounds(fromElement.door)
            + ", net="
            + ctrl.netNo);
    FRLogger.trace(
        "MazeSearchAlgo.expand_to_door_section",
        "assign_raw",
        "selected_section="
            + sectionNo
            + ", from_section="
            + fromElement.sectionNoOfDoor
            + ", backtrack_section="
            + fromElement.sectionNoOfBacktrackDoor
            + ", add_costs="
            + addCosts
            + ", adjustment="
            + adjustment
            + ", roomRipped="
            + roomRipped
            + ", expansionValue="
            + expansionValue
            + ", sortingValue="
            + sortingValue,
        "Net #"
            + ctrl.netNo
            + ", door="
            + describeExpandable(door)
            + ", door_bounds="
            + describeExpandableBounds(door)
            + ", from_door="
            + describeExpandable(fromElement.door)
            + ", from_door_bounds="
            + describeExpandableBounds(fromElement.door),
        toImpactedPoints(shapeEntry));
    this.mazeExpansionList.add(newElement);
    return true;
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

  private void expandToDrill(ExpansionDrill drill, MazeListElement fromElement, int addCosts) {
    int layer = fromElement.nextRoom.getLayer();
    int traceHalfWidth = this.ctrl.compensatedTraceHalfWidth[layer];
    boolean roomShapeIsThin = fromElement.nextRoom.getShape().minWidth() < 2 * traceHalfWidth;

    if (roomShapeIsThin) {
      // expand only drills intersecting the backtrack door
      if (fromElement.backtrackDoor == null
          || !drill.getShape().intersects(fromElement.backtrackDoor.getShape())) {
        traceFanoutDiagnostic(
            "drill_rejected_thin_room_no_backtrack_intersection",
            "drill="
                + describeExpandable(drill)
                + ", from_door="
                + describeExpandable(fromElement.door)
                + ", backtrack="
                + describeExpandable(fromElement.backtrackDoor)
                + ", room="
                + describeRoom(fromElement.nextRoom));
        return;
      }
    }

    double viaRadius = ctrl.viaRadiusArr[layer];
    ConvexShape shrinkedDrillShape = drill.getShape().shrink(viaRadius);
    FloatPoint compareCorner = fromElement.shapeEntry.a.middlePoint(fromElement.shapeEntry.b);
    if (fromElement.door instanceof DrillPage
        && fromElement.backtrackDoor instanceof TargetItemExpansionDoor door) {
      // If expansion comes from a pin with trace exit directions the expansionValue
      // is calculated
      // from the nearest trace exit point instead from the center olf the pin.
      Item fromItem = door.item;
      if (fromItem instanceof Pin pin) {
        FloatPoint nearestExitCorner =
            pin.nearestTraceExitCorner(drill.location.toFloat(), traceHalfWidth, layer);
        if (nearestExitCorner != null) {
          compareCorner = nearestExitCorner;
        }
      }
    }
    FloatPoint nearestPoint = shrinkedDrillShape.nearestPointApprox(compareCorner);
    FloatLine shapeEntry = new FloatLine(nearestPoint, nearestPoint);
    int sectionNo = layer - drill.firstLayer;
    double expansionValue =
        fromElement.expansionValue
            + addCosts
            + nearestPoint.weightedDistance(
                compareCorner, ctrl.traceCosts[layer].horizontal, ctrl.traceCosts[layer].vertical);
    ExpandableObject newBacktrackDoor;
    int newSectionNoOfBacktrackDoor;
    if (fromElement.door instanceof DrillPage) {
      newBacktrackDoor = fromElement.backtrackDoor;
      newSectionNoOfBacktrackDoor = fromElement.sectionNoOfBacktrackDoor;
    } else {
      // Expanded directly through already existing via
      // The step expand_to_drill_page is skipped
      newBacktrackDoor = fromElement.door;
      newSectionNoOfBacktrackDoor = fromElement.sectionNoOfDoor;
      expansionValue += ctrl.minNormalViaCost;
    }
    double sortingValue = expansionValue + this.destinationDistance.calculate(nearestPoint, layer);
    MazeListElement newElement =
        new MazeListElement(
            drill,
            sectionNo,
            newBacktrackDoor,
            newSectionNoOfBacktrackDoor,
            expansionValue,
            sortingValue,
            null,
            shapeEntry,
            fromElement.roomRipped,
            MazeSearchElement.Adjustment.NONE,
            false);
    this.mazeExpansionList.add(newElement);
    traceFanoutDiagnostic(
        "drill_accepted",
        "drill="
            + describeExpandable(drill)
            + ", room="
            + describeRoom(fromElement.nextRoom)
            + ", nearestPoint="
            + nearestPoint
            + ", expansionValue="
            + expansionValue);
  }

  /**
   * A drill page is inserted between an expansion room and the drill to expand in order to prevent
   * performance problems with rooms with big shapes containing many drills.
   */
  private void expandToDrillPage(DrillPage drillPage, MazeListElement fromElement) {

    int layer = fromElement.nextRoom.getLayer();
    FloatPoint fromElementShapeEntryMiddle =
        fromElement.shapeEntry.a.middlePoint(fromElement.shapeEntry.b);
    FloatPoint nearestPoint = drillPage.shape.nearestPoint(fromElementShapeEntryMiddle);
    double expansionValue = fromElement.expansionValue + ctrl.minNormalViaCost;
    double sortingValue =
        expansionValue
            + nearestPoint.weightedDistance(
                fromElementShapeEntryMiddle,
                ctrl.traceCosts[layer].horizontal,
                ctrl.traceCosts[layer].vertical)
            + this.destinationDistance.calculate(nearestPoint, layer);
    MazeListElement newElement =
        new MazeListElement(
            drillPage,
            layer,
            fromElement.door,
            fromElement.sectionNoOfDoor,
            expansionValue,
            sortingValue,
            fromElement.nextRoom,
            fromElement.shapeEntry,
            fromElement.roomRipped,
            MazeSearchElement.Adjustment.NONE,
            false);
    this.mazeExpansionList.add(newElement);
  }

  private void expandToDrillsOfPage(MazeListElement fromElement) {
    int fromRoomLayer = fromElement.sectionNoOfDoor;
    DrillPage drillPage = (DrillPage) fromElement.door;
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
              + describeRoom(fromElement.nextRoom)
              + ", from_door="
              + describeExpandable(fromElement.door));
      if (drillList.isEmpty()) {
        traceFanoutDiagnostic("drill_page_empty", "no_candidates=true");
      }
    }
    // Track the first room-mismatch per fanout attempt for first-mismatch investigation.
    boolean firstMismatchLogged = false;
    for (ExpansionDrill currentDrill : drillList) {
      int sectionNo = fromRoomLayer - currentDrill.firstLayer;
      if (sectionNo < 0 || sectionNo >= currentDrill.roomArr.length) {
        traceFanoutDiagnostic(
            "drill_rejected_section_out_of_range",
            "drill="
                + describeExpandable(currentDrill)
                + ", section="
                + sectionNo
                + ", room_arr_len="
                + currentDrill.roomArr.length);
        continue;
      }
      if (currentDrill.roomArr[sectionNo] != fromElement.nextRoom) {
        traceFanoutDiagnostic(
            "drill_rejected_room_mismatch",
            "drill="
                + describeExpandable(currentDrill)
                + ", expected_room="
                + describeRoom(fromElement.nextRoom)
                + ", drill_room="
                + describeRoom(currentDrill.roomArr[sectionNo]));
        // Log the first mismatch per page-scan with extra geometric context for investigation.
        if (!firstMismatchLogged && shouldTraceFanoutDiagnostics()) {
          firstMismatchLogged = true;
          CompleteExpansionRoom expRoom = fromElement.nextRoom;
          CompleteExpansionRoom drillRoom = currentDrill.roomArr[sectionNo];
          FRLogger.trace(
              "FANOUT_DIAG event=first_room_mismatch_detail"
                  + ", pin="
                  + fanoutDiagnosticLabel()
                  + ", net="
                  + ctrl.netNo
                  + ", drillLocation="
                  + currentDrill.location
                  + ", expansion_room_id="
                  + System.identityHashCode(expRoom)
                  + ", expansion_room_bounds="
                  + (expRoom != null ? expRoom.getShape() : "null")
                  + ", drill_room_id="
                  + System.identityHashCode(drillRoom)
                  + ", drill_room_bounds="
                  + (drillRoom != null ? drillRoom.getShape() : "null")
                  + ", from_door_type="
                  + (fromElement.door != null
                      ? fromElement.door.getClass().getSimpleName()
                      : "null")
                  + ", backtrack_door_type="
                  + (fromElement.backtrackDoor != null
                      ? fromElement.backtrackDoor.getClass().getSimpleName()
                      : "null")
                  + ", sectionNo="
                  + sectionNo
                  + ", layer="
                  + fromRoomLayer);
        }
        continue;
      }
      if (currentDrill.getMazeSearchElement(sectionNo).isOccupied) {
        traceFanoutDiagnostic(
            "drill_rejected_section_occupied",
            "drill=" + describeExpandable(currentDrill) + ", section=" + sectionNo);
        continue;
      }
      expandToDrill(currentDrill, fromElement, 0);
    }
  }

  /** Tries to expand other layers by inserting a via. */
  private void expandToOtherLayers(MazeListElement listElement) {
    int viaLowerBound = 0;
    int viaUpperBound = -1;
    ExpansionDrill currentDrill = (ExpansionDrill) listElement.door;
    int fromLayer = currentDrill.firstLayer + listElement.sectionNoOfDoor;
    boolean smdAttachedOnComponentSide = false;
    boolean smdAttachedOnSolderSide = false;
    boolean roomRipped;
    if (currentDrill.roomArr[listElement.sectionNoOfDoor] instanceof ObstacleExpansionRoom room) {
      // check ripup of an existing via
      if (!this.ctrl.ripupAllowed) {
        return;
      }
      Item currentObstacleItem = room.getItem();
      if (!(currentObstacleItem instanceof Via)) {
        return;
      }
      Padstack currentObstaclePadstack = ((Via) currentObstacleItem).getPadstack();
      if (!this.ctrl.viaRule.containsPadstack(currentObstaclePadstack)
          || currentObstacleItem.clearanceClassNo() != this.ctrl.viaClearanceClass) {
        return;
      }
      viaLowerBound = currentObstaclePadstack.fromLayer();
      viaUpperBound = currentObstaclePadstack.toLayer();
      roomRipped = true;
    } else {
      int[] netNoArr = new int[1];
      netNoArr[0] = ctrl.netNo;

      roomRipped = false;
      int viaLowerLimit = Math.max(currentDrill.firstLayer, ctrl.viaLowerBound);
      final int viaUpperLimit = Math.min(currentDrill.lastLayer, ctrl.viaUpperBound);
      // Calculate the lower bound of possible vias.
      int currentLayer = fromLayer;
      for (; ; ) {
        TileShape currentRoomShape =
            currentDrill.roomArr[currentLayer - currentDrill.firstLayer].getShape();
        ForcedPadRouter.CheckDrillResult drillResult =
            checkLayerWithAnyMatchingVia(currentDrill, currentLayer, currentRoomShape, netNoArr);
        if (drillResult == ForcedPadRouter.CheckDrillResult.NOT_DRILLABLE) {
          viaLowerBound = currentLayer + 1;
          break;
        } else if (drillResult == ForcedPadRouter.CheckDrillResult.DRILLABLE_WITH_ATTACH_SMD) {
          if (currentLayer == 0) {
            smdAttachedOnComponentSide = true;
          } else if (currentLayer == ctrl.layerCount - 1) {
            smdAttachedOnSolderSide = true;
          }
        }
        if (currentLayer <= viaLowerLimit) {
          viaLowerBound = viaLowerLimit;
          break;
        }
        --currentLayer;
      }
      if (viaLowerBound > currentDrill.firstLayer) {
        return;
      }
      currentLayer = fromLayer + 1;
      for (; ; ) {
        if (currentLayer > viaUpperLimit) {
          viaUpperBound = viaUpperLimit;
          break;
        }
        TileShape currentRoomShape =
            currentDrill.roomArr[currentLayer - currentDrill.firstLayer].getShape();
        ForcedPadRouter.CheckDrillResult drillResult =
            checkLayerWithAnyMatchingVia(currentDrill, currentLayer, currentRoomShape, netNoArr);
        if (drillResult == ForcedPadRouter.CheckDrillResult.NOT_DRILLABLE) {
          viaUpperBound = currentLayer - 1;
          break;
        } else if (drillResult == ForcedPadRouter.CheckDrillResult.DRILLABLE_WITH_ATTACH_SMD) {
          if (currentLayer == ctrl.layerCount - 1) {
            smdAttachedOnSolderSide = true;
          }
        }
        ++currentLayer;
      }
      if (viaUpperBound < currentDrill.lastLayer) {
        return;
      }
    }

    for (int toLayer = viaLowerBound; toLayer <= viaUpperBound; toLayer++) {
      if (toLayer == fromLayer) {
        continue;
      }
      // check, there is a fitting via mask.
      int currentFirstLayer;
      int currentLastLayer;
      if (toLayer < fromLayer) {
        currentFirstLayer = toLayer;
        currentLastLayer = fromLayer;
      } else {
        currentFirstLayer = fromLayer;
        currentLastLayer = toLayer;
      }
      boolean maskFound = false;
      for (int i = 0; i < ctrl.viaInfoArr.length; i++) {
        AutorouteControl.ViaMask currentViaInfo = ctrl.viaInfoArr[i];
        if (currentFirstLayer >= currentViaInfo.fromLayer
            && currentLastLayer <= currentViaInfo.toLayer
            && currentViaInfo.fromLayer >= viaLowerBound
            && currentViaInfo.toLayer <= viaUpperBound) {
          boolean maskOk = true;
          if (currentViaInfo.fromLayer == 0 && smdAttachedOnComponentSide
              || currentViaInfo.toLayer == ctrl.layerCount - 1 && smdAttachedOnSolderSide) {
            maskOk = currentViaInfo.attachSmdAllowed;
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
      MazeSearchElement currentDrillLayerInfo =
          currentDrill.getMazeSearchElement(toLayer - currentDrill.firstLayer);
      if (currentDrillLayerInfo.isOccupied) {
        continue;
      }
      double expansionValue =
          listElement.expansionValue + ctrl.addViaCosts[fromLayer].toLayer[toLayer];
      FloatPoint shapeEntryMiddle = listElement.shapeEntry.a.middlePoint(listElement.shapeEntry.b);
      double sortingValue =
          expansionValue + this.destinationDistance.calculate(shapeEntryMiddle, toLayer);
      int currentRoomIndex = toLayer - currentDrill.firstLayer;
      MazeListElement newElement =
          new MazeListElement(
              currentDrill,
              currentRoomIndex,
              currentDrill,
              listElement.sectionNoOfDoor,
              expansionValue,
              sortingValue,
              currentDrill.roomArr[currentRoomIndex],
              listElement.shapeEntry,
              roomRipped,
              MazeSearchElement.Adjustment.NONE,
              false);
      this.mazeExpansionList.add(newElement);
    }
  }

  private ForcedPadRouter.CheckDrillResult checkLayerWithAnyMatchingVia(
      ExpansionDrill drill, int layer, TileShape roomShape, int[] netNoArr) {
    boolean drillableWithAttachSmd = false;
    for (int i = 0; i < this.ctrl.viaRule.viaCount(); i++) {
      ViaInfo viaInfo = this.ctrl.viaRule.getVia(i);
      Padstack viaPadstack = viaInfo.getPadstack();
      if (layer < viaPadstack.fromLayer() || layer > viaPadstack.toLayer()) {
        continue;
      }
      ConvexShape viaShape = viaPadstack.getShape(layer);
      double viaRadius = viaShape == null ? 0 : 0.5 * viaShape.maxWidth();
      double requiredRadius = Math.max(viaRadius, this.ctrl.traceHalfWidth[layer]);
      ForcedPadRouter.CheckDrillResult result =
          ForcedViaInserter.checkLayer(
              requiredRadius,
              viaInfo.getClearanceClass(),
              viaInfo.attachSmdAllowed(),
              roomShape,
              drill.location,
              layer,
              netNoArr,
              this.ctrl.maxShoveTraceRecursionDepth,
              0,
              this.autorouteEngine.board,
              this.ctrl.traceHalfWidth[layer],
              this.ctrl.traceClearanceClassNo);
      if (result == ForcedPadRouter.CheckDrillResult.DRILLABLE) {
        return result;
      }
      if (result == ForcedPadRouter.CheckDrillResult.DRILLABLE_WITH_ATTACH_SMD) {
        drillableWithAttachSmd = true;
      }
    }
    return drillableWithAttachSmd
        ? ForcedPadRouter.CheckDrillResult.DRILLABLE_WITH_ATTACH_SMD
        : ForcedPadRouter.CheckDrillResult.NOT_DRILLABLE;
  }

  /** Initializes the maze search algorithm. Returns false if the initialisation failed. */
  private boolean init(Set<Item> startItems, Set<Item> destinationItems) {
    reduceTraceShapesAtTiePins(startItems, this.ctrl.netNo, this.searchTree);
    reduceTraceShapesAtTiePins(destinationItems, this.ctrl.netNo, this.searchTree);
    // process the destination items
    boolean destinationOk = false;
    for (Item currentItem : destinationItems) {
      if (this.autorouteEngine.isStopRequested()) {
        return false;
      }
      ItemAutorouteInfo currentInfo = currentItem.getAutorouteInfo();
      currentInfo.setStartInfo(false);
      for (int i = 0; i < currentItem.treeShapeCount(this.searchTree); i++) {
        TileShape currentTreeShape = currentItem.getTreeShape(this.searchTree, i);
        if (currentTreeShape != null) {
          destinationDistance.join(currentTreeShape.boundingBox(), currentItem.shapeLayer(i));
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
              + destinationItems.size()
              + ", isFanout: "
              + this.ctrl.isFanout
              + ")");
      return false;
    }
    // process the start items
    Collection<IncompleteFreeSpaceExpansionRoom> startRooms = new LinkedList<>();
    for (Item currentItem : startItems) {
      if (this.autorouteEngine.isStopRequested()) {
        return false;
      }
      ItemAutorouteInfo currentInfo = currentItem.getAutorouteInfo();
      currentInfo.setStartInfo(true);
      if (currentItem instanceof Connectable connectable) {
        for (int i = 0; i < currentItem.treeShapeCount(searchTree); i++) {
          TileShape containedShape = connectable.getTraceConnectionShape(searchTree, i);
          IncompleteFreeSpaceExpansionRoom newStartRoom =
              autorouteEngine.addIncompleteExpansionRoom(
                  null, currentItem.shapeLayer(i), containedShape);
          startRooms.add(newStartRoom);
        }
      }
    }

    // complete the start rooms
    Collection<CompleteFreeSpaceExpansionRoom> completedStartRooms = new LinkedList<>();

    if (this.autorouteEngine.maintainDatabase) {
      // add the completed start rooms carried over from the last autoroute to the
      // start rooms.
      completedStartRooms.addAll(this.autorouteEngine.getRoomsWithTargetItems(startItems));
    }

    for (IncompleteFreeSpaceExpansionRoom currentRoom : startRooms) {
      if (this.autorouteEngine.isStopRequested()) {
        return false;
      }
      Collection<CompleteFreeSpaceExpansionRoom> currentCompletedRooms =
          autorouteEngine.completeExpansionRoom(currentRoom);
      completedStartRooms.addAll(currentCompletedRooms);
    }

    // Put the ItemExpansionDoors of the completed start rooms into
    // the mazeExpansionList.
    boolean startOk = false;
    int expansionDoorsFound = 0;
    int expansionDoorsDestination = 0;
    for (CompleteFreeSpaceExpansionRoom currentRoom : completedStartRooms) {
      for (TargetItemExpansionDoor currentDoor : currentRoom.getTargetDoors()) {
        expansionDoorsFound++;
        if (this.autorouteEngine.isStopRequested()) {
          return false;
        }
        if (currentDoor.isDestinationDoor()) {
          expansionDoorsDestination++;
          continue;
        }
        TileShape connectionShape =
            ((Connectable) currentDoor.item)
                .getTraceConnectionShape(searchTree, currentDoor.treeEntryNo);
        connectionShape = connectionShape.intersection(currentDoor.room.getShape());
        FloatPoint currentCenter = connectionShape.centreOfGravity();
        FloatLine shapeEntry = new FloatLine(currentCenter, currentCenter);
        double sortingValue =
            this.destinationDistance.calculate(currentCenter, currentRoom.getLayer());
        MazeListElement newListElement =
            new MazeListElement(
                currentDoor,
                0,
                null,
                0,
                0,
                sortingValue,
                currentRoom,
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
              + startItems.size()
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

  private boolean roomShapeIsThick(ObstacleExpansionRoom obstacleRoom) {
    Item obstacleItem = obstacleRoom.getItem();
    int layer = obstacleRoom.getLayer();
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
  private int checkRipup(MazeListElement listElement, Item obstacleItem, boolean doorIsSmall) {
    if (!obstacleItem.isRoutable()) {
      return -1;
    }
    if (doorIsSmall) {
      // allow entering a via or trace, if its corresponding border segment is smaller
      // than the
      // current trace width

      if (!enterThroughSmallDoor(listElement, obstacleItem)) {
        return -1;
      }
    }
    CompleteExpansionRoom previousRoom = listElement.door.otherRoom(listElement.nextRoom);
    boolean roomWasShoved = listElement.adjustment != MazeSearchElement.Adjustment.NONE;
    Item previousItem = null;
    if (previousRoom instanceof ObstacleExpansionRoom room) {
      previousItem = room.getItem();
    }
    if (roomWasShoved) {
      if (previousItem != null
          && previousItem != obstacleItem
          && previousItem.sharesNet(obstacleItem)) {
        // The ripped trace may start at a fork.
        return -1;
      }
    } else if (previousItem == obstacleItem) {
      return ALREADY_RIPPED_COSTS;
    }

    double fanoutViaCostFactor = 1.0;
    double costFactor = 1;
    boolean preserveFanoutProtection =
        !this.ctrl.removeUnconnectedVias
            && this.ctrl.ripupCosts <= (this.ctrl.settings.getStartRipupCosts() * 2);
    if (obstacleItem instanceof Trace obstacleTrace) {
      costFactor = obstacleTrace.getHalfWidth();
      if (preserveFanoutProtection) {
        // protect traces between SMD-pins and fanout vias
        fanoutViaCostFactor = calcFanoutViaRipupCostFactor(obstacleTrace);
      }
    } else if (obstacleItem instanceof Via) {
      boolean lookIfFanoutVia = preserveFanoutProtection;
      Collection<Item> contactList = obstacleItem.getNormalContacts();
      int contactCount = 0;
      for (Item currentContact : contactList) {
        if (!(currentContact instanceof Trace obstacleTrace) || currentContact.isUserFixed()) {
          return -1;
        }
        ++contactCount;
        costFactor = Math.max(costFactor, obstacleTrace.getHalfWidth());
        if (lookIfFanoutVia && !this.ctrl.isFanout) {
          double currentFanoutViaCostFactor = calcFanoutViaRipupCostFactor(obstacleTrace);
          if (currentFanoutViaCostFactor > 1) {
            fanoutViaCostFactor = currentFanoutViaCostFactor;
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
    if (fanoutViaCostFactor <= 1 && !this.ctrl.isFanout) {
      // obstacle item does not belong to a fanout, and not during fanout pass
      Connection obstacleConnection = Connection.get(obstacleItem);
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
    if (obstacleItem instanceof app.freerouting.board.Item obstacleBoardItem) {
      int[] nets = new int[obstacleBoardItem.netCount()];
      for (int i = 0; i < nets.length; i++) {
        nets[i] = obstacleBoardItem.getNetNo(i);
      }
      obstacleNets = java.util.Arrays.toString(nets);
    }
    FRLogger.trace(
        "CHECK_RIPUP net="
            + ctrl.netNo
            + ", obstacle_id="
            + (obstacleItem instanceof app.freerouting.board.Item obstItem
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
  private boolean shoveTraceRoom(MazeListElement listElement, ObstacleExpansionRoom obstacleRoom) {
    if (listElement.sectionNoOfDoor != 0
        && listElement.sectionNoOfDoor != listElement.door.mazeSearchElementCount() - 1) {
      // No delay of occupation necessary because inner sections of a door are
      // currently not
      // shoved.
      return true;
    }
    boolean result = false;
    if (listElement.adjustment != MazeSearchElement.Adjustment.RIGHT) {
      Collection<MazeShoveTraceAlgo.DoorSection> leftToDoorSectionList = new LinkedList<>();

      if (MazeShoveTraceAlgo.checkShoveTraceLine(
          listElement,
          obstacleRoom,
          this.autorouteEngine.board,
          this.ctrl,
          false,
          leftToDoorSectionList)) {
        result = true;
      }

      for (MazeShoveTraceAlgo.DoorSection currentLeftDoorSection : leftToDoorSectionList) {
        MazeSearchElement.Adjustment currentAdjustment;
        if (currentLeftDoorSection.door.dimension == 2) {
          // the door is the link door to the next room
          currentAdjustment = MazeSearchElement.Adjustment.LEFT;
        } else {
          currentAdjustment = MazeSearchElement.Adjustment.NONE;
        }

        expandToDoorSection(
            currentLeftDoorSection.door,
            currentLeftDoorSection.sectionNo,
            currentLeftDoorSection.sectionLine,
            listElement,
            0,
            currentAdjustment);
      }
    }

    if (listElement.adjustment != MazeSearchElement.Adjustment.LEFT) {
      Collection<MazeShoveTraceAlgo.DoorSection> rightToDoorSectionList = new LinkedList<>();

      if (MazeShoveTraceAlgo.checkShoveTraceLine(
          listElement,
          obstacleRoom,
          this.autorouteEngine.board,
          this.ctrl,
          true,
          rightToDoorSectionList)) {
        result = true;
      }
      for (MazeShoveTraceAlgo.DoorSection currentRightDoorSection : rightToDoorSectionList) {
        MazeSearchElement.Adjustment currentAdjustment;
        if (currentRightDoorSection.door.dimension == 2) {
          // the door is the link door to the next room
          currentAdjustment = MazeSearchElement.Adjustment.RIGHT;
        } else {
          currentAdjustment = MazeSearchElement.Adjustment.NONE;
        }
        expandToDoorSection(
            currentRightDoorSection.door,
            currentRightDoorSection.sectionNo,
            currentRightDoorSection.sectionLine,
            listElement,
            0,
            currentAdjustment);
      }
    }
    return result;
  }

  /**
   * Checks, if the next room contains a destination pin, where evtl. neckdown is necessary. Return
   * the neck down width in this case, or 0, if no such pin was found,
   */
  private double checkNeckDownAtDestPin(CompleteExpansionRoom room) {
    Collection<TargetItemExpansionDoor> targetDoors = room.getTargetDoors();
    for (TargetItemExpansionDoor currentTargetDoor : targetDoors) {
      if (currentTargetDoor.item instanceof Pin pin) {
        return pin.getTraceNeckdownHalfwidth(room.getLayer());
      }
    }
    return 0;
  }

  /**
   * Checks, if the next room can be entered if the door of listElement is small. If ignoreItem !=
   * null, ignoreItem and all other items directly connected to ignoreItem are ignored in the check.
   */
  private boolean enterThroughSmallDoor(MazeListElement listElement, Item ignoreItem) {
    if (listElement.door.getDimension() != 1) {
      return false;
    }
    TileShape doorShape = listElement.door.getShape();

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
    int currentLayer = listElement.nextRoom.getLayer();
    int checkRadius =
        this.ctrl.compensatedTraceHalfWidth[currentLayer] + AutorouteEngine.TRACE_WIDTH_TOLERANCE;
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
        checkShape, currentLayer, ignoreNetNos, overlappingObjects);

    for (SearchTreeObject currentObject : overlappingObjects) {
      if (!(currentObject instanceof Item currentItem) || currentObject == ignoreItem) {
        continue;
      }
      if (!currentItem.sharesNet(ignoreItem)) {
        return false;
      }
      Set<Item> currentContacts = currentItem.getNormalContacts();
      if (!currentContacts.contains(ignoreItem)) {
        return false;
      }
    }
    return true;
  }

  /** Checks entering a thick room from a via or trace through a small door (after ripup). */
  private boolean checkLeavingRippedItem(MazeListElement listElement) {
    if (!(listElement.door instanceof ExpansionDoor currentDoor)) {
      return false;
    }
    CompleteExpansionRoom fromRoom = currentDoor.otherRoom(listElement.nextRoom);
    if (!(fromRoom instanceof ObstacleExpansionRoom)) {
      return false;
    }
    Item currentItem = ((ObstacleExpansionRoom) fromRoom).getItem();
    if (!currentItem.isRoutable()) {
      return false;
    }
    return enterThroughSmallDoor(listElement, currentItem);
  }

  /** The result type of MazeSearchAlgo.find_connection. */
  public static class Result {

    public final ExpandableObject destinationDoor;
    public final int sectionNoOfDoor;

    Result(ExpandableObject destinationDoor, int sectionNoOfDoor) {
      this.destinationDoor = destinationDoor;
      this.sectionNoOfDoor = sectionNoOfDoor;
    }
  }

  /**
   * Used for the result of MazeShoveViaAlgo.check_shove_via and.
   * MazeShoveThinRoomAlgo.check_shove_thin_room.
   */
  static class ShoveResult {

    /** The opposite door to be expanded. */
    final ExpansionDoor oppositeDoor;

    /** The doors at the adjusted edge of the room shape to be expanded. */
    final Collection<ExpansionDoor> sideDoors;

    /** The passing point of a trace through the from_door after adjustment. */
    final FloatPoint fromDoorPassingPoint;

    /** The passing point of a trace through the opposite door after adjustment. */
    final FloatPoint oppositeDoorPassingPoint;

    ShoveResult(
        ExpansionDoor oppositeDoor,
        Collection<ExpansionDoor> sideDoors,
        FloatPoint fromDoorPassingPoint,
        FloatPoint oppositeDoorPassingPoint) {
      this.oppositeDoor = oppositeDoor;
      this.sideDoors = sideDoors;
      this.fromDoorPassingPoint = fromDoorPassingPoint;
      this.oppositeDoorPassingPoint = oppositeDoorPassingPoint;
    }
  }
}
