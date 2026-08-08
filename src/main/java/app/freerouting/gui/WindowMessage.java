package app.freerouting.gui;

import app.freerouting.management.analytics.FRAnalytics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;

/** Startup window visible when the program is loading. */
public final class WindowMessage extends WindowBase {

  /** Creates a new instance of WindowMessage */
  private WindowMessage(String[] pMessageArr) {
    super(300, 100);

    final JPanel mainPanel = new JPanel();
    final GridBagLayout gridbag = new GridBagLayout();
    mainPanel.setLayout(gridbag);
    final GridBagConstraints gridbagConstraints = new GridBagConstraints();
    gridbagConstraints.insets = new Insets(40, 40, 40, 40);
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    for (int i = 0; i < pMessageArr.length; i++) {
      final JLabel messageLabel = new JLabel();
      messageLabel.setText(pMessageArr[i]);

      gridbag.setConstraints(messageLabel, gridbagConstraints);
      mainPanel.add(messageLabel, gridbagConstraints);
    }
    this.add(mainPanel);
    this.pack();
    this.setLocation(500, 400);
    this.setVisible(true);
  }

  /** Displays a window with the input message at the center of the screen. */
  public static WindowMessage show(String pMessage) {
    String[] messageArr = new String[1];
    messageArr[0] = pMessage;
    return new WindowMessage(messageArr);
  }

  /** Displays a window with the input messages at the center of the screen. */
  public static WindowMessage show(String[] pMessages) {
    return new WindowMessage(pMessages);
  }

  /**
   * Calls a confirm dialog. Returns true, if the user confirmed the action or if p_message is null.
   */
  public static boolean confirm(String message) {
    return confirm(message, JOptionPane.YES_OPTION);
  }

  /**
   * Calls a confirm dialog with a default option. Returns true, if the user confirmed the action or
   * if message is null.
   */
  public static boolean confirm(String message, int defaultOption) {
    if (message == null) {
      return true;
    }
    String yesOption = UIManager.getString("OptionPane.yesButtonText");
    String noOption = UIManager.getString("OptionPane.noButtonText");
    Object[] options = {yesOption, noOption};
    JOptionPane optionPane =
        new JOptionPane(
            message,
            JOptionPane.QUESTION_MESSAGE,
            JOptionPane.YES_NO_OPTION,
            null,
            options,
            options[defaultOption]);
    optionPane.createDialog(null, "").setVisible(true);
    String selectedOption = (String) optionPane.getValue();

    if (selectedOption.equals(yesOption)) {
      FRAnalytics.buttonClicked("dialog_yes", message);
      return true;
    } else {
      FRAnalytics.buttonClicked("dialog_no", message);
      return false;
    }
  }

  /** Calls a dialog with an ok-button. */
  public static void ok(String pMessage) {
    JOptionPane.showMessageDialog(null, pMessage);
  }
}
