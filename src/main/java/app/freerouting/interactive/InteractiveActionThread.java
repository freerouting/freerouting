package app.freerouting.interactive;

import static app.freerouting.Freerouting.globalSettings;

import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.logger.FRLogger;

import app.freerouting.settings.GlobalSettings;
import java.awt.Graphics;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Used for running an interactive action in a separate thread, that can be
 * stopped by the user. This typically represents an action that is triggered by
 * the user on the GUI, such as autorouting, etc.
 */
public abstract class InteractiveActionThread extends StoppableThread {

  public final GuiBoardManager boardManager;
  protected final RoutingJob routingJob;
  protected List<ThreadActionListener> listeners = new ArrayList<>();

  /**
   * Creates a new instance of InteractiveActionThread
   */
  protected InteractiveActionThread(GuiBoardManager boardManager, RoutingJob job) {
    this.boardManager = boardManager;
    this.routingJob = job;
  }

  public static InteractiveActionThread get_autoroute_instance(GuiBoardManager boardManager, RoutingJob job) {
    return new AutorouteThread(boardManager, job);
  }

  public static InteractiveActionThread get_autorouter_and_route_optimizer_instance(GuiBoardManager boardManager,
      RoutingJob job) {
    var routerThread = new AutorouterAndRouteOptimizerThread(boardManager, job);
    routerThread.addListener(new ThreadActionListener() {
      @Override
      public void autorouterStarted() {
      }

      @Override
      public void autorouterAborted() {
      }

      @Override
      public void autorouterFinished() {
        try {
          GlobalSettings.saveAsJson(globalSettings);
        } catch (IOException _) {
          FRLogger.warn("InteractiveActionThread: unable to save global settings");
        }

        // Show the user settings dialog after auto-routing is finished if the number of
        // completed jobs is greater than 5 and the user has not yet set their email
        // address
        if ((globalSettings.statistics.jobsCompleted >= 5) && globalSettings.userProfileSettings.userEmail.isEmpty()) {
          boardManager.get_panel().board_frame.menubar.showProfileDialog();
        }
      }
    });

    return routerThread;
  }

  public static InteractiveActionThread get_pull_tight_instance(GuiBoardManager boardManager, RoutingJob job) {
    return new PullTightThread(boardManager, job);
  }

  public void addListener(ThreadActionListener toAdd) {
    listeners.add(toAdd);
  }

  @Override
  public void run() {
    thread_action();
    boardManager.repaint();
  }

  public synchronized void draw(Graphics p_graphics) {
    // Can be overwritten in derived classes.
  }

  private static class AutorouteThread extends InteractiveActionThread {

    private AutorouteThread(GuiBoardManager p_board_handling, RoutingJob job) {
      super(p_board_handling, job);
    }

    @Override
    protected void thread_action() {
      if (!(boardManager.interactive_state instanceof SelectedItemState)) {
        return;
      }
      InteractiveState return_state = ((SelectedItemState) boardManager.interactive_state).autoroute(this);
      boardManager.set_interactive_state(return_state);
    }
  }

  private static class PullTightThread extends InteractiveActionThread {

    private PullTightThread(GuiBoardManager p_board_handling, RoutingJob job) {
      super(p_board_handling, job);
    }

    @Override
    protected void thread_action() {
      if (!(boardManager.interactive_state instanceof SelectedItemState)) {
        return;
      }
      InteractiveState return_state = ((SelectedItemState) boardManager.interactive_state).pull_tight(this);
      boardManager.set_interactive_state(return_state);
    }
  }
}
