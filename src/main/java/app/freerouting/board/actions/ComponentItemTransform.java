package app.freerouting.board.actions;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.model.structure.Component;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Vector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Validates a transform of detached editor items before committing their component poses. */
public final class ComponentItemTransform {
  private ComponentItemTransform() {}

  /**
   * Returns transformed copies, or null when quantization cannot preserve topology and design
   * rules. Items must be detached from the board and its search trees. The caller owns insertion
   * and its existing undo snapshot. Plane contacts are intentionally not constraints on a move.
   */
  public static List<Item> apply(
      BasicBoard board, Collection<Item> detached, double angle, boolean mirror, IntPoint pole) {
    List<Item> old = new ArrayList<>(detached);
    if (old.stream().anyMatch(Item::isOnTheBoard)) {
      throw new IllegalArgumentException("Transform requires detached items");
    }
    if (!Double.isFinite(angle)) {
      throw new IllegalArgumentException("Rotation must be finite");
    }
    if (mirror) {
      for (Item item : old) {
        if (item instanceof Via via
            && board.library.getMirroredViaPadstack(via.getPadstack()) == null) {
          return null;
        }
        if (item.firstLayer() == item.lastLayer()
            && !board
                .layerStructure
                .layers[board.getLayerCount() - item.firstLayer() - 1]
                .isSignal) {
          return null;
        }
      }
    }
    if (old.stream().anyMatch(Item::isUserFixed)) {
      return null;
    }
    Map<Integer, Component> poses = new TreeMap<>();
    for (Item item : old) {
      if (item.getComponentId() > 0) {
        Component component = board.components.get(item.getComponentId());
        if (component.positionFixed || !board.getComponentItems(component.id).isEmpty()) {
          return null;
        }
        poses.putIfAbsent(component.id, component.clone());
      }
    }
    Map<Integer, Set<Integer>> contacts;
    Set<String> violations;
    Map<String, Set<Integer>> endpointContacts;
    Map<Integer, Point> pinCenters = new HashMap<>();
    insert(board, old);
    try {
      contacts = contacts(old);
      endpointContacts = endpointContacts(old);
      violations = violations(old);
      for (Item item : old) {
        if (item instanceof Pin pin) {
          pinCenters.put(pin.getId(), pin.getCenter());
        }
      }
    } finally {
      remove(board, old);
    }
    List<Item> candidate = old.stream().map(item -> item.copy(item.getId())).toList();
    boolean accepted = false;
    try {
      for (int id : poses.keySet()) {
        transform(
            board.components.get(id),
            angle,
            mirror,
            pole,
            board.components.getFlipStyleRotateFirst());
      }
      for (Item item : candidate) {
        if (mirror) {
          item.changePlacementSide(pole);
        } else if (angle % 90 == 0) {
          item.turn90Degree((int) (angle / 90), pole);
        } else {
          item.rotateApprox(angle, pole.toFloat());
        }
      }
      if (!alignCopper(candidate, contacts, pinCenters, angle, mirror, pole)
          || !legalTraces(board, old, candidate)) {
        return null;
      }
      insert(board, candidate);
      try {
        Set<String> newViolations = violations(candidate);
        newViolations.removeAll(violations);
        accepted =
            contacts.equals(contacts(candidate))
                && endpointContacts.equals(endpointContacts(candidate))
                && newViolations.isEmpty();
      } finally {
        remove(board, candidate);
      }
    } finally {
      for (var entry : poses.entrySet()) {
        board.components.get(entry.getKey()).restorePose(entry.getValue());
      }
    }
    if (!accepted) {
      return null;
    }
    for (int id : poses.keySet()) {
      if (mirror) {
        board.components.changeSide(id, pole);
      } else if (angle % 90 == 0) {
        board.components.turn90Degree(id, (int) (angle / 90), pole);
      } else {
        board.components.rotate(id, angle, pole);
      }
    }
    return candidate;
  }

  private static void transform(
      Component component, double angle, boolean mirror, IntPoint pole, boolean rotateFirst) {
    if (mirror) {
      component.changeSide(pole, rotateFirst);
    } else if (angle % 90 == 0) {
      component.turn90Degree((int) (angle / 90), pole, rotateFirst);
    } else {
      component.rotate(angle, pole, rotateFirst);
    }
  }

  private static Point transformed(Point point, double angle, boolean mirror, IntPoint pole) {
    if (mirror) {
      return point.mirrorVertical(pole);
    }
    return angle % 90 == 0
        ? point.turn90Degree((int) (angle / 90), pole)
        : point.toFloat().rotate(Math.toRadians(angle), pole.toFloat()).round();
  }

  /** Translate each connected copper group uniformly; never distort a trace to close a gap. */
  private static boolean alignCopper(
      List<Item> items,
      Map<Integer, Set<Integer>> contacts,
      Map<Integer, Point> pinCenters,
      double angle,
      boolean mirror,
      IntPoint pole) {
    Map<Integer, Item> byId = new HashMap<>();
    items.forEach(item -> byId.put(item.getId(), item));
    Set<Integer> visited = new HashSet<>();
    for (Item seed : items) {
      if (!copper(seed) || !visited.add(seed.getId())) {
        continue;
      }
      List<Item> group = new ArrayList<>();
      group.add(seed);
      Vector adjustment = null;
      for (int i = 0; i < group.size(); i++) {
        for (int contactId : contacts.get(group.get(i).getId())) {
          Item contact = byId.get(contactId);
          if (contact instanceof Pin pin) {
            Vector required =
                pin.getCenter()
                    .differenceBy(transformed(pinCenters.get(contactId), angle, mirror, pole));
            if (adjustment != null && !adjustment.equals(required)) {
              return false;
            }
            adjustment = required;
          } else if (copper(contact) && visited.add(contactId)) {
            group.add(contact);
          }
        }
      }
      if (adjustment != null) {
        for (Item item : group) {
          item.translateBy(adjustment);
        }
      }
    }
    return true;
  }

  private static boolean copper(Item item) {
    return item instanceof PolylineTrace || item instanceof Via;
  }

  private static boolean legalTraces(BasicBoard board, List<Item> old, List<Item> candidate) {
    AngleRestriction restriction = board.rules.getTraceAngleRestriction();
    for (int i = 0; i < candidate.size(); i++) {
      if (candidate.get(i) instanceof PolylineTrace trace) {
        if (trace.cornerCount() != ((PolylineTrace) old.get(i)).cornerCount()) {
          return false;
        }
        for (int j = 1; j < trace.cornerCount(); j++) {
          var a = trace.polyline().corner(j - 1).toFloat();
          var b = trace.polyline().corner(j).toFloat();
          double dx = Math.abs(a.x - b.x);
          double dy = Math.abs(a.y - b.y);
          if (dx == 0 && dy == 0) {
            return false;
          }
          var previous = ((PolylineTrace) old.get(i)).polyline();
          var oldA = previous.corner(j - 1).toFloat();
          var oldB = previous.corner(j).toFloat();
          // Imported pin escapes may already be non-grid directions. Reject newly introduced
          // violations without preventing rigid moves of those existing escapes.
          if (!legalDirection(dx, dy, restriction)
              && legalDirection(
                  Math.abs(oldA.x - oldB.x), Math.abs(oldA.y - oldB.y), restriction)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private static boolean legalDirection(double dx, double dy, AngleRestriction restriction) {
    return restriction == AngleRestriction.NONE
        || dx == 0
        || dy == 0
        || (restriction == AngleRestriction.FORTYFIVE_DEGREE && dx == dy);
  }

  private static Map<Integer, Set<Integer>> contacts(List<Item> items) {
    Map<Integer, Set<Integer>> result = new HashMap<>();
    for (Item item : items) {
      Set<Integer> ids = new HashSet<>();
      for (Item contact : item.getNormalContacts()) {
        if (!(contact instanceof ConductionArea)) {
          ids.add(contact.getId());
        }
      }
      result.put(item.getId(), ids);
    }
    return result;
  }

  private static Map<String, Set<Integer>> endpointContacts(List<Item> items) {
    Map<String, Set<Integer>> result = new HashMap<>();
    for (Item item : items) {
      if (item instanceof PolylineTrace trace) {
        Point[] endpoints = {trace.firstCorner(), trace.lastCorner()};
        for (int i = 0; i < endpoints.length; i++) {
          Set<Integer> ids = new HashSet<>();
          for (Item contact : trace.getNormalContacts(endpoints[i], false)) {
            if (!(contact instanceof ConductionArea)) {
              ids.add(contact.getId());
            }
          }
          result.put(item.getId() + ":" + i, ids);
        }
      }
    }
    return result;
  }

  private static Set<String> violations(List<Item> items) {
    Set<String> result = new HashSet<>();
    for (Item item : items) {
      for (var violation : item.clearanceViolations()) {
        int a = violation.firstItem.getId();
        int b = violation.secondItem.getId();
        result.add(Math.min(a, b) + ":" + Math.max(a, b) + ":" + violation.layer);
      }
    }
    return result;
  }

  private static void insert(BasicBoard board, List<Item> items) {
    items.forEach(board.searchTreeManager::insert);
  }

  private static void remove(BasicBoard board, List<Item> items) {
    items.forEach(board.searchTreeManager::remove);
  }
}
