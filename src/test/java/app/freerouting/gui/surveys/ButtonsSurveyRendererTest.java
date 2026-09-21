package app.freerouting.gui.surveys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.surveys.SurveyDefinition;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ButtonsSurveyRenderer}. */
class ButtonsSurveyRendererTest {

  @Test
  void rendersTopicQuestionAndButtons() {
    SurveyDefinition survey = new SurveyDefinition();
    survey.id = "s1";
    survey.topic = "Performance";
    survey.question = "How satisfied are you with routing speed?";
    survey.options = new String[] {"Fast", "Average", "Slow"};

    ButtonsSurveyRenderer renderer = new ButtonsSurveyRenderer();
    JComponent comp = renderer.render(survey, opt -> {});

    assertNotNull(comp);
    assertTrue(comp instanceof JPanel);

    List<JButton> buttons = extractButtons(comp);
    assertEquals(3, buttons.size());
    assertEquals("Fast", buttons.get(0).getText());
    assertEquals("Average", buttons.get(1).getText());
    assertEquals("Slow", buttons.get(2).getText());
  }

  @Test
  void clickingOptionDisablesButtonsAndHighlightsCheckmark() {
    SurveyDefinition survey = new SurveyDefinition();
    survey.id = "s1";
    survey.question = "Do you use KiCad integration?";
    survey.options = new String[] {"Yes", "No"};

    AtomicReference<String> chosen = new AtomicReference<>();
    ButtonsSurveyRenderer renderer = new ButtonsSurveyRenderer();
    JComponent comp = renderer.render(survey, chosen::set);

    List<JButton> buttons = extractButtons(comp);
    assertEquals(2, buttons.size());
    assertTrue(buttons.get(0).isEnabled());
    assertTrue(buttons.get(1).isEnabled());

    // Click the first button
    buttons.get(0).doClick();

    assertEquals("Yes", chosen.get());
    assertEquals("✓ Yes", buttons.get(0).getText());
    assertFalse(buttons.get(0).isEnabled());
    assertFalse(buttons.get(1).isEnabled());
  }

  @Test
  void handlesNullTopicGracefully() {
    SurveyDefinition survey = new SurveyDefinition();
    survey.id = "s1";
    survey.topic = null;
    survey.question = "Question without topic?";
    survey.options = new String[] {"Option A", "Option B"};

    ButtonsSurveyRenderer renderer = new ButtonsSurveyRenderer();
    JComponent comp = renderer.render(survey, opt -> {});

    assertNotNull(comp);
    List<JButton> buttons = extractButtons(comp);
    assertEquals(2, buttons.size());
  }

  private static List<JButton> extractButtons(Component parent) {
    List<JButton> result = new ArrayList<>();
    if (parent instanceof JPanel panel) {
      for (Component child : panel.getComponents()) {
        if (child instanceof JButton btn) {
          result.add(btn);
        }
      }
    }
    return result;
  }
}
