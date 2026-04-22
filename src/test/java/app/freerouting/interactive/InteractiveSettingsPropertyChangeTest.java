package app.freerouting.interactive;

import app.freerouting.board.BoardObserverAdaptor;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.core.RoutingJob;
import app.freerouting.settings.RouterSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link java.beans.PropertyChangeSupport} wiring in {@link InteractiveSettings}.
 *
 * <p>Covers Sub-Issue 05: every setter fires the correct named
 * {@link java.beans.PropertyChangeEvent}; listener add/remove API works; read-only gate suppresses
 * events; {@link InteractiveSettings#getSettings()} returns a non-null {@link RouterSettings}.
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
    settings = InteractiveSettings.reset(manager.get_routing_board());
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
  void setLayer_firesPropertyChangeEvent() {
    settings.set_layer(0);
    var events = collectEvents(InteractiveSettings.PROP_LAYER, () -> settings.set_layer(1));

    assertEquals(1, events.size());
    assertEquals(InteractiveSettings.PROP_LAYER, events.get(0).getPropertyName());
    assertEquals(0, events.get(0).getOldValue());
    assertEquals(1, events.get(0).getNewValue());
  }

  @Test
  void setPushEnabled_firesPropertyChangeEvent() {
    settings.set_push_enabled(true);
    var events = collectEvents(InteractiveSettings.PROP_PUSH_ENABLED,
        () -> settings.set_push_enabled(false));

    assertEquals(1, events.size());
    assertEquals(true,  events.get(0).getOldValue());
    assertEquals(false, events.get(0).getNewValue());
  }

  @Test
  void setStitchRoute_firesPropertyChangeEvent() {
    settings.set_stitch_route(false);
    var events = collectEvents(InteractiveSettings.PROP_IS_STITCH_ROUTE,
        () -> settings.set_stitch_route(true));

    assertEquals(1, events.size());
    assertEquals(true, events.get(0).getNewValue());
  }

  @Test
  void setAutomaticNeckdown_firesPropertyChangeEvent() {
    settings.set_automatic_neckdown(true);
    var events = collectEvents(InteractiveSettings.PROP_AUTOMATIC_NECKDOWN,
        () -> settings.set_automatic_neckdown(false));

    assertEquals(1, events.size());
    assertEquals(false, events.get(0).getNewValue());
  }

  @Test
  void setManualTraceHalfWidth_firesPropertyChangeEvent() {
    var events = collectEvents(InteractiveSettings.PROP_MANUAL_TRACE_HALF_WIDTH,
        () -> settings.set_manual_trace_half_width(0, 500));

    assertEquals(1, events.size());
    assertEquals(500, events.get(0).getNewValue());
  }

  @Test
  void setHilightRoutingObstacle_firesPropertyChangeEvent() {
    settings.set_hilight_routing_obstacle(false);
    var events = collectEvents(InteractiveSettings.PROP_HILIGHT_ROUTING_OBSTACLE,
        () -> settings.set_hilight_routing_obstacle(true));

    assertEquals(1, events.size());
    assertEquals(true, events.get(0).getNewValue());
  }

  @Test
  void setZoomWithWheel_firesEventOnlyWhenValueChanges() {
    settings.set_zoom_with_wheel(true);
    // Same value → no event
    var sameValueEvents = collectEvents(InteractiveSettings.PROP_ZOOM_WITH_WHEEL,
        () -> settings.set_zoom_with_wheel(true));
    assertEquals(0, sameValueEvents.size(), "No event expected when value does not change");

    // Different value → one event
    var changedEvents = collectEvents(InteractiveSettings.PROP_ZOOM_WITH_WHEEL,
        () -> settings.set_zoom_with_wheel(false));
    assertEquals(1, changedEvents.size());
  }

  // -------------------------------------------------------------------------
  // Listener add/remove
  // -------------------------------------------------------------------------

  @Test
  void removePropertyChangeListener_stopsReceivingEvents() {
    List<PropertyChangeEvent> events = new ArrayList<>();
    PropertyChangeListener listener = events::add;
    settings.addPropertyChangeListener(InteractiveSettings.PROP_LAYER, listener);
    settings.set_layer(1);
    settings.removePropertyChangeListener(InteractiveSettings.PROP_LAYER, listener);
    settings.set_layer(2); // removed listener must not receive this

    assertEquals(1, events.size());
    assertEquals(1, events.get(0).getNewValue());
  }

  @Test
  void addNullListener_doesNotThrow() {
    // Both variants should silently ignore null without NPE.
    assertDoesNotThrow(() -> settings.addPropertyChangeListener((PropertyChangeListener) null));
    assertDoesNotThrow(() -> settings.removePropertyChangeListener((PropertyChangeListener) null));
  }

  // -------------------------------------------------------------------------
  // getSettings() contract
  // -------------------------------------------------------------------------

  @Test
  void getSettings_returnsNonNullRouterSettings() {
    RouterSettings rs = settings.getSettings();
    assertNotNull(rs, "getSettings() must never return null");
    assertInstanceOf(RouterSettings.class, rs);
  }

  // -------------------------------------------------------------------------
  // read_only gate
  // -------------------------------------------------------------------------

  @Test
  void setter_doesNotFireEvent_whenReadOnly() {
    settings.set_read_only(true);
    var events = collectEvents(InteractiveSettings.PROP_LAYER,
        () -> settings.set_layer(5));
    assertEquals(0, events.size(), "No events expected when read_only is true");
    settings.set_read_only(false);
  }
}

