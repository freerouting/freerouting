package app.freerouting.gui;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.util.ResourceBundle;

public class PopupMenuDisplay extends JPopupMenu {

  protected final BoardPanel board_panel;

  /** Creates a new instance of PopupMenuDisplay */
  public PopupMenuDisplay(BoardFrame p_board_frame) {
    this.board_panel = p_board_frame.board_panel;
    ResourceBundle resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.Default", p_board_frame.get_locale());
    JMenuItem center_display_item = new JMenuItem();
    center_display_item.setText(resources.getString("center_display"));
    center_display_item.addActionListener(
        evt -> board_panel.center_display(board_panel.right_button_click_location));

    this.add(center_display_item);

    JMenu zoom_menu = new JMenu();
    zoom_menu.setText(resources.getString("zoom"));

    JMenuItem zoom_in_item = new JMenuItem();
    zoom_in_item.setText(resources.getString("zoom_in"));
    zoom_in_item.addActionListener(
        evt -> board_panel.zoom_in(board_panel.right_button_click_location));

    zoom_menu.add(zoom_in_item);

    JMenuItem zoom_out_item = new JMenuItem();
    zoom_out_item.setText(resources.getString("zoom_out"));
    zoom_out_item.addActionListener(
        evt -> board_panel.zoom_out(board_panel.right_button_click_location));

    zoom_menu.add(zoom_out_item);

    this.add(zoom_menu);
  }
}
