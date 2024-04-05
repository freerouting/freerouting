package app.freerouting.gui;

import app.freerouting.settings.DisabledFeaturesSettings;
import javax.swing.JMenu;
import javax.swing.JMenuBar;

/** Creates the menu bar of a board frame together with its menu items. */
class BoardMenuBar extends JMenuBar {

  public BoardMenuFile fileMenu;

  /** Creates a new BoardMenuBar together with its menus */
  public BoardMenuBar(BoardFrame boardFrame, DisabledFeaturesSettings disabledFeatures)
  {
    fileMenu = BoardMenuFile.get_instance(boardFrame, disabledFeatures.macros);
    add(fileMenu);
    JMenu display_menu = BoardMenuDisplay.get_instance(boardFrame);
    add(display_menu);
    JMenu parameter_menu = BoardMenuParameter.get_instance(boardFrame);
    add(parameter_menu);
    JMenu rules_menu = BoardMenuRules.get_instance(boardFrame);
    add(rules_menu);
    JMenu info_menu = BoardMenuInfo.get_instance(boardFrame);
    add(info_menu);
    if (!disabledFeatures.other_menu)
    {
      JMenu other_menu = BoardMenuOther.get_instance(boardFrame);
      add(other_menu);
    }
    JMenu help_menu = new BoardMenuHelp(boardFrame);
    add(help_menu);
  }
}
