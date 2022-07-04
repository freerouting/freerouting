package app.freerouting.gui;

/** Popup menu used in the interactive route state. */
public class PopupMenuDynamicRoute extends PopupMenuDisplay {

  private final PopupMenuChangeLayer change_layer_menu;

  /** Creates a new instance of RoutePopupMenu */
  PopupMenuDynamicRoute(BoardFrame p_board_frame) {
    super(p_board_frame);

    java.util.ResourceBundle resources =
        java.util.ResourceBundle.getBundle(
            "app.freerouting.gui.Default", p_board_frame.get_locale());
    app.freerouting.board.LayerStructure layer_structure =
        board_panel.board_handling.get_routing_board().layer_structure;

    javax.swing.JMenuItem end_route_item = new javax.swing.JMenuItem();
    end_route_item.setText(resources.getString("end_route"));
    end_route_item.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            board_panel.board_handling.return_from_state();
          }
        });

    this.add(end_route_item, 0);

    javax.swing.JMenuItem cancel_item = new javax.swing.JMenuItem();
    cancel_item.setText(resources.getString("cancel_route"));
    cancel_item.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            board_panel.board_handling.cancel_state();
          }
        });

    this.add(cancel_item, 1);

    javax.swing.JMenuItem snapshot_item = new javax.swing.JMenuItem();
    snapshot_item.setText(resources.getString("generate_snapshot"));
    snapshot_item.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            board_panel.board_handling.generate_snapshot();
          }
        });

    this.add(snapshot_item, 2);

    if (layer_structure.arr.length > 0) {
      this.change_layer_menu = new PopupMenuChangeLayer(p_board_frame);
      this.add(change_layer_menu, 0);
    } else {
      this.change_layer_menu = null;
    }

    app.freerouting.board.Layer curr_layer =
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
