package app.freerouting.gui;

import app.freerouting.management.FRAnalytics;
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
    JMenuItem popup_center_display_menuitem = new JMenuItem();
    popup_center_display_menuitem.setText(resources.getString("center_display"));
    popup_center_display_menuitem.addActionListener(evt -> board_panel.center_display(board_panel.right_button_click_location));
    popup_center_display_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_center_display_menuitem", popup_center_display_menuitem.getText()));

    this.add(popup_center_display_menuitem);

    JMenu zoom_menu = new JMenu();
    zoom_menu.setText(resources.getString("zoom"));

    JMenuItem popup_zoom_in_menuitem = new JMenuItem();
    popup_zoom_in_menuitem.setText(resources.getString("zoom_in"));
    popup_zoom_in_menuitem.addActionListener(evt -> board_panel.zoom_in(board_panel.right_button_click_location));
    popup_zoom_in_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_zoom_in_menuitem", popup_zoom_in_menuitem.getText()));

    zoom_menu.add(popup_zoom_in_menuitem);

    JMenuItem popup_zoom_out_menuitem = new JMenuItem();
    popup_zoom_out_menuitem.setText(resources.getString("zoom_out"));
    popup_zoom_out_menuitem.addActionListener(evt -> board_panel.zoom_out(board_panel.right_button_click_location));
    popup_zoom_out_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_zoom_out_menuitem", popup_zoom_out_menuitem.getText()));

    zoom_menu.add(popup_zoom_out_menuitem);

    this.add(zoom_menu);
  }
}
