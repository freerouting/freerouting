package app.freerouting.autoroute;

import app.freerouting.autoroute.events.*;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.RouterCounters;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardFileStatistics;
import app.freerouting.settings.RouterSettings;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Interface for named algorithms, e.g. "Freerouting Classic Fast Auto-router v1.0" for auto-router, "Freerouting Classic Optimizer v1.0" for route-optimization.
 */
public abstract class NamedAlgorithm implements Serializable
{
  protected final transient StoppableThread thread;
  protected final transient List<BoardSnapshotEventListener> boardSnapshotEventListeners = new ArrayList<>();
  protected final transient List<BoardUpdatedEventListener> boardUpdatedEventListeners = new ArrayList<>();
  protected final transient List<TaskStateChangedEventListener> taskStateChangedEventListeners = new ArrayList<>();
  protected final RouterSettings settings;
  // The routing board.
  // TODO: This should be a transient field, but it is not possible to serialize the board with the JSON serializer.
  protected transient RoutingBoard board;

  protected NamedAlgorithm(StoppableThread thread, RoutingBoard board, RouterSettings settings)
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

  public void addBoardSnapshotEventListener(BoardSnapshotEventListener listener)
  {
    boardSnapshotEventListeners.add(listener);
  }

  public void fireBoardSnapshotEvent(RoutingBoard board)
  {
    BoardSnapshotEvent event = new BoardSnapshotEvent(this, board);
    for (BoardSnapshotEventListener listener : boardSnapshotEventListeners)
    {
      listener.onBoardSnapshotEvent(event);
    }
  }

  public void addBoardUpdatedEventListener(BoardUpdatedEventListener listener)
  {
    boardUpdatedEventListeners.add(listener);
  }

  /**
   * Fires a board updated event. This happens when the board has been updated, e.g. after a route has been added.
   */
  public void fireBoardUpdatedEvent(BoardFileStatistics boardStatistics, RouterCounters routerCounters, RoutingBoard board)
  {
    BoardUpdatedEvent event = new BoardUpdatedEvent(this, boardStatistics, routerCounters, board);
    for (BoardUpdatedEventListener listener : boardUpdatedEventListeners)
    {
      listener.onBoardUpdatedEvent(event);
    }
  }

  public void addTaskStateChangedEventListener(TaskStateChangedEventListener listener)
  {
    taskStateChangedEventListeners.add(listener);
  }

  /**
   * Fires a task state changed event. This happens when the state of the task changes, e.g. from running to stopped, or we start a new pass of the current process.
   */
  public void fireTaskStateChangedEvent(TaskStateChangedEvent event)
  {
    for (TaskStateChangedEventListener listener : taskStateChangedEventListeners)
    {
      listener.onTaskStateChangedEvent(event);
    }
  }
}