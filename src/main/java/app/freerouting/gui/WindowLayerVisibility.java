package app.freerouting.gui;

import app.freerouting.board.LayerStructure;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.management.TextManager;

/**
 * Interactive Frame to adjust the visibility of the individual board layers
 */
public class WindowLayerVisibility extends WindowVisibility
{
  /**
   * Creates a new instance of LayerVisibilityFrame
   */
  private WindowLayerVisibility(BoardFrame p_board_frame, String p_title, String p_header_message, String[] p_message_arr)
  {

    super(p_board_frame, p_title, p_header_message, p_message_arr);
    setLanguage(p_board_frame.get_locale());
  }

  /**
   * Returns a new instance of LayerVisibilityFrame
   */
  public static WindowLayerVisibility get_instance(BoardFrame p_board_frame)
  {
    BoardPanel board_panel = p_board_frame.board_panel;

    TextManager tm = new TextManager(WindowLayerVisibility.class, p_board_frame.get_locale());

    String title = tm.getText("layer_visibility");
    String header_message = tm.getText("layer_visibility_header");
    LayerStructure layer_structure = board_panel.board_handling.get_routing_board().layer_structure;
    String[] message_arr = new String[layer_structure.arr.length];
    for (int i = 0; i < message_arr.length; ++i)
    {
      message_arr[i] = layer_structure.arr[i].name;
    }
    WindowLayerVisibility result = new WindowLayerVisibility(p_board_frame, title, header_message, message_arr);
    for (int i = 0; i < message_arr.length; ++i)
    {
      result.set_slider_value(i, board_panel.board_handling.graphics_context.get_raw_layer_visibility(i));
    }
    return result;
  }

  @Override
  protected void set_changed_value(int p_index, double p_value)
  {
    get_board_handling().set_layer_visibility(p_index, p_value);
  }

  @Override
  protected void set_all_minimum()
  {
    int layer_count = this.get_board_handling().graphics_context.layer_count();
    for (int i = 0; i < layer_count; ++i)
    {
      if (i != this.get_board_handling().settings.get_layer())
      {
        set_slider_value(i, 0);
        set_changed_value(i, 0);
      }
    }
  }

  /**
   * Refreshes the displayed values in this window.
   */
  @Override
  public void refresh()
  {
    GraphicsContext graphics_context = this.get_board_handling().graphics_context;
    for (int i = 0; i < graphics_context.layer_count(); ++i)
    {
      this.set_slider_value(i, graphics_context.get_raw_layer_visibility(i));
    }
  }
}