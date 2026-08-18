package app.freerouting.board.state;

import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.structure.Component;
import app.freerouting.datastructures.Observers;

/**
 * Interface for the observers of the board. The observers are informed about changes in the board.
 */
public interface BoardObservers extends Observers<Item> {

  /** Enable the observers to synchronize the moved component. */
  void notifyMoved(Component component);
}
