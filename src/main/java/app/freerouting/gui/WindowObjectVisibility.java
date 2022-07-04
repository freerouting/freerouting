package app.freerouting.gui;

import app.freerouting.boardgraphics.ColorIntensityTable.ObjectNames;

/** Interactive Frame to adjust the visibility of the individual board items */
public class WindowObjectVisibility extends WindowVisibility {
  /** Creates a new instance of ItemVisibilityFrame */
  private WindowObjectVisibility(
      BoardFrame p_board_frame, String p_title, String p_header_message, String[] p_message_arr) {

    super(p_board_frame, p_title, p_header_message, p_message_arr);
  }

  /** Returns a new instance of ItemVisibilityFrame */
  public static WindowObjectVisibility get_instance(BoardFrame p_board_frame) {
    java.util.ResourceBundle resources =
        java.util.ResourceBundle.getBundle(
            "app.freerouting.gui.WindowObjectVisibility", p_board_frame.get_locale());
    String title = resources.getString("title");
    String header_message = resources.getString("header_message");
    String[] message_arr = new String[ObjectNames.values().length];
    for (int i = 0; i < message_arr.length; ++i) {
      message_arr[i] = resources.getString(ObjectNames.values()[i].toString());
    }
    WindowObjectVisibility result =
        new WindowObjectVisibility(p_board_frame, title, header_message, message_arr);
    p_board_frame.set_context_sensitive_help(result, "WindowDisplay_ObjectVisibility");
    result.refresh();
    return result;
  }

  /** Refreshs the displayed values in this window. */
  public void refresh() {
    app.freerouting.boardgraphics.ColorIntensityTable color_intensity_table =
        this.get_board_handling().graphics_context.color_intensity_table;
    for (int i = 0; i < ObjectNames.values().length; ++i) {
      this.set_slider_value(i, color_intensity_table.get_value(i));
    }
  }

  protected void set_changed_value(int p_index, double p_value) {

    get_board_handling().graphics_context.color_intensity_table.set_value(p_index, p_value);
  }
}
