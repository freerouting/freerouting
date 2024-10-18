package app.freerouting.gui;

import app.freerouting.logger.FRLogger;
import app.freerouting.settings.FeatureFlagsSettings;
import app.freerouting.settings.GlobalSettings;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import java.io.IOException;

import static app.freerouting.Freerouting.globalSettings;

/**
 * Creates the menu bar of a board frame together with its menu items.
 */
class BoardMenuBar extends JMenuBar
{

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

    // Add the Profile button to the right
    add(Box.createHorizontalGlue());
    add(profileButton);
  }

  /**
   * Displays a modal dialog with user information.
   */
  private void showProfileDialog()
  {
    JDialog profileDialog = new JDialog((Frame) null, "User Settings", true);
    profileDialog.setSize(480, 355);
    profileDialog.setMinimumSize(new Dimension(480, 355));
    profileDialog.setMaximumSize(new Dimension(480, 355));
    profileDialog.setResizable(false);
    profileDialog.setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(5, 15, 5, 15);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;

    int ipadx = 30;

    // User ID
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 0;
    gbc.ipadx = ipadx;
    JLabel userIdLabel = new JLabel("User ID:");
    profileDialog.add(userIdLabel, gbc);
    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.ipadx = 0;
    JTextField userIdField = new JTextField(globalSettings.userProfileSettings.userId);
    userIdField.setEditable(false);
    profileDialog.add(userIdField, gbc);

    // Email
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.weightx = 0;
    gbc.ipadx = ipadx;
    JLabel emailLabel = new JLabel("Email:");
    profileDialog.add(emailLabel, gbc);
    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.ipadx = 0;
    PlaceholderTextField emailField = new PlaceholderTextField("Enter your email address");
    emailField.setText(globalSettings.userProfileSettings.userEmail);
    profileDialog.add(emailField, gbc);

    // Telemetry
    gbc.gridx = 0;
    gbc.gridy = 2;
    gbc.gridwidth = 2;
    JCheckBox telemetryCheckbox = new JCheckBox("<html><strong>Help improve Freerouting</strong> by sending diagnostic and usage data. This data is anonymous and helps us enhance performance, fix issues, and develop new features.</html>");
    telemetryCheckbox.setSelected(globalSettings.userProfileSettings.isTelemetryAllowed);
    telemetryCheckbox.addItemListener(e -> globalSettings.userProfileSettings.isTelemetryAllowed = telemetryCheckbox.isSelected());
    profileDialog.add(telemetryCheckbox, gbc);

    // Contacting
    gbc.gridx = 0;
    gbc.gridy = 3;
    gbc.gridwidth = 2;
    JCheckBox allowContactCheckbox = new JCheckBox("<html><strong>Allow us to contact you</strong> about issues you encounter and features you would like to see in Freerouting.</html>");
    allowContactCheckbox.setSelected(globalSettings.userProfileSettings.isContactAllowed);
    allowContactCheckbox.addItemListener(e -> globalSettings.userProfileSettings.isContactAllowed = allowContactCheckbox.isSelected());
    profileDialog.add(allowContactCheckbox, gbc);

    // Visual separation for statistics
    gbc.gridx = 0;
    gbc.gridy = 4;
    gbc.gridwidth = 2;
    gbc.fill = GridBagConstraints.BOTH;
    JSeparator separator = new JSeparator();
    profileDialog.add(separator, gbc);

    // Statistics header
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = 2;
    gbc.gridy = 5;
    gbc.weightx = 0;
    gbc.ipadx = ipadx;
    JLabel statisticsHeader = new JLabel("Statistics (since " + globalSettings.statistics.startTime.substring(0, 10) + "):");
    profileDialog.add(statisticsHeader, gbc);

    // Statistics
    gbc.gridwidth = 1;
    gbc.gridy = 6;
    gbc.weightx = 0;
    gbc.ipadx = ipadx;
    JLabel sessionsLabel = new JLabel("Sessions:");
    profileDialog.add(sessionsLabel, gbc);
    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.ipadx = 0;
    JLabel sessionsValue = new JLabel(globalSettings.statistics.sessionsTotal.toString());
    profileDialog.add(sessionsValue, gbc);

    gbc.gridx = 0;
    gbc.gridy = 7;
    gbc.weightx = 0;
    gbc.ipadx = ipadx;
    JLabel startedJobsLabel = new JLabel("Started Jobs:");
    profileDialog.add(startedJobsLabel, gbc);
    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.ipadx = 0;
    JLabel startedJobsValue = new JLabel(globalSettings.statistics.jobsStarted.toString());
    profileDialog.add(startedJobsValue, gbc);

    gbc.gridx = 0;
    gbc.gridy = 8;
    gbc.weightx = 0;
    gbc.ipadx = ipadx;
    JLabel completedJobsLabel = new JLabel("Completed Jobs:");
    profileDialog.add(completedJobsLabel, gbc);
    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.ipadx = 0;
    JLabel completedJobsValue = new JLabel(globalSettings.statistics.jobsCompleted.toString());
    profileDialog.add(completedJobsValue, gbc);

    // Update button
    gbc.gridx = 0;
    gbc.gridy = 9;
    gbc.gridwidth = 2;
    gbc.anchor = GridBagConstraints.CENTER;
    JButton updateButton = new JButton("Update");
    var buttonSize = new Dimension(100, updateButton.getPreferredSize().height);
    updateButton.setPreferredSize(buttonSize);
    updateButton.setMaximumSize(buttonSize);
    updateButton.setEnabled(false);
    updateButton.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent e)
      {
        globalSettings.userProfileSettings.userEmail = emailField.getText();
        try
        {
          GlobalSettings.saveAsJson(globalSettings);
        } catch (IOException ex)
        {
          FRLogger.error("Failed to save user profile settings", ex);
        }
        profileDialog.dispose();
      }
    });
    profileDialog.add(updateButton, gbc);

    // Enable the Update button if email or checkboxes change
    DocumentListener documentListener = new DocumentListener()
    {
      @Override
      public void insertUpdate(DocumentEvent e)
      {
        validateEmail(emailField, updateButton);
      }

      @Override
      public void removeUpdate(DocumentEvent e)
      {
        validateEmail(emailField, updateButton);
      }

      @Override
      public void changedUpdate(DocumentEvent e)
      {
        validateEmail(emailField, updateButton);
      }
    };
    emailField.getDocument().addDocumentListener(documentListener);

    ItemListener itemListener = e -> validateEmail(emailField, updateButton);
    telemetryCheckbox.addItemListener(itemListener);
    allowContactCheckbox.addItemListener(itemListener);

    validateEmail(emailField, updateButton);
    if (globalSettings.userProfileSettings.userEmail.isEmpty())
    {
      emailField.requestFocus();
      emailField.setBorder(BorderFactory.createLineBorder(Color.RED));
    }
    else
    {
      updateButton.requestFocus();
    }

    profileDialog.setLocationRelativeTo(null);
    profileDialog.setVisible(true);
  }

  private void validateEmail(JTextField emailField, JButton updateButton)
  {
    String email = emailField.getText();
    boolean isValid = email.isEmpty() || email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    emailField.setBorder(isValid ? BorderFactory.createLineBorder(Color.GRAY) : BorderFactory.createLineBorder(Color.RED));
    updateButton.setEnabled(isValid);
  }
}