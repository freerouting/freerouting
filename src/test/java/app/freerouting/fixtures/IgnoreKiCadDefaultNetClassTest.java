package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import app.freerouting.settings.sources.TestingSettings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * KiCad's default net class is written {@code kicad_default} in a DSN and named {@code Default} in
 * KiCad, but the DSN reader merges it into the board's default class, named {@code default}. {@code
 * ignore_net_classes} compared names literally, so {@code kicad_default} matched nothing: the nets
 * it was meant to leave alone were routed with the rest of the board.
 *
 * <p>KiCad also names composite classes with a comma ({@code Power,Default}); on the command line
 * the comma-separated list splits such a name in two, and neither half matched a class.
 */
class IgnoreKiCadDefaultNetClassTest extends RoutingFixtureTest {

  @ParameterizedTest
  @ValueSource(strings = {"kicad_default", "Default"})
  void fanoutSkipsTheDefaultClassByItsKiCadName(String name) {
    TestingSettings settings = new TestingSettings();
    settings.setEnabled(false);
    settings.setOptimizerEnabled(false);
    settings.setFanoutEnabled(true);
    settings.setJobTimeoutString("00:01:00");

    RoutingJob job = getRoutingJob("Issue558-dev-board.dsn", settings);
    job.routerSettings.autorouter.ignoreNetClasses = new String[] {name};
    runRoutingJob(job);

    RoutingBoard board = job.board;
    NetClass defaultClass = board.rules.getDefaultNetClass();
    assertTrue(defaultClass.isIgnoredByAutorouter, name + " must mark the default class ignored");
    List<String> touched = new ArrayList<>();
    int otherVias = 0;
    for (int i = 1; i <= board.rules.nets.maxNetNumber(); i++) {
      Net net = board.rules.nets.get(i);
      if (net == null) {
        continue;
      }
      long items =
          board.getTraces().stream().filter(t -> t.containsNet(net.netNumber)).count()
              + board.getVias().stream().filter(v -> v.containsNet(net.netNumber)).count();
      if (net.getNetClass() == defaultClass) {
        if (items > 0) {
          touched.add(net.name);
        }
      } else {
        otherVias += (int) items;
      }
    }
    assertTrue(touched.isEmpty(), "Nets of the ignored default class got fanout: " + touched);
    assertTrue(otherVias > 0, "Nets of the other classes must still be fanned out");
    List<String> warnings = job.routerSettings.validationWarnings;
    assertFalse(
        warnings != null && warnings.stream().anyMatch(w -> w.contains(name)),
        "The KiCad name of the default class is not an unknown class");
  }

  @Test
  void aCompositeKiCadNameSplitByTheCommandLineStillMatchesItsClass() {
    TestingSettings settings = new TestingSettings();
    settings.setEnabled(false);
    settings.setOptimizerEnabled(false);
    settings.setFanoutEnabled(true);
    settings.setJobTimeoutString("00:01:00");

    RoutingJob job = getRoutingJob("Issue558-dev-board.dsn", settings);
    // --router.autorouter.ignore_net_classes=Power,Default, as the command line splits it
    job.routerSettings.autorouter.ignoreNetClasses = new String[] {"Power", "Default"};
    runRoutingJob(job);

    NetClass power = job.board.rules.netClasses.get("Power,Default");
    assertTrue(power != null && power.isIgnoredByAutorouter, "Power,Default must be ignored");
    assertFalse(
        job.board.rules.getDefaultNetClass().isIgnoredByAutorouter,
        "Default here is the second half of Power,Default, not the default class");
  }
}
