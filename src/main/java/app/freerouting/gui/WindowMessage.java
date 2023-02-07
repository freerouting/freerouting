package app.freerouting.gui;

/** Startup window visible when the program is loading. */
public class WindowMessage extends WindowBase {

  /** Creates a new instance of WindowMessage */
  private WindowMessage(String[] p_message_arr) {
    super(300, 100);

    final javax.swing.JPanel main_panel = new javax.swing.JPanel();
    final java.awt.GridBagLayout gridbag = new java.awt.GridBagLayout();
    main_panel.setLayout(gridbag);
    final java.awt.GridBagConstraints gridbag_constraints = new java.awt.GridBagConstraints();
    gridbag_constraints.insets = new java.awt.Insets(40, 40, 40, 40);
    gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
    for (int i = 0; i < p_message_arr.length; ++i) {
      final javax.swing.JLabel message_label = new javax.swing.JLabel();
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

    int option =
        javax.swing.JOptionPane.showConfirmDialog(
            null, p_message, null, javax.swing.JOptionPane.YES_NO_OPTION);
    boolean result = option == javax.swing.JOptionPane.YES_OPTION;
    return result;
  }

  /** Calls a dialog with an ok-button. */
  public static void ok(String p_message) {
    javax.swing.JOptionPane.showMessageDialog(null, p_message);
  }
}
