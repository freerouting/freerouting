package app.freerouting.interactive;

import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.board.Pin;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.Via;
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
    if (last_hovered_item == null) {
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