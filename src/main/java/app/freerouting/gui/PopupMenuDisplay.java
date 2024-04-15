package app.freerouting.gui;

import app.freerouting.management.FRAnalytics;
import app.freerouting.management.TextManager;

import javax.swing.*;

public class PopupMenuDisplay extends JPopupMenu
{

  protected final BoardPanel board_panel;

  /**
   * Creates a new instance of PopupMenuDisplay
   */
  public PopupMenuDisplay(BoardFrame p_board_frame)
  {
    this.board_panel = p_board_frame.board_panel;

    TextManager tm = new TextManager(this.getClass(), p_board_frame.get_locale());

    JMenuItem popup_center_display_menuitem = new JMenuItem();
    popup_center_display_menuitem.setText(tm.getText("center_display"));
    popup_center_display_menuitem.addActionListener(evt -> board_panel.center_display(board_panel.right_button_click_location));
    popup_center_display_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_center_display_menuitem", popup_center_display_menuitem.getText()));

    this.add(popup_center_display_menuitem);

    JMenu zoom_menu = new JMenu();
    zoom_menu.setText(tm.getText("zoom"));

    JMenuItem popup_zoom_in_menuitem = new JMenuItem();
    popup_zoom_in_menuitem.setText(tm.getText("zoom_in"));
    popup_zoom_in_menuitem.addActionListener(evt -> board_panel.zoom_in(board_panel.right_button_click_location));
    popup_zoom_in_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_zoom_in_menuitem", popup_zoom_in_menuitem.getText()));

    zoom_menu.add(popup_zoom_in_menuitem);

    JMenuItem popup_zoom_out_menuitem = new JMenuItem();
    popup_zoom_out_menuitem.setText(tm.getText("zoom_out"));
    popup_zoom_out_menuitem.addActionListener(evt -> board_panel.zoom_out(board_panel.right_button_click_location));
    popup_zoom_out_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_zoom_out_menuitem", popup_zoom_out_menuitem.getText()));

    zoom_menu.add(popup_zoom_out_menuitem);

    this.add(zoom_menu);
  }
}