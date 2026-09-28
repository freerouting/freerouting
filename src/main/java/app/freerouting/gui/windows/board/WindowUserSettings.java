package app.freerouting.gui.windows.board;

import static app.freerouting.Freerouting.globalSettings;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.gui.board.BoardFrame;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.util.TextManager;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.net.URI;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * A dialog window that allows users to configure their settings, including telemetry sharing and
 * contact preferences, as well as view usage statistics and access the project's sponsorship
 * options.
 */
public final class WindowUserSettings {

  private final TextManager tm;

  /**
   * Creates and initializes a new user settings dialog window.
   *
   * @param boardFrame the parent board frame, used to retrieve the active locale settings.
   */
  private WindowUserSettings(BoardFrame boardFrame) {
    this.tm = new TextManager(WindowUserSettings.class, boardFrame.getLocale());

    JDialog profileDialog = new JDialog(boardFrame, tm.getText("title"), true);
    JPanel contentPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(5, 15, 5, 15);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;

    final int ipadx = 30;

    // User ID
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 1;
    gbc.weightx = 0;
    gbc.ipadx = ipadx;
    JLabel userIdLabel = new JLabel(tm.getText("user_id"));
    contentPanel.add(userIdLabel, gbc);
    gbc.gridx = 1;
    gbc.gridwidth = 3;
    gbc.weightx = 1.0;
    gbc.ipadx = 0;
    JTextField userIdField = new JTextField(globalSettings.userProfileSettings.userId);
    userIdField.setEditable(false);
    contentPanel.add(userIdField, gbc);

    // Email
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.gridwidth = 1;
    gbc.weightx = 0;
    gbc.ipadx = ipadx;
    JLabel emailLabel = new JLabel(tm.getText("email"));
    contentPanel.add(emailLabel, gbc);
    gbc.gridx = 1;
    gbc.gridwidth = 3;
    gbc.weightx = 1.0;
    gbc.ipadx = 0;

    // Ghost placeholder text field (disappears on click without needing deletion)
    final String placeholder = tm.getText("email_placeholder");

    JTextField emailField =
        new JTextField(globalSettings.userProfileSettings.userEmail) {
          @Override
          protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (getText().isEmpty() && !isFocusOwner()) {
              Graphics2D g2 = (Graphics2D) g.create();
              g2.setColor(Color.GRAY);

              // Cache the FontMetrics calculation locally
              var fm = g2.getFontMetrics();

              int x = getInsets().left;
              int y =
                  fm.getAscent()
                      + getInsets().top
                      + ((getHeight() - getInsets().top - getInsets().bottom - fm.getHeight()) / 2);

              // Draw the pre-loaded string
              g2.drawString(placeholder, x, y);
              g2.dispose();
            }
          }
        };
    emailField.addFocusListener(
        new FocusAdapter() {
          @Override
          public void focusGained(FocusEvent e) {
            emailField.repaint();
          }

          @Override
          public void focusLost(FocusEvent e) {
            emailField.repaint();
          }
        });
    contentPanel.add(emailField, gbc);

    // Email hint
    gbc.gridx = 1;
    gbc.gridy = 2;
    gbc.gridwidth = 3;
    gbc.weightx = 1.0;
    gbc.ipadx = 0;
    JLabel emailHint = new JLabel(tm.getText("email_hint"));
    contentPanel.add(emailHint, gbc);

    // Telemetry
    gbc.gridx = 0;
    gbc.gridy = 3;
    gbc.gridwidth = 4;
    JCheckBox telemetryCheckbox = new JCheckBox(tm.getText("allow_telemetry"));
    telemetryCheckbox.setSelected(globalSettings.userProfileSettings.isTelemetryAllowed);
    telemetryCheckbox.addItemListener(
        _ ->
            globalSettings.userProfileSettings.isTelemetryAllowed = telemetryCheckbox.isSelected());
    contentPanel.add(telemetryCheckbox, gbc);

    // Contacting
    gbc.gridx = 0;
    gbc.gridy = 4;
    gbc.gridwidth = 4;
    JCheckBox allowContactCheckbox = new JCheckBox(tm.getText("allow_contact"));
    allowContactCheckbox.setSelected(globalSettings.userProfileSettings.isContactAllowed);
    allowContactCheckbox.addItemListener(
        _ ->
            globalSettings.userProfileSettings.isContactAllowed =
                allowContactCheckbox.isSelected());
    contentPanel.add(allowContactCheckbox, gbc);

    // Update button
    gbc.gridx = 0;
    gbc.gridy = 5;
    gbc.gridwidth = 4;
    gbc.anchor = GridBagConstraints.CENTER;
    JButton updateButton = new JButton(tm.getText("save_settings_button"));
    var buttonSize = new Dimension(100, updateButton.getPreferredSize().height);
    updateButton.setPreferredSize(buttonSize);
    updateButton.setMaximumSize(buttonSize);
    updateButton.setEnabled(false);
    updateButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            globalSettings.userProfileSettings.userEmail = emailField.getText();
            FRAnalytics.setUserId(
                globalSettings.userProfileSettings.userId,
                globalSettings.userProfileSettings.userEmail);
            FRAnalytics.refreshIdentity();
            FRAnalytics.profileUpdated();
            try {
              GlobalSettings.saveAsJson(globalSettings);
            } catch (IOException ex) {
              FRLogger.error("Failed to save user profile settings", ex);
            }
            profileDialog.dispose();
          }
        });
    updateButton.addActionListener(
        _ -> {
          /* profile analytics emitted in save handler */
        });
    contentPanel.add(updateButton, gbc);

    // Enable the Update button if email or checkboxes change
    DocumentListener documentListener =
        new DocumentListener() {
          @Override
          public void insertUpdate(DocumentEvent e) {
            validateEmail(emailField, updateButton);
          }

          @Override
          public void removeUpdate(DocumentEvent e) {
            validateEmail(emailField, updateButton);
          }

          @Override
          public void changedUpdate(DocumentEvent e) {
            validateEmail(emailField, updateButton);
          }
        };
    emailField.getDocument().addDocumentListener(documentListener);

    ItemListener itemListener = _ -> validateEmail(emailField, updateButton);
    telemetryCheckbox.addItemListener(itemListener);
    allowContactCheckbox.addItemListener(itemListener);

    validateEmail(emailField, updateButton);
    if (globalSettings.userProfileSettings.userEmail.isEmpty()) {
      emailField.requestFocus();
      emailField.setBorder(BorderFactory.createLineBorder(Color.RED));
    } else {
      updateButton.requestFocus();
    }

    // Visual separation for statistics
    gbc.gridx = 0;
    gbc.gridy = 6;
    gbc.gridwidth = 4;
    gbc.fill = GridBagConstraints.BOTH;
    JSeparator separator = new JSeparator();
    contentPanel.add(separator, gbc);

    // Statistics header
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = 4;
    gbc.gridy = 7;
    gbc.weightx = 0;
    gbc.ipadx = ipadx;
    JLabel statisticsHeader =
        new JLabel(
            tm.getText("stats_header", globalSettings.statistics.startTime.substring(0, 10)));
    contentPanel.add(statisticsHeader, gbc);

    // Statistics
    gbc.gridwidth = 1;
    gbc.gridy = 8;
    gbc.gridx = 0;
    gbc.weightx = 0;
    gbc.ipadx = ipadx;
    JLabel sessionsLabel = new JLabel(tm.getText("sessions_total"));
    contentPanel.add(sessionsLabel, gbc);
    gbc.gridx = 1;
    gbc.gridwidth = 3;
    gbc.weightx = 1.0;
    gbc.ipadx = 0;
    JLabel sessionsValue = new JLabel(globalSettings.statistics.sessionsTotal.toString());
    contentPanel.add(sessionsValue, gbc);

    gbc.gridx = 0;
    gbc.gridy = 9;
    gbc.gridwidth = 1;
    gbc.weightx = 0;
    gbc.ipadx = ipadx;
    JLabel startedJobsLabel = new JLabel(tm.getText("jobs_started"));
    contentPanel.add(startedJobsLabel, gbc);
    gbc.gridx = 1;
    gbc.gridwidth = 3;
    gbc.weightx = 1.0;
    gbc.ipadx = 0;
    JLabel startedJobsValue = new JLabel(globalSettings.statistics.jobsStarted.toString());
    contentPanel.add(startedJobsValue, gbc);

    gbc.gridx = 0;
    gbc.gridy = 10;
    gbc.gridwidth = 1;
    gbc.weightx = 0;
    gbc.ipadx = ipadx;
    JLabel completedJobsLabel = new JLabel(tm.getText("jobs_completed"));
    contentPanel.add(completedJobsLabel, gbc);
    gbc.gridx = 1;
    gbc.gridwidth = 3;
    gbc.weightx = 1.0;
    gbc.ipadx = 0;
    JLabel completedJobsValue = new JLabel(globalSettings.statistics.jobsCompleted.toString());
    contentPanel.add(completedJobsValue, gbc);

    // Visual separation for sponsor message
    gbc.gridx = 0;
    gbc.gridy = 11;
    gbc.gridwidth = 4;
    gbc.fill = GridBagConstraints.BOTH;
    JSeparator separator2 = new JSeparator();
    contentPanel.add(separator2, gbc);

    // Sponsor message
    gbc.gridx = 0;
    gbc.gridy = 12;
    gbc.gridwidth = 4;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    JTextArea sponsorMsgArea = new JTextArea(tm.getText("sponsor_message"));
    sponsorMsgArea.setFont(UIManager.getFont("Label.font"));
    sponsorMsgArea.setLineWrap(true);
    sponsorMsgArea.setWrapStyleWord(true);
    sponsorMsgArea.setOpaque(false);
    sponsorMsgArea.setEditable(false);
    sponsorMsgArea.setFocusable(false);
    sponsorMsgArea.setColumns(28);
    contentPanel.add(sponsorMsgArea, gbc);

    // Sponsor button
    gbc.gridy = 13;
    gbc.gridx = 0;
    gbc.gridwidth = 4;
    gbc.weighty = 0.0;
    gbc.insets = new Insets(8, 15, 12, 15);
    gbc.anchor = GridBagConstraints.CENTER;
    gbc.fill = GridBagConstraints.NONE;
    JButton sponsorButton = new JButton(tm.getText("sponsor_button"));
    sponsorButton.setFont(sponsorButton.getFont().deriveFont(java.awt.Font.BOLD, 14f));
    sponsorButton.setForeground(new Color(200, 16, 46));
    var sponsorButtonSize = new Dimension(220, sponsorButton.getPreferredSize().height + 4);
    sponsorButton.setPreferredSize(sponsorButtonSize);
    sponsorButton.setMaximumSize(sponsorButtonSize);
    sponsorButton.addActionListener(
        _ -> {
          try {
            Desktop.getDesktop().browse(new URI("https://www.freerouting.app/donate.html"));
          } catch (Exception ex) {
            FRLogger.error("Failed to open sponsor link", ex);
          }
        });
    contentPanel.add(sponsorButton, gbc);
    profileDialog.add(WindowBase.createScrollableContainer(contentPanel));
    profileDialog.setResizable(false);
    profileDialog.pack();
    profileDialog.setSize(profileDialog.getWidth(), profileDialog.getHeight() + 30);
    WindowBase.clampWindowHeight(profileDialog, boardFrame);
    profileDialog.setVisible(true);
  }

  /**
   * Displays the user settings dialog window, centered relative to the screen.
   *
   * @param boardFrame the parent board frame.
   * @return the created WindowUserSettings instance.
   */
  public static WindowUserSettings show(BoardFrame boardFrame) {
    return new WindowUserSettings(boardFrame);
  }

  /**
   * Validates the email address input and enables or disables the update/save button.
   *
   * @param emailField the text field containing the email input.
   * @param updateButton the button to enable or disable based on validation status.
   */
  private void validateEmail(JTextField emailField, JButton updateButton) {
    String email = emailField.getText();
    boolean isValid = email.isEmpty() || email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    emailField.setBorder(
        isValid
            ? BorderFactory.createLineBorder(Color.GRAY)
            : BorderFactory.createLineBorder(Color.RED));
    updateButton.setEnabled(isValid);
  }
}
