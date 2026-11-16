package app.freerouting.settings;

/**
 * Pure-Java property change event emitted by settings models when a property value changes.
 *
 * <p>Replaces {@code java.beans.PropertyChangeEvent} so {@code settings} and {@code gui} remain
 * decoupled from the {@code java.desktop} module.
 */
public class PropertyChangeEvent {
  private final Object source;
  private final String propertyName;
  private final Object oldValue;
  private final Object newValue;

  /**
   * Constructs a new {@code PropertyChangeEvent}.
   *
   * @param source the bean that fired the event
   * @param propertyName the programmatic name of the property that was changed
   * @param oldValue the old value of the property
   * @param newValue the new value of the property
   */
  public PropertyChangeEvent(Object source, String propertyName, Object oldValue, Object newValue) {
    if (source == null) {
      throw new IllegalArgumentException("null source");
    }
    this.source = source;
    this.propertyName = propertyName;
    this.oldValue = oldValue;
    this.newValue = newValue;
  }

  /** Returns the object on which the event initially occurred. */
  public Object getSource() {
    return source;
  }

  /** Returns the programmatic name of the property that was changed. */
  public String getPropertyName() {
    return propertyName;
  }

  /** Returns the old value of the property, or {@code null} if unknown. */
  public Object getOldValue() {
    return oldValue;
  }

  /** Returns the new value of the property, or {@code null} if unknown. */
  public Object getNewValue() {
    return newValue;
  }
}
