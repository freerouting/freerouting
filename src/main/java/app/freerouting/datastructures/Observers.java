package app.freerouting.datastructures;

/** Interface to observe changes on objects for synchronisation purposes. */
public interface Observers<ObjectType> {

  /** Tell the observers the deletion p_object. */
  void notifyDeleted(ObjectType pObject);

  /** Notify the observers, that they can synchronize the changes on p_object. */
  void notifyChanged(ObjectType pObject);

  /** Enable the observers to synchronize the new created item. */
  void notifyNew(ObjectType pObject);

  /** Starts notifying the observers */
  void activate();

  /** Ends notifying the observers */
  void deactivate();

  /** Returns, if the observer is activated. */
  boolean isActive();
}
