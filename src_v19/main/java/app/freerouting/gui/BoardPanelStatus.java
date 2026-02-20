package app.freerouting.gui;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Panel at the lower border of the board frame containing amongst others the message line and the
 * current layer and cursor position.
 */
class BoardPanelStatus extends JPanel {

  final JLabel status_message;
  final JLabel add_message;
  final JLabel current_layer;
  final JLabel mouse_position;
  /** Creates a new instance of BoardStatusPanel */
  BoardPanelStatus(Locale p_locale) {
    ResourceBundle resources =
        ResourceBundle.getBundle("app.freerouting.gui.BoardPanelStatus", p_locale);
    this.setLayout(new BorderLayout());

    JPanel left_message_panel = new JPanel();
    left_message_panel.setLayout(new BorderLayout());

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
}
