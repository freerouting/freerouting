package app.freerouting.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.interactive.InteractiveSettings;
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
  void clearanceMatrix_parseReturnsNumberForNumericTypes() {
    Float parsedInteger = WindowClearanceMatrix.parseClearanceTableValue(7);
    Float parsedDouble = WindowClearanceMatrix.parseClearanceTableValue(3.75d);

    assertNotNull(parsedInteger);
    assertNotNull(parsedDouble);
    assertEquals(7.0f, parsedInteger);
    assertEquals(3.75f, parsedDouble);
  }
}