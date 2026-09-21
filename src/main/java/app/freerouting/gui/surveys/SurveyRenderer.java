package app.freerouting.gui.surveys;

import app.freerouting.surveys.SurveyDefinition;
import java.util.function.Consumer;
import javax.swing.JComponent;

/**
 * Interface for rendering a {@link SurveyDefinition} into a Swing UI component.
 *
 * <p>Decouples survey UI presentation from the popover container and coordinator, allowing future
 * variations (e.g. text entry, multi-select) to be plugged in without changing popover lifecycle or
 * submission handling.
 */
public interface SurveyRenderer {

  /**
   * Builds the Swing UI component representing the survey.
   *
   * @param survey the survey definition to display
   * @param onAnswer callback invoked when the user selects/submits an answer option
   * @return the rendered {@link JComponent} to display inside the popover
   */
  JComponent render(SurveyDefinition survey, Consumer<String> onAnswer);

  /**
   * Builds the Swing UI component representing the survey, with support for explicit dismissal.
   *
   * @param survey the survey definition to display
   * @param onAnswer callback invoked when the user selects/submits an answer option
   * @param onDismiss callback invoked when the user explicitly dismisses the survey
   * @return the rendered {@link JComponent} to display inside the popover
   */
  default JComponent render(
      SurveyDefinition survey, Consumer<String> onAnswer, Runnable onDismiss) {
    return render(survey, onAnswer);
  }
}
