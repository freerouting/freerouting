package app.freerouting.gui.surveys;

import app.freerouting.surveys.SurveyDefinition;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/**
 * Standard v1 zero-friction renderer that presents a micro-survey as 2–3 prominent option buttons.
 *
 * <p>Clicking an option button constitutes the submission:
 *
 * <ol>
 *   <li>The clicked button is highlighted with a leading {@code ✓}.
 *   <li>All option buttons are immediately disabled to prevent duplicate clicks during the 400ms
 *       dwell.
 *   <li>The {@code onAnswer} callback is invoked to dispatch the async background submission.
 * </ol>
 */
public class ButtonsSurveyRenderer implements SurveyRenderer {

  private static final int PANEL_WIDTH = 320;
  private static final int BUTTON_HEIGHT = 36;

  @Override
  public JComponent render(SurveyDefinition survey, Consumer<String> onAnswer) {
    return render(survey, onAnswer, null);
  }

  @Override
  public JComponent render(SurveyDefinition survey, Consumer<String> onAnswer, Runnable onDismiss) {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

    // Header row with optional topic on the left and dismiss button on the right
    JPanel headerRow = new JPanel();
    headerRow.setLayout(new BoxLayout(headerRow, BoxLayout.X_AXIS));
    headerRow.setOpaque(false);
    headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

    if (survey.topic != null && !survey.topic.isBlank()) {
      JLabel topicLabel =
          new JLabel(
              "<html><span style='color: #888888; font-size: 10px; font-weight: bold;'>"
                  + escapeHtml(survey.topic.toUpperCase())
                  + "</span></html>");
      headerRow.add(topicLabel);
    }
    headerRow.add(Box.createHorizontalGlue());

    if (onDismiss != null) {
      JButton dismissButton = new JButton("✕");
      dismissButton.setToolTipText("Dismiss this survey");
      dismissButton.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
      dismissButton.setContentAreaFilled(false);
      dismissButton.setFocusPainted(false);
      dismissButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      dismissButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
      dismissButton.setForeground(new Color(160, 160, 160));
      dismissButton.addActionListener(e -> onDismiss.run());
      headerRow.add(dismissButton);
    }

    panel.add(headerRow);
    panel.add(Box.createVerticalStrut(6));

    // Question area with word wrapping and clean Sans-Serif system font
    JTextArea questionArea = new JTextArea(survey.question);
    questionArea.setWrapStyleWord(true);
    questionArea.setLineWrap(true);
    questionArea.setOpaque(false);
    questionArea.setEditable(false);
    questionArea.setFocusable(false);
    Font uiFont = UIManager.getFont("Label.font");
    if (uiFont != null) {
      questionArea.setFont(uiFont.deriveFont(Font.BOLD, 13.0f));
    } else {
      questionArea.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
    }
    Color fg = UIManager.getColor("Label.foreground");
    if (fg != null) {
      questionArea.setForeground(fg);
    }
    questionArea.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    questionArea.setAlignmentX(Component.LEFT_ALIGNMENT);
    questionArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
    panel.add(questionArea);
    panel.add(Box.createVerticalStrut(10));

    // Buttons
    List<JButton> buttons = new ArrayList<>();
    if (survey.options != null) {
      for (String option : survey.options) {
        JButton button = new JButton(option);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, BUTTON_HEIGHT));
        button.setPreferredSize(new Dimension(PANEL_WIDTH - 28, BUTTON_HEIGHT));
        button.setMinimumSize(new Dimension(PANEL_WIDTH - 28, BUTTON_HEIGHT));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(6, 12, 6, 12));
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 12.0f));

        button.addActionListener(
            e -> {
              // Disable all buttons immediately to prevent duplicate actions
              for (JButton btn : buttons) {
                btn.setEnabled(false);
              }
              // Visual receipt: checkmark highlight
              button.setText("✓ " + option);
              if (onAnswer != null) {
                onAnswer.accept(option);
              }
            });

        buttons.add(button);
        panel.add(button);
        panel.add(Box.createVerticalStrut(6));
      }
    }

    panel.setPreferredSize(new Dimension(PANEL_WIDTH, panel.getPreferredSize().height));
    panel.setMaximumSize(new Dimension(PANEL_WIDTH, Integer.MAX_VALUE));
    return panel;
  }

  private static String escapeHtml(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}
