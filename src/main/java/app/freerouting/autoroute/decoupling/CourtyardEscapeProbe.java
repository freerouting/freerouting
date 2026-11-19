package app.freerouting.autoroute.decoupling;

import app.freerouting.autoroute.expansion.CompleteFreeSpaceExpansionRoom;
import app.freerouting.autoroute.expansion.ExpansionDoor;
import app.freerouting.autoroute.expansion.ExpansionRoom;
import app.freerouting.autoroute.expansion.IncompleteFreeSpaceExpansionRoom;
import app.freerouting.autoroute.maze.AutorouteEngine;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Verifies that placing a decoupling strap does not trap adjacent unrouted SMD signal pins on the
 * IC or decoupling capacitor.
 *
 * <p>A simple {@code doorCount > 0} check can produce false-negatives because a pin can have an
 * expansion door leading into a sealed dead-end pocket between the strap and the IC body. This
 * probe executes a bounded breadth-first search (up to {@code k = 8} expansion rooms) to verify
 * that each adjacent unrouted signal pin can either:
 *
 * <ol>
 *   <li>Reach a free-space expansion room extending outside the component's pad ring bounds, or
 *   <li>Drop a legal escape via without design rule clearance violations.
 * </ol>
 */
public final class CourtyardEscapeProbe {

  public static final int DEFAULT_MAX_ESCAPE_DEPTH = 8;
  public static final int MAX_VISITED_ROOMS = 64;

  private CourtyardEscapeProbe() {}

  /**
   * Probes all unrouted neighbor signal pins within {@code searchBox} on {@code layer}.
   *
   * @param board routing board context
   * @param ownerComponentId owner IC component ID
   * @param capComponentId capacitor component ID
   * @param searchBox bounding box around the newly routed strap expanded by clearance
   * @param layer routing layer
   * @param railNetNo power rail net number
   * @param groundNetNo ground net number
   * @param thread stoppable thread context
   * @return {@code true} if all adjacent signal pins can escape; {@code false} if any pin is
   *     trapped
   */
  public static boolean isEscapePossible(
      RoutingBoard board,
      int ownerComponentId,
      int capComponentId,
      IntBox searchBox,
      int layer,
      int railNetNo,
      int groundNetNo,
      StoppableThread thread) {
    if (board == null || searchBox == null) {
      return true;
    }

    IntBox padBounds = computeComponentPadBounds(board, ownerComponentId);
    if (padBounds == null) {
      return true;
    }

    List<Pin> neighborPins =
        findNeighborSignalPins(
            board, searchBox, layer, ownerComponentId, capComponentId, railNetNo, groundNetNo);

    if (neighborPins.isEmpty()) {
      return true;
    }

    for (Pin neighborPin : neighborPins) {
      if ((thread != null && thread.isStopRequested()) || Thread.currentThread().isInterrupted()) {
        return false;
      }

      boolean escaped =
          canPinEscape(board, neighborPin, padBounds, layer, DEFAULT_MAX_ESCAPE_DEPTH, thread);
      if (!escaped) {
        if (FRLogger.isTraceEnabled()) {
          FRLogger.trace(
              "CourtyardEscapeProbe: pin "
                  + neighborPin
                  + " on comp "
                  + neighborPin.getComponentId()
                  + " trapped by decoupling strap");
        }
        return false;
      }
    }

    return true;
  }

  /** Computes the bounding box enclosing all pins belonging to the given component. */
  public static IntBox computeComponentPadBounds(RoutingBoard board, int componentId) {
    if (componentId <= 0) {
      return null;
    }
    Collection<Pin> pins = board.getComponentPins(componentId);
    if (pins == null || pins.isEmpty()) {
      return null;
    }
    IntBox bounds = null;
    for (Pin pin : pins) {
      bounds = (bounds == null) ? pin.boundingBox() : bounds.union(pin.boundingBox());
    }
    return bounds;
  }

  /**
   * Identifies unrouted SMD signal pins on the given layer and components within {@code searchBox}.
   */
  public static List<Pin> findNeighborSignalPins(
      RoutingBoard board,
      IntBox searchBox,
      int layer,
      int ownerComponentId,
      int capComponentId,
      int railNetNo,
      int groundNetNo) {
    List<Pin> result = new ArrayList<>();
    Set<SearchTreeObject> objs =
        board.searchTreeManager.getDefaultTree().overlappingObjects(searchBox, layer);

    for (SearchTreeObject obj : objs) {
      if (obj instanceof Pin pin) {
        int compId = pin.getComponentId();
        if (compId == ownerComponentId || compId == capComponentId) {
          if (pin.firstLayer() <= layer && pin.lastLayer() >= layer) {
            if (pin.netCount() == 0 || pin.containsNet(railNetNo) || pin.containsNet(groundNetNo)) {
              continue;
            }
            if (BoardStatistics.isPinEscaped(pin)) {
              continue;
            }
            result.add(pin);
          }
        }
      }
    }

    return result;
  }

  /**
   * Performs a bounded BFS traversal up to {@code maxDepth} expansion rooms to verify pin escape.
   */
  public static boolean canPinEscape(
      RoutingBoard board,
      Pin pin,
      IntBox padBounds,
      int layer,
      int maxDepth,
      StoppableThread thread) {
    if (padBounds == null || pin.netCount() == 0) {
      return true;
    }
    int pinNetNo = pin.getNetNumber(0);
    int clearanceClassIndex = pin.clearanceClassIndex();

    try {
      AutorouteEngine autorouteEngine =
          board.initAutoroute(pinNetNo, clearanceClassIndex, thread, null, false);
      TileShape containedShape =
          pin.getTraceConnectionShape(autorouteEngine.autorouteSearchTree, 0);
      if (containedShape == null || containedShape.isEmpty()) {
        return true;
      }

      IncompleteFreeSpaceExpansionRoom startRoom =
          autorouteEngine.addIncompleteExpansionRoom(null, layer, containedShape);
      Collection<CompleteFreeSpaceExpansionRoom> completed =
          autorouteEngine.completeExpansionRoom(startRoom);

      Queue<CompleteFreeSpaceExpansionRoom> queue = new ArrayDeque<>(completed);
      Set<Integer> visitedRoomIds = new HashSet<>();
      for (CompleteFreeSpaceExpansionRoom r : completed) {
        visitedRoomIds.add(r.getId());
      }

      int depth = 0;
      while (!queue.isEmpty() && depth < maxDepth && visitedRoomIds.size() < MAX_VISITED_ROOMS) {
        if ((thread != null && thread.isStopRequested())
            || Thread.currentThread().isInterrupted()) {
          return false;
        }

        int levelSize = queue.size();
        for (int i = 0; i < levelSize; i++) {
          CompleteFreeSpaceExpansionRoom currentRoom = queue.poll();
          if (currentRoom == null) {
            continue;
          }

          // Check 1: Room extends outside the component pad ring
          IntBox roomBox = currentRoom.getShape().boundingBox();
          if (padBounds != null
              && (roomBox.ll.x < padBounds.ll.x
                  || roomBox.ur.x > padBounds.ur.x
                  || roomBox.ll.y < padBounds.ll.y
                  || roomBox.ur.y > padBounds.ur.y)) {
            return true;
          }

          // Check 2: Can drop a legal via
          app.freerouting.rules.Net net = pinNetNo > 0 ? board.rules.nets.get(pinNetNo) : null;
          boolean hasViaRule =
              net != null && net.getNetClass() != null && net.getNetClass().getViaRule() != null;
          if (hasViaRule && autorouteEngine.probeLegalViaPlacement(currentRoom.getShape())) {
            return true;
          }

          // Expand neighbor rooms through doors
          for (ExpansionDoor door : currentRoom.getDoors()) {
            ExpansionRoom next = door.otherRoom(currentRoom);
            if (next instanceof CompleteFreeSpaceExpansionRoom nextComplete) {
              if (visitedRoomIds.add(nextComplete.getId())) {
                queue.add(nextComplete);
              }
            } else if (next instanceof IncompleteFreeSpaceExpansionRoom nextIncomplete) {
              Collection<CompleteFreeSpaceExpansionRoom> newlyCompleted =
                  autorouteEngine.completeExpansionRoom(nextIncomplete);
              for (CompleteFreeSpaceExpansionRoom nr : newlyCompleted) {
                if (visitedRoomIds.add(nr.getId())) {
                  queue.add(nr);
                }
              }
            }
          }
        }
        depth++;
      }
    } finally {
      board.clearTransientAutorouteState();
    }

    return false;
  }
}
