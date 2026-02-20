package app.freerouting.gui;

import app.freerouting.management.FRAnalytics;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.util.ResourceBundle;

public class BoardMenuHelp extends JMenu {
  protected final BoardFrame board_frame;
  protected final ResourceBundle resources;
  /**
   * Creates a new instance of BoardMenuHelpReduced Separated from BoardMenuHelp to avoid
   * ClassNotFound exception when the library jh.jar is not found, which is only used in the
   * extended help menu.
   */
  public BoardMenuHelp(BoardFrame p_board_frame) {
    this.board_frame = p_board_frame;
    this.resources =
        ResourceBundle.getBundle("app.freerouting.gui.BoardMenuHelp", p_board_frame.get_locale());
    this.setText(this.resources.getString("help"));

    JMenuItem help_about_menuitem = new JMenuItem();
    help_about_menuitem.setText(this.resources.getString("about"));
    help_about_menuitem.addActionListener(evt -> board_frame.about_window.setVisible(true));
    help_about_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("help_about_menuitem", help_about_menuitem.getText()));
    this.add(help_about_menuitem);
  }
}
