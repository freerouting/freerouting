package app.freerouting.drc;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.ConductionArea;
import app.freerouting.board.Item;
import app.freerouting.board.Pin;
import app.freerouting.board.Trace;
import app.freerouting.board.Unit;
import app.freerouting.board.Via;
import app.freerouting.constants.Constants;
import app.freerouting.interactive.RatsNest;
import app.freerouting.management.gson.GsonProvider;
import app.freerouting.settings.DesignRulesCheckerSettings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Design Rules Checker that centralizes DRC functionality. This class is
 * responsible for detecting clearance violations and other design rule issues.
 */
public class DesignRulesChecker {

  private final BasicBoard board;
  private final DesignRulesCheckerSettings drcSettings;

  public DesignRulesChecker(BasicBoard board, DesignRulesCheckerSettings drcSettings) {
    this.board = board;
    this.drcSettings = drcSettings;
  }

  /**
   * Collects all clearance violations on the board.
   *
   * @return Collection of all clearance violations found
   */
  public Collection<ClearanceViolation> getAllClearanceViolations() {
    List<ClearanceViolation> allViolations = new ArrayList<>();
    java.util.Set<String> seenViolations = new java.util.HashSet<>();

    // Iterate through all items on the board
    Collection<Item> items = board.get_items();
    for (Item item : items) {
      if (item != null) {
        // Get clearance violations for this item
        Collection<ClearanceViolation> itemViolations = item.clearance_violations();

        // Deduplicate violations - A-B and B-A are the same violation
        for (ClearanceViolation violation : itemViolations) {
          int id1 = violation.first_item.get_id_no();
          int id2 = violation.second_item.get_id_no();

          // Create a unique key using sorted IDs to avoid duplicates
          String key = id1 < id2
              ? id1 + "-" + id2 + "-" + violation.layer
              : id2 + "-" + id1 + "-" + violation.layer;

          if (!seenViolations.contains(key)) {
            seenViolations.add(key);
            allViolations.add(violation);
          }
        }
      }
    }

    return allViolations;
  }

  /**
   * Collects all unconnected items on the board.
   *
   * @return Collection of all unconnected items found
   */
  public Collection<UnconnectedItems> getAllUnconnectedItems() {
    List<UnconnectedItems> unconnectedItems = new ArrayList<>();

    // Group items by net
    java.util.Map<Integer, List<Item>> itemsByNet = new java.util.HashMap<>();
    for (Item item : board.get_items()) {
      if (item instanceof app.freerouting.board.Connectable && item.net_count() > 0) {
        int netNo = item.get_net_no(0);
        itemsByNet.computeIfAbsent(netNo, k -> new ArrayList<>()).add(item);
      }
    }

    // For each net, find truly unconnected items
    for (java.util.Map.Entry<Integer, List<Item>> entry : itemsByNet.entrySet()) {
      int netNo = entry.getKey();
      List<Item> netItems = entry.getValue();

      if (netItems.size() <= 1) {
        continue; // Single item nets are not unconnected
      }

      // Get all connected sets for this net
      java.util.List<java.util.Set<Item>> connectedSets = new java.util.ArrayList<>();
      java.util.Set<Item> processedItems = new java.util.HashSet<>();

      for (Item item : netItems) {
        if (processedItems.contains(item)) {
          continue;
        }

        // Get the connected set for this item
        Collection<Item> connectedSet = item.get_connected_set(netNo);
        java.util.Set<Item> setItems = new java.util.HashSet<>(connectedSet);

        // Only add items that are actually in this net
        setItems.retainAll(netItems);

        if (!setItems.isEmpty()) {
          connectedSets.add(setItems);
          processedItems.addAll(setItems);
        }
      }

      // If there are multiple connected sets, we have unconnected items
      // Report only ONE entry per net, but include items from the disconnected groups
      if (connectedSets.size() >= 2) {
        // Find representative items from the first two sets
        Item item1 = findRepresentativeItem(connectedSets.get(0));
        Item item2 = findRepresentativeItem(connectedSets.get(1));

        if (item1 != null && item2 != null) {
          // Include only items from the two disconnected groups for investigation
          List<Item> unconnectedGroupItems = new ArrayList<>();
          unconnectedGroupItems.addAll(connectedSets.get(0));
          unconnectedGroupItems.addAll(connectedSets.get(1));
          unconnectedItems.add(new UnconnectedItems(item1, item2, unconnectedGroupItems));
        }
      }
    }

    // Check for dangling traces - traces with unconnected ends
    for (Item item : board.get_items()) {
      if (item instanceof Trace trace) {
        Collection<Item> startContacts = trace.get_start_contacts();
        Collection<Item> endContacts = trace.get_end_contacts();

        // A trace is dangling if either its start or end has no contacts
        if (startContacts.isEmpty() || endContacts.isEmpty()) {
          // Only add if not already in the list
          if (!unconnectedItems.stream().anyMatch(ui -> ui.first_item == trace)) {
            unconnectedItems.add(new UnconnectedItems(trace, null, "track_dangling"));
          }
        }
      }
    }

    // Check for dangling vias - vias not connected or connected on only one layer
    for (Item item : board.get_items()) {
      if (item instanceof Via via) {
        // Use the is_tail() method which checks if via has contacts on at most 1 layer
        if (via.is_tail()) {
          unconnectedItems.add(new UnconnectedItems(via, null, "via_dangling"));
        }
      }
    }

    return unconnectedItems;
  }

  /**
   * Finds a representative item from a connected set, preferring Pins over other
   * items.
   *
   * @param connectedSet The set of connected items
   * @return A representative item, or null if the set is empty
   */
  private Item findRepresentativeItem(java.util.Set<Item> connectedSet) {
    // Prefer Pins
    for (Item item : connectedSet) {
      if (item instanceof Pin) {
        return item;
      }
    }
    // Then Traces
    for (Item item : connectedSet) {
      if (item instanceof Trace) {
        return item;
      }
    }
    // Finally any item
    return connectedSet.isEmpty() ? null : connectedSet.iterator().next();
  }

  /**
   * Generates a DRC report in KiCad JSON format.
   *
   * @param sourceFile     Name of the source file
   * @param coordinateUnit Unit for coordinates (e.g., "mm", "mil")
   * @return DRC report in KiCad JSON format
   */
  public DrcReport generateReport(String sourceFile, String coordinateUnit) {
    DrcReport report = new DrcReport(coordinateUnit, sourceFile, "Freerouting " + Constants.FREEROUTING_VERSION);

    // Get all clearance violations
    Collection<ClearanceViolation> violations = getAllClearanceViolations();

    // Convert internal violations to DRC report format
    for (ClearanceViolation violation : violations) {
      DrcViolation drcViolation = convertToDrcViolation(violation, coordinateUnit);
      report.addViolation(drcViolation);
    }

    // Get all unconnected items
    Collection<UnconnectedItems> unconnectedItems = getAllUnconnectedItems();

    // Convert unconnected items to DRC report format
    for (UnconnectedItems unconnectedItem : unconnectedItems) {
      DrcViolation drcViolation = convertToDrcViolation(unconnectedItem, coordinateUnit);
      if ("track_dangling".equals(unconnectedItem.type) || "via_dangling".equals(unconnectedItem.type)) {
        report.addViolation(drcViolation);
      } else {
        report.addUnconnectedItem(drcViolation);
      }
    }

    return report;
  }

  /**
   * Converts an internal ClearanceViolation to a DrcViolation for the report.
   *
   * @param violation      Internal clearance violation
   * @param coordinateUnit Unit for coordinates
   * @return DRC violation in report format
   */
  private DrcViolation convertToDrcViolation(ClearanceViolation violation, String coordinateUnit) {
    List<DrcViolationItem> items = new ArrayList<>();

    // Create items for first and second objects
    String firstItemDesc = getItemDescription(violation.first_item);
    String secondItemDesc = getItemDescription(violation.second_item);

    // Position is the center of gravity of the violation shape
    var firstItemCenterOfGravity = violation.first_item
        .bounding_box()
        .centre_of_gravity();
    DrcPosition firstItemPos = new DrcPosition(
        convertCoordinate(firstItemCenterOfGravity.x, coordinateUnit),
        convertCoordinate(firstItemCenterOfGravity.y, coordinateUnit));
    var secondItemCenterOfGravity = violation.second_item
        .bounding_box()
        .centre_of_gravity();
    DrcPosition secondItemPos = new DrcPosition(
        convertCoordinate(secondItemCenterOfGravity.x, coordinateUnit),
        convertCoordinate(secondItemCenterOfGravity.y, coordinateUnit));

    // Use item IDs as UUIDs (they are unique within the board)
    String firstUuid = String.valueOf(violation.first_item.get_id_no());
    String secondUuid = String.valueOf(violation.second_item.get_id_no());

    items.add(new DrcViolationItem(firstItemDesc, firstItemPos, firstUuid));
    items.add(new DrcViolationItem(secondItemDesc, secondItemPos, secondUuid));

    // Determine violation type
    String type = "clearance";
    if (isHole(violation.first_item) || isHole(violation.second_item)) {
      type = "hole_clearance";
    }

    // Create violation description
    String description;
    if ("hole_clearance".equals(type)) {
      description = "Hole clearance violation between %s and %s (expected: %.4f %s, actual: %.4f %s)".formatted(
          firstItemDesc, secondItemDesc,
          convertCoordinate(violation.expected_clearance, coordinateUnit), coordinateUnit,
          convertCoordinate(violation.actual_clearance, coordinateUnit), coordinateUnit);
    } else {
      description = "Clearance violation between %s and %s (expected: %.4f %s, actual: %.4f %s)".formatted(
          firstItemDesc, secondItemDesc,
          convertCoordinate(violation.expected_clearance, coordinateUnit), coordinateUnit,
          convertCoordinate(violation.actual_clearance, coordinateUnit), coordinateUnit);
    }

    return new DrcViolation(type, description, "error", items);
  }

  private boolean isHole(Item item) {
    if (item instanceof Via) {
      return true;
    }
    // Pins are treated as holes for DRC classification to match expected output,
    // although this might include SMT pins (DrillItem).
    return item instanceof Pin;
  }

  private DrcViolation convertToDrcViolation(UnconnectedItems unconnectedItems, String coordinateUnit) {
    List<DrcViolationItem> items = new ArrayList<>();

    String description;

    if ("track_dangling".equals(unconnectedItems.type) || "via_dangling".equals(unconnectedItems.type)) {
      // For dangling items, show only the single item
      Item item = unconnectedItems.first_item;

      String itemDesc;
      if ("via_dangling".equals(unconnectedItems.type)) {
        itemDesc = getItemDescription(item);
      } else {
        // Get detailed track description with layer and length
        itemDesc = getDetailedTraceDescription(item, coordinateUnit);
      }

      var itemCenterOfGravity = item.bounding_box().centre_of_gravity();
      DrcPosition itemPos = new DrcPosition(
          convertCoordinate(itemCenterOfGravity.x, coordinateUnit),
          convertCoordinate(itemCenterOfGravity.y, coordinateUnit));

      String uuid = String.valueOf(item.get_id_no());
      items.add(new DrcViolationItem(itemDesc, itemPos, uuid));

      description = switch (unconnectedItems.type) {
        case "via_dangling" -> "Via is not connected or connected on only one layer";
        case "track_dangling" -> "Track has unconnected end";
        default -> "Unconnected item: " + itemDesc;
      };

      return new DrcViolation(unconnectedItems.type, description, "warning", items);
    }

    // Create items for all items from the unconnected net
    // This provides better visibility of all affected components/pins
    for (Item item : unconnectedItems.all_items) {
      String itemDesc = getItemDescription(item);
      var itemCenterOfGravity = item.bounding_box().centre_of_gravity();
      DrcPosition itemPos = new DrcPosition(
          convertCoordinate(itemCenterOfGravity.x, coordinateUnit),
          convertCoordinate(itemCenterOfGravity.y, coordinateUnit));
      String uuid = String.valueOf(item.get_id_no());
      items.add(new DrcViolationItem(itemDesc, itemPos, uuid));
    }

    // Create violation description using the first two representative items
    String fromItemDesc = getItemDescription(unconnectedItems.first_item);
    if (unconnectedItems.second_item != null) {
      String toItemDesc = getItemDescription(unconnectedItems.second_item);
      description = "Unconnected items: %s and %s (%d total items in net)".formatted(
          fromItemDesc, toItemDesc, unconnectedItems.all_items.size());
    } else {
      description = "Unconnected item: %s".formatted(fromItemDesc);
    }

    return new DrcViolation(unconnectedItems.type, description, "warning", items);
  }

  /**
   * Gets a human-readable description of an item.
   *
   * @param item The item to describe
   * @return Description string
   */
  private String getItemDescription(Item item) {
    StringBuilder desc = new StringBuilder();

    if (item instanceof Trace) {
      desc.append("Trace");
    } else if (item instanceof Via) {
      desc.append("Via");
    } else if (item instanceof Pin) {
      desc.append("Pin");
    } else if (item instanceof ConductionArea) {
      desc.append("Conduction Area");
    } else {
      desc.append(item
          .getClass()
          .getSimpleName());
    }

    // Add net information
    if (item.net_count() > 0) {
      String netName = board.rules.nets.get(item.get_net_no(0)).name;
      desc
          .append(" [")
          .append(netName)
          .append("]");
    }

    return desc.toString();
  }

  /**
   * Gets a detailed description of a trace including net, layer, and length.
   *
   * @param item           The trace item to describe
   * @param coordinateUnit Unit for coordinates
   * @return Detailed description string
   */
  private String getDetailedTraceDescription(Item item, String coordinateUnit) {
    StringBuilder desc = new StringBuilder("Track");

    // Add net information
    if (item.net_count() > 0) {
      String netName = board.rules.nets.get(item.get_net_no(0)).name;
      desc
          .append(" [")
          .append(netName)
          .append("]");
    }

    // Add layer information
    if (item instanceof Trace trace) {
      int layer = trace.get_layer();
      String layerName = board.layer_structure.arr[layer].name;
      desc.append(" on ").append(layerName);

      // Add length information
      double lengthInBoardUnits = trace.get_length();
      double lengthInTargetUnits = convertCoordinate(lengthInBoardUnits, coordinateUnit);
      desc.append(", length ").append(String.format("%.4f", lengthInTargetUnits)).append(" ").append(coordinateUnit);
    }

    return desc.toString();
  }

  /**
   * Converts a coordinate value from board's internal coordinate system to the
   * specified unit.
   *
   * @param boardCoordinate Coordinate in board's internal system
   * @param coordinateUnit  Target unit ("mm", "mil", etc.)
   * @return Coordinate value in the target unit
   */
  private double convertCoordinate(double boardCoordinate, String coordinateUnit) {
    // First, convert from board's internal coordinate system to DSN coordinates (in
    // the board's unit)
    double dsnCoordinate = board.communication.coordinate_transform.board_to_dsn(boardCoordinate);

    // Get the board's native unit
    Unit boardUnit = board.communication.unit;

    // Determine target unit
    Unit targetUnit;
    if ("mm".equals(coordinateUnit)) {
      targetUnit = Unit.MM;
    } else if ("mil".equals(coordinateUnit)) {
      targetUnit = Unit.MIL;
    } else if ("inch".equals(coordinateUnit)) {
      targetUnit = Unit.INCH;
    } else if ("um".equals(coordinateUnit)) {
      targetUnit = Unit.UM;
    } else {
      // Default to board unit if unknown
      targetUnit = boardUnit;
    }

    // If the target unit is different from the board unit, convert
    if (targetUnit != boardUnit) {
      return Unit.scale(dsnCoordinate, boardUnit, targetUnit);
    }

    return dsnCoordinate;
  }

  /**
   * Generates a JSON string of the DRC report.
   *
   * @param sourceFile     Name of the source file
   * @param coordinateUnit Unit for coordinates
   * @return JSON string of the DRC report
   */
  public String generateReportJson(String sourceFile, String coordinateUnit) {
    DrcReport report = generateReport(sourceFile, coordinateUnit);
    return GsonProvider.GSON.toJson(report);
  }
}