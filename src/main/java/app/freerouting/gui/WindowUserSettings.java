package app.freerouting.gui;

import app.freerouting.logger.FRLogger;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.management.gson.GsonProvider;
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
 * Startup window visible when the program is loading.
 */
public class WindowUserSettings extends WindowBase
{
  /**
   * Creates a new instance of WindowMessage
   */
  private WindowUserSettings(BoardFrame p_board_frame)
  {
    super(480, 355);

    setLanguage(p_board_frame.get_locale());

    JDialog profileDialog = new JDialog((Frame) null, "User Settings", true);
    profileDialog.setTitle(tm.getText("title"));
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
    JLabel userIdLabel = new JLabel(tm.getText("user_id"));
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
    JLabel emailLabel = new JLabel(tm.getText("email"));
    profileDialog.add(emailLabel, gbc);
    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.ipadx = 0;
    PlaceholderTextField emailField = new PlaceholderTextField(tm.getText("email_placeholder"));
    emailField.setText(globalSettings.userProfileSettings.userEmail);
    profileDialog.add(emailField, gbc);

    // Telemetry
    gbc.gridx = 0;
    gbc.gridy = 2;
    gbc.gridwidth = 2;
    JCheckBox telemetryCheckbox = new JCheckBox(tm.getText("allow_telemetry"));
    telemetryCheckbox.setSelected(globalSettings.userProfileSettings.isTelemetryAllowed);
    telemetryCheckbox.addItemListener(e -> globalSettings.userProfileSettings.isTelemetryAllowed = telemetryCheckbox.isSelected());
    profileDialog.add(telemetryCheckbox, gbc);

    // Contacting
    gbc.gridx = 0;
    gbc.gridy = 3;
    gbc.gridwidth = 2;
    JCheckBox allowContactCheckbox = new JCheckBox(tm.getText("allow_contact"));
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
    JLabel statisticsHeader = new JLabel(tm.getText("statistics_header", globalSettings.statistics.startTime.substring(0, 10)));
    profileDialog.add(statisticsHeader, gbc);

    // Statistics
    gbc.gridwidth = 1;
    gbc.gridy = 6;
    gbc.weightx = 0;
    gbc.ipadx = ipadx;
    JLabel sessionsLabel = new JLabel(tm.getText("sessions_total"));
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
    JLabel startedJobsLabel = new JLabel(tm.getText("jobs_started"));
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
    JLabel completedJobsLabel = new JLabel(tm.getText("jobs_completed"));
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
    JButton updateButton = new JButton(tm.getText("update_button"));
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
    updateButton.addActionListener(evt -> FRAnalytics.buttonClicked("update_button", GsonProvider.GSON.toJson(globalSettings)));
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

  /**
   * Displays a window with the input message at the center of the screen.
   */
  public static WindowUserSettings show(BoardFrame p_board_frame)
  {
    return new WindowUserSettings(p_board_frame);
  }

  private void validateEmail(JTextField emailField, JButton updateButton)
  {
    String email = emailField.getText();
    boolean isValid = email.isEmpty() || email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    emailField.setBorder(isValid ? BorderFactory.createLineBorder(Color.GRAY) : BorderFactory.createLineBorder(Color.RED));
    updateButton.setEnabled(isValid);
  }
}