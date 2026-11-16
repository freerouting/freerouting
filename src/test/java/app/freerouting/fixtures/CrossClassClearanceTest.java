package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.Session;
import app.freerouting.management.sessions.SessionManager;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.Net;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.DsnFileSettings;
import app.freerouting.settings.sources.TestingSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code Cross-class-clearance-garland.dsn}: the default clearance is 300 um, net classes CA and CB
 * are both 150 um. Between two net classes, KiCad applies the larger of the two clearances: 150 um
 * between CA and CB, 300 um between CA and the default class.
 *
 * <p>The DSN reader gave each new net class, against every class already read, the larger of its
 * own clearance and the value it copied from the default class. Between CA and CB that is the
 * default 300 um, not 150 um: the router kept a gap that KiCad does not ask for.
 *
 * <p>Each half of the board is barred by a wall of two pads with a 600 um opening, routed with a
 * 200 um trace. Net B (class CB) has to pass between two pads of class CA: 200 + 2 x 150 = 500 um
 * fits. Net D (class CA) has to pass between two pads of the default class: 200 + 2 x 300 = 800 um
 * does not, and D stays unrouted.
 */
class CrossClassClearanceTest extends RoutingFixtureTest {

  private static final String FIXTURE = "Cross-class-clearance-garland.dsn";

  private static TestingSettings settings(boolean route) {
    TestingSettings settings = new TestingSettings();
    settings.setJobTimeoutString("00:01:00");
    settings.setMaxPasses(5);
    settings.setFanoutEnabled(false);
    settings.setRouterEnabled(route);
    settings.setOptimizerEnabled(false);
    return settings;
  }

  private static long tracesOf(RoutingBoard board, String netName) {
    long count = 0;
    for (Net net : board.rules.nets.get(netName)) {
      count += board.getTraces().stream().filter(t -> t.containsNet(net.netNumber)).count();
    }
    return count;
  }

  @Test
  void twoNetClassesUseTheLargerOfTheirOwnClearances() {
    RoutingJob job = runRoutingJob(getRoutingJob(FIXTURE, settings(false)));
    ClearanceMatrix matrix = job.board.rules.clearanceMatrix;
    int ca = matrix.getNo("CA");
    int cb = matrix.getNo("CB");
    int defaultClass = job.board.rules.getDefaultNetClass().getTraceClearanceClass();
    assertTrue(ca > 0 && cb > 0, "classes CA and CB must have their own clearance class");

    for (int layer = 0; layer < matrix.getLayerCount(); layer++) {
      int caOwn = matrix.getValue(ca, ca, layer, false);
      int defaultOwn = matrix.getValue(defaultClass, defaultClass, layer, false);
      assertTrue(caOwn < defaultOwn, "the fixture needs CA tighter than the default class");
      assertEquals(caOwn, matrix.getValue(cb, cb, layer, false), "CA and CB have the same value");
      assertEquals(caOwn, matrix.getValue(ca, cb, layer, false), "CA against CB");
      assertEquals(caOwn, matrix.getValue(cb, ca, layer, false), "CB against CA");
      assertEquals(defaultOwn, matrix.getValue(ca, defaultClass, layer, false), "CA vs default");
      assertEquals(defaultOwn, matrix.getValue(cb, defaultClass, layer, false), "CB vs default");
    }
  }

  @Test
  void aNetPassesBetweenPadsOfAnotherTighterClassButNotOfTheDefaultClass() {
    RoutingJob job = runRoutingJob(getRoutingJob(FIXTURE, settings(true)));
    RoutingBoard board = job.board;

    assertTrue(tracesOf(board, "B") > 0, "net B (CB) must pass the 600 um opening between CA pads");
    assertEquals(
        0, tracesOf(board, "D"), "net D (CA) must not pass the opening between default pads");
  }

  @Test
  void layerDependentRulesRecomputeCrossClassClearancesPerLayer(@TempDir Path tempDir)
      throws IOException {
    String dsn =
        """
        (pcb layered-cross-class
          (parser (string_quote ") (space_in_quoted_tokens on) (host_cad "hand") (host_version "1"))
          (resolution um 10)
          (unit um)
          (structure
            (layer Top (type signal) (property (index 0)))
            (layer Bottom (type signal) (property (index 1)))
            (boundary (rect pcb 0 0 10000 10000))
            (via "Via[0-1]_600:300_um")
            (rule (width 200) (clearance 300))
          )
          (placement
            (component SMD
              (place P1 2000 5000 front 0)
              (place P2 8000 5000 front 0)
            )
          )
          (library
            (image SMD (pin Pad 1 0 0))
            (padstack Pad (shape (rect Top -200 -200 200 200)) (attach off))
            (padstack "Via[0-1]_600:300_um" (shape (circle Top 600)) (shape (circle Bottom 600)) (attach off))
          )
          (network
            (net NA (pins P1-1))
            (net NB (pins P2-1))
            (class kicad_default (circuit (use_via "Via[0-1]_600:300_um")) (rule (width 200) (clearance 300)))
            (class CA NA (circuit (use_via "Via[0-1]_600:300_um")) (rule (width 200) (clearance 150)))
            (class CB NB (circuit (use_via "Via[0-1]_600:300_um")) (layer_rule Top (rule (width 200) (clearance 500))))
            (class CC (circuit (use_via "Via[0-1]_600:300_um")) (rule (width 200) (clearance 150)) (layer_rule Top (rule (width 200) (clearance 500))))
          )
          (wiring)
        )
        """;
    Path dsnFile = tempDir.resolve("layered-cross-class.dsn");
    Files.writeString(dsnFile, dsn);

    Session session =
        SessionManager.getInstance()
            .createSession(UUID.randomUUID(), "Freerouting/" + Freerouting.VERSION_NUMBER_STRING);
    RoutingJob job = new RoutingJob(session.id);
    job.setInput(dsnFile.toFile());
    SettingsMerger merger =
        new SettingsMerger(
            new DefaultSettings(),
            new DsnFileSettings(job.input.getData(), job.input.getFilename()));
    merger.addOrReplaceSources(settings(false));
    job.routerSettings = merger.merge();
    job = runRoutingJob(job);

    ClearanceMatrix matrix = job.board.rules.clearanceMatrix;
    int ca = matrix.getNo("CA");
    int cb = matrix.getNo("CB");
    int cc = matrix.getNo("CC");
    int top = job.board.layerStructure.getNo("Top");
    int bottom = job.board.layerStructure.getNo("Bottom");

    int c150 = matrix.getValue(ca, ca, top, false);
    int c300 = matrix.getValue(cb, cb, bottom, false);
    int c500 = matrix.getValue(cb, cb, top, false);
    assertTrue(c150 < c300 && c300 < c500);

    // CB has only a Top layer rule (500 um): Top uses max(150, 500) = 500, Bottom uses
    // max(150, 300) = 300.
    assertEquals(c500, matrix.getValue(ca, cb, top, false));
    assertEquals(c500, matrix.getValue(cb, ca, top, false));
    assertEquals(c300, matrix.getValue(ca, cb, bottom, false));
    assertEquals(c300, matrix.getValue(cb, ca, bottom, false));

    // CC has a global 150 um rule followed by a Top layer rule (500 um): Top is 500, Bottom is 150.
    assertEquals(c500, matrix.getValue(ca, cc, top, false));
    assertEquals(c500, matrix.getValue(cc, ca, top, false));
    assertEquals(c150, matrix.getValue(ca, cc, bottom, false));
    assertEquals(c150, matrix.getValue(cc, ca, bottom, false));
  }
}
