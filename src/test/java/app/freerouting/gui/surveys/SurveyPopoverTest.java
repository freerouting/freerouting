package app.freerouting.gui.surveys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import app.freerouting.surveys.SurveyCoordinator;
import app.freerouting.surveys.SurveyDefinition;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SurveyPopover}. */
class SurveyPopoverTest {

  @Test
  void answeringOptionSubmitsToCoordinatorAndClosesAfterDwell() throws Exception {
    SurveyDefinition survey = new SurveyDefinition();
    survey.id = "s1";
    survey.question = "Test Question?";
    survey.options = new String[] {"Opt1", "Opt2"};

    SurveyCoordinator coordinator = mock(SurveyCoordinator.class);
    CountDownLatch closedLatch = new CountDownLatch(1);

    // Use a short dwell of 50ms for test speed
    SurveyPopover popover =
        new SurveyPopover(
            survey, coordinator, new ButtonsSurveyRenderer(), closedLatch::countDown, 50);

    assertFalse(popover.isAnswered());

    // Submit answer
    popover.handleAnswer("Opt1");

    assertTrue(popover.isAnswered());
    verify(coordinator).submitAnswer(survey, "Opt1");

    // Wait for the dwell timer to fire and invoke the close callback
    assertTrue(closedLatch.await(1, TimeUnit.SECONDS));
  }

  @Test
  void explicitDismissalTriggersCoordinatorDismissAndCloses() {
    SurveyDefinition survey = new SurveyDefinition();
    survey.id = "s-dismiss";

    SurveyCoordinator coordinator = mock(SurveyCoordinator.class);
    AtomicBoolean closed = new AtomicBoolean(false);

    SurveyPopover popover =
        new SurveyPopover(
            survey, coordinator, (s, onAnswer) -> new JLabel("test"), () -> closed.set(true));

    assertFalse(popover.isAnswered());
    popover.handleDismiss();

    assertTrue(popover.isAnswered());
    verify(coordinator).dismiss(survey);
    assertTrue(closed.get());
  }

  @Test
  void delegatesRenderingToCustomSurveyRenderer() {
    SurveyDefinition survey = new SurveyDefinition();
    survey.id = "s1";
    survey.question = "Custom Render?";

    AtomicReference<SurveyDefinition> passedSurvey = new AtomicReference<>();
    SurveyRenderer mockRenderer =
        (s, onAnswer) -> {
          passedSurvey.set(s);
          return new JLabel("Custom Component");
        };

    SurveyPopover popover =
        new SurveyPopover(survey, mock(SurveyCoordinator.class), mockRenderer, () -> {});

    assertEquals(survey, passedSurvey.get());
    assertEquals(1, popover.getComponentCount());
    assertTrue(popover.getComponent(0) instanceof JLabel);
  }

  @Test
  void showAnchoredAboveHandlesNonShowingAnchorGracefully() {
    SurveyDefinition survey = new SurveyDefinition();
    survey.id = "s-pos";
    SurveyPopover popover =
        new SurveyPopover(
            survey, mock(SurveyCoordinator.class), new ButtonsSurveyRenderer(), () -> {});

    javax.swing.JButton button = new javax.swing.JButton("test");
    // Not showing on screen: should return early without throwing
    popover.showAnchoredAbove(button);
    assertFalse(popover.isVisible());
  }
}
