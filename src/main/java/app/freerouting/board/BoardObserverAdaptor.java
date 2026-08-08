package app.freerouting.board;

/** Empty adaptor implementing the BoardObservers interface. */
public class BoardObserverAdaptor implements BoardObservers {

  private boolean active;

  /** Tell the observers the deletion p_object. */
  @Override
  public void notifyDeleted(Item p_item) {}

  /** Notify the observers, that they can synchronize the changes on p_object. */
  @Override
  public void notifyChanged(Item p_item) {}

  /** Enable the observers to synchronize the new created item. */
  @Override
  public void notifyNew(Item p_item) {}

  /** Enable the observers to synchronize the moved component. */
  @Override
  public void notifyMoved(Component p_component) {}

  /** activate the observers */
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
