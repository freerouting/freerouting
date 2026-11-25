package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.TestFixtures;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.ObstacleArea;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.Session;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.io.kicad.KiCadDesignRules;
import app.freerouting.management.sessions.SessionManager;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.DsnFileSettings;
import app.freerouting.settings.sources.TestingSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A pad without net that the KiCad export writes as a round keepout is both a pad for the {@code
 * .kicad_dru} rules and a drilled hole for the hole clearance. With a project file, the hole
 * clearance is always applied after the rules: it must add its floor to the clearances the rules
 * gave the keepout, not replace them.
 */
class KiCadDesignRulesHoleClearanceTest extends RoutingFixtureTest {

  private static final String DSN = "Issue110-RelayModule.dsn";

  @TempDir Path dir;

  @Test
  void theHoleClearanceKeepsTheRulesOfAPadExportedAsKeepout() throws IOException {
    String dsn = Files.readString(TestFixtures.resolveFile(DSN).toPath(), StandardCharsets.UTF_8);
    String withKeepout =
        dsn.replace(
            "(image \"Реле:SRD-xxVDC-SL-C\"\n",
            "(image \"Реле:SRD-xxVDC-SL-C\"\n"
                + "      (keepout \"\" (circle F.Cu 1500 -6000 -6000))\n"
                + "      (keepout \"\" (circle B.Cu 1500 -6000 -6000))\n");
    assertTrue(!withKeepout.equals(dsn), "the keepout must be added to the fixture");
    Path file = Files.writeString(dir.resolve("relay-with-pad-keepout.dsn"), withKeepout);

    RoutingJob job = job(file);
    job.kicadDesignRules =
        KiCadDesignRules.parse(
            """
            (version 1)
            (rule "around_k1"
              (condition "A.memberOfFootprint('K1') && !B.memberOfFootprint('K1')")
              (constraint clearance (min 7mm)))
            """,
            "test.kicad_dru");
    // As KiCadProjectSettings sets it whenever a .kicad_pro or a .kicad_pcb is given
    job.routerSettings.holeClearanceUm = 250.0;

    runRoutingJob(job);

    ClearanceMatrix matrix = job.board.rules.clearanceMatrix;
    ObstacleArea keepout = padKeepout(job, "K1");
    int ground = job.board.rules.nets.get("GND", 1).getNetClass().getTraceClearanceClass();
    // the rule of the .kicad_dru: 7 mm between the pad of K1 and the traces of GND
    assertTrue(
        matrix.getValue(keepout.clearanceClassIndex(), ground, 0, false) >= board(job, 7000.0),
        "the 7 mm of around_k1 must survive the hole clearance, found "
            + um(job, matrix.getValue(keepout.clearanceClassIndex(), ground, 0, false))
            + " um");
    // and the floor of the hole clearance, where no rule raises it: K1 against its own pins
    int ownPin = pin(job, "K1").clearanceClassIndex();
    assertTrue(
        matrix.getValue(keepout.clearanceClassIndex(), ownPin, 0, false) >= board(job, 250.0),
        "the hole clearance must still apply to the keepout, found "
            + um(job, matrix.getValue(keepout.clearanceClassIndex(), ownPin, 0, false))
            + " um");
  }

  private RoutingJob job(Path file) throws IOException {
    Session session =
        SessionManager.getInstance()
            .createSession(UUID.randomUUID(), "Freerouting/" + Freerouting.VERSION_NUMBER_STRING);
    RoutingJob job = new RoutingJob(session.id);
    job.setInput(file.toFile());
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setJobTimeoutString("00:01:00");
    testingSettings.setMaxPasses(1);
    testingSettings.setOptimizerEnabled(false);
    SettingsMerger merger =
        new SettingsMerger(
            new DefaultSettings(),
            new DsnFileSettings(job.input.getData(), job.input.getFilename()));
    merger.addOrReplaceSources(testingSettings);
    job.routerSettings = merger.merge();
    return job;
  }

  private static ObstacleArea padKeepout(RoutingJob job, String reference) {
    for (Item item : job.board.getItems()) {
      if (item.getClass() == ObstacleArea.class
          && item.getComponentId() > 0
          && ((ObstacleArea) item).getRelativeArea() instanceof Circle
          && reference.equals(job.board.components.get(item.getComponentId()).name)) {
        return (ObstacleArea) item;
      }
    }
    throw new AssertionError("no keepout in " + reference);
  }

  private static Pin pin(RoutingJob job, String reference) {
    for (Item item : job.board.getItems()) {
      if (item instanceof Pin pin
          && reference.equals(job.board.components.get(pin.getComponentId()).name)) {
        return pin;
      }
    }
    throw new AssertionError("no pin in " + reference);
  }

  private static double board(RoutingJob job, double um) {
    return job.board.communication.physicalToBoard(um - 1.0, Unit.UM);
  }

  private static long um(RoutingJob job, int value) {
    return Math.round(value / job.board.communication.physicalToBoard(1.0, Unit.UM));
  }
}
