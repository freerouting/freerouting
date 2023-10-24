package app.freerouting.gui;

import app.freerouting.management.FRAnalytics;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/** Startup window visible when the program is loading. */
public class WindowMessage extends WindowBase {

  /** Creates a new instance of WindowMessage */
  private WindowMessage(String[] p_message_arr) {
    super(300, 100);

    final JPanel main_panel = new JPanel();
    final GridBagLayout gridbag = new GridBagLayout();
    main_panel.setLayout(gridbag);
    final GridBagConstraints gridbag_constraints = new GridBagConstraints();
    gridbag_constraints.insets = new Insets(40, 40, 40, 40);
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;
    for (int i = 0; i < p_message_arr.length; ++i) {
      final JLabel message_label = new JLabel();
      message_label.setText(p_message_arr[i]);

      gridbag.setConstraints(message_label, gridbag_constraints);
      main_panel.add(message_label, gridbag_constraints);
    }
    this.add(main_panel);
    this.pack();
    this.setLocation(500, 400);
    this.setVisible(true);
  }

  /** Displays a window with the input message at the center of the screen. */
  public static WindowMessage show(String p_message) {
    String[] message_arr = new String[1];
    message_arr[0] = p_message;
    return new WindowMessage(message_arr);
  }

  /** Displays a window with the input messages at the center of the screen. */
  public static WindowMessage show(String[] p_messages) {
    return new WindowMessage(p_messages);
  }

  /**
   * Calls a confirm dialog. Returns true, if the user confirmed the action or if p_message is null.
   */
  public static boolean confirm(String p_message) {
    if (p_message == null) {
      return true;
    }

    int selected_option = JOptionPane.showConfirmDialog(null, p_message, null, JOptionPane.YES_NO_OPTION);

    if (selected_option == JOptionPane.YES_OPTION)
    {
      FRAnalytics.buttonClicked("dialog_yes", p_message);
      return true;
    } else
    {
      FRAnalytics.buttonClicked("dialog_no", p_message);
      return false;
    }
  }

  /** Calls a dialog with an ok-button. */
  public static void ok(String p_message) {
    JOptionPane.showMessageDialog(null, p_message);
  }
}
