package app.freerouting.gui;

import app.freerouting.management.FRAnalytics;
import app.freerouting.management.TextManager;

import javax.swing.*;

public class BoardMenuHelp extends JMenu
{
  protected final BoardFrame board_frame;

  /**
   * Creates a new instance of BoardMenuHelpReduced Separated from BoardMenuHelp to avoid
   * ClassNotFound exception when the library jh.jar is not found, which is only used in the
   * extended help menu.
   */
  public BoardMenuHelp(BoardFrame p_board_frame)
  {
    this.board_frame = p_board_frame;
    TextManager tm = new TextManager(this.getClass(), p_board_frame.get_locale());
    this.setText(tm.getText("help"));

    JMenuItem help_about_menuitem = new JMenuItem();
    help_about_menuitem.setText(tm.getText("about"));
    help_about_menuitem.addActionListener(evt -> board_frame.about_window.setVisible(true));
    help_about_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("help_about_menuitem", help_about_menuitem.getText()));
    this.add(help_about_menuitem);
  }
}