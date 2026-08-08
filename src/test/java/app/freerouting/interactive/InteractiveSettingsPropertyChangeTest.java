package app.freerouting.interactive;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.board.BoardObserverAdaptor;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.core.RoutingJob;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.settings.RouterSettings;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link java.beans.PropertyChangeSupport} wiring in {@link
 * InteractiveSettings}.
 *
 * <p>Covers Sub-Issue 05: every setter fires the correct named {@link
 * java.beans.PropertyChangeEvent}; listener add/remove API works; read-only gate suppresses events;
 * {@link InteractiveSettings#getSettings()} returns a non-null {@link RouterSettings}.
 */
class InteractiveSettingsPropertyChangeTest {

  private static final String TEST_DSN = "fixtures/empty_board.dsn";

  private InteractiveSettings settings;

  @BeforeEach
  void setUp() throws FileNotFoundException {
    InteractiveSettings.resetForTesting();
    // Load a real board so that InteractiveSettings can be properly constructed.
    RoutingJob job = new RoutingJob();
    HeadlessBoardManager manager = new HeadlessBoardManager(job);
    manager.loadFromSpecctraDsn(
        new FileInputStream(TEST_DSN),
        new BoardObserverAdaptor(),
        new ItemIdentificationNumberGenerator());
    // Reset so the GUI singleton is created against the real board.
    settings = InteractiveSettings.reset(manager.getRoutingBoard());
  }

  @AfterEach
  void tearDown() {
    InteractiveSettings.resetForTesting();
  }

  // -------------------------------------------------------------------------
  // Helper: collect all fired events for a named property
  // -------------------------------------------------------------------------
  private List<PropertyChangeEvent> collectEvents(String propertyName, Runnable action) {
    List<PropertyChangeEvent> events = new ArrayList<>();
    PropertyChangeListener listener = events::add;
    settings.addPropertyChangeListener(propertyName, listener);
    action.run();
    settings.removePropertyChangeListener(propertyName, listener);
    return events;
  }

  // -------------------------------------------------------------------------
  // Setter → event tests
  // -------------------------------------------------------------------------

  @Test
  void setLayerFiresPropertyChangeEvent() {
    settings.setLayer(0);
    var events = collectEvents(InteractiveSettings.PROP_LAYER, () -> settings.setLayer(1));

    assertEquals(1, events.size());
    assertEquals(InteractiveSettings.PROP_LAYER, events.get(0).getPropertyName());
    assertEquals(0, events.get(0).getOldValue());
    assertEquals(1, events.get(0).getNewValue());
  }

  @Test
  void setPushEnabledFiresPropertyChangeEvent() {
    settings.setPushEnabled(true);
    var events =
        collectEvents(
            InteractiveSettings.PROP_PUSH_ENABLED, () -> settings.setPushEnabled(false));

    assertEquals(1, events.size());
    assertEquals(true, events.get(0).getOldValue());
    assertEquals(false, events.get(0).getNewValue());
  }

  @Test
  void setStitchRouteFiresPropertyChangeEvent() {
    settings.setStitchRoute(false);
    var events =
        collectEvents(
            InteractiveSettings.PROP_IS_STITCH_ROUTE, () -> settings.setStitchRoute(true));

    assertEquals(1, events.size());
    assertEquals(true, events.get(0).getNewValue());
  }

  @Test
  void setAutomaticNeckdownFiresPropertyChangeEvent() {
    settings.setAutomaticNeckdown(true);
    var events =
        collectEvents(
            InteractiveSettings.PROP_AUTOMATIC_NECKDOWN,
            () -> settings.setAutomaticNeckdown(false));

    assertEquals(1, events.size());
    assertEquals(false, events.get(0).getNewValue());
  }

  @Test
  void setManualTraceHalfWidthFiresPropertyChangeEvent() {
    var events =
        collectEvents(
            InteractiveSettings.PROP_MANUAL_TRACE_HALF_WIDTH,
            () -> settings.setManualTraceHalfWidth(0, 500));

    assertEquals(1, events.size());
    assertEquals(500, events.get(0).getNewValue());
  }

  @Test
  void setHilightRoutingObstacleFiresPropertyChangeEvent() {
    settings.setHilightRoutingObstacle(false);
    var events =
        collectEvents(
            InteractiveSettings.PROP_HILIGHT_ROUTING_OBSTACLE,
            () -> settings.setHilightRoutingObstacle(true));

    assertEquals(1, events.size());
    assertEquals(true, events.get(0).getNewValue());
  }

  @Test
  void setZoomWithWheelFiresEventOnlyWhenValueChanges() {
    settings.setZoomWithWheel(true);
    // Same value → no event
    var sameValueEvents =
        collectEvents(
            InteractiveSettings.PROP_ZOOM_WITH_WHEEL, () -> settings.setZoomWithWheel(true));
    assertEquals(0, sameValueEvents.size(), "No event expected when value does not change");

    // Different value → one event
    var changedEvents =
        collectEvents(
            InteractiveSettings.PROP_ZOOM_WITH_WHEEL, () -> settings.setZoomWithWheel(false));
    assertEquals(1, changedEvents.size());
  }

  // -------------------------------------------------------------------------
  // Listener add/remove
  // -------------------------------------------------------------------------

  @Test
  void removePropertyChangeListenerStopsReceivingEvents() {
    List<PropertyChangeEvent> events = new ArrayList<>();
    PropertyChangeListener listener = events::add;
    settings.addPropertyChangeListener(InteractiveSettings.PROP_LAYER, listener);
    settings.setLayer(1);
    settings.removePropertyChangeListener(InteractiveSettings.PROP_LAYER, listener);
    settings.setLayer(2); // removed listener must not receive this

    assertEquals(1, events.size());
    assertEquals(1, events.get(0).getNewValue());
  }

  @Test
  void addNullListenerDoesNotThrow() {
    // Both variants should silently ignore null without NPE.
    assertDoesNotThrow(() -> settings.addPropertyChangeListener((PropertyChangeListener) null));
    assertDoesNotThrow(() -> settings.removePropertyChangeListener((PropertyChangeListener) null));
  }

  // -------------------------------------------------------------------------
  // getSettings() contract
  // -------------------------------------------------------------------------

  @Test
  void getSettingsReturnsNonNullRouterSettings() {
    RouterSettings rs = settings.getSettings();
    assertNotNull(rs, "getSettings() must never return null");
    assertInstanceOf(RouterSettings.class, rs);
  }

  // -------------------------------------------------------------------------
  // readOnly gate
  // -------------------------------------------------------------------------

  @Test
  void setterDoesNotFireEventWhenReadOnly() {
    settings.setReadOnly(true);
    var events = collectEvents(InteractiveSettings.PROP_LAYER, () -> settings.setLayer(5));
    assertEquals(0, events.size(), "No events expected when readOnly is true");
    settings.setReadOnly(false);
  }
}
