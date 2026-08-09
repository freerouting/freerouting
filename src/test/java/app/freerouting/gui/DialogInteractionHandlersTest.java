package app.freerouting.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.freerouting.board.CoordinateTransform;
import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.interactive.InteractiveSettings;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.NetClass;
import app.freerouting.settings.RouterSettings;
import org.junit.jupiter.api.Test;

class DialogInteractionHandlersTest {

  @Test
  void routingSettings_checkboxAndRadioInteractionsInvokeExpectedSetters() {
    InteractiveSettings interactiveSettings = mock(InteractiveSettings.class);
    GuiBoardManager boardManager = mock(GuiBoardManager.class);

    WindowRouteParameter.applyStitchRouteSelection(interactiveSettings, true);
    WindowRouteParameter.applyPushAndShoveSelection(interactiveSettings, false);
    WindowRouteParameter.applyIgnoreConductionSelection(boardManager, true);
    WindowRouteParameter.applyClearanceCompensationSelection(boardManager, false);
    WindowRouteParameter.applyPinExitEdgeToTurnDistance(boardManager, 125.5f);

    verify(interactiveSettings).set_stitch_route(true);
    verify(interactiveSettings).set_push_enabled(false);
    verify(boardManager).set_ignore_conduction(true);
    verify(boardManager).set_clearance_compensation(false);
    verify(boardManager).set_pin_edge_to_turn_dist(125.5f);
  }

  @Test
  void autoRouterSettings_checkboxAndAlgorithmInteractionsInvokeExpectedSetters() {
    RouterSettings settings = mock(RouterSettings.class);

    WindowAutorouteParameter.applyViasAllowedSelection(settings, true);
    WindowAutorouteParameter.applyAutorouteEnabledSelection(settings, false);
    WindowAutorouteParameter.applyOptimizerEnabledSelection(settings, true);
    WindowAutorouteParameter.applyAlgorithmSelection(settings, true);
    WindowAutorouteParameter.applyAlgorithmSelection(settings, false);

    verify(settings).setViasAllowed(true);
    verify(settings).setEnabled(false);
    verify(settings).setOptimizerEnabled(true);
    verify(settings).setAlgorithm(RouterSettings.ALGORITHM_V19);
    verify(settings).setAlgorithm(RouterSettings.ALGORITHM_CURRENT);
  }

  @Test
  void autoRouterSettings_textFieldNormalizationHandlesBoundsAndInvalidInputs() {
    assertEquals(1, WindowAutorouteParameter.normalizeIntInput(0, 9, 1, 9999));
    assertEquals(9999, WindowAutorouteParameter.normalizeIntInput(12000, 9, 1, 9999));
    assertEquals(42, WindowAutorouteParameter.normalizeIntInput(42, 9, 1, 9999));
    assertEquals(9, WindowAutorouteParameter.normalizeIntInput("bad", 9, 1, 9999));

    assertEquals(
        "12:00:00",
        WindowAutorouteParameter.normalizeTimeoutInput("12:00:00", "00:30:00"));
    assertEquals("00:30:00", WindowAutorouteParameter.normalizeTimeoutInput("bad", "00:30:00"));

    assertEquals(2.5, WindowAutorouteParameter.normalizePositiveDoubleInput(2.5, 1.0));
    assertEquals(1.0, WindowAutorouteParameter.normalizePositiveDoubleInput(-3.0, 1.0));
  }

  @Test
  void clearanceMatrix_gridCellParsingAndLayeredApplyWorkAsExpected() {
    assertTrue(WindowClearanceMatrix.isLegalClassName("default2"));
    assertFalse(WindowClearanceMatrix.isLegalClassName("invalid_name"));
    assertFalse(WindowClearanceMatrix.isLegalClassName(""));

    assertEquals(2.5f, WindowClearanceMatrix.parseClearanceTableValue(2.5f));
    assertEquals(1.25f, WindowClearanceMatrix.parseClearanceTableValue("1.25"));
    assertNull(WindowClearanceMatrix.parseClearanceTableValue("not-a-number"));

    ClearanceMatrix matrix = mock(ClearanceMatrix.class);
    WindowClearanceMatrix.applyClearanceValue(matrix, 2, 1, ComboBoxLayer.ALL_LAYER_INDEX, 300);
    verify(matrix).set_value(2, 1, 300);
    verify(matrix).set_value(1, 2, 300);

    ClearanceMatrix innerMatrix = mock(ClearanceMatrix.class);
    WindowClearanceMatrix.applyClearanceValue(
        innerMatrix,
        2,
        1,
        ComboBoxLayer.INNER_LAYER_INDEX,
        220);
    verify(innerMatrix).set_inner_value(2, 1, 220);
    verify(innerMatrix).set_inner_value(1, 2, 220);

    ClearanceMatrix singleLayerMatrix = mock(ClearanceMatrix.class);
    WindowClearanceMatrix.applyClearanceValue(singleLayerMatrix, 2, 1, 3, 180);
    verify(singleLayerMatrix).set_value(2, 1, 3, 180);
    verify(singleLayerMatrix).set_value(1, 2, 3, 180);
  }

  @Test
  void netClasses_buttonAndTableHelpersApplyExpectedChanges() {
    assertFalse(WindowNetClasses.canRemoveNetClass(1, 0));
    assertFalse(WindowNetClasses.canRemoveNetClass(2, -1));
    assertTrue(WindowNetClasses.canRemoveNetClass(2, 0));

    NetClass netClass = mock(NetClass.class);
    WindowNetClasses.applyShoveFixedSelection(netClass, true);
    verify(netClass).set_shove_fixed(true);
    verify(netClass).set_pull_tight(false);

    NetClass fieldNetClass = mock(NetClass.class);
    assertFalse(fieldNetClass.is_ignored_by_autorouter);
    WindowNetClasses.applyAutorouterIgnoreSelection(fieldNetClass, true);
    assertTrue(fieldNetClass.is_ignored_by_autorouter);
    WindowNetClasses.applyAutorouterIgnoreSelection(fieldNetClass, false);
    assertFalse(fieldNetClass.is_ignored_by_autorouter);
  }

  @Test
  void netClasses_traceWidthApplyHandlesValidInvalidAndZeroInputs() {
    CoordinateTransform transform = mock(CoordinateTransform.class);
    when(transform.user_to_board(anyDouble()))
        .thenAnswer(inv -> ((Double) inv.getArgument(0)) * 1000.0);

    NetClass positiveFloat = mock(NetClass.class);
    assertTrue(WindowNetClasses.applyTraceWidthValue(positiveFloat, transform, 0.2f));
    verify(positiveFloat).set_trace_half_width(100);
    verify(positiveFloat, never()).set_all_layers_active(anyBoolean());

    NetClass positiveString = mock(NetClass.class);
    assertTrue(WindowNetClasses.applyTraceWidthValue(positiveString, transform, "  0.25  "));
    verify(positiveString).set_trace_half_width(125);

    NetClass zeroValue = mock(NetClass.class);
    assertTrue(WindowNetClasses.applyTraceWidthValue(zeroValue, transform, 0f));
    verify(zeroValue).set_trace_half_width(0);
    verify(zeroValue).set_all_layers_active(false);

    NetClass invalid = mock(NetClass.class);
    assertFalse(WindowNetClasses.applyTraceWidthValue(invalid, transform, null));
    assertFalse(WindowNetClasses.applyTraceWidthValue(invalid, transform, "not-a-number"));
    assertFalse(WindowNetClasses.applyTraceWidthValue(invalid, transform, "width_multiple"));
    assertFalse(WindowNetClasses.applyTraceWidthValue(invalid, transform, -0.5f));
    assertFalse(WindowNetClasses.applyTraceWidthValue(invalid, transform, Float.NaN));
    assertFalse(WindowNetClasses.applyTraceWidthValue(invalid, transform, Float.POSITIVE_INFINITY));
    assertFalse(WindowNetClasses.applyTraceWidthValue(invalid, transform, 0.0001f));
    assertFalse(WindowNetClasses.applyTraceWidthValue(invalid, transform, 7));
    verify(invalid, never()).set_trace_half_width(anyInt());

    assertFalse(WindowNetClasses.applyTraceWidthValue(null, transform, 0.2f));
    assertFalse(WindowNetClasses.applyTraceWidthValue(mock(NetClass.class), null, 0.2f));
  }

  @Test
  void netClasses_traceWidthEditActuallyMutatesTheBoardModel() {
    // Build a real adjacency between NetClass and its model as used by the GUI table.
    LayerStructure layerStructure =
        new LayerStructure(
            new Layer[] {new Layer("Top", true), new Layer("In1", false), new Layer("Bottom", true)});
    ClearanceMatrix clearanceMatrix = ClearanceMatrix.get_default_instance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.create_default_net_class();
    NetClass netClass = boardRules.get_default_net_class();

    CoordinateTransform transform = mock(CoordinateTransform.class);
    when(transform.user_to_board(anyDouble()))
        .thenAnswer(inv -> ((Double) inv.getArgument(0)) * 1000.0);

    int originalHalfWidth = netClass.get_trace_half_width(0);
    assertTrue(originalHalfWidth > 0);

    // Editing the "track width" from the GUI cell persists into the real model.
    assertTrue(WindowNetClasses.applyTraceWidthValue(netClass, transform, "0.25"));
    assertEquals(125, netClass.get_trace_half_width(0));
    assertNotEquals(originalHalfWidth, netClass.get_trace_half_width(0));
    for (int i = 0; i < layerStructure.arr.length; i++) {
      assertEquals(125, netClass.get_trace_half_width(i));
      if (layerStructure.arr[i].is_signal) {
        // a positive edit must never silently re-activate or deactivate layers
        assertTrue(netClass.is_active_routing_layer(i));
      }
    }

    // Explicit 0 keeps the legacy semantic: routing disabled on this class.
    assertTrue(WindowNetClasses.applyTraceWidthValue(netClass, transform, 0f));
    assertEquals(0, netClass.get_trace_half_width(0));
    for (int i = 0; i < layerStructure.arr.length; i++) {
      assertEquals(0, netClass.get_trace_half_width(i));
      if (layerStructure.arr[i].is_signal) {
        assertFalse(netClass.is_active_routing_layer(i));
      }
    }
  }

  @Test
  void clearanceMatrix_parseReturnsNumberForNumericTypes() {
    Float parsedInteger = WindowClearanceMatrix.parseClearanceTableValue(7);
    Float parsedDouble = WindowClearanceMatrix.parseClearanceTableValue(3.75d);

    assertNotNull(parsedInteger);
    assertNotNull(parsedDouble);
    assertEquals(7.0f, parsedInteger);
    assertEquals(3.75f, parsedDouble);
  }
}