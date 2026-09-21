package app.freerouting.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.gui.a11y.GuiA11yHarness;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.gui.board.BoardPanelStatus;
import java.awt.Component;
import java.util.Locale;
import javax.accessibility.AccessibleRole;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * MVP accessibility workflow #1 — read the status bar (component-only, forced headless via {@code
 * testGui}).
 *
 * <p>Verifies the status-bar controls resolve by stable locator (never translated label), expose
 * accessible names/roles/state, and contain no duplicate/empty sibling names. See {@code
 * docs/gui/accessibility-contract.md} and the SoC plan §6 Phase 2 (D7/D8/D22).
 */
@Tag("gui")
class BoardPanelStatusA11yTest {

  @Test
  void statusBarControlsResolveByLocatorAndAreAccessible() {
    BoardPanelStatus statusBar = GuiA11yHarness.onEdt(() -> new BoardPanelStatus(Locale.ENGLISH));

    GuiA11yHarness.onEdt(
        () -> {
          // Main status message line: role + non-empty name + enabled.
          Component status = GuiA11yHarness.findByLocator(statusBar, GuiLocators.STATUS_MESSAGE);
          GuiA11yHarness.requireRole(status, GuiLocators.STATUS_MESSAGE, AccessibleRole.LABEL);
          GuiA11yHarness.requireAccessibleName(status, GuiLocators.STATUS_MESSAGE);
          GuiA11yHarness.requireEnabled(status, GuiLocators.STATUS_MESSAGE);

          // Current-layer and board-score indicators.
          GuiA11yHarness.requireAccessibleName(
              GuiA11yHarness.findByLocator(statusBar, GuiLocators.STATUS_CURRENT_LAYER),
              GuiLocators.STATUS_CURRENT_LAYER);
          GuiA11yHarness.requireAccessibleName(
              GuiA11yHarness.findByLocator(statusBar, GuiLocators.STATUS_BOARD_SCORE),
              GuiLocators.STATUS_BOARD_SCORE);

          // Remaining indicators resolve by locator.
          GuiA11yHarness.findByLocator(statusBar, GuiLocators.STATUS_ADDITIONAL_MESSAGE);
          GuiA11yHarness.findByLocator(statusBar, GuiLocators.STATUS_MOUSE_POSITION);
          GuiA11yHarness.findByLocator(statusBar, GuiLocators.STATUS_UNIT);
          GuiA11yHarness.findByLocator(statusBar, GuiLocators.STATUS_ERROR_COUNT);
          GuiA11yHarness.findByLocator(statusBar, GuiLocators.STATUS_WARNING_COUNT);
          GuiA11yHarness.findByLocator(statusBar, GuiLocators.STATUS_SURVEY_TRIGGER);

          // No duplicate or empty sibling accessible names anywhere in the status bar.
          GuiA11yHarness.requireUniqueSiblingNames(statusBar);
        });
  }

  @Test
  void surveyTriggerPillBecomesVisibleAndAccessibleWhenSurveyProvided() {
    BoardPanelStatus statusBar = GuiA11yHarness.onEdt(() -> new BoardPanelStatus(Locale.ENGLISH));

    GuiA11yHarness.onEdt(
        () -> {
          Component button =
              GuiA11yHarness.findByLocator(statusBar, GuiLocators.STATUS_SURVEY_TRIGGER);
          assertNotNull(button);
          assertEquals(false, button.isVisible(), "survey trigger should initially be hidden");

          app.freerouting.surveys.SurveyDefinition survey =
              new app.freerouting.surveys.SurveyDefinition();
          survey.id = "test-status-poll";
          survey.topic = "Performance";
          survey.question = "How is routing performance?";
          survey.options = new String[] {"Great", "Needs work"};

          statusBar.showSurveyTrigger(survey);

          assertEquals(true, button.isVisible(), "survey trigger should be visible once active");
          GuiA11yHarness.requireRole(
              button, GuiLocators.STATUS_SURVEY_TRIGGER, AccessibleRole.PUSH_BUTTON);
          GuiA11yHarness.requireAccessibleName(button, GuiLocators.STATUS_SURVEY_TRIGGER);
          assertEquals("💬 Performance", ((javax.swing.JButton) button).getText());
        });
  }

  @Test
  void statusMessageExposesTranslatedAccessibleName() {
    BoardPanelStatus statusBar = GuiA11yHarness.onEdt(() -> new BoardPanelStatus(Locale.ENGLISH));

    GuiA11yHarness.onEdt(
        () -> {
          Component status = GuiA11yHarness.findByLocator(statusBar, GuiLocators.STATUS_MESSAGE);
          assertEquals(
              "Status line",
              GuiA11yHarness.accessibleName(status),
              "status.message accessible name should be the translated label");
        });
  }

  @Test
  void statusMessageExposesTranslatedAccessibleNameInHungarian() {
    BoardPanelStatus statusBar =
        GuiA11yHarness.onEdt(() -> new BoardPanelStatus(Locale.forLanguageTag("hu")));

    GuiA11yHarness.onEdt(
        () -> {
          Component status = GuiA11yHarness.findByLocator(statusBar, GuiLocators.STATUS_MESSAGE);
          String name = GuiA11yHarness.accessibleName(status);
          assertNotNull(name, "Hungarian status.message accessible name must be present");
          assertEquals(
              "Állapotsor",
              name,
              "status.message accessible name should be the Hungarian label (D19)");
        });
  }
}
