package app.freerouting.gui;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.util.TextManager;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

/** Creates the display menu of a board frame. */
public final class BoardMenuDisplay extends JMenu {

  private final BoardFrame boardFrame;
  private final TextManager tm;

  /** Creates a new instance of BoardDisplayMenu. */
  private BoardMenuDisplay(BoardFrame boardFrame) {
    this.boardFrame = boardFrame;
    tm = new TextManager(this.getClass(), boardFrame.getLocale());
  }

  /** Returns a new display menu for the board frame. */
  public static BoardMenuDisplay getInstance(BoardFrame boardFrame) {
    final BoardMenuDisplay displayMenu = new BoardMenuDisplay(boardFrame);
    displayMenu.setText(displayMenu.tm.getText("display"));

    JMenuItem displayVisibilityMenuitem = new JMenuItem();
    displayVisibilityMenuitem.setText(displayMenu.tm.getText("visibility"));
    displayVisibilityMenuitem.setToolTipText(displayMenu.tm.getText("visibility_tooltip"));
    displayVisibilityMenuitem.addActionListener(
        _ -> displayMenu.boardFrame.visibilityWindow.setVisible(true));
    displayVisibilityMenuitem.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "displayVisibilityMenuitem", displayVisibilityMenuitem.getText()));

    displayMenu.add(displayVisibilityMenuitem);

    JMenuItem displayColorsMenuitem = new JMenuItem();
    displayColorsMenuitem.setText(displayMenu.tm.getText("colors"));
    displayColorsMenuitem.setToolTipText(displayMenu.tm.getText("colors_tooltip"));
    displayColorsMenuitem.addActionListener(
        _ -> displayMenu.boardFrame.colorManager.setVisible(true));
    displayColorsMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("displayColorsMenuitem", displayColorsMenuitem.getText()));

    displayMenu.add(displayColorsMenuitem);

    JMenuItem displayMiscellaneousMenuitem = new JMenuItem();
    displayMiscellaneousMenuitem.setText(displayMenu.tm.getText("miscellaneous"));
    displayMiscellaneousMenuitem.addActionListener(
        _ -> displayMenu.boardFrame.displayMiscWindow.setVisible(true));
    displayMiscellaneousMenuitem.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "displayMiscellaneousMenuitem", displayMiscellaneousMenuitem.getText()));

    displayMenu.add(displayMiscellaneousMenuitem);

    return displayMenu;
  }
}
