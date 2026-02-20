package app.freerouting.gui;

import javax.swing.JMenu;
import javax.swing.JMenuBar;

/** Creates the menu bar of a board frame together with its menu items. */
class BoardMenuBar extends JMenuBar {

  private BoardMenuFile file_menu;

  /** Creates a new BoardMenuBar together with its menus */
  static BoardMenuBar get_instance(
      BoardFrame p_board_frame, boolean p_help_system_used, boolean p_session_file_option) {
    BoardMenuBar menubar = new BoardMenuBar();
    menubar.file_menu = BoardMenuFile.get_instance(p_board_frame, p_session_file_option);
    menubar.add(menubar.file_menu);
    JMenu display_menu = BoardMenuDisplay.get_instance(p_board_frame);
    menubar.add(display_menu);
    JMenu parameter_menu = BoardMenuParameter.get_instance(p_board_frame);
    menubar.add(parameter_menu);
    JMenu rules_menu = BoardMenuRules.get_instance(p_board_frame);
    menubar.add(rules_menu);
    JMenu info_menu = BoardMenuInfo.get_instance(p_board_frame);
    menubar.add(info_menu);
    JMenu other_menu = BoardMenuOther.get_instance(p_board_frame);
    menubar.add(other_menu);
    JMenu help_menu = new BoardMenuHelp(p_board_frame);
    menubar.add(help_menu);
    return menubar;
  }

  void add_design_dependent_items() {
    this.file_menu.add_design_dependent_items();
  }
}
