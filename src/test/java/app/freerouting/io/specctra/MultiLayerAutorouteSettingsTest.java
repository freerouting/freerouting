package app.freerouting.io.specctra;

import app.freerouting.Freerouting;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.RouterSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for parsing {@code (autoroute_settings ...)} from DSN files
 * with more than two copper layers.
 *
 * <p>Original behavior: in {@code Structure.read_scope} the {@code AUTOROUTE_SETTINGS}
 * branch only invoked {@code AutorouteSettings.read_scope} when {@code layer_structure}
 * was still {@code null}. Once any earlier scope (a keepout, a plane, etc.) had
 * forced {@code layer_structure} to be constructed, the entire {@code (autoroute_settings ...)}
 * block was silently ignored — leaving {@code par.autoroute_settings == null}.
 *
 * <p>Combined with the {@code ReflectionUtil.copyFields} array-length-mismatch
 * bug (see {@link app.freerouting.management.ReflectionUtilTest}), this caused
 * {@code RouterSettings.isLayerActive[]} to stay at the 2-layer default on
 * 4-layer boards, producing
 * {@code "AutorouteSettings.get_layer_active: p_layer=N out of range [0..1]"}
 * warnings.
 */
class MultiLayerAutorouteSettingsTest {

  /**
   * 4-layer DSN with {@code (autoroute_settings ...)} placed AFTER a {@code (keepout ...)}
   * scope. The keepout forces {@code layer_structure} to be constructed before the parser
   * reaches {@code autoroute_settings} — which is what triggers the original bug.
   */
  private static final String FOUR_LAYER_DSN_KEEPOUT_BEFORE_AUTOROUTE =
      "(pcb test\n"
          + "  (resolution um 10)\n"
          + "  (structure\n"
          + "    (boundary (rect pcb 0.0 0.0 100000.0 -100000.0))\n"
          + "    (layer F.Cu (type signal))\n"
          + "    (layer In1.Cu (type signal))\n"
          + "    (layer In2.Cu (type signal))\n"
          + "    (layer B.Cu (type signal))\n"
          + "    (rule (width 250.0) (clear 200.0))\n"
          + "    (keepout (polygon F.Cu 0 1000.0 -1000.0 2000.0 -1000.0 2000.0 -2000.0 1000.0 -2000.0))\n"
          + "    (autoroute_settings\n"
          + "      (autoroute on)\n"
          + "      (postroute on)\n"
          + "      (vias on)\n"
          + "      (via_costs 50)\n"
          + "      (plane_via_costs 5)\n"
          + "      (start_ripup_costs 100)\n"
          + "      (layer_rule F.Cu   (active on)  (preferred_direction horizontal) (preferred_direction_trace_costs 1.0) (against_preferred_direction_trace_costs 2.0))\n"
          + "      (layer_rule In1.Cu (active off) (preferred_direction vertical)   (preferred_direction_trace_costs 1.0) (against_preferred_direction_trace_costs 2.0))\n"
          + "      (layer_rule In2.Cu (active off) (preferred_direction horizontal) (preferred_direction_trace_costs 1.0) (against_preferred_direction_trace_costs 2.0))\n"
          + "      (layer_rule B.Cu   (active on)  (preferred_direction vertical)   (preferred_direction_trace_costs 1.0) (against_preferred_direction_trace_costs 2.0))\n"
          + "    )\n"
          + "  )\n"
          + ")\n";

  /**
   * Same 4-layer DSN, but with no scope between the layers and {@code (autoroute_settings ...)}.
   * Here {@code layer_structure} is still {@code null} when the parser reaches
   * {@code autoroute_settings}, so the original code did parse it. Used as a control
   * to confirm the parser still works in the unaffected case.
   */
  private static final String FOUR_LAYER_DSN_NO_KEEPOUT =
      "(pcb test\n"
          + "  (resolution um 10)\n"
          + "  (structure\n"
          + "    (boundary (rect pcb 0.0 0.0 100000.0 -100000.0))\n"
          + "    (layer F.Cu (type signal))\n"
          + "    (layer In1.Cu (type signal))\n"
          + "    (layer In2.Cu (type signal))\n"
          + "    (layer B.Cu (type signal))\n"
          + "    (rule (width 250.0) (clear 200.0))\n"
          + "    (autoroute_settings\n"
          + "      (autoroute on)\n"
          + "      (postroute on)\n"
          + "      (vias on)\n"
          + "      (via_costs 50)\n"
          + "      (plane_via_costs 5)\n"
          + "      (start_ripup_costs 100)\n"
          + "      (layer_rule F.Cu   (active on)  (preferred_direction horizontal) (preferred_direction_trace_costs 1.0) (against_preferred_direction_trace_costs 2.0))\n"
          + "      (layer_rule In1.Cu (active off) (preferred_direction vertical)   (preferred_direction_trace_costs 1.0) (against_preferred_direction_trace_costs 2.0))\n"
          + "      (layer_rule In2.Cu (active off) (preferred_direction horizontal) (preferred_direction_trace_costs 1.0) (against_preferred_direction_trace_costs 2.0))\n"
          + "      (layer_rule B.Cu   (active on)  (preferred_direction vertical)   (preferred_direction_trace_costs 1.0) (against_preferred_direction_trace_costs 2.0))\n"
          + "    )\n"
          + "  )\n"
          + ")\n";

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  @Test
  void parsesAutorouteSettingsAfterKeepoutOnFourLayerBoard() {
    DsnReadResult result = DsnReader.readMetadata(streamOf(FOUR_LAYER_DSN_KEEPOUT_BEFORE_AUTOROUTE));

    assertInstanceOf(DsnReadResult.Success.class, result);
    DsnMetadata meta = ((DsnReadResult.Success) result).metadata();
    assertEquals(4, meta.layerCount(), "structure should report 4 layers");

    RouterSettings settings = meta.routerSettings();
    assertNotNull(settings,
        "autoroute_settings must be parsed even when layer_structure was already constructed "
            + "by an earlier scope (e.g. a keepout) — original code skipped this block silently.");
    assertEquals(4, settings.getLayerCount(),
        "isLayerActive[] must be sized to the layer count parsed from the DSN.");

    // Per-layer values from the DSN: F.Cu=on, In1.Cu=off, In2.Cu=off, B.Cu=on
    assertTrue(settings.get_layer_active(0),  "F.Cu must be active");
    assertFalse(settings.get_layer_active(1), "In1.Cu must be inactive");
    assertFalse(settings.get_layer_active(2), "In2.Cu must be inactive");
    assertTrue(settings.get_layer_active(3),  "B.Cu must be active");
  }

  @Test
  void parsesAutorouteSettingsWithoutPriorKeepoutOnFourLayerBoard() {
    DsnReadResult result = DsnReader.readMetadata(streamOf(FOUR_LAYER_DSN_NO_KEEPOUT));

    assertInstanceOf(DsnReadResult.Success.class, result);
    DsnMetadata meta = ((DsnReadResult.Success) result).metadata();
    assertEquals(4, meta.layerCount());

    RouterSettings settings = meta.routerSettings();
    assertNotNull(settings, "autoroute_settings must be parsed");
    assertEquals(4, settings.getLayerCount(),
        "isLayerActive[] must be sized to the layer count parsed from the DSN.");
    assertTrue(settings.get_layer_active(0));
    assertFalse(settings.get_layer_active(1));
    assertFalse(settings.get_layer_active(2));
    assertTrue(settings.get_layer_active(3));
  }

  private static InputStream streamOf(String dsn) {
    return new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8));
  }
}
