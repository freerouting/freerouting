package app.freerouting.board;

import app.freerouting.datastructures.Observers;

public interface BoardObservers extends Observers<Item> {
  /** Enable the observers to syncronize the moved component. */
  void notify_moved(Component p_component);
}
