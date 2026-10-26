package app.freerouting.gui.menus;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.gui.a11y.A11y;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.gui.board.BoardFrame;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.awt.Desktop;
import java.net.URI;
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
    TextManager tm = new TextManager(this.getClass(), boardFrame.getLocale());
    this.setText(tm.getText("help"));

    JMenuItem helpAboutMenuitem = new JMenuItem();
    helpAboutMenuitem.setText(tm.getText("about"));
    A11y.tag(helpAboutMenuitem, GuiLocators.MENU_HELP_ABOUT);
    A11y.describe(helpAboutMenuitem, helpAboutMenuitem.getText(), null);
    helpAboutMenuitem.addActionListener(
        _ -> {
          boardFrame.aboutWindow.selectAboutTab();
          boardFrame.aboutWindow.setVisible(true);
        });
    helpAboutMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("helpAboutMenuitem", helpAboutMenuitem.getText()));
    this.add(helpAboutMenuitem);

    JMenuItem helpPatronsMenuItem = new JMenuItem();
    helpPatronsMenuItem.setText(tm.getText("patrons"));
    A11y.tag(helpPatronsMenuItem, GuiLocators.MENU_HELP_PATRONS);
    A11y.describe(helpPatronsMenuItem, helpPatronsMenuItem.getText(), null);
    helpPatronsMenuItem.addActionListener(
        _ -> {
          boardFrame.aboutWindow.selectPatronsTab();
          boardFrame.aboutWindow.setVisible(true);
        });
    helpPatronsMenuItem.addActionListener(
        _ -> FRAnalytics.buttonClicked("helpPatronsMenuItem", helpPatronsMenuItem.getText()));
    this.add(helpPatronsMenuItem);

    JMenuItem helpSponsorMenuItem = new JMenuItem();
    helpSponsorMenuItem.setText(tm.getText("sponsor"));
    A11y.tag(helpSponsorMenuItem, GuiLocators.MENU_HELP_SPONSOR);
    A11y.describe(helpSponsorMenuItem, helpSponsorMenuItem.getText(), null);
    helpSponsorMenuItem.addActionListener(
        _ -> {
          try {
            Desktop.getDesktop().browse(new URI("https://www.freerouting.app/donate.html"));
          } catch (Exception ex) {
            FRLogger.error("Failed to open sponsor link", ex);
          }
        });
    helpSponsorMenuItem.addActionListener(
        _ -> FRAnalytics.buttonClicked("helpSponsorMenuItem", helpSponsorMenuItem.getText()));
    this.add(helpSponsorMenuItem);
  }
}
