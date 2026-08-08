package app.freerouting.datastructures;

/** Interface to observe changes on objects for synchronisation purposes. */
public interface Observers<ObjectType> {

  /** Tell the observers the deletion p_object. */
  void notifyDeleted(ObjectType p_object);

  /** Notify the observers, that they can synchronize the changes on p_object. */
  void notifyChanged(ObjectType p_object);

  /** Enable the observers to synchronize the new created item. */
  void notifyNew(ObjectType p_object);

  /** Starts notifying the observers */
  void activate();

  /** Ends notifying the observers */
  void deactivate();

  /** Returns, if the observer is activated. */
  boolean isActive();
}
