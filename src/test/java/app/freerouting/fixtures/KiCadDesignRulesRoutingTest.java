package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.TestFixtures;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.core.RoutingJob;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.io.kicad.KiCadDesignRules;
import app.freerouting.rules.Net;
import app.freerouting.settings.DesignRulesCheckerSettings;
import app.freerouting.settings.sources.TestingSettings;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * KiCad exports only the net class clearances to Specctra: the custom rules of the .kicad_dru are
 * lost. With the .kicad_dru given to the job, the board is routed with them: here a 3 mm isolation
 * between the HighVoltage class and the rest of the board, where the DSN says 2 mm.
 */
class KiCadDesignRulesRoutingTest extends RoutingFixtureTest {

  @Test
  void routesWithTheClearancesOfTheKiCadRules() throws IOException {
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setMaxPasses(2);
    testingSettings.setOptimizerEnabled(false);
    testingSettings.setJobTimeoutString("00:02:00");
    RoutingJob job = getRoutingJob("Issue110-RelayModule.dsn", testingSettings);
    job.kicadDesignRules =
        KiCadDesignRules.read(TestFixtures.resolvePath("Issue110-RelayModule.kicad_dru"));

    runRoutingJob(job);

    assertNotNull(job.board.rules.netClasses.get("HighVoltage_2"), "HighVoltage must be split");
    // Only fixed pads violate the translated clearances: the terminal blocks XT2 and XT3, whose
    // pins are 2.08 mm apart where the rule that cannot be evaluated (near_the_edge, 4 mm) may
    // match. The routing adds no violation.
    assertRoutingResult(job, "Issue110-RelayModule.dsn")
        .maxClearanceViolations(4)
        .maxDuration(Duration.ofMinutes(2))
        .check();
    var violations =
        new DesignRulesChecker(job.board, new DesignRulesCheckerSettings())
            .getAllClearanceViolations();
    for (var violation : violations) {
      assertTrue(
          violation.firstItem instanceof Pin && violation.secondItem instanceof Pin,
          "a trace or via violates a translated clearance: " + violation);
    }

    // and the traces it adds use them: a new HighVoltage trace keeps 3 mm from the GND net
    Net highVoltage = job.board.rules.nets.get("Net-(FU2-Pad1)", 1);
    Net ground = job.board.rules.nets.get("GND", 1);
    int expected =
        (int) Math.ceil(job.board.communication.physicalToBoard(3000.0, Unit.UM)) / 2 * 2;
    int highVoltageTraces = 0;
    for (Item item : job.board.getItems()) {
      if (item instanceof Trace trace && trace.containsNet(highVoltage.netNumber)) {
        highVoltageTraces++;
        assertTrue(
            job.board.rules.clearanceMatrix.getValue(
                    trace.clearanceClassIndex(),
                    ground.getNetClass().getTraceClearanceClass(),
                    trace.getLayer(),
                    false)
                >= expected,
            "a HighVoltage trace must keep the 3 mm of mains_isolation from GND");
      }
    }
    assertTrue(highVoltageTraces > 0, "the HighVoltage net must have been routed");
  }

  /**
   * Creepage-later-clearance-garland: in each of three corridors, a low-voltage net LV_x routes
   * around a mains pad HV_x, and the .kicad_dru asks for 3 mm of creepage between them (no slots:
   * the copper to copper distance). In corridor C, a later rule sets only a clearance of 0.3 mm on
   * LV_C; KiCad keeps one rule list per constraint type, so the 3 mm creepage still applies.
   */
  @Test
  void keepsTheCreepageOfAnEarlierRuleWhenALaterRuleSetsOnlyAClearance() throws IOException {
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setMaxPasses(20);
    testingSettings.setOptimizerEnabled(false);
    testingSettings.setJobTimeoutString("00:01:00");
    RoutingJob job = getRoutingJob("Creepage-later-clearance-garland.dsn", testingSettings);
    job.kicadDesignRules =
        KiCadDesignRules.read(
            TestFixtures.resolvePath("Creepage-later-clearance-garland.kicad_dru"));

    runRoutingJob(job);

    assertRoutingResult(job, "Creepage-later-clearance-garland.dsn")
        .maxIncompleteConnections(0)
        .maxClearanceViolations(0)
        .check();
    double required = job.board.communication.physicalToBoard(2999.0, Unit.UM);
    for (String corridor : new String[] {"A", "B", "C"}) {
      Net lowVoltage = job.board.rules.nets.get("LV_" + corridor, 1);
      Net mains = job.board.rules.nets.get("HV_" + corridor, 1);
      double smallest = Double.MAX_VALUE;
      int traces = 0;
      for (Item item : job.board.getItems()) {
        if (item instanceof Trace trace && trace.containsNet(lowVoltage.netNumber)) {
          traces++;
          for (Item other : job.board.getItems()) {
            if (other instanceof Pin pin && pin.containsNet(mains.netNumber)) {
              smallest = Math.min(smallest, distance(trace, pin));
            }
          }
        }
      }
      assertTrue(traces > 0, "LV_" + corridor + " must have been routed");
      assertTrue(
          smallest >= required,
          "LV_"
              + corridor
              + " must keep the 3 mm creepage from HV_"
              + corridor
              + ", found "
              + Math.round(smallest / job.board.communication.physicalToBoard(1.0, Unit.UM))
              + " um");
    }
  }

  /**
   * Creepage-relay-slot-garland: two mains relays whose coil and contacts are 7.86 mm apart, with
   * an 8 mm creepage and a later 7.8 mm clearance-only rule within each relay. A slot under K1
   * makes its creepage path longer than 8 mm, so KiCad accepts it (and reports K2, which has none).
   * Freerouting does not model the slot: between two pads it leaves the creepage to KiCad, so the
   * board loads without violations, while the traces it adds keep the 8 mm creepage from the mains
   * pads.
   */
  @Test
  void leavesTheCreepageBetweenTwoPadsToKiCad() throws IOException {
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setMaxPasses(20);
    testingSettings.setOptimizerEnabled(false);
    testingSettings.setJobTimeoutString("00:01:00");
    RoutingJob job = getRoutingJob("Creepage-relay-slot-garland.dsn", testingSettings);
    job.kicadDesignRules =
        KiCadDesignRules.read(TestFixtures.resolvePath("Creepage-relay-slot-garland.kicad_dru"));

    runRoutingJob(job);

    assertRoutingResult(job, "Creepage-relay-slot-garland.dsn")
        .maxIncompleteConnections(0)
        .maxClearanceViolations(0)
        .check();
    double required = job.board.communication.physicalToBoard(7999.0, Unit.UM);
    int traces = 0;
    for (Item item : job.board.getItems()) {
      if (item instanceof Trace trace
          && !job.board.rules.nets.get(trace.getNetNumber(0)).name.startsWith("/MAINS/")) {
        traces++;
        for (Item other : job.board.getItems()) {
          if (other instanceof Pin pin
              && pin.netCount() > 0
              && job.board.rules.nets.get(pin.getNetNumber(0)).name.startsWith("/MAINS/")) {
            double distance = distance(trace, pin);
            assertTrue(
                distance >= required,
                "a low-voltage trace must keep the 8 mm creepage from the mains pad "
                    + pin
                    + ", found "
                    + Math.round(distance / job.board.communication.physicalToBoard(1.0, Unit.UM))
                    + " um");
          }
        }
      }
    }
    assertTrue(traces > 0, "the low-voltage nets must have been routed");
  }

  /** The smallest distance between the copper of two items, from the corners of their shapes. */
  private static double distance(Item first, Item second) {
    double result = Double.MAX_VALUE;
    for (int i = 0; i < first.tileShapeCount(); i++) {
      TileShape a = first.getTileShape(i);
      for (int j = 0; j < second.tileShapeCount(); j++) {
        TileShape b = second.getTileShape(j);
        for (int k = 0; k < a.borderLineCount(); k++) {
          result = Math.min(result, b.distance(a.cornerApprox(k)));
        }
        for (int k = 0; k < b.borderLineCount(); k++) {
          result = Math.min(result, a.distance(b.cornerApprox(k)));
        }
      }
    }
    return result;
  }
}
