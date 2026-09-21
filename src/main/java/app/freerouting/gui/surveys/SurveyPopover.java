package app.freerouting.gui.surveys;

import app.freerouting.surveys.SurveyCoordinator;
import app.freerouting.surveys.SurveyDefinition;
import java.awt.Color;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.JPopupMenu;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * Non-modal anchored flyout popover for displaying micro-surveys.
 *
 * <p>Lifecycle rules:
 *
 * <ul>
 *   <li><b>Optimistic 400ms UI rule:</b> When an option is clicked, the answer is dispatched
 *       immediately to the {@link SurveyCoordinator} (which updates the local cache and begins an
 *       async fire-and-forget HTTP POST). The popover dwells for 400ms on the Swing EDT to give the
 *       user visual feedback, then automatically closes.
 *   <li><b>Click-away dismissal:</b> If the user clicks away or presses Escape without selecting an
 *       option, the popover automatically dismisses and marks the survey as dismissed in the cache.
 *   <li><b>Canvas non-blocking:</b> Built as a lightweight {@link JPopupMenu}, ensuring it never
 *       blocks the routing canvas or steals global window modality.
 * </ul>
 */
public class SurveyPopover extends JPopupMenu {

  /** Visual receipt dwell duration in milliseconds before auto-closing. */
  public static final int DEFAULT_DWELL_MS = 400;

  private final SurveyDefinition survey;
  private final SurveyCoordinator coordinator;
  private final SurveyRenderer renderer;
  private final Runnable onClosed;
  private final int dwellMs;

  private boolean answered = false;
  private boolean closed = false;

  /**
   * Creates a survey popover with the default 400ms dwell duration.
   *
   * @param survey the active survey to present
   * @param coordinator the coordinator handling submission and cache state
   * @param renderer pluggable renderer for the survey layout
   * @param onClosed callback invoked when the popover closes (answered or dismissed)
   */
  public SurveyPopover(
      SurveyDefinition survey,
      SurveyCoordinator coordinator,
      SurveyRenderer renderer,
      Runnable onClosed) {
    this(survey, coordinator, renderer, onClosed, DEFAULT_DWELL_MS);
  }

  /**
   * Creates a survey popover with an explicit dwell duration (useful for unit tests).
   *
   * @param survey the active survey to present
   * @param coordinator the coordinator handling submission and cache state
   * @param renderer pluggable renderer for the survey layout
   * @param onClosed callback invoked when the popover closes (answered or dismissed)
   * @param dwellMs dwell duration in milliseconds
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

    // Render survey content into the popup
    add(this.renderer.render(this.survey, this::handleAnswer));

    // Handle dismissal when user clicks outside without answering
    addPopupMenuListener(
        new PopupMenuListener() {
          @Override
          public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}

          @Override
          public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            if (!answered && coordinator != null) {
              coordinator.dismiss(survey);
            }
            triggerClosed();
          }

          @Override
          public void popupMenuCanceled(PopupMenuEvent e) {}
        });
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

  private void triggerClosed() {
    if (!closed) {
      closed = true;
      if (onClosed != null) {
        onClosed.run();
      }
    }
  }

  /** Returns whether an answer has been selected. */
  public boolean isAnswered() {
    return answered;
  }
}
