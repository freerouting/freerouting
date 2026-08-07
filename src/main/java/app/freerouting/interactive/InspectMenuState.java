package app.freerouting.interactive;

import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.board.Pin;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.Via;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.rules.Net;
import java.awt.Color;
import java.util.Set;

/**
 * Class implementing the different functionality in the inspect menu, especially the different
 * behaviour of the mouse button 1.
 */
public final class InspectMenuState extends MenuState {

  private Item lastHoveredItem;
  private ClearanceViolation lastHoveredViolation;
  private String backupMessage;

  /** Creates a new instance of InspectMenuState */
  private InspectMenuState(GuiBoardManager p_board_handling) {
    super(p_board_handling);
  }

  /** Returns a new instance of InspectMenuState */
  public static InspectMenuState get_instance(GuiBoardManager p_board_handling) {
    return new InspectMenuState(p_board_handling);
  }

  @Override
  public InteractiveState left_button_clicked(FloatPoint p_location) {
    return select_items(p_location);
  }

  @Override
  public InteractiveState mouse_dragged(FloatPoint p_point) {
    return InspectItemsInRegionState.get_instance(hdlg.get_current_mouse_position(), this, hdlg);
  }

  @Override
  public InteractiveState mouse_moved() {
    super.mouse_moved();

    FloatPoint currentPosition = hdlg.get_current_mouse_position();
    if (currentPosition == null) {
      return this;
    }

    // Check for clearance violations first (higher priority)
    ClearanceViolation currentViolation = null;
    if (hdlg.clearanceViolations != null && !hdlg.clearanceViolations.list.isEmpty()) {
      for (ClearanceViolation violation : hdlg.clearanceViolations.list) {
        if (violation.shape.contains(currentPosition)) {
          currentViolation = violation;
          break;
        }
      }
    }

    // If we found a violation, handle it
    if (currentViolation != null) {
      if (currentViolation != lastHoveredViolation) {
        // Backup message if entering from no violation
        if (lastHoveredViolation == null && lastHoveredItem == null) {
          backupMessage = tm.getText("in_inspect_menu");
        }

        // Clear previous item/violation
        lastHoveredItem = null;
        lastHoveredViolation = currentViolation;
        display_violation_info(currentViolation);
        hdlg.repaint();
      }
      return this;
    } else if (lastHoveredViolation != null) {
      // We left a violation
      lastHoveredViolation = null;
      if (backupMessage != null) {
        hdlg.screenMessages.set_status_message(backupMessage);
        backupMessage = null;
      }
      hdlg.repaint();
    }

    // If no violation, check for items
    Set<Item> pickedItems = hdlg.pick_items(currentPosition);

    // Find the first relevant item (DrillItem or Trace)
    Item currentItem = null;
    for (Item item : pickedItems) {
      if (item instanceof DrillItem || item instanceof PolylineTrace) {
        currentItem = item;
        break;
      }
    }

    // Handle item change
    if (currentItem != lastHoveredItem) {
      // Restore backup message if we left the previous item
      if (lastHoveredItem != null && currentItem == null && backupMessage != null) {
        hdlg.screenMessages.set_status_message(backupMessage);
        backupMessage = null;
      }

      // Clear highlight on previous item
      if (lastHoveredItem != null) {
        lastHoveredItem = null;
        hdlg.repaint();
      }

      // Set new item
      if (currentItem != null) {
        // Backup current message if we're entering a new item from no item
        if (lastHoveredItem == null) {
          backupMessage = tm.getText("in_inspect_menu");
        }

        lastHoveredItem = currentItem;
        display_item_info(currentItem);
        hdlg.repaint();
      }
    }

    return this;
  }

  private void display_item_info(Item item) {
    StringBuilder info = new StringBuilder();

    if (item instanceof Pin) {
      Pin pin = (Pin) item;
      info.append("Pin: ");
      if (pin.get_component_no() > 0) {
        info.append(hdlg.get_routing_board().components.get(pin.get_component_no()).name);
        info.append(" - ");
      }
      info.append("Padstack: ").append(pin.get_padstack().name);
      appendNetInfo(info, item);
    } else if (item instanceof Via) {
      Via via = (Via) item;
      info.append("Via: ");
      info.append("Padstack: ").append(via.get_padstack().name);
      info.append(" (L").append(via.get_padstack().from_layer());
      info.append("-L").append(via.get_padstack().to_layer()).append(")");
      appendNetInfo(info, item);
    } else if (item instanceof PolylineTrace) {
      PolylineTrace trace = (PolylineTrace) item;
      info.append("Trace: ");
      info.append("ID ").append(trace.get_id_no());
      info.append(", Layer: ")
          .append(hdlg.get_routing_board().layerStructure.arr[trace.get_layer()].name);
      info.append(", Width: ").append(2 * trace.get_half_width());

      // Add segment count
      int segmentCount = trace.corner_count() - 1;
      info.append(", Segments: ").append(segmentCount);

      // Add total length
      double length = trace.get_length();
      info.append(", Length: ").append(String.format("%.2f", length));

      appendNetInfo(info, item);
    }

    hdlg.screenMessages.set_status_message(info.toString());
  }

  private void display_violation_info(ClearanceViolation violation) {
    StringBuilder info = new StringBuilder();

    info.append("CLEARANCE VIOLATION");

    // Add layer information
    String layerName = hdlg.get_routing_board().layerStructure.arr[violation.layer].name;
    info.append(" | Layer: ").append(layerName);

    // Add clearance information - convert from board units to display units
    double expectedMm = violation.expectedClearance / 10000.0;
    double actualMm = violation.actualClearance / 10000.0;
    double violationMm = expectedMm - actualMm;

    info.append(String.format(" | Required: %.4f mm", expectedMm));
    info.append(String.format(", Actual: %.4f mm", actualMm));
    info.append(String.format(", Violation: %.4f mm", violationMm));

    // Add clearance class information
    String clearanceClass1 =
        hdlg.get_routing_board()
            .rules
            .clearanceMatrix
            .get_name(violation.firstItem.clearance_class_no());
    String clearanceClass2 =
        hdlg.get_routing_board()
            .rules
            .clearanceMatrix
            .get_name(violation.secondItem.clearance_class_no());

    info.append(" | Classes: ").append(clearanceClass1).append(" <-> ").append(clearanceClass2);

    // Add item information
    info.append(" | Between: ");
    info.append(getItemDescription(violation.firstItem));
    info.append(" and ");
    info.append(getItemDescription(violation.secondItem));

    hdlg.screenMessages.set_status_message(info.toString());
  }

  private String getItemDescription(Item item) {
    if (item instanceof Pin) {
      Pin pin = (Pin) item;
      if (pin.get_component_no() > 0) {
        return "Pin(" + hdlg.get_routing_board().components.get(pin.get_component_no()).name + ")";
      }
      return "Pin";
    } else if (item instanceof Via) {
      return "Via";
    } else if (item instanceof PolylineTrace) {
      return "Trace(ID:" + item.get_id_no() + ")";
    } else {
      return item.getClass().getSimpleName();
    }
  }

  private void appendNetInfo(StringBuilder info, Item item) {
    if (item.net_count() > 0) {
      info.append(" | Net: ");
      for (int i = 0; i < item.net_count(); i++) {
        if (i > 0) {
          info.append(", ");
        }
        Net net = hdlg.get_routing_board().rules.nets.get(item.get_net_no(i));
        info.append(net.name);
      }
    }
  }

  @Override
  public void draw(java.awt.Graphics p_graphics) {
    // Draw the hovered clearance violation with highlight
    if (lastHoveredViolation != null && hdlg.graphicsContext != null) {
      Color violationColor = hdlg.graphicsContext.get_violations_color();
      double intensity = hdlg.graphicsContext.get_layer_visibility(lastHoveredViolation.layer);

      // Draw the violation area with increased brightness
      hdlg.graphicsContext.fill_area(
          lastHoveredViolation.shape, p_graphics, violationColor, Math.min(1.0, intensity * 1.8));

      // Draw a prominent circle around the violation
      double drawRadius = hdlg.get_routing_board().rules.get_min_trace_half_width() * 8;
      hdlg.graphicsContext.draw_circle(
          lastHoveredViolation.shape.centre_of_gravity(),
          drawRadius,
          0.15 * drawRadius,
          violationColor,
          p_graphics,
          Math.min(1.0, intensity * 1.5));
    }

    // Draw the hovered item with highlight
    if (lastHoveredItem != null && hdlg.graphicsContext != null) {
      Color[] highlightColors = lastHoveredItem.get_draw_colors(hdlg.graphicsContext);

      // Increase intensity for highlight effect
      double baseIntensity = lastHoveredItem.get_draw_intensity(hdlg.graphicsContext);
      double highlightIntensity = Math.min(1.0, baseIntensity * 1.5);

      // Draw with increased brightness
      lastHoveredItem.draw(p_graphics, hdlg.graphicsContext, highlightColors, highlightIntensity);
    }
  }

  @Override
  public void display_default_message() {
    if (lastHoveredItem == null && lastHoveredViolation == null) {
      hdlg.screenMessages.set_status_message(tm.getText("in_inspect_menu"));
    }
  }

  @Override
  public String get_help_id() {
    return "MenuState_InspectMenuState";
  }

  public Item get_last_hovered_item() {
    return lastHoveredItem;
  }
}
