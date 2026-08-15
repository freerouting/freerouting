package app.freerouting.gui;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.util.TextManager;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

/** Provides actions for moving and rotating selected board items. */
public class PopupMenuMove extends PopupMenuDisplay {

  /** Creates a new instance of PopupMenuMove. */
  public PopupMenuMove(BoardFrame boardFrame) {
    super(boardFrame);
    TextManager tm = new TextManager(this.getClass(), boardFrame.getLocale());

    // Add menu for turning the items by a multiple of 90 degree

    JMenuItem rotateMenu = new JMenu();
    rotateMenu.setText(tm.getText("turn"));
    this.add(rotateMenu, 0);

    JMenuItem popupTurn90Menuitem = new JMenuItem();
    popupTurn90Menuitem.setText(tm.getText("90_degree"));
    popupTurn90Menuitem.addActionListener(_ -> turn45Degree(2));
    popupTurn90Menuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupTurn90Menuitem", popupTurn90Menuitem.getText()));
    rotateMenu.add(popupTurn90Menuitem);

    JMenuItem popupTurn180Menuitem = new JMenuItem();
    popupTurn180Menuitem.setText(tm.getText("180_degree"));
    popupTurn180Menuitem.addActionListener(_ -> turn45Degree(4));
    popupTurn180Menuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupTurn180Menuitem", popupTurn180Menuitem.getText()));
    rotateMenu.add(popupTurn180Menuitem);

    JMenuItem popupTurn270Menuitem = new JMenuItem();
    popupTurn270Menuitem.setText(tm.getText("-90_degree"));
    popupTurn270Menuitem.addActionListener(_ -> turn45Degree(6));
    popupTurn270Menuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupTurn270Menuitem", popupTurn270Menuitem.getText()));
    rotateMenu.add(popupTurn270Menuitem);

    JMenuItem popupTurn45Menuitem = new JMenuItem();
    popupTurn45Menuitem.setText(tm.getText("45_degree"));
    popupTurn45Menuitem.addActionListener(_ -> turn45Degree(1));
    popupTurn45Menuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupTurn45Menuitem", popupTurn45Menuitem.getText()));
    rotateMenu.add(popupTurn45Menuitem);

    JMenuItem popupTurn135Menuitem = new JMenuItem();
    popupTurn135Menuitem.setText(tm.getText("135_degree"));
    popupTurn135Menuitem.addActionListener(_ -> turn45Degree(3));
    popupTurn135Menuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupTurn135Menuitem", popupTurn135Menuitem.getText()));
    rotateMenu.add(popupTurn135Menuitem);

    JMenuItem popupTurn225Menuitem = new JMenuItem();
    popupTurn225Menuitem.setText(tm.getText("-135_degree"));
    popupTurn225Menuitem.addActionListener(_ -> turn45Degree(5));
    popupTurn225Menuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupTurn225Menuitem", popupTurn225Menuitem.getText()));
    rotateMenu.add(popupTurn225Menuitem);

    JMenuItem popupTurn315Menuitem = new JMenuItem();
    popupTurn315Menuitem.setText(tm.getText("-45_degree"));
    popupTurn315Menuitem.addActionListener(_ -> turn45Degree(7));
    popupTurn315Menuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupTurn315Menuitem", popupTurn315Menuitem.getText()));
    rotateMenu.add(popupTurn315Menuitem);

    JMenuItem popupChangeSideMenuitem = new JMenuItem();
    popupChangeSideMenuitem.setText(tm.getText("change_side"));
    popupChangeSideMenuitem.addActionListener(_ -> boardPanel.boardHandling.changePlacementSide());
    popupChangeSideMenuitem.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "popupChangeSideMenuitem", popupChangeSideMenuitem.getText()));

    this.add(popupChangeSideMenuitem, 1);

    JMenuItem popupResetRotationMenuitem = new JMenuItem();
    popupResetRotationMenuitem.setText(tm.getText("reset_rotation"));
    popupResetRotationMenuitem.addActionListener(_ -> boardPanel.boardHandling.resetRotation());
    popupResetRotationMenuitem.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "popupResetRotationMenuitem", popupResetRotationMenuitem.getText()));

    this.add(popupResetRotationMenuitem, 2);

    JMenuItem popupInsertMenuitem = new JMenuItem();
    popupInsertMenuitem.setText(tm.getText("insert"));
    popupInsertMenuitem.addActionListener(_ -> boardPanel.boardHandling.returnFromState());
    popupInsertMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupInsertMenuitem", popupInsertMenuitem.getText()));

    this.add(popupInsertMenuitem, 3);

    JMenuItem popupCancelMenuitem = new JMenuItem();
    popupCancelMenuitem.setText(tm.getText("cancel"));
    popupCancelMenuitem.addActionListener(_ -> boardPanel.boardHandling.cancelState());
    popupCancelMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupCancelMenuitem", popupCancelMenuitem.getText()));

    this.add(popupCancelMenuitem, 4);
  }

  private void turn45Degree(int factor) {
    boardPanel.boardHandling.turn45Degree(factor);
    boardPanel.moveMouse(boardPanel.rightButtonClickLocation);
  }
}
