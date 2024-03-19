package app.freerouting.gui;

import java.awt.*;
import java.awt.Cursor;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * The `BoardPanelStatus` class represents a status bar at the lower border of the board frame.
 * It contains components such as message lines, current layer indicator, and cursor position.
 */
class BoardPanelStatus extends JPanel {
  // An icon for errors and warnings
  private final JLabel errorIcon;
  public final JLabel errorLabel;
  private final JLabel warningIcon;
  public final JLabel warningLabel;
  public final JLabel statusMessage;
  public final JLabel additionalMessage;
  public final JLabel currentLayer;
  public final JLabel mousePosition;
  public final JLabel unitLabel;

  // List to hold the listeners for error or warning label clicks
  private final List<ErrorOrWarningLabelClickedListener> errorOrWarningLabelClickedListeners = new ArrayList<>();

  /**
   * Creates a new instance of the `BoardPanelStatus` class.
   *
   * @param locale the locale to use for resource bundles
   */
  BoardPanelStatus(Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    setLayout(new BorderLayout());

    // Left panel with warnings, errors, and status messages
    JPanel leftMessagePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

    // Load the Material Icons for warnings and errors
    warningIcon = new JLabel();
    tm.setText(warningIcon, "{{icon:alert}}");
    errorIcon = new JLabel();
    tm.setText(errorIcon, "{{icon:close-octagon}}");

    // Initialize labels with icons
    warningLabel = new JLabel("0", SwingConstants.LEADING);
    errorLabel = new JLabel("0", SwingConstants.LEADING);

    // Add error and warning labels
    leftMessagePanel.add(errorIcon, BorderLayout.WEST);
    leftMessagePanel.add(errorLabel, BorderLayout.WEST);
    leftMessagePanel.add(warningIcon, BorderLayout.WEST);
    leftMessagePanel.add(warningLabel, BorderLayout.WEST);

    // Add mouse listeners for error and warning labels
    addErrorOrWarningLabelClickedListener();

    // Add margin to the right of the labels
    int top = 0;
    int left = 0;
    int bottom = 0;
    int right = 10;
    warningLabel.setBorder(new EmptyBorder(top, left, bottom, right));
    errorLabel.setBorder(new EmptyBorder(top, left, bottom, right));

    // Initialize status message label
    statusMessage = new JLabel();
    statusMessage.setHorizontalAlignment(SwingConstants.CENTER);
    tm.setText(statusMessage, "status_line");
    leftMessagePanel.add(statusMessage, BorderLayout.CENTER);

    // Initialize additional message label
    additionalMessage = new JLabel();
    tm.setText(additionalMessage, "additional_text_field");
    additionalMessage.setMaximumSize(new Dimension(300, 14));
    additionalMessage.setMinimumSize(new Dimension(140, 14));
    additionalMessage.setPreferredSize(new Dimension(180, 14));
    leftMessagePanel.add(additionalMessage, BorderLayout.EAST);
    add(leftMessagePanel, BorderLayout.CENTER);

    // Right panel with current layer and cursor position
    JPanel rightMessagePanel = new JPanel(new BorderLayout());
    rightMessagePanel.setMinimumSize(new Dimension(200, 20));
    rightMessagePanel.setOpaque(false);
    rightMessagePanel.setPreferredSize(new Dimension(450, 20));

    // Initialize current layer label
    currentLayer = new JLabel();
    tm.setText(currentLayer, "current_layer");
    rightMessagePanel.add(currentLayer, BorderLayout.CENTER);

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
  }

  /**
   * Adds mouse listeners for error and warning labels to handle click events.
   */
  private void addErrorOrWarningLabelClickedListener() {
    // Raise an event if the user clicks on the error or warning label
    errorIcon.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        raiseErrorOrWarningLabelClickedEvent();
      }
    });
    errorLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        raiseErrorOrWarningLabelClickedEvent();
      }
    });

    warningIcon.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        raiseErrorOrWarningLabelClickedEvent();
      }
    });
    warningLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        raiseErrorOrWarningLabelClickedEvent();
      }
    });

    // Change the mouse cursor to a hand when hovering over these labels
    errorIcon.setCursor(new Cursor(Cursor.HAND_CURSOR));
    errorLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
    warningIcon.setCursor(new Cursor(Cursor.HAND_CURSOR));
    warningLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
  }

  /**
   * Raises the `ErrorOrWarningLabelClicked` event for all registered listeners.
   */
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
   * The `ErrorOrWarningLabelClickedListener` interface defines a method to handle
   * the click event on the error or warning labels.
   */
  @FunctionalInterface
  public interface ErrorOrWarningLabelClickedListener {
    /**
     * Invoked when the error or warning label is clicked.
     */
    void errorOrWarningLabelClicked();
  }
}