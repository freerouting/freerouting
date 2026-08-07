package app.freerouting.gui;

import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.util.TextManager;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

/** Creates the parameter menu of a board frame. */
public final class BoardMenuParameter extends JMenu {

  private final BoardFrame boardFrame;
  private final TextManager tm;

  /** Creates a new instance of BoardSelectMenu */
  private BoardMenuParameter(BoardFrame p_board_frame) {
    boardFrame = p_board_frame;
    tm = new TextManager(this.getClass(), p_board_frame.get_locale());
  }

  /** Returns a new windows menu for the board frame. */
  public static BoardMenuParameter get_instance(BoardFrame p_board_frame) {
    final BoardMenuParameter parameterMenu = new BoardMenuParameter(p_board_frame);

    parameterMenu.setText(parameterMenu.tm.getText("parameter"));

    JMenuItem settingsSelectionMenuitem = new JMenuItem();
    settingsSelectionMenuitem.setText(parameterMenu.tm.getText("select"));
    settingsSelectionMenuitem.addActionListener(
        _ -> parameterMenu.boardFrame.selectParameterWindow.setVisible(true));
    settingsSelectionMenuitem.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsSelectionMenuitem", settingsSelectionMenuitem.getText()));

    parameterMenu.add(settingsSelectionMenuitem);

    JMenuItem settingsRoutingMenuitem = new JMenuItem();
    settingsRoutingMenuitem.setText(parameterMenu.tm.getText("route"));
    settingsRoutingMenuitem.addActionListener(
        _ -> parameterMenu.boardFrame.routeParameterWindow.setVisible(true));
    settingsRoutingMenuitem.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsRoutingMenuitem", settingsRoutingMenuitem.getText()));

    parameterMenu.add(settingsRoutingMenuitem);

    JMenuItem settingsAutorouterMenuitem = new JMenuItem();
    settingsAutorouterMenuitem.setText(parameterMenu.tm.getText("autoroute"));
    settingsAutorouterMenuitem.addActionListener(
        _ -> parameterMenu.boardFrame.autorouteParameterWindow.setVisible(true));
    settingsAutorouterMenuitem.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsAutorouterMenuitem", settingsAutorouterMenuitem.getText()));

    parameterMenu.add(settingsAutorouterMenuitem);

    JMenuItem settingsControlsMenuitem = new JMenuItem();
    settingsControlsMenuitem.setText(parameterMenu.tm.getText("move"));
    settingsControlsMenuitem.addActionListener(
        _ -> parameterMenu.boardFrame.moveParameterWindow.setVisible(true));
    settingsControlsMenuitem.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "settingsControlsMenuitem", settingsControlsMenuitem.getText()));

    parameterMenu.add(settingsControlsMenuitem);

    return parameterMenu;
  }
}
