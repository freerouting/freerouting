package app.freerouting.gui.controls;

import java.awt.Color;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.JTextField;

/** A text field that displays a placeholder until the user enters text. */
public class PlaceholderTextField extends JTextField {

  private final String placeholder;

  /**
   * Creates a text field with the supplied placeholder.
   *
   * @param placeholder text displayed while the field is empty
   */
  public PlaceholderTextField(String placeholder) {
    this.placeholder = placeholder;
    setForeground(Color.GRAY);
    setText(placeholder);

    addFocusListener(
        new FocusAdapter() {
          @Override
          public void focusGained(FocusEvent e) {
            if (getText().equals(placeholder) || getText().isEmpty()) {
              setForeground(Color.BLACK);
              selectAll();
            }
          }

          @Override
          public void focusLost(FocusEvent e) {
            if (getText().isEmpty() || getText().equals(placeholder)) {
              setForeground(Color.GRAY);
              setText(placeholder);
            }
          }
        });
  }

  @Override
  public String getText() {
    String text = super.getText();
    return text.equals(placeholder) ? "" : text;
  }

  @Override
  public void setText(String text) {
    super.setText(text.isEmpty() ? placeholder : text);
    setForeground(text.isEmpty() ? Color.GRAY : Color.BLACK);
  }
}
