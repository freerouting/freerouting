package app.freerouting.gui;

import app.freerouting.board.RoutingBoard;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.util.TextManager;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

public final class BoardMenuOther extends JMenu {

  private final BoardFrame boardFrame;
  private final TextManager tm;

  /** Creates a new instance of BoardMenuOther */
  private BoardMenuOther(BoardFrame p_board_frame) {
    boardFrame = p_board_frame;
    tm = new TextManager(this.getClass(), p_board_frame.get_locale());
  }

  /** Returns a new other menu for the board frame. */
  public static BoardMenuOther getInstance(BoardFrame p_board_frame) {
    final BoardMenuOther otherMenu = new BoardMenuOther(p_board_frame);

    otherMenu.setText(otherMenu.tm.getText("other"));

    // Add Delete All Tracks and Vias menu item
    JMenuItem otherDeleteAllTracksMenuitem = new JMenuItem();
    otherDeleteAllTracksMenuitem.setText(otherMenu.tm.getText("delete_all_tracks_and_vias"));
    otherDeleteAllTracksMenuitem.setToolTipText(
        otherMenu.tm.getText("delete_all_tracks_and_vias_tooltip"));
    otherDeleteAllTracksMenuitem.addActionListener(
        _ -> {
          RoutingBoard board = otherMenu.boardFrame.boardPanel.boardHandling.getRoutingBoard();
          // delete all tracks and vias
          board.deleteAllTracksAndVias();
          // unfill conduction areas
          board.unfillConductionAreas();
          // update the board
          otherMenu.boardFrame.boardPanel.boardHandling.replaceRoutingBoard(board);
          // create a deep copy of the routing board
          board = otherMenu.boardFrame.boardPanel.boardHandling.getRoutingBoard().deepCopy();
          // update the board again
          otherMenu.boardFrame.boardPanel.boardHandling.replaceRoutingBoard(board);
          // create ratsnest
          otherMenu.boardFrame.boardPanel.boardHandling.createRatsnest();
          // redraw the board
          otherMenu.boardFrame.boardPanel.boardHandling.repaint();
        });
    otherDeleteAllTracksMenuitem.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "otherDeleteAllTracksMenuitem", otherDeleteAllTracksMenuitem.getText()));
    otherMenu.add(otherDeleteAllTracksMenuitem);

    return otherMenu;
  }
}
