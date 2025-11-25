package app.freerouting.drc;

import app.freerouting.board.*;
import app.freerouting.constants.Constants;
import app.freerouting.interactive.RatsNest;
import app.freerouting.management.gson.GsonProvider;
import app.freerouting.settings.DesignRulesCheckerSettings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Design Rules Checker that centralizes DRC functionality.
 * This class is responsible for detecting clearance violations and other design rule issues.
 */
public class DesignRulesChecker
{
  private final BasicBoard board;
  private final DesignRulesCheckerSettings drcSettings;

  public DesignRulesChecker(BasicBoard board, DesignRulesCheckerSettings drcSettings)
  {
    this.board = board;
    this.drcSettings = drcSettings;
  }

  /**
   * Collects all clearance violations on the board.
   *
   * @return Collection of all clearance violations found
   */
  public Collection<ClearanceViolation> getAllClearanceViolations()
  {
    List<ClearanceViolation> allViolations = new ArrayList<>();

    // Iterate through all items on the board
    Collection<Item> items = board.get_items();
    for (Item item : items)
    {
      if (item != null)
      {
        // Get clearance violations for this item
        Collection<ClearanceViolation> itemViolations = item.clearance_violations();
        allViolations.addAll(itemViolations);
      }
    }

    return allViolations;
  }

  /**
   * Collects all unconnected items on the board.
   *
   * @return Collection of all unconnected items found
   */
  public Collection<UnconnectedItems> getAllUnconnectedItems()
  {
    List<UnconnectedItems> unconnectedItems = new ArrayList<>();

    var ratsnest = new RatsNest(board);

    // Iterate through all items on the board
    for (RatsNest.AirLine airline : ratsnest.get_airlines())
    {
      // Create an unconnected items object
      unconnectedItems.add(new UnconnectedItems(airline.from_item, airline.to_item));
    }

    return unconnectedItems;
  }


  /**
   * Generates a DRC report in KiCad JSON format.
   *
   * @param sourceFile     Name of the source file
   * @param coordinateUnit Unit for coordinates (e.g., "mm", "mil")
   * @return DRC report in KiCad JSON format
   */
  public DrcReport generateReport(String sourceFile, String coordinateUnit)
  {
    DrcReport report = new DrcReport(coordinateUnit, sourceFile, "Freerouting " + Constants.FREEROUTING_VERSION);

    // Get all clearance violations
    Collection<ClearanceViolation> violations = getAllClearanceViolations();

    // Convert internal violations to DRC report format
    for (ClearanceViolation violation : violations)
    {
      DrcViolation drcViolation = convertToDrcViolation(violation, coordinateUnit);
      report.addViolation(drcViolation);
    }

    // Get all unconnected items
    Collection<UnconnectedItems> unconnectedItems = getAllUnconnectedItems();

    // Convert unconnected items to DRC report format
    for (UnconnectedItems unconnectedItem : unconnectedItems)
    {
      DrcViolation drcViolation = convertToDrcViolation(unconnectedItem, coordinateUnit);
      report.addUnconnectedItem(drcViolation);
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
  private DrcViolation convertToDrcViolation(ClearanceViolation violation, String coordinateUnit)
  {
    List<DrcViolationItem> items = new ArrayList<>();

    // Convert coordinates based on the unit
    double unitScale = getUnitScale(coordinateUnit);

    // Create items for first and second objects
    String firstItemDesc = getItemDescription(violation.first_item);
    String secondItemDesc = getItemDescription(violation.second_item);

    // Position is the center of gravity of the violation shape
    var firstItemCenterOfGravity = violation.first_item
        .bounding_box()
        .centre_of_gravity();
    DrcPosition firstItemPos = new DrcPosition(firstItemCenterOfGravity.x * unitScale, firstItemCenterOfGravity.y * unitScale);
    var secondItemCenterOfGravity = violation.second_item
        .bounding_box()
        .centre_of_gravity();
    DrcPosition secondItemPos = new DrcPosition(secondItemCenterOfGravity.x * unitScale, secondItemCenterOfGravity.y * unitScale);

    // Use item IDs as UUIDs (they are unique within the board)
    String firstUuid = String.valueOf(violation.first_item.get_id_no());
    String secondUuid = String.valueOf(violation.second_item.get_id_no());

    items.add(new DrcViolationItem(firstItemDesc, firstItemPos, firstUuid));
    items.add(new DrcViolationItem(secondItemDesc, secondItemPos, secondUuid));

    // Create violation description
    String description = String.format("Clearance violation between %s and %s (expected: %.4f %s, actual: %.4f %s)", firstItemDesc, secondItemDesc, violation.expected_clearance * unitScale, coordinateUnit, violation.actual_clearance * unitScale, coordinateUnit);

    return new DrcViolation("clearance", description, "error", items);
  }

  private DrcViolation convertToDrcViolation(UnconnectedItems unconnectedItems, String coordinateUnit)
  {
    List<DrcViolationItem> items = new ArrayList<>();

    // Convert coordinates based on the unit
    double unitScale = getUnitScale(coordinateUnit);

    // Create items for from and to objects
    String fromItemDesc = getItemDescription(unconnectedItems.first_item);
    String toItemDesc = getItemDescription(unconnectedItems.second_item);

    // Position is the center of gravity of the item
    var fromItemCenterOfGravity = unconnectedItems.first_item
        .bounding_box()
        .centre_of_gravity();
    DrcPosition fromItemPos = new DrcPosition(fromItemCenterOfGravity.x * unitScale, fromItemCenterOfGravity.y * unitScale);
    var toItemCenterOfGravity = unconnectedItems.second_item
        .bounding_box()
        .centre_of_gravity();
    DrcPosition toItemPos = new DrcPosition(toItemCenterOfGravity.x * unitScale, toItemCenterOfGravity.y * unitScale);

    // Use item IDs as UUIDs (they are unique within the board)
    String fromUuid = String.valueOf(unconnectedItems.first_item.get_id_no());
    String toUuid = String.valueOf(unconnectedItems.second_item.get_id_no());

    items.add(new DrcViolationItem(fromItemDesc, fromItemPos, fromUuid));
    items.add(new DrcViolationItem(toItemDesc, toItemPos, toUuid));

    // Create violation description
    String description = String.format("Unconnected items: %s and %s", fromItemDesc, toItemDesc);

    return new DrcViolation("unconnected", description, "warning", items);
  }

  /**
   * Gets a human-readable description of an item.
   *
   * @param item The item to describe
   * @return Description string
   */
  private String getItemDescription(Item item)
  {
    StringBuilder desc = new StringBuilder();

    if (item instanceof Trace)
    {
      desc.append("Trace");
    }
    else if (item instanceof Via)
    {
      desc.append("Via");
    }
    else if (item instanceof Pin)
    {
      desc.append("Pin");
    }
    else if (item instanceof ConductionArea)
    {
      desc.append("Conduction Area");
    }
    else
    {
      desc.append(item
          .getClass()
          .getSimpleName());
    }

    // Add net information
    if (item.net_count() > 0)
    {
      String netName = board.rules.nets.get(item.get_net_no(0)).name;
      desc
          .append(" [")
          .append(netName)
          .append("]");
    }

    return desc.toString();
  }

  /**
   * Gets the scale factor to convert from board units to the specified unit.
   *
   * @param coordinateUnit Target unit ("mm", "mil", etc.)
   * @return Scale factor
   */
  private double getUnitScale(String coordinateUnit)
  {
    // Get the board's native unit
    Unit boardUnit = board.communication.unit;

    // Determine target unit
    Unit targetUnit;
    if ("mm".equals(coordinateUnit))
    {
      targetUnit = Unit.MM;
    }
    else if ("mil".equals(coordinateUnit))
    {
      targetUnit = Unit.MIL;
    }
    else if ("inch".equals(coordinateUnit))
    {
      targetUnit = Unit.INCH;
    }
    else if ("um".equals(coordinateUnit))
    {
      targetUnit = Unit.UM;
    }
    else
    {
      // Default to board unit if unknown
      targetUnit = boardUnit;
    }

    // Use the board's scale method to convert 1 unit
    return Unit.scale(1.0, boardUnit, targetUnit);
  }

  /**
   * Generates a JSON string of the DRC report.
   *
   * @param sourceFile     Name of the source file
   * @param coordinateUnit Unit for coordinates
   * @return JSON string of the DRC report
   */
  public String generateReportJson(String sourceFile, String coordinateUnit)
  {
    DrcReport report = generateReport(sourceFile, coordinateUnit);
    return GsonProvider.GSON.toJson(report);
  }
}