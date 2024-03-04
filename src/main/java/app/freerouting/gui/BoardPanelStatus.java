package app.freerouting.gui;

import java.awt.FlowLayout;
import java.awt.Image;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

/**
 * Status bar at the lower border of the board frame containing amongst others the message line and the
 * current layer and cursor position.
 */
class BoardPanelStatus extends JPanel {
  // An icon for errors and warnings
  final JLabel errorLabel;
  final JLabel warningLabel;
  final JLabel status_message;
  final JLabel add_message;
  final JLabel current_layer;
  final JLabel mouse_position;
  /** Creates a new instance of BoardStatusPanel */
  BoardPanelStatus(Locale p_locale) {
    ResourceBundle resources =
        ResourceBundle.getBundle("app.freerouting.gui.BoardPanelStatus", p_locale);
    this.setLayout(new BorderLayout());

    // The status bar is separated into two parts.

    // The left part contains the warnings and errors icons and status message.
    JPanel left_message_panel = new JPanel();
    left_message_panel.setLayout(new FlowLayout(FlowLayout.LEFT));

    // Get warning and error icons from UIManager
    Icon originalWarningIcon = UIManager.getIcon("OptionPane.warningIcon");
    Icon originalErrorIcon = UIManager.getIcon("OptionPane.errorIcon");

    // Resize icons to 16x16 pixels
    Icon warningIcon = new ImageIcon(((ImageIcon) originalWarningIcon).getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH));
    Icon errorIcon = new ImageIcon(((ImageIcon) originalErrorIcon).getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH));

    // You can then use these icons in your JLabels or other components
    warningLabel = new JLabel("0", warningIcon, SwingConstants.LEADING);
    errorLabel = new JLabel("0", errorIcon, SwingConstants.LEADING);

    // Add the components to the status bar
    left_message_panel.add(errorLabel, BorderLayout.WEST);
    left_message_panel.add(warningLabel, BorderLayout.WEST);

    // Raise an event if the user clicks on the error or warning label
    errorLabel.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        // Raise the event
        for (ErrorOrWarningLabelClickedListener listener : errorOrWarningLabelClickedListeners) {
          listener.errorOrWarningLabelClicked();
        }
      }
    });
    warningLabel.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        // Raise the event
        for (ErrorOrWarningLabelClickedListener listener : errorOrWarningLabelClickedListeners) {
          listener.errorOrWarningLabelClicked();
        }
      }
    });

    // Add margin to the right of the labels
    int top = 0;
    int left = 0;
    int bottom = 0;
    int right = 10; // Adjust the right margin as needed
    warningLabel.setBorder(new EmptyBorder(top, left, bottom, right));
    errorLabel.setBorder(new EmptyBorder(top, left, bottom, right));

    status_message = new JLabel();
    status_message.setHorizontalAlignment(SwingConstants.CENTER);
    status_message.setText(resources.getString("status_line"));
    left_message_panel.add(status_message, BorderLayout.CENTER);

    add_message = new JLabel();
    add_message.setText(resources.getString("additional_text_field"));
    add_message.setMaximumSize(new Dimension(300, 14));
    add_message.setMinimumSize(new Dimension(140, 14));
    add_message.setPreferredSize(new Dimension(180, 14));
    left_message_panel.add(add_message, BorderLayout.EAST);

    this.add(left_message_panel, BorderLayout.CENTER);

    // The right part contains the current layer and cursor position.
    JPanel right_message_panel = new JPanel();
    right_message_panel.setLayout(new BorderLayout());

    right_message_panel.setMinimumSize(new Dimension(200, 20));
    right_message_panel.setOpaque(false);
    right_message_panel.setPreferredSize(new Dimension(450, 20));

    current_layer = new JLabel();
    current_layer.setText(resources.getString("current_layer"));
    right_message_panel.add(current_layer, BorderLayout.CENTER);

    JPanel cursor_panel = new JPanel();
    cursor_panel.setLayout(new BorderLayout());
    cursor_panel.setMinimumSize(new Dimension(220, 14));
    cursor_panel.setPreferredSize(new Dimension(220, 14));

    JLabel cursor = new JLabel();
    cursor.setHorizontalAlignment(SwingConstants.CENTER);
    cursor.setText(resources.getString("cursor"));
    cursor.setMaximumSize(new Dimension(100, 14));
    cursor.setMinimumSize(new Dimension(50, 14));
    cursor.setPreferredSize(new Dimension(50, 14));
    cursor_panel.add(cursor, BorderLayout.WEST);

    mouse_position = new JLabel();
    mouse_position.setText("(0,0)");
    mouse_position.setMaximumSize(new Dimension(170, 14));
    mouse_position.setPreferredSize(new Dimension(170, 14));
    cursor_panel.add(mouse_position, BorderLayout.EAST);

    right_message_panel.add(cursor_panel, BorderLayout.EAST);

    this.add(right_message_panel, BorderLayout.EAST);
  }

  // Event to be raised when a log entry is added
  public static interface ErrorOrWarningLabelClickedListener {
    void errorOrWarningLabelClicked();
  }

  private final List<ErrorOrWarningLabelClickedListener> errorOrWarningLabelClickedListeners = new ArrayList<>();

  public void addErrorOrWarningLabelClickedListener(ErrorOrWarningLabelClickedListener listener)
  {
    errorOrWarningLabelClickedListeners.add(listener);
  }
}
