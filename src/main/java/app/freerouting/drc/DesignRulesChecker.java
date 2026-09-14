package app.freerouting.drc;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.Component;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.constants.Constants;
import app.freerouting.core.library.Package;
import app.freerouting.geometry.planar.Point;
import app.freerouting.io.kicad.KiCadDrcPosition;
import app.freerouting.io.kicad.KiCadDrcReport;
import app.freerouting.io.kicad.KiCadDrcViolation;
import app.freerouting.io.kicad.KiCadDrcViolationItem;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import app.freerouting.settings.DesignRulesCheckerSettings;
import app.freerouting.util.gson.GsonProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Design Rules Checker that centralizes DRC functionality. This class is responsible for detecting
 * clearance violations and other design rule issues.
 */
public class DesignRulesChecker {

  private final BasicBoard board;
  private final DesignRulesCheckerSettings drcSettings;
  public int maxConnections;
  // State for incomplete connections (ratsnest)
  private NetIncompletes[] netIncompletes;

  /**
   * Creates a design rules checker for the given board and settings.
   *
   * @param board the board to inspect
   * @param drcSettings checker configuration
   */
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
    Collection<Item> items = board.getItems();
    for (Item item : items) {
      if (item != null) {
        // Get clearance violations for this item
        Collection<ClearanceViolation> itemViolations = item.clearanceViolations();

        // Deduplicate violations - A-B and B-A are the same violation
        for (ClearanceViolation violation : itemViolations) {
          int id1 = violation.firstItem.getId();
          int id2 = violation.secondItem.getId();

          // Create a unique key using sorted IDs to avoid duplicates
          String key =
              id1 < id2
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
    for (Item item : board.getItems()) {
      if (item instanceof app.freerouting.board.model.items.Connectable && item.netCount() > 0) {
        int netNumber = item.getNetNumber(0);
        itemsByNet.computeIfAbsent(netNumber, k -> new ArrayList<>()).add(item);
      }
    }

    // For each net, find truly unconnected items
    for (java.util.Map.Entry<Integer, List<Item>> entry : itemsByNet.entrySet()) {
      int netNumber = entry.getKey();
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
        Collection<Item> connectedSet = item.getConnectedSet(netNumber);
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
    for (Item item : board.getItems()) {
      if (item instanceof Trace trace) {
        Collection<Item> startContacts = trace.getStartContacts();
        Collection<Item> endContacts = trace.getEndContacts();

        // A trace is dangling if either its start or end has no contacts
        if (startContacts.isEmpty() || endContacts.isEmpty()) {
          // Only add if not already in the list
          if (!unconnectedItems.stream().anyMatch(ui -> ui.firstItem == trace)) {
            unconnectedItems.add(new UnconnectedItems(trace, null, "track_dangling"));
          }
        }
      }
    }

    // Check for dangling vias - vias not connected or connected on only one layer
    for (Item item : board.getItems()) {
      if (item instanceof Via via) {
        // Use the isTail() method which checks if via has contacts on at most 1 layer
        if (via.isTail()) {
          unconnectedItems.add(new UnconnectedItems(via, null, "via_dangling"));
        }
      }
    }

    return unconnectedItems;
  }

  /**
   * Checks for copper pour (conduction area) fragmentation, detecting isolated copper islands where
   * pins or vias are severed from the net's primary connected set, or dead floating copper islands.
   *
   * @return Collection of detected zone island violations
   */
  public Collection<ZoneIslandViolation> getZoneIslandViolations() {
    List<ZoneIslandViolation> violations = new ArrayList<>();
    Collection<ConductionArea> conductionAreas = board.getConductionAreas();
    if (conductionAreas == null || conductionAreas.isEmpty()) {
      return violations;
    }

    double minDeadCopperArea =
        1.0
            * board.communication.getResolution(Unit.MM)
            * board.communication.getResolution(Unit.MM);

    for (ConductionArea ca : conductionAreas) {
      java.awt.geom.Area detailedFill = ca.getDetailedFillArea();
      if (detailedFill == null || detailedFill.isEmpty()) {
        continue;
      }

      List<java.awt.geom.Area> islands = decomposeAreaIntoIslands(detailedFill);
      if (islands.size() <= 1) {
        continue; // Single continuous pour, no fragmentation
      }

      int netNumber = ca.netCount() > 0 ? ca.getNetNumber(0) : 0;
      int layer = ca.getLayer();

      // Find all connectable items belonging to this net on this layer
      List<Item> sameNetItemsOnLayer = new ArrayList<>();
      for (Item item : board.getItems()) {
        if (item != ca
            && item instanceof app.freerouting.board.model.items.Connectable
            && item.sharesNet(ca)
            && item.sharesLayer(ca)) {
          sameNetItemsOnLayer.add(item);
        }
      }

      // Map items to each island
      List<List<Item>> islandItems = new ArrayList<>(islands.size());
      for (java.awt.geom.Area island : islands) {
        List<Item> contained = new ArrayList<>();
        for (Item item : sameNetItemsOnLayer) {
          Point center = null;
          if (item instanceof app.freerouting.board.model.items.DrillItem drillItem) {
            center = drillItem.getCenter();
          } else if (item instanceof Trace trace) {
            center = trace.firstCorner();
          }
          if (center != null) {
            app.freerouting.geometry.planar.FloatPoint fp = center.toFloat();
            if (island.contains(fp.x, fp.y)) {
              contained.add(item);
            }
          }
        }
        islandItems.add(contained);
      }

      // Determine which island is the primary (has the most connected items or connects to the main
      // set)
      int primaryIndex = -1;
      int maxConnectedItems = -1;
      for (int i = 0; i < islands.size(); i++) {
        int count = islandItems.get(i).size();
        if (count > maxConnectedItems) {
          maxConnectedItems = count;
          primaryIndex = i;
        }
      }

      // Check each island against the primary
      for (int i = 0; i < islands.size(); i++) {
        java.awt.geom.Area island = islands.get(i);
        List<Item> items = islandItems.get(i);

        if (!items.isEmpty()) {
          if (i != primaryIndex) {
            // Check if any item in this island can reach the items in the primary island through
            // traces/vias elsewhere on the board
            boolean connectedToPrimary = false;
            if (primaryIndex >= 0 && !islandItems.get(primaryIndex).isEmpty()) {
              Item repItemInPrimary = islandItems.get(primaryIndex).get(0);
              Collection<Item> connectedSet = repItemInPrimary.getConnectedSet(netNumber, true);
              for (Item item : items) {
                if (connectedSet.contains(item)) {
                  connectedToPrimary = true;
                  break;
                }
              }
            }

            if (!connectedToPrimary) {
              violations.add(
                  new ZoneIslandViolation(
                      ca, island, layer, netNumber, "isolated_island_unconnected", items));
            }
          }
        } else {
          // Island with zero contact items: check if it exceeds minimum dead copper threshold
          java.awt.geom.Rectangle2D bounds = island.getBounds2D();
          double approxArea = bounds.getWidth() * bounds.getHeight();
          if (approxArea >= minDeadCopperArea) {
            violations.add(
                new ZoneIslandViolation(
                    ca,
                    island,
                    layer,
                    netNumber,
                    "isolated_island_dead_copper",
                    Collections.emptyList()));
          }
        }
      }
    }

    return violations;
  }

  /** Decomposes a potentially disjoint java.awt.geom.Area into separate contiguous island Areas. */
  private List<java.awt.geom.Area> decomposeAreaIntoIslands(java.awt.geom.Area area) {
    List<java.awt.geom.Area> islands = new ArrayList<>();
    if (area == null || area.isEmpty()) {
      return islands;
    }

    java.awt.geom.PathIterator it = area.getPathIterator(null);
    java.awt.geom.Path2D.Double currentPolygon = null;
    double[] coords = new double[6];

    while (!it.isDone()) {
      int segType = it.currentSegment(coords);
      switch (segType) {
        case java.awt.geom.PathIterator.SEG_MOVETO -> {
          if (currentPolygon != null) {
            java.awt.geom.Area piece = new java.awt.geom.Area(currentPolygon);
            if (!piece.isEmpty()) {
              mergeOrAddIsland(islands, piece);
            }
          }
          currentPolygon = new java.awt.geom.Path2D.Double();
          currentPolygon.moveTo(coords[0], coords[1]);
        }
        case java.awt.geom.PathIterator.SEG_LINETO -> {
          if (currentPolygon != null) {
            currentPolygon.lineTo(coords[0], coords[1]);
          }
        }
        case java.awt.geom.PathIterator.SEG_QUADTO -> {
          if (currentPolygon != null) {
            currentPolygon.quadTo(coords[0], coords[1], coords[2], coords[3]);
          }
        }
        case java.awt.geom.PathIterator.SEG_CUBICTO -> {
          if (currentPolygon != null) {
            currentPolygon.curveTo(
                coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]);
          }
        }
        case java.awt.geom.PathIterator.SEG_CLOSE -> {
          if (currentPolygon != null) {
            currentPolygon.closePath();
          }
        }
        default -> {}
      }
      it.next();
    }

    if (currentPolygon != null) {
      java.awt.geom.Area piece = new java.awt.geom.Area(currentPolygon);
      if (!piece.isEmpty()) {
        mergeOrAddIsland(islands, piece);
      }
    }

    return islands;
  }

  private void mergeOrAddIsland(List<java.awt.geom.Area> islands, java.awt.geom.Area piece) {
    // If piece is a hole (subtractive), it will be enclosed by an existing island outer boundary.
    // In java.awt.geom.Area path iteration, outer boundaries and inner holes are consecutive
    // contours with winding rules. We test containment against existing island bounding boxes:
    boolean incorporated = false;
    for (int i = 0; i < islands.size(); i++) {
      java.awt.geom.Area existing = islands.get(i);
      java.awt.geom.Area intersection = new java.awt.geom.Area(existing);
      intersection.intersect(piece);
      if (!intersection.isEmpty()) {
        // Intersects an existing component (e.g. hole or connected segment) -> subtract or add
        // according to Area winding
        existing.exclusiveOr(piece);
        incorporated = true;
        break;
      }
    }
    if (!incorporated) {
      islands.add(piece);
    }
  }

  /**
   * Finds a representative item from a connected set, preferring Pins over other items.
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
   * @param sourceFile Name of the source file
   * @param coordinateUnit Unit for coordinates (e.g., "mm", "mil")
   * @return DRC report in KiCad JSON format
   */
  public KiCadDrcReport generateReport(String sourceFile, String coordinateUnit) {
    KiCadDrcReport report =
        new KiCadDrcReport(
            coordinateUnit, sourceFile, "Freerouting " + Constants.FREEROUTING_VERSION);

    // Get all clearance violations
    Collection<ClearanceViolation> violations = getAllClearanceViolations();

    FRLogger.trace(
        "DesignRulesChecker.generateReport",
        "drc_check_started",
        "DRC check started: total_clearance_violations="
            + violations.size()
            + ", coordinate_unit="
            + coordinateUnit
            + ", source_file="
            + sourceFile,
        "DRC Check",
        new Point[0]);

    // Convert internal violations to DRC report format
    for (ClearanceViolation violation : violations) {
      KiCadDrcViolation kiCadDrcViolation = convertToDrcViolation(violation, coordinateUnit);
      report.addViolation(kiCadDrcViolation);

      FRLogger.trace(
          "DesignRulesChecker.generateReport",
          "drc_violation",
          "DRC violation: type=clearance"
              + ", item1="
              + violation.firstItem.toString()
              + ", item2="
              + violation.secondItem.toString()
              + ", layer="
              + violation.layer
              + ", expected="
              + (violation.expectedClearance / 10000.0)
              + "mm"
              + ", actual="
              + (violation.actualClearance / 10000.0)
              + "mm"
              + ", delta="
              + ((violation.expectedClearance - violation.actualClearance) / 10000.0)
              + "mm",
          "DRC Check",
          new Point[] {violation.shape.centreOfGravity().round()});
    }

    // Get all unconnected items
    Collection<UnconnectedItems> unconnectedItems = getAllUnconnectedItems();

    FRLogger.trace(
        "DesignRulesChecker.generateReport",
        "unconnectedItems",
        "Unconnected items found: count=" + unconnectedItems.size(),
        "DRC Check",
        new Point[0]);

    // Convert unconnected items to DRC report format
    for (UnconnectedItems unconnectedItem : unconnectedItems) {
      KiCadDrcViolation kiCadDrcViolation = convertToDrcViolation(unconnectedItem, coordinateUnit);
      if ("track_dangling".equals(unconnectedItem.type)
          || "via_dangling".equals(unconnectedItem.type)) {
        report.addViolation(kiCadDrcViolation);
      } else {
        report.addUnconnectedItem(kiCadDrcViolation);
      }
    }

    // Get all zone island violations
    Collection<ZoneIslandViolation> zoneViolations = getZoneIslandViolations();
    for (ZoneIslandViolation zv : zoneViolations) {
      KiCadDrcViolation kiCadDrcViolation = convertToDrcViolation(zv, coordinateUnit);
      if ("isolated_island_unconnected".equals(zv.type)) {
        report.addUnconnectedItem(kiCadDrcViolation);
      } else {
        report.addViolation(kiCadDrcViolation);
      }
    }

    FRLogger.trace(
        "DesignRulesChecker.generateReport",
        "drc_check_completed",
        "DRC check completed: total_violations="
            + report.violations.size()
            + ", total_unconnected="
            + report.unconnectedItems.size()
            + ", total_zone_islands="
            + zoneViolations.size(),
        "DRC Check",
        new Point[0]);

    return report;
  }

  /**
   * Converts an internal ClearanceViolation to a DrcViolation for the report.
   *
   * @param violation Internal clearance violation
   * @param coordinateUnit Unit for coordinates
   * @return DRC violation in report format
   */
  private KiCadDrcViolation convertToDrcViolation(
      ClearanceViolation violation, String coordinateUnit) {
    List<KiCadDrcViolationItem> items = new ArrayList<>();

    // Create items for first and second objects
    String firstItemDesc = getItemDescription(violation.firstItem);
    String secondItemDesc = getItemDescription(violation.secondItem);

    // Position is the center of gravity of the violation shape
    var firstItemCenterOfGravity = violation.firstItem.boundingBox().centreOfGravity();
    KiCadDrcPosition firstItemPos =
        new KiCadDrcPosition(
            convertCoordinate(firstItemCenterOfGravity.x, coordinateUnit),
            convertCoordinate(firstItemCenterOfGravity.y, coordinateUnit));
    var secondItemCenterOfGravity = violation.secondItem.boundingBox().centreOfGravity();
    KiCadDrcPosition secondItemPos =
        new KiCadDrcPosition(
            convertCoordinate(secondItemCenterOfGravity.x, coordinateUnit),
            convertCoordinate(secondItemCenterOfGravity.y, coordinateUnit));

    // Use item IDs as UUIDs (they are unique within the board)
    String firstUuid = String.valueOf(violation.firstItem.getId());
    String secondUuid = String.valueOf(violation.secondItem.getId());

    items.add(new KiCadDrcViolationItem(firstItemDesc, firstItemPos, firstUuid));
    items.add(new KiCadDrcViolationItem(secondItemDesc, secondItemPos, secondUuid));

    // Determine violation type
    String type = "clearance";
    if (isHole(violation.firstItem) || isHole(violation.secondItem)) {
      type = "holeClearance";
    }

    // Create violation description
    String description;
    if ("holeClearance".equals(type)) {
      description =
          "Hole clearance violation between %s and %s (expected: %.4f %s, actual: %.4f %s)"
              .formatted(
                  firstItemDesc,
                  secondItemDesc,
                  convertCoordinate(violation.expectedClearance, coordinateUnit),
                  coordinateUnit,
                  convertCoordinate(violation.actualClearance, coordinateUnit),
                  coordinateUnit);
    } else {
      description =
          "Clearance violation between %s and %s (expected: %.4f %s, actual: %.4f %s)"
              .formatted(
                  firstItemDesc,
                  secondItemDesc,
                  convertCoordinate(violation.expectedClearance, coordinateUnit),
                  coordinateUnit,
                  convertCoordinate(violation.actualClearance, coordinateUnit),
                  coordinateUnit);
    }

    return new KiCadDrcViolation(type, description, "error", items);
  }

  private KiCadDrcViolation convertToDrcViolation(
      UnconnectedItems unconnectedItems, String coordinateUnit) {
    List<KiCadDrcViolationItem> items = new ArrayList<>();

    String description;

    if ("track_dangling".equals(unconnectedItems.type)
        || "via_dangling".equals(unconnectedItems.type)) {
      // For dangling items, show only the single item
      Item item = unconnectedItems.firstItem;

      String itemDesc;
      if ("via_dangling".equals(unconnectedItems.type)) {
        itemDesc = getItemDescription(item);
      } else {
        // Get detailed track description with layer and length
        itemDesc = getDetailedTraceDescription(item, coordinateUnit);
      }

      var itemCenterOfGravity = item.boundingBox().centreOfGravity();
      KiCadDrcPosition itemPos =
          new KiCadDrcPosition(
              convertCoordinate(itemCenterOfGravity.x, coordinateUnit),
              convertCoordinate(itemCenterOfGravity.y, coordinateUnit));

      String uuid = String.valueOf(item.getId());
      items.add(new KiCadDrcViolationItem(itemDesc, itemPos, uuid));

      description =
          switch (unconnectedItems.type) {
            case "via_dangling" -> "Via is not connected or connected on only one layer";
            case "track_dangling" -> "Track has unconnected end";
            default -> "Unconnected item: " + itemDesc;
          };

      return new KiCadDrcViolation(unconnectedItems.type, description, "warning", items);
    }

    // Create items for all items from the unconnected net
    // This provides better visibility of all affected components/pins
    for (Item item : unconnectedItems.allItems) {
      String itemDesc = getItemDescription(item);
      var itemCenterOfGravity = item.boundingBox().centreOfGravity();
      KiCadDrcPosition itemPos =
          new KiCadDrcPosition(
              convertCoordinate(itemCenterOfGravity.x, coordinateUnit),
              convertCoordinate(itemCenterOfGravity.y, coordinateUnit));
      String uuid = String.valueOf(item.getId());
      items.add(new KiCadDrcViolationItem(itemDesc, itemPos, uuid));
    }

    // Create violation description using the first two representative items
    String fromItemDesc = getItemDescription(unconnectedItems.firstItem);
    if (unconnectedItems.secondItem != null) {
      String toItemDesc = getItemDescription(unconnectedItems.secondItem);
      description =
          "Unconnected items: %s and %s (%d total items in net)"
              .formatted(fromItemDesc, toItemDesc, unconnectedItems.allItems.size());
    } else {
      description = "Unconnected item: %s".formatted(fromItemDesc);
    }

    return new KiCadDrcViolation(unconnectedItems.type, description, "warning", items);
  }

  private KiCadDrcViolation convertToDrcViolation(
      ZoneIslandViolation zoneViolation, String coordinateUnit) {
    List<KiCadDrcViolationItem> items = new ArrayList<>();

    double[] center = zoneViolation.getCenter();
    KiCadDrcPosition pos =
        new KiCadDrcPosition(
            convertCoordinate(center[0], coordinateUnit),
            convertCoordinate(center[1], coordinateUnit));

    String caDesc = getItemDescription(zoneViolation.conductionArea);
    String caUuid = String.valueOf(zoneViolation.conductionArea.getId());
    items.add(new KiCadDrcViolationItem(caDesc, pos, caUuid));

    for (Item item : zoneViolation.itemsInIsland) {
      String itemDesc = getItemDescription(item);
      var itemCenter = item.boundingBox().centreOfGravity();
      KiCadDrcPosition itemPos =
          new KiCadDrcPosition(
              convertCoordinate(itemCenter.x, coordinateUnit),
              convertCoordinate(itemCenter.y, coordinateUnit));
      String uuid = String.valueOf(item.getId());
      items.add(new KiCadDrcViolationItem(itemDesc, itemPos, uuid));
    }

    String netName = "unknown";
    if (zoneViolation.netNumber > 0) {
      Net net = board.rules.nets.get(zoneViolation.netNumber);
      if (net != null) {
        netName = net.name;
      }
    }

    String description;
    if ("isolated_island_dead_copper".equals(zoneViolation.type)) {
      description =
          "Isolated copper island without electrical connection on net ["
              + netName
              + "] (Conduction Area #"
              + zoneViolation.conductionArea.getId()
              + ")";
    } else {
      description =
          "Isolated copper island on net ["
              + netName
              + "] containing "
              + zoneViolation.itemsInIsland.size()
              + " disconnected item(s) from Conduction Area #"
              + zoneViolation.conductionArea.getId();
    }

    String severity =
        "isolated_island_unconnected".equals(zoneViolation.type) ? "error" : "warning";
    return new KiCadDrcViolation(zoneViolation.type, description, severity, items);
  }

  private boolean isHole(Item item) {
    if (item instanceof Via) {
      return true;
    }
    // Pins are treated as holes for DRC classification to match expected output,
    // although this might include SMT pins (DrillItem).
    return item instanceof Pin;
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
      desc.append(item.getClass().getSimpleName());
    }

    // Add net information
    if (item.netCount() > 0) {
      String netName = board.rules.nets.get(item.getNetNumber(0)).name;
      desc.append(" [").append(netName).append("]");
    }

    return desc.toString();
  }

  /**
   * Gets a detailed description of a trace including net, layer, and length.
   *
   * @param item The trace item to describe
   * @param coordinateUnit Unit for coordinates
   * @return Detailed description string
   */
  private String getDetailedTraceDescription(Item item, String coordinateUnit) {
    StringBuilder desc = new StringBuilder("Track");

    // Add net information
    if (item.netCount() > 0) {
      String netName = board.rules.nets.get(item.getNetNumber(0)).name;
      desc.append(" [").append(netName).append("]");
    }

    // Add layer information
    if (item instanceof Trace trace) {
      int layer = trace.getLayer();
      String layerName = board.layerStructure.layers[layer].name;
      desc.append(" on ").append(layerName);

      // Add length information
      double lengthInBoardUnits = trace.getLength();
      double lengthInTargetUnits = convertCoordinate(lengthInBoardUnits, coordinateUnit);
      desc.append(", length ")
          .append(String.format("%.4f", lengthInTargetUnits))
          .append(" ")
          .append(coordinateUnit);
    }

    return desc.toString();
  }

  /**
   * Converts a coordinate value from board's internal coordinate system to the specified unit.
   *
   * @param boardCoordinate Coordinate in board's internal system
   * @param coordinateUnit Target unit ("mm", "mil", etc.)
   * @return Coordinate value in the target unit
   */
  private double convertCoordinate(double boardCoordinate, String coordinateUnit) {
    // First, convert from board's internal coordinate system to DSN coordinates (in
    // the board's unit)
    double dsnCoordinate = board.communication.coordinateTransform.boardToDsn(boardCoordinate);

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
   * Initializes the incomplete connection calculations for all nets on the board. Incomplete
   * connections (airlines) are determined based on the items associated with each net. This is not
   * equivalent to the total number of connections, as some nets may have multiple items already
   * connected together. This is also not equivalent to the number of not-completed nets, as a net
   * may have multiple connections with some connections completed while others remain incomplete.
   */
  public void calculateAllIncompletes() {
    int maxNetNo = board.rules.nets.maxNetNumber();
    // Create the net item lists at once for performance reasons.
    java.util.Vector<Collection<Item>> netItemLists = new java.util.Vector<>(maxNetNo);
    for (int i = 0; i < maxNetNo; i++) {
      netItemLists.add(new java.util.LinkedList<>());
    }
    java.util.Iterator<app.freerouting.datastructures.UndoableObjects.UndoableObjectNode> it =
        board.itemList.startReadObject();
    for (; ; ) {
      Item currentItem = (Item) board.itemList.readObject(it);
      if (currentItem == null) {
        break;
      }
      if (currentItem instanceof app.freerouting.board.model.items.Connectable) {
        for (int i = 0; i < currentItem.netCount(); i++) {
          netItemLists.get(currentItem.getNetNumber(i) - 1).add(currentItem);
        }
      }
    }
    // Correct formula: for each net with ≥2 items, (items - 1) connections are needed
    // (minimum spanning tree). Nets with 0 or 1 items contribute 0.
    // The old formula (total_items - netCount) incorrectly included empty nets in the
    // denominator, producing a maxConnections value that was too small and could even be
    // negative or zero, which caused getNormalizedScore() to always return 0.
    this.maxConnections =
        netItemLists.stream()
            .filter(list -> !list.isEmpty())
            .mapToInt(
                list -> {
                  long endpointCount =
                      list.stream()
                          .filter(item -> item instanceof Pin || item instanceof ConductionArea)
                          .count();
                  return (int) Math.max(0, endpointCount - 1);
                })
            .sum();

    int totalItems = netItemLists.stream().mapToInt(Collection::size).sum();
    FRLogger.trace(
        "DesignRulesChecker.calculateAllIncompletes",
        "maxConnections",
        "Calculated maxConnections="
            + this.maxConnections
            + ", total_items="
            + totalItems
            + ", netCount="
            + netItemLists.size()
            + " (formula: total_items - netCount)",
        "Incomplete Count",
        new Point[0]);

    int[] focusNets = new int[] {98, 99};
    for (int netNumber : focusNets) {
      if (netNumber >= 1 && netNumber <= netItemLists.size()) {
        int netItemsCount = netItemLists.get(netNumber - 1).size();
        Net net = board.rules.nets.get(netNumber);
        String netName = net != null ? net.name : "unknown";
        FRLogger.trace(
            "DesignRulesChecker.calculateAllIncompletes",
            "netItemCount",
            "Net item count: net=" + netNumber + ", name=" + netName + ", items=" + netItemsCount,
            "Net #" + netNumber + " (" + netName + ")",
            new Point[0]);

        // Let's validate all the polyline traces for this net
        var netItems = netItemLists.get(netNumber - 1);
        for (Item item : netItems) {
          if (item instanceof PolylineTrace trace) {
            // trace.validateAndLogPolylineIntegrity();
          }
        }
      }
    }

    this.netIncompletes = new NetIncompletes[maxNetNo];
    for (int i = 0; i < netIncompletes.length; i++) {
      // netNumber is 1-based, index is 0-based
      int netNumber = i + 1;
      netIncompletes[i] = new NetIncompletes(netNumber, netItemLists.get(i), board);
    }
  }

  /**
   * Recalculates the incomplete connections (airlines) for the specified net.
   *
   * @param netNumber The number of the net to recalculate.
   */
  public void recalculateNetIncompletes(int netNumber) {
    if (netIncompletes == null) {
      calculateAllIncompletes();
      return;
    }
    if (netNumber >= 1 && netNumber <= netIncompletes.length) {
      Collection<Item> itemList = board.getConnectableItems(netNumber);
      netIncompletes[netNumber - 1] = new NetIncompletes(netNumber, itemList, board);
    }
  }

  /**
   * Recalculates the incomplete connections for the specified net using a provided list of items.
   *
   * @param netNumber The number of the net to recalculate.
   * @param itemList The collection of items belonging to the net.
   */
  public void recalculateNetIncompletes(int netNumber, Collection<Item> itemList) {
    if (netIncompletes == null) {
      calculateAllIncompletes(); // Initialize if not already done, though this might be expensive
      // if we only
      // want one net. catch-22.
      // But effectively we need the array initialized.
    }
    if (netNumber >= 1 && netNumber <= netIncompletes.length) {
      // copy itemList, because it will be changed inside the constructor of
      // NetIncompletes
      Collection<Item> items = new java.util.LinkedList<>(itemList);
      netIncompletes[netNumber - 1] = new NetIncompletes(netNumber, items, board);
    }
  }

  /** Returns the total number of incomplete connections (airlines) across all nets. */
  public int getIncompleteCount() {
    if (netIncompletes == null) {
      calculateAllIncompletes();
    }

    int result = 0;
    StringBuilder detailsBuilder = new StringBuilder();
    int netsWithIncompletes = 0;

    for (int i = 0; i < netIncompletes.length; i++) {
      int count = this.netIncompletes[i].count();
      if (count > 0) {
        result += count;
        netsWithIncompletes++;
        if (netsWithIncompletes <= 10) { // Log first 10 nets with incompletes
          Net net = board.rules.nets.get(i + 1);
          String netName = net != null ? net.name : "unknown";
          detailsBuilder
              .append("Net #")
              .append(i + 1)
              .append(" (")
              .append(netName)
              .append("): ")
              .append(netIncompletes)
              .append(" incomplete(s); ");
        }
      }
    }

    FRLogger.trace(
        "DesignRulesChecker.getIncompleteCount",
        "total_incompletes_calculated",
        "Total incomplete count: "
            + result
            + ", nets_with_incompletes="
            + netsWithIncompletes
            + ", first_few_nets="
            + detailsBuilder.toString(),
        "Incomplete Count",
        new Point[0]);

    return result;
  }

  /** Returns the number of incomplete connections for a specific net. */
  public int getIncompleteCount(int netNumber) {
    if (netIncompletes == null) {
      calculateAllIncompletes();
    }
    if (netNumber <= 0 || netNumber > netIncompletes.length) {
      return 0;
    }

    int result = netIncompletes[netNumber - 1].count();
    Net net = board.rules.nets.get(netNumber);
    String netName = net != null ? net.name : "unknown";

    FRLogger.trace(
        "DesignRulesChecker.getIncompleteCount",
        "net_incomplete_count",
        "Net incomplete count: net="
            + netNumber
            + ", name="
            + netName
            + ", incompleteCount="
            + result,
        "Net #" + netNumber + " (" + netName + ")",
        new Point[0]);

    return result;
  }

  /** Returns the total number of nets that violate length restrictions. */
  public int getLengthViolationCount() {
    if (netIncompletes == null) {
      calculateAllIncompletes();
    }
    int result = 0;
    for (int i = 0; i < netIncompletes.length; i++) {
      if (netIncompletes[i].getLengthViolation() != 0) {
        ++result;
      }
    }
    return result;
  }

  /** Returns the magnitude of the length violation for the specified net. */
  public double getLengthViolation(int netNumber) {
    if (netIncompletes == null) {
      calculateAllIncompletes();
    }
    if (netNumber <= 0 || netNumber > netIncompletes.length) {
      return 0;
    }
    return netIncompletes[netNumber - 1].getLengthViolation();
  }

  /**
   * Recalculates length matching violations for all nets.
   *
   * @return true if the status of any length violation has changed.
   */
  public boolean recalculateLengthViolations() {
    if (netIncompletes == null) {
      calculateAllIncompletes();
      return true; // Technically changed from nothing to something
    }
    boolean result = false;
    for (int i = 0; i < netIncompletes.length; i++) {
      if (netIncompletes[i].calcLengthViolation()) {
        result = true;
      }
    }
    return result;
  }

  /** Retrieves all airlines (incomplete connections) for the entire board. */
  public AirLine[] getAllAirlines() {
    if (netIncompletes == null) {
      calculateAllIncompletes();
    }
    int count = getIncompleteCount();
    AirLine[] result = new AirLine[count];
    int currentIndex = 0;
    for (int i = 0; i < netIncompletes.length; i++) {
      Collection<AirLine> currentList = netIncompletes[i].incompletes;
      for (AirLine currentLine : currentList) {
        result[currentIndex] = currentLine;
        ++currentIndex;
      }
    }
    return result;
  }

  /**
   * Gets the NetIncompletes object for a specific net. Useful for drawing or detailed inspection.
   */
  public NetIncompletes getNetIncompletes(int netNumber) {
    if (netIncompletes == null) {
      calculateAllIncompletes();
    }
    if (netNumber <= 0 || netNumber > netIncompletes.length) {
      return null;
    }
    return netIncompletes[netNumber - 1];
  }

  /**
   * Generates a JSON string of the DRC report.
   *
   * @param sourceFile Name of the source file
   * @param coordinateUnit Unit for coordinates
   * @return JSON string of the DRC report
   */
  public String generateReportJson(String sourceFile, String coordinateUnit) {
    KiCadDrcReport report = generateReport(sourceFile, coordinateUnit);
    return GsonProvider.GSON.toJson(report);
  }

  /**
   * Generates a concise diagnostic summary of DRC violations with component context, spatial
   * congestion clustering, and layout auto-correction hints.
   *
   * @return structured DrcSummaryResponse
   */
  public DrcSummaryResponse generateSummary() {
    DrcSummaryResponse summary = new DrcSummaryResponse();

    Collection<ClearanceViolation> violations = getAllClearanceViolations();
    summary.clearanceViolationsCount = violations.size();

    Collection<UnconnectedItems> unconnecteds = getAllUnconnectedItems();
    summary.unconnectedNetsCount = unconnecteds.size();

    // 1. Process clearance violations
    for (ClearanceViolation v : violations) {
      String layerName =
          (v.layer >= 0 && v.layer < board.layerStructure.layers.length)
              ? board.layerStructure.layers[v.layer].name
              : ("Layer " + v.layer);

      String item1Desc = formatItemWithContext(v.firstItem);
      String item2Desc = formatItemWithContext(v.secondItem);

      double expectedMm = v.expectedClearance / 10000.0;
      double actualMm = v.actualClearance / 10000.0;
      double shortfallMm = Math.max(0.0, (v.expectedClearance - v.actualClearance) / 10000.0);

      String explanation =
          String.format(
              Locale.US,
              "Clearance violation between %s and %s on %s: required %.3f mm, found %.3f mm (shortfall: %.3f mm).",
              item1Desc,
              item2Desc,
              layerName,
              expectedMm,
              actualMm,
              shortfallMm);

      List<String> items = List.of(item1Desc, item2Desc);
      summary.violations.add(
          new DrcSummaryResponse.DiagnosticViolation(
              "clearance",
              "error",
              layerName,
              explanation,
              expectedMm,
              actualMm,
              shortfallMm,
              items));
    }

    // 2. Process unconnected items
    for (UnconnectedItems u : unconnecteds) {
      String item1Desc = formatItemWithContext(u.firstItem);
      String item2Desc = u.secondItem != null ? formatItemWithContext(u.secondItem) : null;

      String netName = "unknown";
      if (u.firstItem != null && u.firstItem.netCount() > 0) {
        Net net = board.rules.nets.get(u.firstItem.getNetNumber(0));
        if (net != null) {
          netName = net.name;
        }
      }

      String explanation;
      if ("track_dangling".equals(u.type)) {
        explanation = "Dangling trace segment on net [" + netName + "] with unconnected endpoint.";
      } else if ("via_dangling".equals(u.type)) {
        explanation = "Dangling via on net [" + netName + "] connected on at most one layer.";
      } else if (item2Desc != null) {
        explanation =
            String.format(
                Locale.US,
                "Unconnected net [%s]: break between %s and %s (%d elements).",
                netName,
                item1Desc,
                item2Desc,
                u.allItems.size());
      } else {
        explanation = "Unconnected item on net [" + netName + "]: " + item1Desc + ".";
      }

      List<String> items = new ArrayList<>();
      if (u.firstItem != null) {
        items.add(item1Desc);
      }
      if (item2Desc != null) {
        items.add(item2Desc);
      }

      summary.violations.add(
          new DrcSummaryResponse.DiagnosticViolation(
              u.type, "warning", "all", explanation, null, null, null, items));
    }

    // 3. Process zone island violations
    Collection<ZoneIslandViolation> zoneViolations = getZoneIslandViolations();
    for (ZoneIslandViolation zv : zoneViolations) {
      String layerName =
          (zv.layer >= 0 && zv.layer < board.layerStructure.layers.length)
              ? board.layerStructure.layers[zv.layer].name
              : ("Layer " + zv.layer);
      Net net = board.rules.nets.get(zv.netNumber);
      String netName = (net != null) ? net.name : ("Net " + zv.netNumber);

      List<String> items = new ArrayList<>();
      items.add("Zone on " + layerName + " [" + netName + "]");
      for (Item it : zv.itemsInIsland) {
        items.add(formatItemWithContext(it));
      }

      String explanation;
      String severity;
      if ("isolated_island_unconnected".equals(zv.type)) {
        severity = "error";
        summary.unconnectedNetsCount++;
        explanation =
            String.format(
                Locale.US,
                "Copper pour on %s [net %s] is fragmented: island contains %d pin(s)/via(s) disconnected from main pour.",
                layerName,
                netName,
                zv.itemsInIsland.size());
      } else {
        severity = "warning";
        double areaMm2 =
            zv.getAreaInBoardUnits()
                / (board.communication.getResolution(Unit.MM)
                    * board.communication.getResolution(Unit.MM));
        explanation =
            String.format(
                Locale.US,
                "Dead copper island detected on %s [net %s]: area %.2f mm² with no electrical connections.",
                layerName,
                netName,
                areaMm2);
      }

      summary.violations.add(
          new DrcSummaryResponse.DiagnosticViolation(
              zv.type, severity, layerName, explanation, null, null, null, items));
    }

    // 4. Detect spatial congestion zones
    summary.congestionZones = detectCongestionZones(violations);

    // 5. Generate actionable hints
    summary.hints = generateActionableHints(violations, unconnecteds, summary.congestionZones);

    return summary;
  }

  private String formatItemWithContext(Item item) {
    if (item == null) {
      return "none";
    }
    StringBuilder sb = new StringBuilder();
    if (item instanceof Pin pin) {
      sb.append("Pin ");
      Component comp = board.components.get(pin.getComponentId());
      if (comp != null) {
        sb.append(comp.name).append(".");
        Package pkg = comp.getPackage();
        if (pkg != null && pin.pinIndex >= 0 && pin.pinIndex < pkg.pinCount()) {
          sb.append(pkg.getPin(pin.pinIndex).name);
        } else {
          sb.append(pin.pinIndex + 1);
        }
      } else {
        sb.append("#").append(pin.pinIndex + 1);
      }
    } else if (item instanceof Trace) {
      sb.append("Trace");
    } else if (item instanceof Via) {
      sb.append("Via");
    } else if (item instanceof ConductionArea) {
      sb.append("ConductionArea");
    } else {
      sb.append(item.getClass().getSimpleName());
    }

    if (item.netCount() > 0) {
      Net net = board.rules.nets.get(item.getNetNumber(0));
      if (net != null) {
        sb.append(" (net ").append(net.name).append(")");
      }
    }
    return sb.toString();
  }

  private List<DrcSummaryResponse.CongestionZone> detectCongestionZones(
      Collection<ClearanceViolation> violations) {
    List<DrcSummaryResponse.CongestionZone> zones = new ArrayList<>();
    if (violations.isEmpty()) {
      return zones;
    }

    // Simple centroid clustering with a 5.0 mm neighborhood threshold
    final double clusterRadiusMm = 5.0;
    List<Point> points = new ArrayList<>();
    for (ClearanceViolation v : violations) {
      if (v.shape != null) {
        points.add(v.shape.centreOfGravity().round());
      }
    }

    boolean[] visited = new boolean[points.size()];
    for (int i = 0; i < points.size(); i++) {
      if (visited[i]) {
        continue;
      }
      visited[i] = true;
      Point p1 = points.get(i);
      double p1Xmm = p1.toFloat().x / 10000.0;
      double p1Ymm = p1.toFloat().y / 10000.0;

      int clusterCount = 1;
      double sumX = p1Xmm;
      double sumY = p1Ymm;

      for (int j = i + 1; j < points.size(); j++) {
        if (visited[j]) {
          continue;
        }
        Point p2 = points.get(j);
        double p2Xmm = p2.toFloat().x / 10000.0;
        double p2Ymm = p2.toFloat().y / 10000.0;
        double dx = p1Xmm - p2Xmm;
        double dy = p1Ymm - p2Ymm;
        if (Math.hypot(dx, dy) <= clusterRadiusMm) {
          visited[j] = true;
          clusterCount++;
          sumX += p2Xmm;
          sumY += p2Ymm;
        }
      }

      if (clusterCount >= 2) {
        double avgX = sumX / clusterCount;
        double avgY = sumY / clusterCount;
        zones.add(
            new DrcSummaryResponse.CongestionZone(
                avgX,
                avgY,
                clusterRadiusMm,
                clusterCount,
                String.format(
                    Locale.US,
                    "High bottleneck concentration: %d violations within %.1f mm of (%.2f, %.2f) mm.",
                    clusterCount,
                    clusterRadiusMm,
                    avgX,
                    avgY)));
      }
    }
    return zones;
  }

  private List<String> generateActionableHints(
      Collection<ClearanceViolation> violations,
      Collection<UnconnectedItems> unconnecteds,
      List<DrcSummaryResponse.CongestionZone> zones) {
    List<String> hints = new ArrayList<>();

    if (!violations.isEmpty()) {
      double maxShortfall = 0.0;
      for (ClearanceViolation v : violations) {
        double shortfall = (v.expectedClearance - v.actualClearance) / 10000.0;
        if (shortfall > maxShortfall) {
          maxShortfall = shortfall;
        }
      }
      if (maxShortfall > 0.0) {
        hints.add(
            String.format(
                Locale.US,
                "Maximum clearance shortfall is %.3f mm. Consider reducing trace width or clearance class threshold if manufacturing constraints permit.",
                maxShortfall));
      }
    }

    if (!zones.isEmpty()) {
      hints.add(
          String.format(
              Locale.US,
              "Detected %d congested hotspot zone(s). Increasing pin escape distance or spreading component pads in these areas may resolve routing conflicts.",
              zones.size()));
    }

    if (!unconnecteds.isEmpty()) {
      long danglingCount =
          unconnecteds.stream()
              .filter(u -> "track_dangling".equals(u.type) || "via_dangling".equals(u.type))
              .count();
      if (danglingCount > 0) {
        hints.add(
            danglingCount
                + " dangling stub(s) detected. Running an optimizer cleanup pass will prune unneeded stubs.");
      }
      long incompleteNetCount = unconnecteds.size() - danglingCount;
      if (incompleteNetCount > 0) {
        hints.add(
            incompleteNetCount
                + " unrouted net connection(s) remain. Verify layer count or allow additional routing passes (-mp / maxPasses).");
      }
    }

    if (hints.isEmpty()) {
      hints.add("All design rules satisfied. Board is DRC clean.");
    }

    return hints;
  }
}
