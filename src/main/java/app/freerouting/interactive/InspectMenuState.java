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
 * Class implementing the different functionality in the inspect menu,
 * especially
 * the different behaviour of the mouse button 1.
 */
public class InspectMenuState extends MenuState {

  private Item last_hovered_item = null;
  private ClearanceViolation last_hovered_violation = null;
  private String backup_message = null;

  /**
   * Creates a new instance of InspectMenuState
   */
  private InspectMenuState(GuiBoardManager p_board_handling) {
    super(p_board_handling);
  }

  /**
   * Returns a new instance of InspectMenuState
   */
  public static InspectMenuState get_instance(GuiBoardManager p_board_handling) {
    InspectMenuState new_state = new InspectMenuState(p_board_handling);
    return new_state;
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

    FloatPoint current_position = hdlg.get_current_mouse_position();
    if (current_position == null) {
      return this;
    }

    // Check for clearance violations first (higher priority)
    ClearanceViolation current_violation = null;
    if (hdlg.clearance_violations != null && !hdlg.clearance_violations.list.isEmpty()) {
      for (ClearanceViolation violation : hdlg.clearance_violations.list) {
        if (violation.shape.contains(current_position)) {
          current_violation = violation;
          break;
        }
      }
    }

    // If we found a violation, handle it
    if (current_violation != null) {
      if (current_violation != last_hovered_violation) {
        // Backup message if entering from no violation
        if (last_hovered_violation == null && last_hovered_item == null) {
          backup_message = tm.getText("in_inspect_menu");
        }

        // Clear previous item/violation
        last_hovered_item = null;
        last_hovered_violation = current_violation;
        display_violation_info(current_violation);
        hdlg.repaint();
      }
      return this;
    } else if (last_hovered_violation != null) {
      // We left a violation
      last_hovered_violation = null;
      if (backup_message != null) {
        hdlg.screen_messages.set_status_message(backup_message);
        backup_message = null;
      }
      hdlg.repaint();
    }

    // If no violation, check for items
    Set<Item> picked_items = hdlg.pick_items(current_position);

    // Find the first relevant item (DrillItem or Trace)
    Item current_item = null;
    for (Item item : picked_items) {
      if (item instanceof DrillItem || item instanceof PolylineTrace) {
        current_item = item;
        break;
      }
    }

    // Handle item change
    if (current_item != last_hovered_item) {
      // Restore backup message if we left the previous item
      if (last_hovered_item != null && current_item == null && backup_message != null) {
        hdlg.screen_messages.set_status_message(backup_message);
        backup_message = null;
      }

      // Clear highlight on previous item
      if (last_hovered_item != null) {
        last_hovered_item = null;
        hdlg.repaint();
      }

      // Set new item
      if (current_item != null) {
        // Backup current message if we're entering a new item from no item
        if (last_hovered_item == null) {
          backup_message = tm.getText("in_inspect_menu");
        }

        last_hovered_item = current_item;
        display_item_info(current_item);
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
      info.append(", Layer: ").append(hdlg.get_routing_board().layer_structure.arr[trace.get_layer()].name);
      info.append(", Width: ").append(2 * trace.get_half_width());

      // Add segment count
      int segment_count = trace.corner_count() - 1;
      info.append(", Segments: ").append(segment_count);

      // Add total length
      double length = trace.get_length();
      info.append(", Length: ").append(String.format("%.2f", length));

      appendNetInfo(info, item);
    }

    hdlg.screen_messages.set_status_message(info.toString());
  }

  private void display_violation_info(ClearanceViolation violation) {
    StringBuilder info = new StringBuilder();

    info.append("CLEARANCE VIOLATION");

    // Add layer information
    String layerName = hdlg.get_routing_board().layer_structure.arr[violation.layer].name;
    info.append(" | Layer: ").append(layerName);

    // Add clearance information - convert from board units to display units
    double expected_mm = violation.expected_clearance / 10000.0;
    double actual_mm = violation.actual_clearance / 10000.0;
    double violation_mm = expected_mm - actual_mm;

    info.append(String.format(" | Required: %.4f mm", expected_mm));
    info.append(String.format(", Actual: %.4f mm", actual_mm));
    info.append(String.format(", Violation: %.4f mm", violation_mm));

    // Add clearance class information
    String clearanceClass1 = hdlg.get_routing_board().rules.clearance_matrix.get_name(violation.first_item.clearance_class_no());
    String clearanceClass2 = hdlg.get_routing_board().rules.clearance_matrix.get_name(violation.second_item.clearance_class_no());

    info.append(" | Classes: ").append(clearanceClass1).append(" <-> ").append(clearanceClass2);

    // Add item information
    info.append(" | Between: ");
    info.append(getItemDescription(violation.first_item));
    info.append(" and ");
    info.append(getItemDescription(violation.second_item));

    hdlg.screen_messages.set_status_message(info.toString());
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
    if (last_hovered_violation != null && hdlg.graphics_context != null) {
      Color violationColor = hdlg.graphics_context.get_violations_color();
      double intensity = hdlg.graphics_context.get_layer_visibility(last_hovered_violation.layer);

      // Draw the violation area with increased brightness
      hdlg.graphics_context.fill_area(last_hovered_violation.shape, p_graphics, violationColor, Math.min(1.0, intensity * 1.8));

      // Draw a prominent circle around the violation
      double draw_radius = hdlg.get_routing_board().rules.get_min_trace_half_width() * 8;
      hdlg.graphics_context.draw_circle(last_hovered_violation.shape.centre_of_gravity(),
                                        draw_radius,
                                        0.15 * draw_radius,
                                        violationColor,
                                        p_graphics,
                                        Math.min(1.0, intensity * 1.5));
    }

    // Draw the hovered item with highlight
    if (last_hovered_item != null && hdlg.graphics_context != null) {
      Color[] highlight_colors = last_hovered_item.get_draw_colors(hdlg.graphics_context);

      // Increase intensity for highlight effect
      double base_intensity = last_hovered_item.get_draw_intensity(hdlg.graphics_context);
      double highlight_intensity = Math.min(1.0, base_intensity * 1.5);

      // Draw with increased brightness
      last_hovered_item.draw(p_graphics, hdlg.graphics_context, highlight_colors, highlight_intensity);
    }
  }

  @Override
  public void display_default_message() {
    if (last_hovered_item == null && last_hovered_violation == null) {
      hdlg.screen_messages.set_status_message(tm.getText("in_inspect_menu"));
    }
  }

  @Override
  public String get_help_id() {
    return "MenuState_InspectMenuState";
  }

  public Item get_last_hovered_item() {
    return last_hovered_item;
  }
}