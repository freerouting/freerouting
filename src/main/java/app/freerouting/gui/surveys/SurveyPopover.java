package app.freerouting.gui.surveys;

import app.freerouting.surveys.SurveyCoordinator;
import app.freerouting.surveys.SurveyDefinition;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JPopupMenu;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * Lightweight, non-modal popover that presents a micro-survey anchored to a UI element.
 *
 * <p>Key lifecycle guarantees:
 *
 * <ul>
 *   <li><b>Optimistic feedback:</b> Selecting an option button disables all options and highlights
 *       the choice with a checkmark for 400ms before closing.
 *   <li><b>Session accessibility:</b> Closing the popover by clicking outside or pressing Escape
 *       does <i>not</i> permanently dismiss the survey or hide the trigger button. The user can
 *       re-open it at any time during their session.
 *   <li><b>Explicit dismissal:</b> Clicking the explicit dismiss ("✕") button permanently dismisses
 *       the survey and hides the trigger button.
 *   <li><b>Non-blocking delivery:</b> The user's selection is dispatched immediately to the {@link
 *       SurveyCoordinator} without blocking the UI.
 * </ul>
 */
public class SurveyPopover extends JPopupMenu {

  /** Dwell duration (in milliseconds) before the popover automatically closes. */
  public static final int DEFAULT_DWELL_MS = 400;

  private final SurveyDefinition survey;
  private final SurveyCoordinator coordinator;
  private final SurveyRenderer renderer;
  private final Runnable onClosed;
  private final int dwellMs;

  private boolean answered = false;
  private boolean closed = false;

  /**
   * Creates a popover with the default 400ms dwell duration.
   *
   * @param survey the survey definition to display
   * @param coordinator coordinator to receive submit and dismiss events
   * @param renderer visual renderer for question and options
   * @param onClosed callback invoked when the survey is answered or explicitly dismissed
   */
  public SurveyPopover(
      SurveyDefinition survey,
      SurveyCoordinator coordinator,
      SurveyRenderer renderer,
      Runnable onClosed) {
    this(survey, coordinator, renderer, onClosed, DEFAULT_DWELL_MS);
  }

  /**
   * Creates a popover with a configurable dwell duration (primarily for tests).
   *
   * @param survey the survey definition to display
   * @param coordinator coordinator to receive submit and dismiss events
   * @param renderer visual renderer for question and options
   * @param onClosed callback invoked when the survey is answered or explicitly dismissed
   * @param dwellMs delay in milliseconds before closing after answer selection
   */
  public SurveyPopover(
      SurveyDefinition survey,
      SurveyCoordinator coordinator,
      SurveyRenderer renderer,
      Runnable onClosed,
      int dwellMs) {
    this.survey = survey;
    this.coordinator = coordinator;
    this.renderer = renderer != null ? renderer : new ButtonsSurveyRenderer();
    this.onClosed = onClosed;
    this.dwellMs = dwellMs;

    initUi();
  }

  private void initUi() {
    Color bg = UIManager.getColor("Panel.background");
    if (bg != null) {
      setBackground(bg);
    }
    setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 180), 1),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)));

    // Render survey content into the popup with both answer and explicit dismiss callbacks
    add(this.renderer.render(this.survey, this::handleAnswer, this::handleDismiss));
  }

  /**
   * Shows the popover anchored directly beneath the specified component.
   *
   * @param anchor the component to anchor beneath (e.g. the survey trigger button)
   */
  public void showAnchored(Component anchor) {
    if (anchor == null || !anchor.isShowing()) {
      return;
    }
    show(anchor, 0, anchor.getHeight() + 2);
  }

  /**
   * Shows the popover anchored directly above the specified component, aligned to its right edge so
   * that the popover stays within the window viewport.
   *
   * @param anchor the component to anchor above (e.g. the status bar survey trigger button)
   */
  public void showAnchoredAbove(Component anchor) {
    if (anchor == null || !anchor.isShowing()) {
      return;
    }
    Dimension pref = getPreferredSize();
    int x = anchor.getWidth() - pref.width;
    int y = -pref.height - 2;
    try {
      java.awt.Point screenLoc = anchor.getLocationOnScreen();
      if (screenLoc.x + x < 0) {
        x = -screenLoc.x + 4; // Shift right to remain fully visible on screen
      }
    } catch (Exception ignored) {
      // Best-effort check when component location is accessible
    }
    show(anchor, x, y);
  }

  /**
   * Handles user submission of an option. Dispatches async network request and starts the 400ms EDT
   * dwell timer.
   */
  void handleAnswer(String option) {
    if (answered) {
      return;
    }
    answered = true;

    if (coordinator != null) {
      coordinator.submitAnswer(survey, option);
    }

    Timer timer =
        new Timer(
            dwellMs,
            e -> {
              setVisible(false);
              triggerClosed();
            });
    timer.setRepeats(false);
    timer.start();
  }

  /** Handles explicit dismissal (e.g. clicking the close '✕' button). */
  void handleDismiss() {
    if (answered) {
      return;
    }
    answered = true;

    if (coordinator != null) {
      coordinator.dismiss(survey);
    }

    setVisible(false);
    triggerClosed();
  }

  private void triggerClosed() {
    if (!closed) {
      closed = true;
      if (onClosed != null) {
        onClosed.run();
      }
    }
  }

  /** Returns whether an answer has been selected or explicitly dismissed. */
  public boolean isAnswered() {
    return answered;
  }
}
