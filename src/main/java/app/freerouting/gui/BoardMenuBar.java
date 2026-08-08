package app.freerouting.gui;

import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.settings.FeatureFlagsSettings;
import app.freerouting.util.TextManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuBar;

/** Creates the menu bar of a board frame together with its menu items. */
public class BoardMenuBar extends JMenuBar {

  private final BoardFrame boardFrame;
  public BoardMenuFile fileMenu;
  public BoardMenuDisplay appereanceMenu;
  public BoardMenuParameter settingsMenu;
  public BoardMenuRules rulesMenu;
  public BoardMenuInfo infoMenu;

  /** Creates a new BoardMenuBar together with its menus */
  public BoardMenuBar(BoardFrame boardFrame, FeatureFlagsSettings featureFlags) {
    this.boardFrame = boardFrame;
    fileMenu = new BoardMenuFile(boardFrame);
    add(fileMenu);
    appereanceMenu = BoardMenuDisplay.getInstance(boardFrame);
    add(appereanceMenu);
    settingsMenu = BoardMenuParameter.getInstance(boardFrame);
    add(settingsMenu);
    rulesMenu = BoardMenuRules.getInstance(boardFrame);
    add(rulesMenu);
    infoMenu = BoardMenuInfo.getInstance(boardFrame);
    add(infoMenu);
    if (featureFlags.otherMenu) {
      JMenu otherMenu = BoardMenuOther.getInstance(boardFrame);
      add(otherMenu);
    }
    JMenu helpMenu = new BoardMenuHelp(boardFrame);
    add(helpMenu);

    // Create the Profile button
    TextManager tm = new TextManager(BoardFrame.class, boardFrame.get_locale());
    JButton profileButton = new JButton(tm.getText("user_settings_button"));
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

  /** Displays a modal dialog with user information. */
  public void showProfileDialog() {
    WindowUserSettings.show(this.boardFrame);
  }
}
