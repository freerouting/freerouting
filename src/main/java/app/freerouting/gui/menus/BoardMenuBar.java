package app.freerouting.gui.menus;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.gui.a11y.A11y;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.gui.board.BoardFrame;
import app.freerouting.gui.windows.board.WindowUserSettings;
import app.freerouting.settings.FeatureFlagsSettings;
import app.freerouting.util.TextManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

/** Creates the menu bar of a board frame together with its menu items. */
public class BoardMenuBar extends JMenuBar {

  private final BoardFrame boardFrame;
  public BoardMenuFile fileMenu;
  public BoardMenuDisplay appereanceMenu;
  public BoardMenuParameter settingsMenu;
  public BoardMenuRules rulesMenu;
  public BoardMenuInfo infoMenu;

  /** Creates a new BoardMenuBar together with its menus. */
  public BoardMenuBar(BoardFrame boardFrame, FeatureFlagsSettings featureFlags) {
    this.boardFrame = boardFrame;
    fileMenu = new BoardMenuFile(boardFrame);
    tagMenu(fileMenu, GuiLocators.MENU_FILE);
    add(fileMenu);
    appereanceMenu = BoardMenuDisplay.getInstance(boardFrame);
    tagMenu(appereanceMenu, GuiLocators.MENU_DISPLAY);
    add(appereanceMenu);
    settingsMenu = BoardMenuParameter.getInstance(boardFrame);
    tagMenu(settingsMenu, GuiLocators.MENU_PARAMETER);
    add(settingsMenu);
    rulesMenu = BoardMenuRules.getInstance(boardFrame);
    tagMenu(rulesMenu, GuiLocators.MENU_RULES);
    add(rulesMenu);
    infoMenu = BoardMenuInfo.getInstance(boardFrame);
    tagMenu(infoMenu, GuiLocators.MENU_INFO);
    add(infoMenu);
    if (featureFlags.otherMenu) {
      JMenu otherMenu = BoardMenuOther.getInstance(boardFrame);
      tagMenu(otherMenu, GuiLocators.MENU_OTHER);
      add(otherMenu);
    }
    JMenu helpMenu = new BoardMenuHelp(boardFrame);
    tagMenu(helpMenu, GuiLocators.MENU_HELP);
    add(helpMenu);

    // Create the Profile button
    TextManager tm = new TextManager(BoardFrame.class, boardFrame.getLocale());
    JButton profileButton = new JButton(tm.getText("user_settings_button"));
    A11y.tag(profileButton, GuiLocators.MENU_PROFILE);
    A11y.describe(profileButton, profileButton.getText(), null);
    profileButton.setBorderPainted(false);
    profileButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            showProfileDialog();
          }
        });
    profileButton.addActionListener(
        _ -> FRAnalytics.buttonClicked("profile_button", profileButton.getText()));

    // Add the Profile button to the right
    add(Box.createHorizontalGlue());
    add(profileButton);
  }

  /**
   * Builds the board menu component without constructing a {@link BoardFrame}.
   *
   * <p>This seam intentionally contains no frame/window references. It is used by accessibility
   * tests and by embedders that need a keyboard-operable menu model before a board is available.
   * Production frame construction continues to use the constructor above, while both paths share
   * the stable locator registry and translated resource keys.
   *
   * @param locale locale for visible and accessible text
   * @param includeOtherMenu whether the optional Other menu should be included
   * @param actionListener receives the locator of each invoked menu item
   * @return a component-only menu bar
   */
  public static JMenuBar createComponentOnly(
      Locale locale, boolean includeOtherMenu, Consumer<String> actionListener) {
    Consumer<String> listener = actionListener == null ? _ -> {} : actionListener;
    JMenuBar menuBar = new JMenuBar();
    A11y.tag(menuBar, GuiLocators.MENU_BAR);
    A11y.describe(menuBar, "Board menus", null);

    menuBar.add(
        createMenu(
            BoardMenuFile.class,
            locale,
            "file",
            GuiLocators.MENU_FILE,
            new String[] {"open", "save_as", "exit"},
            new String[] {
              GuiLocators.MENU_FILE_OPEN, GuiLocators.MENU_FILE_SAVE_AS, GuiLocators.MENU_FILE_EXIT
            },
            new KeyStroke[] {
              KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK),
              KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK),
              KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.ALT_DOWN_MASK)
            },
            listener));
    menuBar.add(
        createMenu(
            BoardMenuDisplay.class,
            locale,
            "display",
            GuiLocators.MENU_DISPLAY,
            new String[] {"visibility", "colors", "miscellaneous"},
            new String[] {
              GuiLocators.MENU_DISPLAY_VISIBILITY,
              GuiLocators.MENU_DISPLAY_COLORS,
              GuiLocators.MENU_DISPLAY_MISCELLANEOUS
            },
            null,
            listener));
    menuBar.add(
        createMenu(
            BoardMenuParameter.class,
            locale,
            "parameter",
            GuiLocators.MENU_PARAMETER,
            new String[] {"select", "route", "autoroute", "move"},
            new String[] {
              GuiLocators.MENU_PARAMETER_SELECT,
              GuiLocators.MENU_PARAMETER_ROUTE,
              GuiLocators.MENU_PARAMETER_AUTOROUTE,
              GuiLocators.MENU_PARAMETER_MOVE
            },
            null,
            listener));
    menuBar.add(
        createMenu(
            BoardMenuRules.class,
            locale,
            "rules",
            GuiLocators.MENU_RULES,
            new String[] {"clearance_matrix", "vias", "nets", "net_classes"},
            new String[] {
              GuiLocators.MENU_RULES_CLEARANCE_MATRIX,
              GuiLocators.MENU_RULES_VIAS,
              GuiLocators.MENU_RULES_NETS,
              GuiLocators.MENU_RULES_NET_CLASSES
            },
            null,
            listener));
    menuBar.add(
        createMenu(
            BoardMenuInfo.class,
            locale,
            "info",
            GuiLocators.MENU_INFO,
            new String[] {"incompletes", "clearance_violations"},
            new String[] {
              GuiLocators.MENU_INFO_INCOMPLETES, GuiLocators.MENU_INFO_CLEARANCE_VIOLATIONS
            },
            null,
            listener));
    if (includeOtherMenu) {
      menuBar.add(
          createMenu(
              BoardMenuOther.class,
              locale,
              "other",
              GuiLocators.MENU_OTHER,
              new String[] {"delete_all_tracks_and_vias"},
              new String[] {GuiLocators.TOOLBAR_DELETE_TRACKS},
              null,
              listener));
    }
    menuBar.add(
        createMenu(
            BoardMenuHelp.class,
            locale,
            "help",
            GuiLocators.MENU_HELP,
            new String[] {"about", "sponsor"},
            new String[] {GuiLocators.MENU_HELP_ABOUT, GuiLocators.MENU_HELP_SPONSOR},
            null,
            listener));

    TextManager frameTm = new TextManager(BoardFrame.class, locale);
    JButton profileButton = new JButton(frameTm.getText("user_settings_button"));
    A11y.tag(profileButton, GuiLocators.MENU_PROFILE);
    A11y.describe(profileButton, profileButton.getText(), null);
    profileButton.addActionListener(_ -> listener.accept(GuiLocators.MENU_PROFILE));
    menuBar.add(Box.createHorizontalGlue());
    menuBar.add(profileButton);
    return menuBar;
  }

  private static JMenu createMenu(
      Class<?> resourceClass,
      Locale locale,
      String titleKey,
      String menuLocator,
      String[] itemKeys,
      String[] itemLocators,
      KeyStroke[] accelerators,
      Consumer<String> actionListener) {
    TextManager tm = new TextManager(resourceClass, locale);
    JMenu menu = new JMenu(tm.getText(titleKey));
    A11y.tag(menu, menuLocator);
    A11y.describe(menu, menu.getText(), null);
    for (int i = 0; i < itemKeys.length; i++) {
      JMenuItem item = new JMenuItem(tm.getText(itemKeys[i]));
      if (accelerators != null && accelerators[i] != null) {
        item.setAccelerator(accelerators[i]);
      }
      A11y.tag(item, itemLocators[i]);
      A11y.describe(item, item.getText(), null);
      String locator = itemLocators[i];
      item.addActionListener(_ -> actionListener.accept(locator));
      menu.add(item);
    }
    return menu;
  }

  private static void tagMenu(JMenu menu, String locator) {
    A11y.tag(menu, locator);
    A11y.describe(menu, menu.getText(), null);
  }

  /** Displays a modal dialog with user information. */
  public void showProfileDialog() {
    WindowUserSettings.show(this.boardFrame);
  }
}
