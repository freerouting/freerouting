package app.freerouting.gui;

import static app.freerouting.Freerouting.globalSettings;

import app.freerouting.gui.a11y.A11y;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.util.TextManager;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * A dialog window that allows users to configure their settings, including telemetry sharing and
 * contact preferences, as well as view usage statistics and access the project's sponsorship
 * options.
 */
public final class WindowUserSettings extends WindowBase {

  private static final Dimension PREFERRED_VIEWPORT_SIZE = new Dimension(480, 570);
  private static final int SCROLL_UNIT_INCREMENT = 24;

  /** Creates and initializes a new user settings dialog window. */
  private WindowUserSettings(BoardFrame boardFrame) {
    super(480, 355);

    setLanguage(boardFrame.get_locale());

    JDialog profileDialog = new JDialog(boardFrame, "User Settings", true);
    profileDialog.setTitle(tm.getText("title"));
    JPanel contentPanel =
        createContentPanel(
            tm,
            currentValues(),
            new UserSettingsActions(
                email -> saveEmail(profileDialog, email),
                allowed -> globalSettings.userProfileSettings.isTelemetryAllowed = allowed,
                allowed -> globalSettings.userProfileSettings.isContactAllowed = allowed,
                WindowUserSettings::openSponsorLink));
    profileDialog.setContentPane(createScrollableSurface(contentPanel));
    profileDialog.pack();
    profileDialog.setLocationRelativeTo(boardFrame);
    fitDialogToUsableBounds(profileDialog, boardFrame);
    profileDialog.setVisible(true);
  }

  /**
   * Creates the User Settings content without constructing a top-level window.
   *
   * <p>This extraction lets the forced-headless GUI tests exercise the same controls used by the
   * dialog without constructing a top-level window.
   *
   * @param locale locale for translated labels and accessible names
   * @return the reusable User Settings content panel
   */
  static JPanel createContentOnly(Locale locale) {
    TextManager textManager = new TextManager(WindowUserSettings.class, locale);
    return createContentPanel(
        textManager,
        new UserSettingsValues("", "", false, false, "2025-01-01", 0, 0, 0),
        new UserSettingsActions(_ -> {}, _ -> {}, _ -> {}, () -> {}));
  }

  /** Returns the scrollable component-only surface used by the regression test. */
  static JComponent createComponentOnly(Locale locale) {
    return createScrollableSurface(createContentOnly(locale));
  }

  private static JScrollPane createScrollableSurface(JPanel contentPanel) {
    JScrollPane scrollPane =
        new JScrollPane(
            contentPanel,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
    scrollPane.setPreferredSize(new Dimension(PREFERRED_VIEWPORT_SIZE));
    return scrollPane;
  }

  private static void fitDialogToUsableBounds(JDialog dialog, Window owner) {
    GraphicsConfiguration graphicsConfiguration =
        owner == null ? dialog.getGraphicsConfiguration() : owner.getGraphicsConfiguration();
    if (graphicsConfiguration == null) {
      graphicsConfiguration = dialog.getGraphicsConfiguration();
    }
    if (graphicsConfiguration == null) {
      return;
    }

    Rectangle screenBounds = graphicsConfiguration.getBounds();
    Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration);
    Rectangle usableBounds =
        new Rectangle(
            screenBounds.x + screenInsets.left,
            screenBounds.y + screenInsets.top,
            screenBounds.width - screenInsets.left - screenInsets.right,
            screenBounds.height - screenInsets.top - screenInsets.bottom);

    Dimension size = dialog.getSize();
    int width = Math.min(size.width, usableBounds.width);
    int height = Math.min(size.height, usableBounds.height);
    dialog.setSize(width, height);

    Point location = dialog.getLocation();
    int right = usableBounds.x + usableBounds.width - width;
    int x = Math.max(usableBounds.x, Math.min(location.x, right));
    int y =
        Math.max(
            usableBounds.y, Math.min(location.y, usableBounds.y + usableBounds.height - height));
    dialog.setLocation(x, y);
  }

  private static JPanel createContentPanel(
      TextManager textManager, UserSettingsValues values, UserSettingsActions actions) {
    JPanel profilePanel = A11y.tag(new ScrollableContentPanel(), GuiLocators.USER_SETTINGS_ROOT);
    A11y.describe(profilePanel, textManager.getText("title"), null);

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
    JLabel userIdLabel = new JLabel(textManager.getText("user_id"));
    profilePanel.add(userIdLabel, gbc);
    gbc.gridx = 1;
    gbc.gridwidth = 3;
    gbc.weightx = 1.0;
    gbc.ipadx = 0;
    JTextField userIdField = new JTextField(values.userId());
    userIdField.setEditable(false);
    userIdLabel.setLabelFor(userIdField);
    profilePanel.add(userIdField, gbc);

    // Email
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.gridwidth = 1;
    gbc.weightx = 0;
    gbc.ipadx = ipadx;
    JLabel emailLabel = new JLabel(textManager.getText("email"));
    profilePanel.add(emailLabel, gbc);
    gbc.gridx = 1;
    gbc.gridwidth = 3;
    gbc.weightx = 1.0;
    gbc.ipadx = 0;

    // Ghost placeholder text field (disappears on click without needing deletion)
    final String placeholder = textManager.getText("email_placeholder");

    JTextField emailField =
        A11y.tag(
            new JTextField(values.userEmail()) {
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
                          + ((getHeight() - getInsets().top - getInsets().bottom - fm.getHeight())
                              / 2);

                  // Draw the pre-loaded string
                  g2.drawString(placeholder, x, y);
                  g2.dispose();
                }
              }
            },
            GuiLocators.USER_SETTINGS_EMAIL);
    A11y.describe(emailField, textManager.getText("email_hint"), null);
    emailLabel.setLabelFor(emailField);
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
    profilePanel.add(emailField, gbc);

    // Email hint
    gbc.gridx = 1;
    gbc.gridy = 2;
    gbc.gridwidth = 3;
    gbc.weightx = 1.0;
    gbc.ipadx = 0;
    JLabel emailHint = new JLabel(textManager.getText("email_hint"));
    profilePanel.add(emailHint, gbc);

    // Telemetry
    gbc.gridx = 0;
    gbc.gridy = 3;
    gbc.gridwidth = 4;
    JCheckBox telemetryCheckbox = new JCheckBox(textManager.getText("allow_telemetry"));
    telemetryCheckbox.setSelected(values.telemetryAllowed());
    telemetryCheckbox.addItemListener(
        _ -> actions.telemetryChanged().accept(telemetryCheckbox.isSelected()));
    profilePanel.add(telemetryCheckbox, gbc);

    // Contacting
    gbc.gridx = 0;
    gbc.gridy = 4;
    gbc.gridwidth = 4;
    JCheckBox allowContactCheckbox = new JCheckBox(textManager.getText("allow_contact"));
    allowContactCheckbox.setSelected(values.contactAllowed());
    allowContactCheckbox.addItemListener(
        _ -> actions.contactChanged().accept(allowContactCheckbox.isSelected()));
    profilePanel.add(allowContactCheckbox, gbc);

    // Update button
    gbc.gridx = 0;
    gbc.gridy = 5;
    gbc.gridwidth = 4;
    gbc.anchor = GridBagConstraints.CENTER;
    JButton updateButton =
        A11y.tag(
            new JButton(textManager.getText("save_settings_button")),
            GuiLocators.USER_SETTINGS_SAVE);
    A11y.describe(updateButton, textManager.getText("save_settings_button"), null);
    var buttonSize = new Dimension(100, updateButton.getPreferredSize().height);
    updateButton.setPreferredSize(buttonSize);
    updateButton.setMaximumSize(buttonSize);
    updateButton.setEnabled(false);
    updateButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            actions.saveEmail().accept(emailField.getText());
          }
        });
    profilePanel.add(updateButton, gbc);

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
    if (values.userEmail().isEmpty()) {
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
    profilePanel.add(separator, gbc);

    // Statistics header
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = 4;
    gbc.gridy = 7;
    gbc.weightx = 0;
    gbc.ipadx = ipadx;
    JLabel statisticsHeader =
        new JLabel(
            textManager.getText("stats_header", values.statisticsStartDate().substring(0, 10)));
    profilePanel.add(statisticsHeader, gbc);

    // Statistics
    gbc.gridwidth = 1;
    gbc.gridy = 8;
    gbc.gridx = 0;
    gbc.weightx = 0;
    gbc.ipadx = ipadx;
    JLabel sessionsLabel = new JLabel(textManager.getText("sessions_total"));
    profilePanel.add(sessionsLabel, gbc);
    gbc.gridx = 1;
    gbc.gridwidth = 3;
    gbc.weightx = 1.0;
    gbc.ipadx = 0;
    JLabel sessionsValue = new JLabel(values.sessionsTotal().toString());
    profilePanel.add(sessionsValue, gbc);

    gbc.gridx = 0;
    gbc.gridy = 9;
    gbc.gridwidth = 1;
    gbc.weightx = 0;
    gbc.ipadx = ipadx;
    JLabel startedJobsLabel = new JLabel(textManager.getText("jobs_started"));
    profilePanel.add(startedJobsLabel, gbc);
    gbc.gridx = 1;
    gbc.gridwidth = 3;
    gbc.weightx = 1.0;
    gbc.ipadx = 0;
    JLabel startedJobsValue = new JLabel(values.jobsStarted().toString());
    profilePanel.add(startedJobsValue, gbc);

    gbc.gridx = 0;
    gbc.gridy = 10;
    gbc.gridwidth = 1;
    gbc.weightx = 0;
    gbc.ipadx = ipadx;
    JLabel completedJobsLabel = new JLabel(textManager.getText("jobs_completed"));
    profilePanel.add(completedJobsLabel, gbc);
    gbc.gridx = 1;
    gbc.gridwidth = 3;
    gbc.weightx = 1.0;
    gbc.ipadx = 0;
    JLabel completedJobsValue = new JLabel(values.jobsCompleted().toString());
    profilePanel.add(completedJobsValue, gbc);

    // Visual separation for sponsor message
    gbc.gridx = 0;
    gbc.gridy = 11;
    gbc.gridwidth = 4;
    gbc.fill = GridBagConstraints.BOTH;
    JSeparator separator2 = new JSeparator();
    profilePanel.add(separator2, gbc);

    // Sponsor message
    gbc.gridx = 0;
    gbc.gridy = 12;
    gbc.gridwidth = 4;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    JLabel sponsorMessage = new JLabel(textManager.getText("sponsor_message"));
    profilePanel.add(sponsorMessage, gbc);

    // Sponsor button
    gbc.gridy = 13;
    gbc.gridx = 0;
    gbc.gridwidth = 4;
    gbc.weighty = 1.0;
    gbc.anchor = GridBagConstraints.PAGE_END;
    gbc.fill = GridBagConstraints.NONE;
    JButton sponsorButton =
        A11y.tag(
            new JButton(">  " + textManager.getText("sponsor_button") + "  <"),
            GuiLocators.USER_SETTINGS_SPONSOR);
    A11y.describe(sponsorButton, textManager.getText("sponsor_button"), null);
    sponsorButton.setFont(sponsorButton.getFont().deriveFont(java.awt.Font.BOLD, 14f));
    sponsorButton.setForeground(new Color(200, 16, 46));
    var sponsorButtonSize = new Dimension(220, sponsorButton.getPreferredSize().height + 4);
    sponsorButton.setPreferredSize(sponsorButtonSize);
    sponsorButton.setMaximumSize(sponsorButtonSize);
    sponsorButton.addActionListener(_ -> actions.sponsor().run());
    profilePanel.add(sponsorButton, gbc);

    return profilePanel;
  }

  private static UserSettingsValues currentValues() {
    return new UserSettingsValues(
        globalSettings.userProfileSettings.userId,
        globalSettings.userProfileSettings.userEmail,
        globalSettings.userProfileSettings.isTelemetryAllowed,
        globalSettings.userProfileSettings.isContactAllowed,
        globalSettings.statistics.startTime,
        globalSettings.statistics.sessionsTotal,
        globalSettings.statistics.jobsStarted,
        globalSettings.statistics.jobsCompleted);
  }

  private static void saveEmail(JDialog profileDialog, String email) {
    globalSettings.userProfileSettings.userEmail = email;
    FRAnalytics.setUserId(
        globalSettings.userProfileSettings.userId, globalSettings.userProfileSettings.userEmail);
    FRAnalytics.refreshIdentity();
    FRAnalytics.profileUpdated();
    try {
      GlobalSettings.saveAsJson(globalSettings);
    } catch (IOException ex) {
      FRLogger.error("Failed to save user profile settings", ex);
    }
    profileDialog.dispose();
  }

  private static void openSponsorLink() {
    try {
      Desktop.getDesktop().browse(new URI("https://github.com/sponsors/andrasfuchs"));
    } catch (Exception ex) {
      FRLogger.error("Failed to open sponsor link", ex);
    }
  }

  private record UserSettingsValues(
      String userId,
      String userEmail,
      boolean telemetryAllowed,
      boolean contactAllowed,
      String statisticsStartDate,
      Integer sessionsTotal,
      Integer jobsStarted,
      Integer jobsCompleted) {}

  private record UserSettingsActions(
      Consumer<String> saveEmail,
      Consumer<Boolean> telemetryChanged,
      Consumer<Boolean> contactChanged,
      Runnable sponsor) {}

  private static final class ScrollableContentPanel extends JPanel implements Scrollable {

    private ScrollableContentPanel() {
      super(new GridBagLayout());
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return new Dimension(PREFERRED_VIEWPORT_SIZE);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
      return SCROLL_UNIT_INCREMENT;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
      int extent =
          orientation == javax.swing.SwingConstants.VERTICAL
              ? visibleRect.height
              : visibleRect.width;
      return Math.max(SCROLL_UNIT_INCREMENT, extent - SCROLL_UNIT_INCREMENT);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      return false;
    }
  }

  /**
   * Displays the user settings dialog window, centered relative to the parent board frame.
   *
   * @param boardFrame the parent board frame
   * @return the created WindowUserSettings instance
   */
  public static WindowUserSettings show(BoardFrame boardFrame) {
    return new WindowUserSettings(boardFrame);
  }

  /** Validates the email address input and updates the save control state. */
  private static void validateEmail(JTextField emailField, JButton updateButton) {
    String email = emailField.getText();
    boolean isValid = email.isEmpty() || email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    emailField.setBorder(
        isValid
            ? BorderFactory.createLineBorder(Color.GRAY)
            : BorderFactory.createLineBorder(Color.RED));
    updateButton.setEnabled(isValid);
  }
}
