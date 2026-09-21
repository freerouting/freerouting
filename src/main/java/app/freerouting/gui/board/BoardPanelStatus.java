package app.freerouting.gui.board;

import app.freerouting.Freerouting;
import app.freerouting.gui.a11y.A11y;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.gui.controls.SmartLabel;
import app.freerouting.gui.support.GuiTextManager;
import app.freerouting.gui.surveys.ButtonsSurveyRenderer;
import app.freerouting.gui.surveys.SurveyPopover;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.AppPaths;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.surveys.SurveyCache;
import app.freerouting.surveys.SurveyClient;
import app.freerouting.surveys.SurveyCoordinator;
import app.freerouting.surveys.SurveyDefinition;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * The `BoardPanelStatus` class represents a status bar at the lower border of the board frame. It
 * contains components such as message lines, current layer indicator, and cursor position.
 */
public class BoardPanelStatus extends JPanel {

  public final JLabel errorLabel;
  public final JLabel warningLabel;
  public final JLabel statusMessage;
  public final JLabel additionalMessage;
  public final JLabel currentLayer;
  public final JLabel currentBoardScore;
  public final JLabel mousePosition;
  public final JLabel unitLabel;
  public final JButton surveyTriggerButton;
  private SurveyCoordinator surveyCoordinator;
  // An icon for errors and warnings
  private final JPanel errorsWarningsPanel;
  private final JLabel errorIcon;
  private final JLabel warningIcon;
  // List to hold the listeners for error or warning label clicks
  private final List<ErrorOrWarningLabelClickedListener> errorOrWarningLabelClickedListeners =
      new ArrayList<>();

  /**
   * Creates a new instance of the `BoardPanelStatus` class.
   *
   * @param locale the locale to use for resource bundles
   */
  public BoardPanelStatus(Locale locale) {
    final GuiTextManager tm = new GuiTextManager(this.getClass(), locale);

    setLayout(new BorderLayout());

    // Left panel with warnings, errors, and status messages
    errorsWarningsPanel = new JPanel(new BorderLayout());

    // Load the Material Icons for warnings and errors
    warningIcon = new JLabel();
    tm.setText(warningIcon, "{{icon:alert}}");
    errorIcon = new JLabel();
    tm.setText(errorIcon, "{{icon:close-octagon}}");

    // Initialize labels with icons
    warningLabel = new JLabel("0", SwingConstants.LEADING);
    errorLabel = new JLabel("0", SwingConstants.LEADING);

    // Left-aligned panel for icons and counts
    JPanel countsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    countsPanel.add(errorIcon);
    countsPanel.add(errorLabel);
    countsPanel.add(warningIcon);
    countsPanel.add(warningLabel);
    errorsWarningsPanel.add(countsPanel, BorderLayout.WEST);

    // Add mouse listeners for error and warning labels
    installErrorOrWarningLabelClickedListener();

    // Add margin to the right of the labels
    int top = 0;
    int left = 0;
    int bottom = 0;
    int right = 10;
    warningLabel.setBorder(new EmptyBorder(top, left, bottom, right));
    errorLabel.setBorder(new EmptyBorder(top, left, bottom, right));

    // Initialize status message label with SmartLabel for auto-truncation
    statusMessage = new SmartLabel();
    statusMessage.setHorizontalAlignment(SwingConstants.CENTER);
    tm.setText(statusMessage, "status_line");
    errorsWarningsPanel.add(statusMessage, BorderLayout.CENTER);

    // Initialize additional message label with SmartLabel for auto-truncation
    additionalMessage = new SmartLabel();
    tm.setText(additionalMessage, "additional_text_field");
    additionalMessage.setMaximumSize(new Dimension(300, 14));
    additionalMessage.setMinimumSize(new Dimension(140, 14));
    additionalMessage.setPreferredSize(new Dimension(180, 14));
    errorsWarningsPanel.add(additionalMessage, BorderLayout.EAST);
    add(errorsWarningsPanel, BorderLayout.CENTER);

    // Right panel with current layer and cursor position
    JPanel rightMessagePanel = new JPanel(new BorderLayout());
    rightMessagePanel.setMinimumSize(new Dimension(200, 20));
    rightMessagePanel.setOpaque(false);
    rightMessagePanel.setPreferredSize(new Dimension(550, 20));

    // Initialize current layer label
    currentLayer = new JLabel();
    tm.setText(currentLayer, "currentLayer");
    rightMessagePanel.add(currentLayer, BorderLayout.CENTER);

    // Initialize current board score label
    currentBoardScore = new JLabel();
    tm.setText(currentBoardScore, "current_board_score");
    rightMessagePanel.add(currentBoardScore, BorderLayout.CENTER);

    // Create cursor panel
    JPanel cursorPanel = new JPanel(new BorderLayout());
    cursorPanel.setMinimumSize(new Dimension(220, 20));
    cursorPanel.setPreferredSize(new Dimension(340, 20));

    // Initialize mouse position label
    mousePosition = new JLabel();
    mousePosition.setText("X 0.00   Y 0.00");
    mousePosition.setMaximumSize(new Dimension(170, 14));
    mousePosition.setPreferredSize(new Dimension(170, 14));
    cursorPanel.add(mousePosition, BorderLayout.WEST);

    // Initialize cursor label
    unitLabel = new JLabel();
    unitLabel.setHorizontalAlignment(SwingConstants.CENTER);
    unitLabel.setText("unit");
    unitLabel.setMaximumSize(new Dimension(100, 14));
    unitLabel.setMinimumSize(new Dimension(50, 14));
    unitLabel.setPreferredSize(new Dimension(50, 14));

    // Initialize survey trigger pill in status bar (hidden by default)
    surveyTriggerButton = new JButton();
    surveyTriggerButton.setVisible(false);
    surveyTriggerButton.setFocusPainted(false);
    surveyTriggerButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    surveyTriggerButton.setFont(surveyTriggerButton.getFont().deriveFont(11.0f));
    surveyTriggerButton.setMargin(new Insets(1, 6, 1, 6));
    surveyTriggerButton.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 180), 1),
            BorderFactory.createEmptyBorder(1, 6, 1, 6)));

    JPanel unitAndSurveyPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
    unitAndSurveyPanel.setOpaque(false);
    unitAndSurveyPanel.add(unitLabel);
    unitAndSurveyPanel.add(surveyTriggerButton);
    cursorPanel.add(unitAndSurveyPanel, BorderLayout.EAST);

    rightMessagePanel.add(cursorPanel, BorderLayout.EAST);

    add(rightMessagePanel, BorderLayout.EAST);

    wireAccessibility(tm);

    initSurveys(createDefaultSurveyCoordinator());
  }

  /**
   * Registers stable, locale-independent locators (D22) and accessible names on the status-bar
   * controls so assistive technology and the a11y test harness can resolve them. Accessible names
   * reuse the already-translated visible text (no new resource-bundle keys, preserving cross-locale
   * parity).
   */
  private void wireAccessibility(GuiTextManager tm) {
    A11y.tag(statusMessage, GuiLocators.STATUS_MESSAGE);
    A11y.describe(statusMessage, statusMessage.getText(), null);

    A11y.tag(additionalMessage, GuiLocators.STATUS_ADDITIONAL_MESSAGE);
    A11y.describe(additionalMessage, additionalMessage.getText(), null);

    A11y.tag(currentLayer, GuiLocators.STATUS_CURRENT_LAYER);
    A11y.describe(currentLayer, currentLayer.getText(), null);

    A11y.tag(currentBoardScore, GuiLocators.STATUS_BOARD_SCORE);
    A11y.describe(currentBoardScore, currentBoardScore.getText(), null);

    A11y.tag(mousePosition, GuiLocators.STATUS_MOUSE_POSITION);
    A11y.tag(unitLabel, GuiLocators.STATUS_UNIT);

    A11y.tag(errorLabel, GuiLocators.STATUS_ERROR_COUNT);
    A11y.describe(errorLabel, tm.getText("errors"), null);
    A11y.tag(warningLabel, GuiLocators.STATUS_WARNING_COUNT);
    A11y.describe(warningLabel, tm.getText("warnings"), null);

    A11y.tag(surveyTriggerButton, GuiLocators.STATUS_SURVEY_TRIGGER);
    A11y.describe(surveyTriggerButton, "Quick Poll", null);
  }

  /** Adds mouse listeners for error and warning labels to handle click events. */
  private void installErrorOrWarningLabelClickedListener() {
    // Raise an event if the user clicks on the error or warning label
    errorsWarningsPanel.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            raiseErrorOrWarningLabelClickedEvent();
          }
        });

    // Change the mouse cursor to a hand when hovering over these labels
    errorsWarningsPanel.setCursor(new Cursor(Cursor.HAND_CURSOR));
  }

  /** Raises the `ErrorOrWarningLabelClicked` event for all registered listeners. */
  private void raiseErrorOrWarningLabelClickedEvent() {
    for (ErrorOrWarningLabelClickedListener listener : errorOrWarningLabelClickedListeners) {
      listener.errorOrWarningLabelClicked();
    }
  }

  /**
   * Adds an `ErrorOrWarningLabelClickedListener` to the list of listeners.
   *
   * @param listener the listener to be added
   */
  public void addErrorOrWarningLabelClickedListener(ErrorOrWarningLabelClickedListener listener) {
    errorOrWarningLabelClickedListeners.add(listener);
  }

  /**
   * Initializes the micro-survey trigger button with the provided coordinator. Polls for an
   * eligible survey asynchronously and displays the trigger pill on the EDT if available.
   *
   * @param coordinator the survey coordinator to use
   */
  public void initSurveys(SurveyCoordinator coordinator) {
    this.surveyCoordinator = coordinator;
    if (this.surveyCoordinator == null) {
      return;
    }
    this.surveyCoordinator
        .pollForSurvey()
        .thenAccept(
            survey -> {
              if (survey != null) {
                SwingUtilities.invokeLater(() -> showSurveyTrigger(survey));
              }
            });
  }

  /**
   * Displays the survey trigger pill for the active survey definition.
   *
   * @param survey the survey definition to present
   */
  public void showSurveyTrigger(SurveyDefinition survey) {
    if (survey == null) {
      return;
    }
    String label =
        (survey.topic != null && !survey.topic.isBlank()) ? "💬 " + survey.topic : "💬 Quick Poll";
    surveyTriggerButton.setText(label);
    surveyTriggerButton.setToolTipText(survey.question);
    for (var l : surveyTriggerButton.getActionListeners()) {
      surveyTriggerButton.removeActionListener(l);
    }
    surveyTriggerButton.addActionListener(
        _ -> {
          SurveyPopover popover =
              new SurveyPopover(
                  survey,
                  surveyCoordinator,
                  new ButtonsSurveyRenderer(),
                  () -> surveyTriggerButton.setVisible(false));
          popover.showAnchoredAbove(surveyTriggerButton);
        });
    surveyTriggerButton.setVisible(true);
    revalidate();
    repaint();
  }

  private static SurveyCoordinator createDefaultSurveyCoordinator() {
    try {
      if (Freerouting.globalSettings == null) {
        return null;
      }
      String userId =
          Freerouting.globalSettings.userProfileSettings != null
                  && Freerouting.globalSettings.userProfileSettings.userId != null
              ? Freerouting.globalSettings.userProfileSettings.userId.toString()
              : "";
      String version = GlobalSettings.getReleaseSafeVersion();
      SurveyCache cache = new SurveyCache(AppPaths.getDefaultDataDirectory());
      SurveyClient client = new SurveyClient();
      return new SurveyCoordinator(
          client,
          cache,
          userId,
          version,
          () ->
              Freerouting.globalSettings != null
                  && Freerouting.globalSettings.userProfileSettings != null
                  && Freerouting.globalSettings.userProfileSettings.isSurveysAllowed(
                      Freerouting.globalSettings.usageAndDiagnosticData.disableAnalytics));
    } catch (Exception e) {
      FRLogger.warn("Failed to initialize SurveyCoordinator: " + e.getMessage());
      return null;
    }
  }

  /**
   * The `ErrorOrWarningLabelClickedListener` interface defines a method to handle the click event
   * on the error or warning labels.
   */
  @FunctionalInterface
  public interface ErrorOrWarningLabelClickedListener {

    /** Invoked when the error or warning label is clicked. */
    public void errorOrWarningLabelClicked();
  }
}
