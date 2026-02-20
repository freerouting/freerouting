package app.freerouting.gui;

import app.freerouting.board.RoutingBoard;

import app.freerouting.management.FRAnalytics;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.util.ResourceBundle;

public class BoardMenuOther extends JMenu {
  private final BoardFrame board_frame;
  private final ResourceBundle resources;

  /** Creates a new instance of BoardMenuOther */
  private BoardMenuOther(BoardFrame p_board_frame) {
    board_frame = p_board_frame;
    resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.BoardMenuOther", p_board_frame.get_locale());
  }

  /** Returns a new other menu for the board frame. */
  public static BoardMenuOther get_instance(BoardFrame p_board_frame) {
    final BoardMenuOther other_menu = new BoardMenuOther(p_board_frame);

    other_menu.setText(other_menu.resources.getString("other"));

    // Add Snapshots menu item
    JMenuItem other_snapshots_menuitem = new JMenuItem();
    other_snapshots_menuitem.setText(other_menu.resources.getString("snapshots"));
    other_snapshots_menuitem.setToolTipText(other_menu.resources.getString("snapshots_tooltip"));
    other_snapshots_menuitem.addActionListener(evt -> other_menu.board_frame.snapshot_window.setVisible(true));
    other_snapshots_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("other_snapshots_menuitem", other_snapshots_menuitem.getText()));
    other_menu.add(other_snapshots_menuitem);

    // Add Delete All Tracks and Vias menu item
    JMenuItem other_delete_all_tracks_menuitem = new JMenuItem();
    other_delete_all_tracks_menuitem.setText(other_menu.resources.getString("delete_all_tracks_and_vias"));
    other_delete_all_tracks_menuitem.setToolTipText(other_menu.resources.getString("delete_all_tracks_and_vias_tooltip"));
    other_delete_all_tracks_menuitem.addActionListener(
        evt -> {
          RoutingBoard board = other_menu.board_frame.board_panel.board_handling.get_routing_board();
          // delete all tracks and vias
          board.delete_all_tracks_and_vias();
          // update the board
          other_menu.board_frame.board_panel.board_handling.update_routing_board(board);
          // create a deep copy of the routing board
          board = other_menu.board_frame.board_panel.board_handling.deep_copy_routing_board();
          // update the board again
          other_menu.board_frame.board_panel.board_handling.update_routing_board(board);
          // create ratsnest
          other_menu.board_frame.board_panel.board_handling.create_ratsnest();
          // redraw the board
          other_menu.board_frame.board_panel.board_handling.repaint();
        });
    other_delete_all_tracks_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("other_delete_all_tracks_menuitem", other_delete_all_tracks_menuitem.getText()));
    other_menu.add(other_delete_all_tracks_menuitem);

    return other_menu;
  }
}
