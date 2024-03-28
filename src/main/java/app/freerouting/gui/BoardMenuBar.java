package app.freerouting.gui;

import javax.swing.JMenu;
import javax.swing.JMenuBar;

/** Creates the menu bar of a board frame together with its menu items. */
class BoardMenuBar extends JMenuBar {

  public BoardMenuFile fileMenu;

  /** Creates a new BoardMenuBar together with its menus */
  static BoardMenuBar get_instance(BoardFrame p_board_frame, boolean p_disable_feature_macros) {
    BoardMenuBar menubar = new BoardMenuBar();
    menubar.fileMenu = BoardMenuFile.get_instance(p_board_frame, p_disable_feature_macros);
    menubar.add(menubar.fileMenu);
    JMenu display_menu = BoardMenuDisplay.get_instance(p_board_frame);
    menubar.add(display_menu);
    JMenu parameter_menu = BoardMenuParameter.get_instance(p_board_frame);
    menubar.add(parameter_menu);
    JMenu rules_menu = BoardMenuRules.get_instance(p_board_frame);
    menubar.add(rules_menu);
    JMenu info_menu = BoardMenuInfo.get_instance(p_board_frame);
    menubar.add(info_menu);
    if (!MainApplication.globalSettings.disabledFeatures.other_menu)
    {
      JMenu other_menu = BoardMenuOther.get_instance(p_board_frame);
      menubar.add(other_menu);
    }
    JMenu help_menu = new BoardMenuHelp(p_board_frame);
    menubar.add(help_menu);
    return menubar;
  }
}
