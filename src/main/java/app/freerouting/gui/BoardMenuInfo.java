package app.freerouting.gui;

import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.util.TextManager;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

/** Creates the information menu for a board frame. */
public final class BoardMenuInfo extends JMenu {

  private final BoardFrame boardFrame;
  private final TextManager tm;

  /** Creates a new instance of BoardLibraryMenu. */
  private BoardMenuInfo(BoardFrame boardFrame) {
    this.boardFrame = boardFrame;
    tm = new TextManager(this.getClass(), boardFrame.get_locale());
  }

  /** Returns a new info menu for the board frame. */
  public static BoardMenuInfo getInstance(BoardFrame boardFrame) {
    final BoardMenuInfo infoMenu = new BoardMenuInfo(boardFrame);

    infoMenu.setText(infoMenu.tm.getText("info"));

    JMenuItem infoPackagesMenuitem = new JMenuItem();
    infoPackagesMenuitem.setText(infoMenu.tm.getText("library_packages"));
    infoPackagesMenuitem.addActionListener(
        _ -> infoMenu.boardFrame.packagesWindow.setVisible(true));
    infoPackagesMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("infoPackagesMenuitem", infoPackagesMenuitem.getText()));
    infoMenu.add(infoPackagesMenuitem);

    JMenuItem infoPadstacksMenuitem = new JMenuItem();
    infoPadstacksMenuitem.setText(infoMenu.tm.getText("library_padstacks"));
    infoPadstacksMenuitem.addActionListener(
        _ -> infoMenu.boardFrame.padstacksWindow.setVisible(true));
    infoPadstacksMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("infoPadstacksMenuitem", infoPadstacksMenuitem.getText()));
    infoMenu.add(infoPadstacksMenuitem);

    JMenuItem infoComponentsMenuitem = new JMenuItem();
    infoComponentsMenuitem.setText(infoMenu.tm.getText("board_components"));
    infoComponentsMenuitem.addActionListener(
        _ -> infoMenu.boardFrame.componentsWindow.setVisible(true));
    infoComponentsMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("infoComponentsMenuitem", infoComponentsMenuitem.getText()));
    infoMenu.add(infoComponentsMenuitem);

    JMenuItem infoIncompletesMenuitem = new JMenuItem();
    infoIncompletesMenuitem.setText(infoMenu.tm.getText("incompletes"));
    infoIncompletesMenuitem.addActionListener(
        _ -> infoMenu.boardFrame.incompletesWindow.setVisible(true));
    infoIncompletesMenuitem.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "infoIncompletesMenuitem", infoIncompletesMenuitem.getText()));
    infoMenu.add(infoIncompletesMenuitem);

    JMenuItem infoLengthViolationsMenuitem = new JMenuItem();
    infoLengthViolationsMenuitem.setText(infoMenu.tm.getText("lengthViolations"));
    infoLengthViolationsMenuitem.addActionListener(
        _ -> infoMenu.boardFrame.lengthViolationsWindow.setVisible(true));
    infoLengthViolationsMenuitem.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "infoLengthViolationsMenuitem", infoLengthViolationsMenuitem.getText()));
    infoMenu.add(infoLengthViolationsMenuitem);

    JMenuItem infoClearanceViolationsMenuitem = new JMenuItem();
    infoClearanceViolationsMenuitem.setText(infoMenu.tm.getText("clearanceViolations"));
    infoClearanceViolationsMenuitem.addActionListener(
        _ -> infoMenu.boardFrame.clearanceViolationsWindow.setVisible(true));
    infoClearanceViolationsMenuitem.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "infoClearanceViolationsMenuitem", infoClearanceViolationsMenuitem.getText()));
    infoMenu.add(infoClearanceViolationsMenuitem);

    JMenuItem infoUnconnectedRoutesMenuitem = new JMenuItem();
    infoUnconnectedRoutesMenuitem.setText(infoMenu.tm.getText("unconnected_route"));
    infoUnconnectedRoutesMenuitem.addActionListener(
        _ -> infoMenu.boardFrame.unconnectedRouteWindow.setVisible(true));
    infoUnconnectedRoutesMenuitem.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "infoUnconnectedRoutesMenuitem", infoUnconnectedRoutesMenuitem.getText()));
    infoMenu.add(infoUnconnectedRoutesMenuitem);

    JMenuItem infoRouteStubsMenuitem = new JMenuItem();
    infoRouteStubsMenuitem.setText(infoMenu.tm.getText("route_stubs"));
    infoRouteStubsMenuitem.addActionListener(
        _ -> infoMenu.boardFrame.routeStubsWindow.setVisible(true));
    infoRouteStubsMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("infoRouteStubsMenuitem", infoRouteStubsMenuitem.getText()));
    infoMenu.add(infoRouteStubsMenuitem);

    return infoMenu;
  }
}
