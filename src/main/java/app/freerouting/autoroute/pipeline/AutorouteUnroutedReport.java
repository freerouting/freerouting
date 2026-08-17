package app.freerouting.autoroute.pipeline;

import app.freerouting.board.Component;
import app.freerouting.board.Item;
import app.freerouting.board.Pin;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.library.Package;
import app.freerouting.drc.AirLine;
import app.freerouting.drc.DesignRulesChecker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Formats the diagnostic report emitted when autoroute stagnates. */
final class AutorouteUnroutedReport {

  private AutorouteUnroutedReport() {}

  static String build(RoutingBoard board) {
    DesignRulesChecker tempDrc = new DesignRulesChecker(board, null);
    tempDrc.calculateAllIncompletes();
    AirLine[] airlines = tempDrc.getAllAirlines();

    if (airlines == null || airlines.length == 0) {
      return "  (no unrouted connections found)";
    }

    Map<String, List<String>> byNet = new LinkedHashMap<>();
    for (AirLine airline : airlines) {
      String netName = airline.net != null ? airline.net.name : "(unknown net)";
      String fromDesc = describeItem(board, airline.fromItem);
      String toDesc = describeItem(board, airline.toItem);
      byNet
          .computeIfAbsent(netName, k -> new java.util.ArrayList<>())
          .add("    - " + fromDesc + "  ->  " + toDesc);
    }

    StringBuilder result = new StringBuilder();
    for (Map.Entry<String, List<String>> entry : byNet.entrySet()) {
      int count = entry.getValue().size();
      result
          .append("  Net '")
          .append(entry.getKey())
          .append("' (")
          .append(count)
          .append(" unrouted connection")
          .append(count == 1 ? "" : "s")
          .append("):\n");
      for (String line : entry.getValue()) {
        result.append(line).append('\n');
      }
    }
    return result.toString().stripTrailing();
  }

  /**
   * Returns a short, user-friendly description of a board item suitable for the stagnation report.
   * For pins the format is {@code ComponentName-PinName}; all other item types use a generic form.
   */
  private static String describeItem(RoutingBoard board, Item item) {
    if (item instanceof Pin pin) {
      try {
        Component component = board.components.get(pin.getComponentId());
        if (component != null) {
          Package componentPackage = component.getPackage();
          if (componentPackage != null) {
            Package.Pin packagePin = componentPackage.getPin(pin.pinIndex);
            if (packagePin != null) {
              return component.name + "-" + packagePin.name;
            }
          }
          return component.name + " (pin #" + pin.pinIndex + ")";
        }
      } catch (Exception e) {
        // Fall through to the generic representation.
      }
    }
    return item != null ? item.toString() : "(unknown)";
  }
}
