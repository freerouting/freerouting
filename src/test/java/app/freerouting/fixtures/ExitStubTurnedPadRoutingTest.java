package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.io.specctra.SesReader;
import app.freerouting.io.specctra.SesWriter;
import app.freerouting.settings.sources.TestingSettings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code Exit-stub-turned-qfn-garland.dsn}: a QFN-20 turned 45 degrees and placed off the origin,
 * each pin on its own net to a pad of a ring around it. With a 100 um trace and a 274 um clearance,
 * the way out of 12 pins meets the clearances exactly, and the router places exit stubs there. A
 * trace joins a pin only at the pin centre; a stub started from the midpoint of the turned pad's
 * bounding box, one unit beside it, left the pin unconnected once the session was read back.
 */
class ExitStubTurnedPadRoutingTest extends RoutingFixtureTest {

  private static final String FIXTURE = "Exit-stub-turned-qfn-garland.dsn";

  @Test
  void everyPinOfATurnedPartIsJoinedAfterTheSessionRoundTrip() throws IOException {
    TestingSettings route = new TestingSettings();
    route.setJobTimeoutString("00:01:00");
    route.setMaxPasses(20);
    route.setFanoutEnabled(false);
    route.setRouterEnabled(true);
    route.setOptimizerEnabled(false);
    route.setExitStubs(true);
    RoutingJob job = runRoutingJob(getRoutingJob(FIXTURE, route));
    assertEquals(RoutingJobState.COMPLETED, job.state);

    ByteArrayOutputStream ses = new ByteArrayOutputStream();
    SesWriter.write(job.board, ses, FIXTURE);

    TestingSettings load = new TestingSettings();
    load.setJobTimeoutString("00:01:00");
    load.setFanoutEnabled(false);
    load.setRouterEnabled(false);
    load.setOptimizerEnabled(false);
    RoutingJob reread = runRoutingJob(getRoutingJob(FIXTURE, load));
    SesReader.read(new ByteArrayInputStream(ses.toByteArray()), reread.board);

    RoutingBoard board = reread.board;
    List<String> unjoined = new ArrayList<>();
    for (Pin pin : board.getPins()) {
      String part = board.components.get(pin.getComponentId()).name;
      if ("U1".equals(part)
          && pin.netCount() > 0
          && pin.getNormalContacts().stream().noneMatch(i -> i instanceof Trace)) {
        unjoined.add(part + "-" + pin.name() + " at " + pin.getCenter());
      }
    }
    assertTrue(unjoined.isEmpty(), "Pins of U1 with no trace at their centre: " + unjoined);
  }
}
