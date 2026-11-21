package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.TestFixtures;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.DsnFileSettings;
import app.freerouting.settings.sources.KiCadProjectSettings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A board written by KiCad 5 has no {@code .kicad_pro}: its minimum track width is the {@code
 * (setup (trace_min ...))} of its {@code .kicad_pcb}, and KiCad 10 still checks it ({@code
 * track_width}). On this PCBench board (kitspace_cseduinov4, minimum and net class width 0.508 mm),
 * the micro-neckdown at the pins left 0.381 mm segments. With the {@code .kicad_pcb} given to the
 * job, no trace is narrower than the minimum.
 */
class KiCadBoardMinTrackWidthRoutingTest extends RoutingFixtureTest {

  @Test
  void noTraceNarrowerThanTheTraceMinOfTheBoard() throws IOException {
    RoutingJob job = getRoutingJob("PCBench-kitspace-cseduinov4.dsn");
    assertTrue(
        job.loadKiCadBoardZones(
            TestFixtures.resolvePath("PCBench-kitspace-cseduinov4.kicad_pcb").toString()));
    SettingsMerger merger =
        new SettingsMerger(
            new DefaultSettings(),
            new DsnFileSettings(job.input.getData(), job.input.getFilename()));
    if (job.kicadProject != null) {
      merger.addOrReplaceSources(new KiCadProjectSettings(job.kicadProject));
    }
    merger.addOrReplaceSources(
        new CliSettings(
            new String[] {
              "-mp",
              "20",
              "--router.max_threads=1",
              "--router.optimizer.enabled=false",
              "--router.job_timeout=00:02:00"
            }));
    job.routerSettings = merger.merge();

    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    assertEquals(0, new BoardStatistics(job.board).connections.incompleteCount);
    int minHalfWidth = (int) Math.floor(job.board.communication.physicalToBoard(508, Unit.UM) / 2);
    List<String> narrow = new ArrayList<>();
    for (Trace trace : job.board.getTraces()) {
      if (trace.getHalfWidth() < minHalfWidth) {
        narrow.add("net " + trace.getNetNumber(0) + ": half width " + trace.getHalfWidth());
      }
    }
    assertTrue(
        narrow.isEmpty(),
        () -> narrow.size() + " traces narrower than the 0.508 mm trace_min: " + narrow);
  }
}
