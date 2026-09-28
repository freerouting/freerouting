package app.freerouting.autoroute.maze;

import app.freerouting.autoroute.drill.DrillPage;
import app.freerouting.autoroute.drill.ExpansionDrill;
import app.freerouting.autoroute.expansion.CompleteExpansionRoom;
import app.freerouting.autoroute.expansion.ExpandableObject;
import app.freerouting.autoroute.expansion.ObstacleExpansionRoom;
import app.freerouting.board.actions.ForcedPadRouter;
import app.freerouting.board.actions.ForcedViaInserter;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Via;
import app.freerouting.core.library.Padstack;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.ViaInfo;
import java.util.Collection;

/** Expands drill pages, existing vias, and candidate via layers for a maze search. */
final class MazeExpansionEngine {

  private final MazeSearchEngine search;

  MazeExpansionEngine(MazeSearchEngine search) {
    this.search = search;
  }

  void expandToDrill(ExpansionDrill drill, MazeListElement fromElement, int addCosts) {
    AutorouteControl ctrl = search.ctrl;
    int layer = fromElement.nextRoom.getLayer();
    int traceHalfWidth = ctrl.compensatedTraceHalfWidth[layer];
    boolean roomShapeIsThin = fromElement.nextRoom.getShape().minWidth() < 2 * traceHalfWidth;

    if (roomShapeIsThin
        && (fromElement.backtrackDoor == null
            || !drill.getShape().intersects(fromElement.backtrackDoor.getShape()))) {
      search.fanoutDiagnostics.trace(
          "drill_rejected_thin_room_no_backtrack_intersection",
          "drill="
              + MazeSearchEngine.describeExpandable(drill)
              + ", from_door="
              + MazeSearchEngine.describeExpandable(fromElement.door)
              + ", backtrack="
              + MazeSearchEngine.describeExpandable(fromElement.backtrackDoor)
              + ", room="
              + MazeSearchEngine.describeRoom(fromElement.nextRoom));
      return;
    }

    double viaRadius = ctrl.viaRadii[layer];
    ConvexShape shrinkedDrillShape = drill.getShape().shrink(viaRadius);
    FloatPoint compareCorner = fromElement.shapeEntry.a.middlePoint(fromElement.shapeEntry.b);
    if (fromElement.door instanceof DrillPage
        && fromElement.backtrackDoor
            instanceof app.freerouting.autoroute.expansion.TargetItemExpansionDoor door
        && door.item instanceof Pin pin) {
      FloatPoint nearestExitCorner =
          pin.nearestTraceExitCorner(drill.location.toFloat(), traceHalfWidth, layer);
      if (nearestExitCorner != null) {
        compareCorner = nearestExitCorner;
      }
    }
    FloatPoint nearestPoint = shrinkedDrillShape.nearestPointApprox(compareCorner);
    FloatLine shapeEntry = new FloatLine(nearestPoint, nearestPoint);
    int sectionIndex = layer - drill.firstLayer;
    double expansionValue =
        fromElement.expansionValue
            + addCosts
            + nearestPoint.weightedDistance(
                compareCorner,
                ctrl.traceCosts[layer].horizontal(),
                ctrl.traceCosts[layer].vertical());
    ExpandableObject newBacktrackDoor;
    int newSectionNoOfBacktrackDoor;
    if (fromElement.door instanceof DrillPage) {
      newBacktrackDoor = fromElement.backtrackDoor;
      newSectionNoOfBacktrackDoor = fromElement.sectionNoOfBacktrackDoor;
    } else {
      newBacktrackDoor = fromElement.door;
      newSectionNoOfBacktrackDoor = fromElement.sectionNoOfDoor;
      expansionValue += ctrl.minNormalViaCost;
    }
    double sortingValue =
        expansionValue + search.destinationDistance.calculate(nearestPoint, layer);
    MazeListElement newElement =
        new MazeListElement(
            drill,
            sectionIndex,
            newBacktrackDoor,
            newSectionNoOfBacktrackDoor,
            expansionValue,
            sortingValue,
            null,
            shapeEntry,
            fromElement.roomRipped,
            MazeSearchElement.Adjustment.NONE,
            false);
    search.mazeExpansionList.add(newElement);
    search.fanoutDiagnostics.trace(
        "drill_accepted",
        "drill="
            + MazeSearchEngine.describeExpandable(drill)
            + ", room="
            + MazeSearchEngine.describeRoom(fromElement.nextRoom)
            + ", nearestPoint="
            + nearestPoint
            + ", expansionValue="
            + expansionValue);
  }

  /** Inserts a drill page between a room and its candidate drills. */
  void expandToDrillPage(DrillPage drillPage, MazeListElement fromElement) {
    AutorouteControl ctrl = search.ctrl;
    int layer = fromElement.nextRoom.getLayer();
    FloatPoint fromElementShapeEntryMiddle =
        fromElement.shapeEntry.a.middlePoint(fromElement.shapeEntry.b);
    FloatPoint nearestPoint = drillPage.shape.nearestPoint(fromElementShapeEntryMiddle);
    double expansionValue = fromElement.expansionValue + ctrl.minNormalViaCost;
    double sortingValue =
        expansionValue
            + nearestPoint.weightedDistance(
                fromElementShapeEntryMiddle,
                ctrl.traceCosts[layer].horizontal(),
                ctrl.traceCosts[layer].vertical())
            + search.destinationDistance.calculate(nearestPoint, layer);
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
    search.mazeExpansionList.add(newElement);
  }

  void expandToDrillsOfPage(MazeListElement fromElement) {
    AutorouteControl ctrl = search.ctrl;
    int fromRoomLayer = fromElement.sectionNoOfDoor;
    DrillPage drillPage = (DrillPage) fromElement.door;
    Collection<ExpansionDrill> drillList =
        drillPage.getDrills(search.autorouteEngine, ctrl.attachSmdAllowed);
    if (search.fanoutDiagnostics.enabled()) {
      search.fanoutDiagnostics.trace(
          "drill_page_scan",
          "candidate_count="
              + drillList.size()
              + ", attachSmdAllowed="
              + ctrl.attachSmdAllowed
              + ", room="
              + MazeSearchEngine.describeRoom(fromElement.nextRoom)
              + ", from_door="
              + MazeSearchEngine.describeExpandable(fromElement.door));
      if (drillList.isEmpty()) {
        search.fanoutDiagnostics.trace("drill_page_empty", "no_candidates=true");
      }
    }
    boolean firstMismatchLogged = false;
    for (ExpansionDrill currentDrill : drillList) {
      int sectionIndex = fromRoomLayer - currentDrill.firstLayer;
      if (sectionIndex < 0 || sectionIndex >= currentDrill.roomArr.length) {
        search.fanoutDiagnostics.trace(
            "drill_rejected_section_out_of_range",
            "drill="
                + MazeSearchEngine.describeExpandable(currentDrill)
                + ", section="
                + sectionIndex
                + ", room_arr_len="
                + currentDrill.roomArr.length);
        continue;
      }
      if (currentDrill.roomArr[sectionIndex] != fromElement.nextRoom) {
        search.fanoutDiagnostics.trace(
            "drill_rejected_room_mismatch",
            "drill="
                + MazeSearchEngine.describeExpandable(currentDrill)
                + ", expected_room="
                + MazeSearchEngine.describeRoom(fromElement.nextRoom)
                + ", drill_room="
                + MazeSearchEngine.describeRoom(currentDrill.roomArr[sectionIndex]));
        if (!firstMismatchLogged && search.fanoutDiagnostics.enabled()) {
          firstMismatchLogged = true;
          CompleteExpansionRoom expansionRoom = fromElement.nextRoom;
          CompleteExpansionRoom drillRoom = currentDrill.roomArr[sectionIndex];
          FRLogger.trace(
              "FANOUT_DIAG event=first_room_mismatch_detail"
                  + ", pin="
                  + search.fanoutDiagnostics.labelForLog()
                  + ", net="
                  + ctrl.netNumber
                  + ", drillLocation="
                  + currentDrill.location
                  + ", expansion_room_id="
                  + System.identityHashCode(expansionRoom)
                  + ", expansion_room_bounds="
                  + (expansionRoom != null ? expansionRoom.getShape() : "null")
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
                  + ", sectionIndex="
                  + sectionIndex
                  + ", layer="
                  + fromRoomLayer);
        }
        continue;
      }
      if (currentDrill.getMazeSearchElement(sectionIndex).isOccupied) {
        search.fanoutDiagnostics.trace(
            "drill_rejected_section_occupied",
            "drill="
                + MazeSearchEngine.describeExpandable(currentDrill)
                + ", section="
                + sectionIndex);
        continue;
      }
      expandToDrill(currentDrill, fromElement, 0);
    }
  }

  void expandToOtherLayers(MazeListElement listElement) {
    AutorouteControl ctrl = search.ctrl;
    int viaLowerBound = 0;
    int viaUpperBound = -1;
    ExpansionDrill currentDrill = (ExpansionDrill) listElement.door;
    int fromLayer = currentDrill.firstLayer + listElement.sectionNoOfDoor;
    boolean smdAttachedOnComponentSide = false;
    boolean smdAttachedOnSolderSide = false;
    boolean roomRipped;
    if (currentDrill.roomArr[listElement.sectionNoOfDoor] instanceof ObstacleExpansionRoom room) {
      if (!ctrl.ripupAllowed) {
        return;
      }
      Item currentObstacleItem = room.getItem();
      if (!(currentObstacleItem instanceof Via)) {
        return;
      }
      Padstack currentObstaclePadstack = ((Via) currentObstacleItem).getPadstack();
      if (!ctrl.viaRule.containsPadstack(currentObstaclePadstack)
          || currentObstacleItem.clearanceClassIndex() != ctrl.viaClearanceClass) {
        return;
      }
      viaLowerBound = currentObstaclePadstack.fromLayer();
      viaUpperBound = currentObstaclePadstack.toLayer();
      roomRipped = true;
    } else {
      int[] netNumbers = new int[] {ctrl.netNumber};
      roomRipped = false;
      int viaLowerLimit = Math.max(currentDrill.firstLayer, ctrl.viaLowerBound);
      int viaUpperLimit = Math.min(currentDrill.lastLayer, ctrl.viaUpperBound);
      int currentLayer = fromLayer;
      for (; ; ) {
        TileShape currentRoomShape =
            currentDrill.roomArr[currentLayer - currentDrill.firstLayer].getShape();
        ForcedPadRouter.CheckDrillResult drillResult =
            checkLayerWithAnyMatchingVia(currentDrill, currentLayer, currentRoomShape, netNumbers);
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
            checkLayerWithAnyMatchingVia(currentDrill, currentLayer, currentRoomShape, netNumbers);
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
      for (AutorouteControl.ViaMask currentViaInfo : ctrl.viaInfos) {
        if (currentFirstLayer >= currentViaInfo.fromLayer
            && currentLastLayer <= currentViaInfo.toLayer
            && currentViaInfo.fromLayer >= viaLowerBound
            && currentViaInfo.toLayer <= viaUpperBound) {
          boolean maskOk =
              !(currentViaInfo.fromLayer == 0 && smdAttachedOnComponentSide
                      || currentViaInfo.toLayer == ctrl.layerCount - 1 && smdAttachedOnSolderSide)
                  || currentViaInfo.attachSmdAllowed;
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
          expansionValue + search.destinationDistance.calculate(shapeEntryMiddle, toLayer);
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
      search.mazeExpansionList.add(newElement);
    }
  }

  private ForcedPadRouter.CheckDrillResult checkLayerWithAnyMatchingVia(
      ExpansionDrill drill, int layer, TileShape roomShape, int[] netNumbers) {
    AutorouteControl ctrl = search.ctrl;
    boolean drillableWithAttachSmd = false;
    for (int i = 0; i < ctrl.viaRule.viaCount(); i++) {
      ViaInfo viaInfo = ctrl.viaRule.getVia(i);
      Padstack viaPadstack = viaInfo.getPadstack();
      if (layer < viaPadstack.fromLayer() || layer > viaPadstack.toLayer()) {
        continue;
      }
      ConvexShape viaShape = viaPadstack.getShape(layer);
      double viaRadius = viaShape == null ? 0 : 0.5 * viaShape.maxWidth();
      double requiredRadius = Math.max(viaRadius, ctrl.traceHalfWidth[layer]);
      ForcedPadRouter.CheckDrillResult result =
          ForcedViaInserter.checkLayer(
              requiredRadius,
              viaInfo.getClearanceClassIndex(),
              viaInfo.attachSmdAllowed(),
              roomShape,
              drill.location,
              layer,
              netNumbers,
              ctrl.maxShoveTraceRecursionDepth,
              0,
              search.autorouteEngine.board,
              ctrl.traceHalfWidth[layer],
              ctrl.traceClearanceClassIndex);
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
}
