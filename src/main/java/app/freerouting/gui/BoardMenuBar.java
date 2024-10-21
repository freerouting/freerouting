package app.freerouting.gui;

import app.freerouting.management.FRAnalytics;
import app.freerouting.settings.FeatureFlagsSettings;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Creates the menu bar of a board frame together with its menu items.
 */
public class BoardMenuBar extends JMenuBar
{

  private final BoardFrame boardFrame;
  public BoardMenuFile fileMenu;
  public BoardMenuDisplay appereanceMenu;
  public BoardMenuParameter settingsMenu;
  public BoardMenuRules rulesMenu;
  public BoardMenuInfo infoMenu;

  /**
   * Creates a new BoardMenuBar together with its menus
   */
  public BoardMenuBar(BoardFrame boardFrame, FeatureFlagsSettings featureFlags)
  {
    this.boardFrame = boardFrame;
    fileMenu = new BoardMenuFile(boardFrame, !featureFlags.macros);
    add(fileMenu);
    appereanceMenu = BoardMenuDisplay.get_instance(boardFrame);
    add(appereanceMenu);
    settingsMenu = BoardMenuParameter.get_instance(boardFrame);
    add(settingsMenu);
    rulesMenu = BoardMenuRules.get_instance(boardFrame);
    add(rulesMenu);
    infoMenu = BoardMenuInfo.get_instance(boardFrame);
    add(infoMenu);
    if (featureFlags.otherMenu)
    {
      JMenu other_menu = BoardMenuOther.get_instance(boardFrame);
      add(other_menu);
    }
    JMenu help_menu = new BoardMenuHelp(boardFrame);
    add(help_menu);

    // Create the Profile button
    JButton profileButton = new JButton("User Settings");
    profileButton.setBorderPainted(false);
    profileButton.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent e)
      {
        showProfileDialog();
      }
    });
    profileButton.addActionListener(evt -> FRAnalytics.buttonClicked("profile_button", profileButton.getText()));

    // Add the Profile button to the right
    add(Box.createHorizontalGlue());
    add(profileButton);
  }

  /**
   * Displays a modal dialog with user information.
   */
  public void showProfileDialog()
  {
    WindowUserSettings.show(this.boardFrame);
  }
}