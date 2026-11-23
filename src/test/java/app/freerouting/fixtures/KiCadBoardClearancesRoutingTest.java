package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.TestFixtures;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.structure.BoardOutline;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
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
 * KiCad does not write the copper to edge clearance of a board into a DSN. On a board given without
 * its project file (here KiCad 5 PCBench boards), the KiCad 10 DRC checks its default, 0.5 mm,
 * where Freerouting kept 0.25 mm. With the {@code .kicad_pcb} given to the job, the traces keep it.
 */
class KiCadBoardClearancesRoutingTest extends RoutingFixtureTest {

  /** Rounding of the octagons and of the micrometre conversion. */
  private static final double TOLERANCE_UM = 2.0;

  /**
   * ESP-12-breakout: traces at 0.43 mm from the outline. Only next to a pin that is itself closer
   * to the outline than the clearance may the traces of its net come closer too.
   */
  @Test
  void theTracesKeepTheCopperToEdgeClearanceOfKiCad() throws IOException {
    RoutingJob job = routedWithTheBoardFile("PCBench-ESP-12-breakout");
    double clearance = toBoard(job, 500.0 - TOLERANCE_UM);
    BoardOutline outline = job.board.getOutline();

    List<String> tooClose = new ArrayList<>();
    for (Trace trace : job.board.getTraces()) {
      if (!(trace instanceof PolylineTrace polylineTrace)) {
        continue;
      }
      FloatPoint[] corners = polylineTrace.polyline().cornerApproxArr();
      for (int i = 0; i + 1 < corners.length; i++) {
        double gap = distanceToOutline(outline, corners[i], corners[i + 1]) - trace.getHalfWidth();
        if (gap < clearance
            && !nextToANearEdgePinOfItsNet(
                job, outline, clearance, trace, corners[i], corners[i + 1])) {
          tooClose.add(
              "net " + trace.getNetNumber(0) + " at " + corners[i] + ": " + toUm(job, gap) + " um");
        }
      }
    }
    assertTrue(
        tooClose.isEmpty(),
        () -> tooClose.size() + " trace segments closer than 0.5 mm to the outline: " + tooClose);
  }

  /**
   * retroreflectors_CONGFLOCK: pins of /GATE are 0.22 to 0.36 mm from the outline, closer than the
   * 0.5 mm that KiCad checks. The outline next to them does not block their net, so every
   * connection is still routed.
   */
  @Test
  void aPinCloserToTheOutlineThanTheClearanceIsStillReached() throws IOException {
    RoutingJob job = routedWithTheBoardFile("PCBench-retroreflectors-CONGFLOCK");
    assertEquals(
        toBoard(job, 500.0),
        job.board.rules.clearanceMatrix.getValue(
            job.board.getOutline().clearanceClassIndex(), 1, 0, false),
        1.0);
  }

  private RoutingJob routedWithTheBoardFile(String name) throws IOException {
    RoutingJob job = getRoutingJob(name + ".dsn");
    assertTrue(job.loadKiCadBoardZones(TestFixtures.resolvePath(name + ".kicad_pcb").toString()));
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
              // the settings of the PCBench runs
              "--router.max_threads=1",
              "--router.optimizer.max_threads=1",
              "--router.fanout.enabled=true",
              "--router.job_timeout=00:02:00"
            }));
    job.routerSettings = merger.merge();

    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    assertEquals(0, new BoardStatistics(job.board).connections.incompleteCount);
    return job;
  }

  /** The distance between a trace segment and the outline lines (both ways, end points). */
  private static double distanceToOutline(BoardOutline outline, FloatPoint from, FloatPoint to) {
    FloatLine segment = new FloatLine(from, to);
    double result = Double.POSITIVE_INFINITY;
    for (int s = 0; s < outline.shapeCount(); s++) {
      PolylineShape shape = outline.getShape(s);
      int count = shape.borderLineCount();
      for (int i = 0; i < count; i++) {
        FloatPoint a = shape.corner(i).toFloat();
        FloatPoint b = shape.corner((i + 1) % count).toFloat();
        FloatLine line = new FloatLine(a, b);
        result =
            Math.min(
                result,
                Math.min(
                    Math.min(line.segmentDistance(from), line.segmentDistance(to)),
                    Math.min(segment.segmentDistance(a), segment.segmentDistance(b))));
      }
    }
    return result;
  }

  /**
   * Whether the segment reaches 1 mm around a pin of the net of the trace that is itself closer to
   * the outline than the clearance.
   */
  private static boolean nextToANearEdgePinOfItsNet(
      RoutingJob job,
      BoardOutline outline,
      double clearance,
      Trace trace,
      FloatPoint from,
      FloatPoint to) {
    int margin = (int) toBoard(job, 1000.0);
    for (Pin pin : job.board.getPins()) {
      if (!trace.sharesNet(pin)) {
        continue;
      }
      IntBox pinBox = pin.boundingBox();
      FloatPoint low = pinBox.ll.toFloat();
      FloatPoint high = pinBox.ur.toFloat();
      FloatPoint lowHigh = new FloatPoint(low.x, high.y);
      FloatPoint highLow = new FloatPoint(high.x, low.y);
      double pinGap =
          Math.min(
              distanceToOutline(outline, low, highLow), distanceToOutline(outline, lowHigh, high));
      if (pinGap >= clearance) {
        continue;
      }
      IntBox box = pinBox.offset(margin);
      if (box.contains(from) || box.contains(to)) {
        return true;
      }
    }
    return false;
  }

  private static double toBoard(RoutingJob job, double um) {
    return job.board.communication.physicalToBoard(um, Unit.UM);
  }

  private static long toUm(RoutingJob job, double boardUnits) {
    return Math.round(boardUnits / toBoard(job, 1.0));
  }
}
