package app.freerouting.gui;

import java.awt.FontMetrics;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.JLabel;

/**
 * A JLabel that automatically handles text that is too long to display.
 * When text doesn't fit, it shows the beginning with ellipsis and sets a tooltip with the full text.
 */
public class SmartLabel extends JLabel {

  private static final String ELLIPSIS = "...";
  private String fullText = "";

  /**
   * Creates a new SmartLabel with no text.
   */
  public SmartLabel() {
    super();
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        updateDisplayedText();
      }
    });
  }

  /**
   * Creates a new SmartLabel with the specified text.
   */
  public SmartLabel(String text) {
    this();
    setText(text);
  }

  /**
   * Creates a new SmartLabel with the specified text and horizontal alignment.
   */
  public SmartLabel(String text, int horizontalAlignment) {
    this(text);
    setHorizontalAlignment(horizontalAlignment);
  }

  @Override
  public void setText(String text) {
    if (text == null) {
      text = "";
    }
    this.fullText = text;
    updateDisplayedText();
  }

  private void updateDisplayedText() {
    if (fullText == null || fullText.isEmpty()) {
      super.setText(fullText);
      setToolTipText(null);
      return;
    }

    int width = getWidth();
    if (width <= 0) {
      // Component not yet sized, just set the text
      super.setText(fullText);
      setToolTipText(null);
      return;
    }

    FontMetrics fm = getFontMetrics(getFont());
    if (fm == null) {
      super.setText(fullText);
      setToolTipText(null);
      return;
    }

    int availableWidth = width - getInsets().left - getInsets().right;
    int textWidth = fm.stringWidth(fullText);

    if (textWidth <= availableWidth) {
      // Text fits completely
      super.setText(fullText);
      setToolTipText(null);
    } else {
      // Text needs truncation
      int ellipsisWidth = fm.stringWidth(ELLIPSIS);
      int availableForText = availableWidth - ellipsisWidth;

      if (availableForText <= 0) {
        super.setText(ELLIPSIS);
        setToolTipText("<html>" + escapeHtml(fullText) + "</html>");
        return;
      }

      // Find the longest substring that fits
      int charCount = 0;
      for (int i = 1; i <= fullText.length(); i++) {
        String substring = fullText.substring(0, i);
        if (fm.stringWidth(substring) <= availableForText) {
          charCount = i;
        } else {
          break;
        }
      }

      String truncatedText = fullText.substring(0, charCount) + ELLIPSIS;
      super.setText(truncatedText);
      setToolTipText("<html>" + escapeHtml(fullText) + "</html>");
    }
  }

  /**
   * Escapes HTML special characters in the text.
   */
  private String escapeHtml(String text) {
    return text.replace("&", "&amp;")
               .replace("<", "&lt;")
               .replace(">", "&gt;")
               .replace("\"", "&quot;")
               .replace("'", "&#x27;");
  }
}