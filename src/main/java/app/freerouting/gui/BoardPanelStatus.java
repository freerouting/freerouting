package app.freerouting.gui;

import app.freerouting.management.TextManager;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/**
 * The `BoardPanelStatus` class represents a status bar at the lower border of the board frame. 
 * It handles the positioning of layout messages, cursor telemetry, and system trackers.
 */
class BoardPanelStatus extends JPanel {

  public final JLabel errorLabel;
  public final JLabel warningLabel;
  public final JLabel statusMessage;
  public final JLabel additionalMessage;
  public final JLabel currentLayer;
  public final JLabel currentBoardScore;
  public final JLabel mousePosition;
  public final JLabel unitLabel;
  
  private final JPanel errorsWarningsPanel;
  private final JLabel errorIcon;
  private final JLabel warningIcon;
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

    addErrorOrWarningLabelClickedListener();

    int top = 0;
    int left = 0;
    int bottom = 0;
    int right = 10;
    warningLabel.setBorder(new EmptyBorder(top, left, bottom, right));
    errorLabel.setBorder(new EmptyBorder(top, left, bottom, right));

    statusMessage = new SmartLabel();
    statusMessage.setHorizontalAlignment(SwingConstants.CENTER);
    tm.setText(statusMessage, "status_line");
    errorsWarningsPanel.add(statusMessage, BorderLayout.CENTER);

    additionalMessage = new SmartLabel();
    tm.setText(additionalMessage, "additional_text_field");
    additionalMessage.setMaximumSize(new Dimension(300, 14));
    additionalMessage.setMinimumSize(new Dimension(140, 14));
    additionalMessage.setPreferredSize(new Dimension(180, 14));
    errorsWarningsPanel.add(additionalMessage, BorderLayout.EAST);
    add(errorsWarningsPanel, BorderLayout.CENTER);

    // -------------------------------------------------------------------------
    // RIGHT PANEL FIX: Implemented a rigid BoxLayout to strictly prevent 
    // any text element from physically overlapping another text element.
    // -------------------------------------------------------------------------
    
    JPanel rightMessagePanel = new JPanel();
    rightMessagePanel.setLayout(new BoxLayout(rightMessagePanel, BoxLayout.X_AXIS));
    rightMessagePanel.setOpaque(false);

    // Initialize current board score label
    currentBoardScore = new JLabel();
    tm.setText(currentBoardScore, "current_board_score");
    currentBoardScore.setAlignmentY(Component.CENTER_ALIGNMENT);

    // Initialize current layer label
    currentLayer = new JLabel();
    tm.setText(currentLayer, "current_layer");
    currentLayer.setAlignmentY(Component.CENTER_ALIGNMENT);

    // Initialize mouse position label 
    mousePosition = new JLabel("X 0.00   Y 0.00");
    mousePosition.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
    mousePosition.setAlignmentY(Component.CENTER_ALIGNMENT);

    // Initialize unit label
    unitLabel = new JLabel("unit");
    unitLabel.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
    unitLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
    
    // Inject our standalone system metrics component cleanly
    // FIX: Passed 'locale' here!
    SysInfoStatusBarItem sysInfo = new SysInfoStatusBarItem(locale);
    sysInfo.setAlignmentY(Component.CENTER_ALIGNMENT);
    
    // Assemble the components sequentially using rigid, unbreakable spacer struts
    rightMessagePanel.add(currentBoardScore);
    rightMessagePanel.add(Box.createHorizontalStrut(25)); // Solid 25px gap
    
    rightMessagePanel.add(currentLayer);
    rightMessagePanel.add(Box.createHorizontalStrut(30)); // Solid 30px gap
    
    rightMessagePanel.add(mousePosition);
    rightMessagePanel.add(Box.createHorizontalStrut(15)); // Solid 15px gap
    
    rightMessagePanel.add(unitLabel);
    rightMessagePanel.add(Box.createHorizontalStrut(20)); // Solid 20px gap
    
    rightMessagePanel.add(sysInfo);
    rightMessagePanel.add(Box.createHorizontalStrut(10)); // End padding gap
    
    add(rightMessagePanel, BorderLayout.EAST);
  }

  private void addErrorOrWarningLabelClickedListener() {
    errorsWarningsPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        raiseErrorOrWarningLabelClickedEvent();
      }
    });
    errorsWarningsPanel.setCursor(new Cursor(Cursor.HAND_CURSOR));
  }

  private void raiseErrorOrWarningLabelClickedEvent() {
    for (ErrorOrWarningLabelClickedListener listener : errorOrWarningLabelClickedListeners) {
      listener.errorOrWarningLabelClicked();
    }
  }

  public void addErrorOrWarningLabelClickedListener(ErrorOrWarningLabelClickedListener listener) {
    errorOrWarningLabelClickedListeners.add(listener);
  }

  @FunctionalInterface
  public interface ErrorOrWarningLabelClickedListener {
    void errorOrWarningLabelClicked();
  }
}