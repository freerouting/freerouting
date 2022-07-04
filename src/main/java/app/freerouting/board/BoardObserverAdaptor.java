package app.freerouting.board;

/** Empty adaptor implementing the BoardObservers interface. */
public class BoardObserverAdaptor implements BoardObservers {
  private boolean active = false;

  /** Tell the observers the deletion p_object. */
  public void notify_deleted(Item p_item) {}

  /** Notify the observers, that they can syncronize the changes on p_object. */
  public void notify_changed(Item p_item) {}

  /** Enable the observers to syncronize the new created item. */
  public void notify_new(Item p_item) {}

  /** Enable the observers to syncronize the moved component. */
  public void notify_moved(Component p_component) {}

  /** activate the observers */
  public void activate() {
    active = true;
  }

  /** Deactivate the observers. */
  public void deactivate() {
    active = false;
  }

  /** Returns, if the observer is activated. */
  public boolean is_active() {
    return active;
  }
}
