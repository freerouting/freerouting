package app.freerouting.gui;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.util.TextManager;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

/** Creates the help menu for a board frame. */
public class BoardMenuHelp extends JMenu {

  protected final BoardFrame boardFrame;

  /**
   * Creates a new instance of BoardMenuHelpReduced Separated from BoardMenuHelp to avoid
   * ClassNotFound exception when the library jh.jar is not found, which is only used in the
   * extended help menu.
   */
  public BoardMenuHelp(BoardFrame boardFrame) {
    this.boardFrame = boardFrame;
    TextManager tm = new TextManager(this.getClass(), boardFrame.get_locale());
    this.setText(tm.getText("help"));

    JMenuItem helpAboutMenuitem = new JMenuItem();
    helpAboutMenuitem.setText(tm.getText("about"));
    helpAboutMenuitem.addActionListener(_ -> boardFrame.aboutWindow.setVisible(true));
    helpAboutMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("helpAboutMenuitem", helpAboutMenuitem.getText()));
    this.add(helpAboutMenuitem);
  }
}
