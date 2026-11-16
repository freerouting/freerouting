package app.freerouting.settings;

/**
 * Listener notified when a bound settings property changes.
 *
 * <p>Replaces {@code java.beans.PropertyChangeListener} so {@code settings} and {@code gui} remain
 * decoupled from the {@code java.desktop} module.
 */
@FunctionalInterface
public interface PropertyChangeListener {

  /**
   * Called when a bound property is changed.
   *
   * @param evt a {@link PropertyChangeEvent} describing the event source and the property that has
   *     changed
   */
  void propertyChange(PropertyChangeEvent evt);
}
