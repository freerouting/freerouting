package app.freerouting.gui;

import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;

import app.freerouting.management.FRAnalytics;
import javax.swing.JMenuItem;
import java.util.ResourceBundle;

/** Popup menu used in the interactive route state. */
public class PopupMenuDynamicRoute extends PopupMenuDisplay {

  private final PopupMenuChangeLayer change_layer_menu;

  /** Creates a new instance of RoutePopupMenu */
  PopupMenuDynamicRoute(BoardFrame p_board_frame) {
    super(p_board_frame);

    ResourceBundle resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.Default", p_board_frame.get_locale());
    LayerStructure layer_structure =
        board_panel.board_handling.get_routing_board().layer_structure;

    JMenuItem popup_end_route_menuitem = new JMenuItem();
    popup_end_route_menuitem.setText(resources.getString("end_route"));
    popup_end_route_menuitem.addActionListener(evt -> board_panel.board_handling.return_from_state());
    popup_end_route_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_end_route_menuitem", popup_end_route_menuitem.getText()));

    this.add(popup_end_route_menuitem, 0);

    JMenuItem popup_cancel_menuitem = new JMenuItem();
    popup_cancel_menuitem.setText(resources.getString("cancel_route"));
    popup_cancel_menuitem.addActionListener(evt -> board_panel.board_handling.cancel_state());
    popup_cancel_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_cancel_menuitem", popup_cancel_menuitem.getText()));

    this.add(popup_cancel_menuitem, 1);

    JMenuItem popup_snapshot_menuitem = new JMenuItem();
    popup_snapshot_menuitem.setText(resources.getString("generate_snapshot"));
    popup_snapshot_menuitem.addActionListener(evt -> board_panel.board_handling.generate_snapshot());
    popup_snapshot_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_snapshot_menuitem", popup_snapshot_menuitem.getText()));

    this.add(popup_snapshot_menuitem, 2);

    if (layer_structure.arr.length > 0) {
      this.change_layer_menu = new PopupMenuChangeLayer(p_board_frame);
      this.add(change_layer_menu, 0);
    } else {
      this.change_layer_menu = null;
    }

    Layer curr_layer =
        layer_structure.arr[board_panel.board_handling.settings.get_layer()];
    disable_layer_item(layer_structure.get_signal_layer_no(curr_layer));
  }

  /** Disables the p_no-th item in the change_layer_menu. */
  void disable_layer_item(int p_no) {
    if (this.change_layer_menu != null) {
      this.change_layer_menu.disable_item(p_no);
    }
  }
}
