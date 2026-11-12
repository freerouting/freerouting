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
}
