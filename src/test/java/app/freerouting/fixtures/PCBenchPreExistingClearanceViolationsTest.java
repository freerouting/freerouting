package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying that pre-existing false-positive clearance violations on PCBench fixtures (caused
 * by sub-micron rounding errors, same-net composite pads, and thermal via arrays) are eliminated.
 */
class PCBenchPreExistingClearanceViolationsTest {

  private int countPreExistingViolations(String fixtureSubdirectory) throws Exception {
    File dsnFile =
        Path.of("scripts/benchmark/fixtures/PCBench", fixtureSubdirectory, "unrouted.dsn").toFile();
    if (!dsnFile.exists()) {
      throw new IllegalArgumentException("Fixture DSN not found: " + dsnFile.getAbsolutePath());
    }

    try (FileInputStream fis = new FileInputStream(dsnFile)) {
      BoardReadResult result = DsnReader.readBoard(fis, null, null, dsnFile.getName());
      BasicBoard board =
          switch (result) {
            case BoardReadResult.Success s -> s.board();
            case BoardReadResult.OutlineMissing o -> o.board();
            case null, default -> throw new RuntimeException("Failed to read board: " + result);
          };

      DesignRulesChecker drc = new DesignRulesChecker(board, null);
      Collection<ClearanceViolation> violations = drc.getAllClearanceViolations();
      return violations.size();
    }
  }

  @Test
  void testMicrodoxSubMicronRoundingViolationsResolved() throws Exception {
    // Had 8 violations due to 0.68 um imperial-to-metric conversion shortfall on P1 USB pins
    int count = countPreExistingViolations("Microdox-PCB_Print-Microdox");
    assertEquals(0, count, "Microdox should have 0 pre-existing clearance violations");
  }

  @Test
  void testMppt2420HcCornerDiscretizationViolationResolved() throws Exception {
    // Had 1 violation due to 0.50 um rounded-rectangle discretization shortfall on U2
    int count = countPreExistingViolations("mppt-2420-hc_mppt-2420-hc");
    assertEquals(0, count, "mppt-2420-hc should have 0 pre-existing clearance violations");
  }

  @Test
  void testDriverinoShieldThermalViasAndPerimeterPinsResolved() throws Exception {
    // Had 28 violations: 16 from same-net thermal vias inside pad 41, 12 from 0.1 um shortfalls
    int count = countPreExistingViolations("kitspace_Driverino-Shield");
    assertEquals(0, count, "Driverino-Shield should have 0 pre-existing clearance violations");
  }

  @Test
  void testA123BatteryTiledThermalPadResolved() throws Exception {
    // Had 6 violations from a 2x2 grid of same-net sub-pads forming exposed pad 9 of U202
    int count = countPreExistingViolations("a123-battery-integration_BCM");
    assertEquals(0, count, "a123-battery should have 0 pre-existing clearance violations");
  }

  @Test
  void testBuckLedDriverSot89CompositeTabResolved() throws Exception {
    // Had 3 violations from overlapping trapezoid and rectangle sub-pads on SOT-89 tab
    int count = countPreExistingViolations("Hardware_Playground_buck_led_driver");
    assertEquals(0, count, "buck_led_driver should have 0 pre-existing clearance violations");
  }

  @Test
  void testR1007TransistorCompositeDrainPadsResolved() throws Exception {
    // Had 4 violations from stacked rectangular sub-pads on SOT transistor drains
    int count = countPreExistingViolations("R1007_R1007");
    assertEquals(0, count, "R1007 should have 0 pre-existing clearance violations");
  }

  @Test
  void testInkjetPiezoDriverFloatingThermalPadResolved() throws Exception {
    // Had 6 violations from 4 netless sub-pads forming floating thermal pad 21 of U1
    int count = countPreExistingViolations("Inkjet_PiezoDriver");
    assertEquals(0, count, "Inkjet_PiezoDriver should have 0 pre-existing clearance violations");
  }

  @Test
  void testRoombaEspConnectorSameNetPinsResolved() throws Exception {
    // Had 2 violations between pins 1 and 2 of mini DIN connector P1 sharing net /VBat
    int count = countPreExistingViolations("roomba-ESP12E_roomba-esp");
    assertEquals(0, count, "roomba-ESP12E should have 0 pre-existing clearance violations");
  }

  @Test
  void testBasePinNameNormalization() {
    assertEquals("21", app.freerouting.board.model.items.Pin.getBasePinName("21@1"));
    assertEquals("21", app.freerouting.board.model.items.Pin.getBasePinName("21@2"));
    assertEquals("pad", app.freerouting.board.model.items.Pin.getBasePinName("pad#1"));
    assertEquals("pad_1", app.freerouting.board.model.items.Pin.getBasePinName("pad_1_1"));
    assertEquals("pad_1", app.freerouting.board.model.items.Pin.getBasePinName("pad_1_2"));
    assertEquals("EP", app.freerouting.board.model.items.Pin.getBasePinName("EP-1"));
    assertEquals("EP", app.freerouting.board.model.items.Pin.getBasePinName("EP-2"));
    assertEquals("1", app.freerouting.board.model.items.Pin.getBasePinName("1"));
    assertEquals("GND", app.freerouting.board.model.items.Pin.getBasePinName("GND"));
    assertEquals("", app.freerouting.board.model.items.Pin.getBasePinName(null));
  }
}
