package app.freerouting.gui;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.gui.a11y.A11y;
import app.freerouting.gui.a11y.GuiLocators;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;

/** Startup window visible when the program is loading. */
public final class WindowMessage extends WindowBase {

  /** Creates a new instance of WindowMessage. */
  private WindowMessage(String[] messageArr) {
    super(300, 100);

    this.add(createContent(messageArr));
    this.pack();
    this.setLocation(500, 400);
    this.setVisible(true);
  }

  /**
   * Builds the message labels without constructing a top-level window.
   *
   * <p>The returned panel is the same content used by {@link #show(String)} and {@link
   * #show(String[])}, making legacy window content testable in forced-headless component tests.
   *
   * @param messageArr messages to display
   * @return a reusable message content panel
   */
  public static JPanel createContent(String[] messageArr) {
    final JPanel mainPanel = new JPanel();
    final GridBagLayout gridbag = new GridBagLayout();
    mainPanel.setLayout(gridbag);
    A11y.tag(mainPanel, GuiLocators.WINDOW_MESSAGE_CONTENT);
    A11y.describe(mainPanel, "Message content", null);

    final GridBagConstraints gridbagConstraints = new GridBagConstraints();
    gridbagConstraints.insets = new Insets(40, 40, 40, 40);
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;
    for (int i = 0; i < messageArr.length; i++) {
      final JLabel messageLabel = new JLabel();
      messageLabel.setText(messageArr[i]);
      A11y.tag(messageLabel, GuiLocators.windowMessageLabel(i));
      A11y.describe(messageLabel, messageArr[i], null);

      gridbag.setConstraints(messageLabel, gridbagConstraints);
      mainPanel.add(messageLabel, gridbagConstraints);
    }
    return mainPanel;
  }

  /** Displays a window with the input message at the center of the screen. */
  public static WindowMessage show(String message) {
    String[] messageArr = new String[1];
    messageArr[0] = message;
    return new WindowMessage(messageArr);
  }

  /** Displays a window with the input messages at the center of the screen. */
  public static WindowMessage show(String[] messages) {
    return new WindowMessage(messages);
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
  public static void ok(String message) {
    JOptionPane.showMessageDialog(null, message);
  }
}
