package app.freerouting.datastructures;

/** Interface to observe changes on objects for synchronisation purposes. */
public interface Observers<ObjectType> {
  /** Tell the observers the deletion p_object. */
  void notify_deleted(ObjectType p_object);

  /** Notify the observers, that they can syncronize the changes on p_object. */
  void notify_changed(ObjectType p_object);

  /** Enable the observers to syncronize the new created item. */
  void notify_new(ObjectType p_object);

  /** Starts notifying the observers */
  void activate();

  /** Ends notifying the observers */
  void deactivate();

  /** Returns, if the observer is activated. */
  boolean is_active();
}
