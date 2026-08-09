package app.freerouting.datastructures;

/** Interface to observe changes on objects for synchronisation purposes. */
public interface Observers<T> {

  /** Tell the observers about the deletion of object. */
  void notifyDeleted(T object);

  /** Notify the observers, that they can synchronize the changes on object. */
  void notifyChanged(T object);

  /** Enable the observers to synchronize the new created item. */
  void notifyNew(T object);

  /** Starts notifying the observers. */
  void activate();

  /** Ends notifying the observers. */
  void deactivate();

  /** Returns, if the observer is activated. */
  boolean isActive();
}
