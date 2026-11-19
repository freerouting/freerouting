package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.TestFixtures;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.io.kicad.KiCadProject;
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
 * KiCad does not write the minimum track width of the board into the DSN, and its DRC reports any
 * narrower trace as a {@code track_width} error. On this KiCad 10 export of DAC2020 bm07
 * (discussion #508, minimum 0.2 mm, net class width 0.2 mm), the micro-neckdown of trace insertion
 * left a 0.15 mm segment next to a pin. With the .kicad_pro given to the job, the minimum track
 * width is the neck width, and no trace is narrower.
 */
class KiCadProjectMinTrackWidthRoutingTest extends RoutingFixtureTest {

  @Test
  void noTraceNarrowerThanTheMinimumTrackWidthOfTheProject() throws IOException {
    // The default settings of a CLI run, as in discussion #508: with the testing settings, this
    // board is routed without the narrow segment.
    RoutingJob job = jobWithTheProject();
    assertEquals(200.0, job.routerSettings.getNeckWidthUm(), 1e-9);

    runRoutingJob(job);

    assertNoTraceNarrowerThanTheMinimum(job, 200.0);
  }

  /**
   * The fallback strategies {@code no-neck} and {@code continue-no-neck} set the neck width to 0;
   * the minimum track width of the project still holds, because it is also the narrowest trace the
   * board allows.
   */
  @Test
  void theMinimumTrackWidthHoldsWhenTheNeckWidthIsRelaxed() throws IOException {
    RoutingJob job = jobWithTheProject();
    // what no-neck and continue-no-neck do to the settings of a run
    job.routerSettings.setNeckWidthUm(0);

    runRoutingJob(job);

    assertNoTraceNarrowerThanTheMinimum(job, 200.0);
  }

  /**
   * When the {@code .kicad_pro} minimum track width exceeds a DSN net-class or pin neckdown width
   * (here 0.25 mm vs. 0.20 mm in the DSN), both ordinary trace routing and pin neckdown still clamp
   * to the project minimum.
   */
  @Test
  void theMinimumTrackWidthOverridesNarrowerNetClassAndPinWidths() throws IOException {
    KiCadProject project =
        KiCadProject.parse(
            "{\"board\":{\"design_settings\":{\"rules\":{\"min_track_width\":0.25}}}}",
            "test.kicad_pro");
    RoutingJob job = jobWithProject(project);
    assertEquals(250.0, job.routerSettings.getNeckWidthUm(), 1e-9);

    runRoutingJob(job);

    assertNoTraceNarrowerThanTheMinimum(job, 250.0);
  }

  private RoutingJob jobWithTheProject() throws IOException {
    return jobWithProject(
        KiCadProject.read(TestFixtures.resolvePath("Issue508-DAC2020_bm07-kicad10.kicad_pro")));
  }

  private RoutingJob jobWithProject(KiCadProject project) throws IOException {
    RoutingJob job = getRoutingJob("Issue508-DAC2020_bm07-kicad10.dsn");
    job.kicadProject = project;
    job.routerSettings =
        new SettingsMerger(
                new DefaultSettings(),
                new DsnFileSettings(job.input.getData(), job.input.getFilename()),
                new KiCadProjectSettings(job.kicadProject),
                new CliSettings(
                    new String[] {
                      "-mp",
                      "20",
                      "--router.max_threads=4",
                      "--router.optimizer.enabled=false",
                      "--router.job_timeout=00:01:00"
                    }))
            .merge();
    return job;
  }

  private static void assertNoTraceNarrowerThanTheMinimum(RoutingJob job, double minWidthUm) {
    assertEquals(RoutingJobState.COMPLETED, job.state);
    assertEquals(0, new BoardStatistics(job.board).connections.incompleteCount);
    int minHalfWidth =
        (int) Math.floor(job.board.communication.physicalToBoard(minWidthUm, Unit.UM) / 2);
    List<String> narrow = new ArrayList<>();
    for (Trace trace : job.board.getTraces()) {
      if (trace.getHalfWidth() < minHalfWidth) {
        narrow.add(
            "net "
                + trace.getNetNumber(0)
                + " on layer "
                + trace.getLayer()
                + ": half width "
                + trace.getHalfWidth());
      }
    }
    assertTrue(
        narrow.isEmpty(),
        () ->
            narrow.size()
                + " traces narrower than the "
                + (minWidthUm / 1000.0)
                + " mm minimum track width: "
                + narrow);
  }
}
