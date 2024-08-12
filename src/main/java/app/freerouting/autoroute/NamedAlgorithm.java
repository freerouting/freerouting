package app.freerouting.autoroute;

import app.freerouting.board.BoardStatistics;
import app.freerouting.board.RoutingBoard;
import app.freerouting.interactive.InteractiveActionThread;
import app.freerouting.settings.RouterSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Interface for named algorithms, eg. "Freerouting Classic Fast Auto-router v1.0" for auto-router, "Freerouting Classic Optimizer v1.0" for route-optimization.
 */
public abstract class NamedAlgorithm
{
  // TODO: change the type from InteractiveActionThread to Thread to support headless mode
  protected final InteractiveActionThread thread;
  protected final List<Consumer<BoardStatistics>> boardUpdatedEventListeners = new ArrayList<>();
  protected final List<Consumer<TaskState>> taskStateChangedEventListeners = new ArrayList<>();
  protected final RouterSettings settings;
  protected RoutingBoard board;

  protected NamedAlgorithm(InteractiveActionThread thread, RoutingBoard board, RouterSettings settings)
  {
    this.thread = thread;
    this.board = board;
    this.settings = settings;
  }

  /**
   * Returns the id of the algorithm.
   *
   * @return The id of the algorithm.
   */
  protected abstract String getId();

  /**
   * Returns the name of the algorithm.
   *
   * @return The name of the algorithm.
   */
  protected abstract String getName();

  /**
   * Returns the version of the algorithm.
   *
   * @return The version of the algorithm.
   */
  protected abstract String getVersion();

  /**
   * Returns the description of the algorithm.
   *
   * @return The description of the algorithm.
   */
  protected abstract String getDescription();

  /**
   * Returns the type of the algorithm.
   *
   * @return The type of the algorithm.
   */
  protected abstract NamedAlgorithmType getType();

  public void addBoardUpdatedEventListener(Consumer<BoardStatistics> listener)
  {
    boardUpdatedEventListeners.add(listener);
  }

  public void addTaskStateChangedEventListener(Consumer<TaskState> listener)
  {
    taskStateChangedEventListeners.add(listener);
  }
}