package app.freerouting.settings;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe pure-Java property change notification helper.
 *
 * <p>Replaces {@code java.beans.PropertyChangeSupport} so {@code settings} and {@code gui} remain
 * decoupled from the {@code java.desktop} module while preserving {@code Objects.equals}
 * short-circuiting and named-property listener semantics.
 */
public class PropertyChangeSupport {
  private final Object sourceBean;
  private final CopyOnWriteArrayList<PropertyChangeListener> listeners =
      new CopyOnWriteArrayList<>();
  private final ConcurrentHashMap<String, CopyOnWriteArrayList<PropertyChangeListener>>
      namedListeners = new ConcurrentHashMap<>();

  /**
   * Constructs a {@code PropertyChangeSupport} object.
   *
   * @param sourceBean the bean to be given as the source for any events
   */
  public PropertyChangeSupport(Object sourceBean) {
    if (sourceBean == null) {
      throw new NullPointerException("sourceBean");
    }
    this.sourceBean = sourceBean;
  }

  /**
   * Adds a {@link PropertyChangeListener} to the listener list. The listener is registered for all
   * properties.
   *
   * @param listener the listener to add; ignored if {@code null}
   */
  public void addPropertyChangeListener(PropertyChangeListener listener) {
    if (listener != null) {
      listeners.add(listener);
    }
  }

  /**
   * Adds a {@link PropertyChangeListener} for a specific property.
   *
   * @param propertyName the name of the property to listen on
   * @param listener the listener to add; ignored if {@code propertyName} or {@code listener} is
   *     {@code null}
   */
  public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
    if (propertyName != null && listener != null) {
      namedListeners.computeIfAbsent(propertyName, _ -> new CopyOnWriteArrayList<>()).add(listener);
    }
  }

  /**
   * Removes a {@link PropertyChangeListener} from the listener list.
   *
   * @param listener the listener to remove; ignored if {@code null}
   */
  public void removePropertyChangeListener(PropertyChangeListener listener) {
    if (listener != null) {
      listeners.remove(listener);
    }
  }

  /**
   * Removes a {@link PropertyChangeListener} for a specific property.
   *
   * @param propertyName the name of the property that was listened on
   * @param listener the listener to remove; ignored if {@code propertyName} or {@code listener} is
   *     {@code null}
   */
  public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
    if (propertyName != null && listener != null) {
      CopyOnWriteArrayList<PropertyChangeListener> list = namedListeners.get(propertyName);
      if (list != null) {
        list.remove(listener);
      }
    }
  }

  /**
   * Reports a bound property update to listeners that have been registered to track updates of all
   * properties or a property with the specified name.
   *
   * <p>No event is fired if {@code Objects.equals(oldValue, newValue)} is {@code true}.
   */
  public void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
    if (Objects.equals(oldValue, newValue)) {
      return;
    }
    firePropertyChange(new PropertyChangeEvent(sourceBean, propertyName, oldValue, newValue));
  }

  /** Reports an {@code int} bound property update if {@code oldValue != newValue}. */
  public void firePropertyChange(String propertyName, int oldValue, int newValue) {
    if (oldValue == newValue) {
      return;
    }
    firePropertyChange(new PropertyChangeEvent(sourceBean, propertyName, oldValue, newValue));
  }

  /** Reports a {@code boolean} bound property update if {@code oldValue != newValue}. */
  public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
    if (oldValue == newValue) {
      return;
    }
    firePropertyChange(new PropertyChangeEvent(sourceBean, propertyName, oldValue, newValue));
  }

  /** Fires a property change event to all matching listeners. */
  public void firePropertyChange(PropertyChangeEvent event) {
    if (event == null || Objects.equals(event.getOldValue(), event.getNewValue())) {
      return;
    }
    for (PropertyChangeListener listener : listeners) {
      listener.propertyChange(event);
    }
    String propertyName = event.getPropertyName();
    if (propertyName != null) {
      CopyOnWriteArrayList<PropertyChangeListener> specific = namedListeners.get(propertyName);
      if (specific != null) {
        for (PropertyChangeListener listener : specific) {
          listener.propertyChange(event);
        }
      }
    }
  }
}
