package app.freerouting.interactive;

import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.TextManager;
import app.freerouting.settings.GlobalSettings;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static app.freerouting.Freerouting.globalSettings;

/**
 * Used for running an interactive action in a separate thread, that can be stopped by the user.
 * This typically represents an action that is triggered by the user on the GUI, such as autorouting, fanout, etc.
 */
public abstract class InteractiveActionThread extends StoppableThread
{
  public final GuiBoardManager hdlg;
  protected final RoutingJob routingJob;
  protected List<ThreadActionListener> listeners = new ArrayList<>();

  /**
   * Creates a new instance of InteractiveActionThread
   */
  protected InteractiveActionThread(GuiBoardManager boardManager, RoutingJob job)
  {
    this.hdlg = boardManager;
    this.routingJob = job;
  }

  public static InteractiveActionThread get_autoroute_instance(GuiBoardManager boardManager, RoutingJob job)
  {
    return new AutorouteThread(boardManager, job);
  }

  public static InteractiveActionThread get_autorouter_and_route_optimizer_instance(GuiBoardManager boardManager, RoutingJob job)
  {
    // TODO: we should not need this, but if we don't do this, the following values in routerSettings are not set properly
    job.routerSettings.isLayerActive = boardManager.settings.autoroute_settings.isLayerActive.clone();
    job.routerSettings.isPreferredDirectionHorizontalOnLayer = boardManager.settings.autoroute_settings.isPreferredDirectionHorizontalOnLayer.clone();
    job.routerSettings.preferredDirectionTraceCost = boardManager.settings.autoroute_settings.preferredDirectionTraceCost.clone();
    job.routerSettings.undesiredDirectionTraceCost = boardManager.settings.autoroute_settings.undesiredDirectionTraceCost.clone();

    var routerThread = new AutorouterAndRouteOptimizerThread(boardManager, job);
    routerThread.addListener(new ThreadActionListener()
    {
      @Override
      public void autorouterStarted()
      {
      }

      @Override
      public void autorouterAborted()
      {
      }

      @Override
      public void autorouterFinished()
      {
        try
        {
          GlobalSettings.saveAsJson(globalSettings);
        } catch (IOException e)
        {
          FRLogger.warn("InteractiveActionThread: unable to save global settings");
        }

        // Show the user settings dialog after auto-routing is finished if the number of completed jobs is greater than 5 and the user has not yet set their email address
        if ((globalSettings.statistics.jobsCompleted >= 5) && globalSettings.userProfileSettings.userEmail.isEmpty())
        {
          boardManager.get_panel().board_frame.menubar.showProfileDialog();
        }
      }
    });

    return routerThread;
  }

  public static InteractiveActionThread get_fanout_instance(GuiBoardManager boardManager, RoutingJob job)
  {
    return new FanoutThread(boardManager, job);
  }

  public static InteractiveActionThread get_pull_tight_instance(GuiBoardManager boardManager, RoutingJob job)
  {
    return new PullTightThread(boardManager, job);
  }

  public static InteractiveActionThread get_read_logfile_instance(GuiBoardManager boardManager, RoutingJob job, InputStream p_input_stream)
  {
    return new ReadLogfileThread(boardManager, job, p_input_stream);
  }

  public void addListener(ThreadActionListener toAdd)
  {
    listeners.add(toAdd);
  }

  @Override
  public void run()
  {
    thread_action();
    hdlg.repaint();
  }

  public synchronized void draw(Graphics p_graphics)
  {
    // Can be overwritten in derived classes.
  }

  private static class AutorouteThread extends InteractiveActionThread
  {

    private AutorouteThread(GuiBoardManager p_board_handling, RoutingJob job)
    {
      super(p_board_handling, job);
    }

    @Override
    protected void thread_action()
    {
      if (!(hdlg.interactive_state instanceof SelectedItemState))
      {
        return;
      }
      InteractiveState return_state = ((SelectedItemState) hdlg.interactive_state).autoroute(this);
      hdlg.set_interactive_state(return_state);
    }
  }

  private static class FanoutThread extends InteractiveActionThread
  {

    private FanoutThread(GuiBoardManager p_board_handling, RoutingJob job)
    {
      super(p_board_handling, job);
    }

    @Override
    protected void thread_action()
    {
      if (!(hdlg.interactive_state instanceof SelectedItemState))
      {
        return;
      }
      InteractiveState return_state = ((SelectedItemState) hdlg.interactive_state).fanout(this);
      hdlg.set_interactive_state(return_state);
    }
  }

  private static class PullTightThread extends InteractiveActionThread
  {

    private PullTightThread(GuiBoardManager p_board_handling, RoutingJob job)
    {
      super(p_board_handling, job);
    }

    @Override
    protected void thread_action()
    {
      if (!(hdlg.interactive_state instanceof SelectedItemState))
      {
        return;
      }
      InteractiveState return_state = ((SelectedItemState) hdlg.interactive_state).pull_tight(this);
      hdlg.set_interactive_state(return_state);
    }
  }

  private static class ReadLogfileThread extends InteractiveActionThread
  {

    private final InputStream input_stream;

    private ReadLogfileThread(GuiBoardManager p_board_handling, RoutingJob job, InputStream p_input_stream)
    {
      super(p_board_handling, job);
      this.input_stream = p_input_stream;
    }

    @Override
    protected void thread_action()
    {
      TextManager tm = new TextManager(InteractiveState.class, hdlg.get_locale());

      boolean saved_board_read_only = hdlg.is_board_read_only();
      hdlg.set_board_read_only(true);
      String start_message = tm.getText("logfile") + " " + tm.getText("stop_message");
      hdlg.screen_messages.set_status_message(start_message);
      hdlg.screen_messages.set_write_protected(true);
      boolean done = false;
      InteractiveState previous_state = hdlg.interactive_state;
      if (!hdlg.activityReplayFile.start_read(this.input_stream))
      {
        done = true;
      }
      boolean interrupted = false;
      int debug_counter = 0;
      hdlg.get_panel().board_frame.refresh_windows();
      hdlg.paint_immediately = true;
      while (!done)
      {
        if (isStopRequested())
        {
          interrupted = true;
          done = true;
        }
        ++debug_counter;
        ActivityReplayFileScope logfile_scope = hdlg.activityReplayFile.start_read_scope();
        if (logfile_scope == null)
        {
          done = true; // end of logfile
        }
        if (!done)
        {
          try
          {
            InteractiveState new_state = logfile_scope.read_scope(hdlg.activityReplayFile, hdlg.interactive_state, hdlg);
            if (new_state == null)
            {
              FRLogger.warn("BoardHandling:read_logfile: inconsistent logfile scope");
              new_state = previous_state;
            }
            hdlg.repaint();
            hdlg.set_interactive_state(new_state);
          } catch (Exception e)
          {
            done = true;
          }
        }
      }
      hdlg.paint_immediately = false;
      try
      {
        this.input_stream.close();
      } catch (IOException e)
      {
        FRLogger.error("ReadLogfileThread: unable to close input stream", e);
      }
      hdlg.get_panel().board_frame.refresh_windows();
      hdlg.screen_messages.set_write_protected(false);
      String curr_message;
      if (interrupted)
      {
        curr_message = tm.getText("interrupted");
      }
      else
      {
        curr_message = tm.getText("completed");
      }
      String end_message = tm.getText("logfile") + " " + curr_message;
      hdlg.screen_messages.set_status_message(end_message);
      hdlg.set_board_read_only(saved_board_read_only);
      hdlg.get_panel().board_frame.repaint_all();
    }
  }
}