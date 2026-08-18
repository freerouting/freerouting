package app.freerouting.board.state;

import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.structure.Component;

/** Empty adaptor implementing the BoardObservers interface. */
public class BoardObserverAdaptor implements BoardObservers {

  private boolean active;

  /** Tell the observers the deletion object. */
  @Override
  public void notifyDeleted(Item item) {}

  /** Notify the observers, that they can synchronize the changes on object. */
  @Override
  public void notifyChanged(Item item) {}

  /** Enable the observers to synchronize the new created item. */
  @Override
  public void notifyNew(Item item) {}

  /** Enable the observers to synchronize the moved component. */
  @Override
  public void notifyMoved(Component component) {}

  /** Activate the observers. */
  @Override
  public void activate() {
    active = true;
  }

  /** Deactivate the observers. */
  @Override
  public void deactivate() {
    active = false;
  }

  /** Returns, if the observer is activated. */
  @Override
  public boolean isActive() {
    return active;
  }
}
