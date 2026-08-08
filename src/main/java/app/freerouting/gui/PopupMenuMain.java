package app.freerouting.gui;

import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.util.TextManager;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

/** Popup Menu used in the interactive select state. */
class PopupMenuMain extends PopupMenuDisplay {

  /** Creates a new instance of MainPopupMenu */
  PopupMenuMain(BoardFrame pBoardFrame) {
    super(pBoardFrame);
    TextManager tm = new TextManager(this.getClass(), pBoardFrame.get_locale());

    // add the item for selecting items

    JMenuItem popupSelectItemMenuitem = new JMenuItem();
    popupSelectItemMenuitem.setText(tm.getText("select_item"));
    popupSelectItemMenuitem.addActionListener(
        _ -> boardPanel.boardHandling.selectItems(boardPanel.rightButtonClickLocation));
    popupSelectItemMenuitem.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "popupSelectItemMenuitem", popupSelectItemMenuitem.getText()));

    this.add(popupSelectItemMenuitem, 0);

    // Insert the start route item.

    JMenuItem popupStartRouteMenuitem = new JMenuItem();
    popupStartRouteMenuitem.setText(tm.getText("start_route"));
    popupStartRouteMenuitem.addActionListener(
        _ -> boardPanel.boardHandling.startRoute(boardPanel.rightButtonClickLocation));
    popupStartRouteMenuitem.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "popupStartRouteMenuitem", popupStartRouteMenuitem.getText()));

    this.add(popupStartRouteMenuitem, 1);

    // Insert the createObstacleMenu.

    JMenu createObstacleMenu = new JMenu();

    createObstacleMenu.setText(tm.getText("create_keepout"));

    JMenuItem popupCreateTileMenuitem = new JMenuItem();
    popupCreateTileMenuitem.setText(tm.getText("tile"));
    popupCreateTileMenuitem.addActionListener(
        _ -> boardPanel.boardHandling.startTile(boardPanel.rightButtonClickLocation));
    popupCreateTileMenuitem.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "popupCreateTileMenuitem", popupCreateTileMenuitem.getText()));

    createObstacleMenu.add(popupCreateTileMenuitem);

    JMenuItem popupCreateCircleMenuitem = new JMenuItem();
    popupCreateCircleMenuitem.setText(tm.getText("circle"));
    popupCreateCircleMenuitem.addActionListener(
        _ -> boardPanel.boardHandling.startCircle(boardPanel.rightButtonClickLocation));
    popupCreateCircleMenuitem.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "popupCreateCircleMenuitem", popupCreateCircleMenuitem.getText()));

    createObstacleMenu.add(popupCreateCircleMenuitem);

    JMenuItem popupCreatePolygonMenuitem = new JMenuItem();
    popupCreatePolygonMenuitem.setText(tm.getText("polygon"));
    popupCreatePolygonMenuitem.addActionListener(
        _ -> boardPanel.boardHandling.startPolygonshapeItem(boardPanel.rightButtonClickLocation));
    popupCreatePolygonMenuitem.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "popupCreatePolygonMenuitem", popupCreatePolygonMenuitem.getText()));

    createObstacleMenu.add(popupCreatePolygonMenuitem);

    JMenuItem popupAddHoleMenuitem = new JMenuItem();
    popupAddHoleMenuitem.setText(tm.getText("hole"));
    popupAddHoleMenuitem.addActionListener(
        _ -> boardPanel.boardHandling.startAddingHole(boardPanel.rightButtonClickLocation));
    popupAddHoleMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupAddHoleMenuitem", popupAddHoleMenuitem.getText()));

    createObstacleMenu.add(popupAddHoleMenuitem);

    this.add(createObstacleMenu, 2);

    // Insert the pin swap item.

    if (boardPanel.boardHandling.getRoutingBoard().library.logicalParts.count() > 0) {
      // the board contains swappable gates or pins
      JMenuItem popupSwapPinMenuitem = new JMenuItem();
      popupSwapPinMenuitem.setText(tm.getText("swap_pin"));
      popupSwapPinMenuitem.addActionListener(
          _ -> boardPanel.boardHandling.swapPin(boardPanel.rightButtonClickLocation));
      popupSwapPinMenuitem.addActionListener(
          _ -> FRAnalytics.buttonClicked("popupSwapPinMenuitem", popupSwapPinMenuitem.getText()));

      this.add(popupSwapPinMenuitem, 3);
    }
  }
}
