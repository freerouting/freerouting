package app.freerouting.board;

import app.freerouting.datastructures.Observers;

/**
 * Interface for the observers of the board. The observers are informed about changes in the board.
 */
public interface BoardObservers extends Observers<Item>
{
  /**
   * Enable the observers to synchronize the moved component.
   */
  void notify_moved(Component p_component);
}