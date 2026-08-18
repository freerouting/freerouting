package app.freerouting.gui.board;

import app.freerouting.gui.a11y.A11y;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.gui.controls.SmartLabel;
import app.freerouting.gui.support.GuiTextManager;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
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
    rightMessagePanel.setPreferredSize(new Dimension(450, 20));

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
    cursorPanel.setMinimumSize(new Dimension(220, 14));
    cursorPanel.setPreferredSize(new Dimension(220, 14));

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
    cursorPanel.add(unitLabel, BorderLayout.EAST);

    rightMessagePanel.add(cursorPanel, BorderLayout.EAST);

    add(rightMessagePanel, BorderLayout.EAST);

    wireAccessibility(tm);
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
   * The `ErrorOrWarningLabelClickedListener` interface defines a method to handle the click event
   * on the error or warning labels.
   */
  @FunctionalInterface
  public interface ErrorOrWarningLabelClickedListener {

    /** Invoked when the error or warning label is clicked. */
    public void errorOrWarningLabelClicked();
  }
}
