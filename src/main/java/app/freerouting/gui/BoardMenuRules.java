package app.freerouting.gui;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.util.TextManager;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

/** Creates the rules menu of a board frame. */
public final class BoardMenuRules extends JMenu {

  private final BoardFrame boardFrame;
  private final TextManager tm;

  /** Creates a new instance of BoardRulesMenu. */
  private BoardMenuRules(BoardFrame boardFrame) {
    this.boardFrame = boardFrame;
    tm = new TextManager(this.getClass(), boardFrame.getLocale());
  }

  /** Returns a new windows menu for the board frame. */
  public static BoardMenuRules getInstance(BoardFrame boardFrame) {
    final BoardMenuRules rulesMenu = new BoardMenuRules(boardFrame);

    rulesMenu.setText(rulesMenu.tm.getText("rules"));

    JMenuItem rulesClearanceMenuitem = new JMenuItem();
    rulesClearanceMenuitem.setText(rulesMenu.tm.getText("clearanceMatrix"));
    rulesClearanceMenuitem.addActionListener(
        _ -> rulesMenu.boardFrame.clearanceMatrixWindow.setVisible(true));
    rulesClearanceMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("rulesClearanceMenuitem", rulesClearanceMenuitem.getText()));
    rulesMenu.add(rulesClearanceMenuitem);

    JMenuItem rulesViasMenuitem = new JMenuItem();
    rulesViasMenuitem.setText(rulesMenu.tm.getText("vias"));
    rulesViasMenuitem.addActionListener(_ -> rulesMenu.boardFrame.viaWindow.setVisible(true));
    rulesViasMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("rulesViasMenuitem", rulesViasMenuitem.getText()));
    rulesMenu.add(rulesViasMenuitem);

    JMenuItem rulesNetsMenuitem = new JMenuItem();
    rulesNetsMenuitem.setText(rulesMenu.tm.getText("nets"));
    rulesNetsMenuitem.addActionListener(_ -> rulesMenu.boardFrame.netInfoWindow.setVisible(true));
    rulesNetsMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("rulesNetsMenuitem", rulesNetsMenuitem.getText()));
    rulesMenu.add(rulesNetsMenuitem);

    JMenuItem rulesNetClassMenuitem = new JMenuItem();
    rulesNetClassMenuitem.setText(rulesMenu.tm.getText("netClasses"));
    rulesNetClassMenuitem.addActionListener(
        _ -> rulesMenu.boardFrame.editNetRulesWindow.setVisible(true));
    rulesNetClassMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("rulesNetClassMenuitem", rulesNetClassMenuitem.getText()));
    rulesMenu.add(rulesNetClassMenuitem);

    return rulesMenu;
  }
}
