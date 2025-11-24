package app.freerouting.board;

import app.freerouting.constants.Constants;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.management.gson.GsonProvider;

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

  public DesignRulesChecker(BasicBoard board)
  {
    this.board = board;
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
   * Generates a DRC report in KiCad JSON format.
   * 
   * @param sourceFile Name of the source file
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
    
    return report;
  }

  /**
   * Converts an internal ClearanceViolation to a DrcViolation for the report.
   * 
   * @param violation Internal clearance violation
   * @param coordinateUnit Unit for coordinates
   * @return DRC violation in report format
   */
  private DrcViolation convertToDrcViolation(ClearanceViolation violation, String coordinateUnit)
  {
    List<DrcViolationItem> items = new ArrayList<>();
    
    // Get the center of the violation shape
    FloatPoint center = violation.shape.centre_of_gravity();
    
    // Convert coordinates based on the unit
    double unitScale = getUnitScale(coordinateUnit);
    double x = center.x * unitScale;
    double y = center.y * unitScale;
    
    // Create items for first and second objects
    DrcPosition pos = new DrcPosition(x, y);
    
    String firstItemDesc = getItemDescription(violation.first_item);
    String secondItemDesc = getItemDescription(violation.second_item);
    
    // Use item IDs as UUIDs (they are unique within the board)
    String firstUuid = String.valueOf(violation.first_item.get_id_no());
    String secondUuid = String.valueOf(violation.second_item.get_id_no());
    
    items.add(new DrcViolationItem(firstItemDesc, pos, firstUuid));
    items.add(new DrcViolationItem(secondItemDesc, pos, secondUuid));
    
    // Create violation description
    String description = String.format("Clearance violation between %s and %s (expected: %.4f%s, actual: %.4f%s)",
        firstItemDesc, secondItemDesc,
        violation.expected_clearance * unitScale, coordinateUnit,
        violation.actual_clearance * unitScale, coordinateUnit);
    
    return new DrcViolation("clearance", description, "error", items);
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
      desc.append(item.getClass().getSimpleName());
    }
    
    // Add net information
    if (item.net_count() > 0)
    {
      String netName = board.rules.nets.get(item.get_net_no(0)).name;
      desc.append(" [").append(netName).append("]");
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
    // Board units are typically in 0.0001 mm (tenth of a micron)
    // This is a simplified conversion - adjust based on actual board unit
    if ("mm".equals(coordinateUnit))
    {
      return 0.0001; // Convert from 0.0001mm to mm
    }
    else if ("mil".equals(coordinateUnit))
    {
      return 0.0001 * 39.3701; // Convert to mils (1mm = 39.3701 mil)
    }
    return 1.0; // Default: no conversion
  }

  /**
   * Generates a JSON string of the DRC report.
   * 
   * @param sourceFile Name of the source file
   * @param coordinateUnit Unit for coordinates
   * @return JSON string of the DRC report
   */
  public String generateReportJson(String sourceFile, String coordinateUnit)
  {
    DrcReport report = generateReport(sourceFile, coordinateUnit);
    return GsonProvider.GSON.toJson(report);
  }
}
