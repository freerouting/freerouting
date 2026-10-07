package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.ObstacleArea;
import app.freerouting.board.model.structure.Component;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.library.Package;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.io.specctra.SesWriter;
import app.freerouting.settings.sources.TestingSettings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Regression test verifying that footprint instances with image variations (e.g. suffix "::1")
 * retain their independent package identities, keepout dimensions, and back-side placements.
 */
class FootprintImageVariantKeepoutTest extends RoutingFixtureTest {

  @Test
  void testFootprintImageVariantsPreserveDistinctKeepoutsAndPlacement() throws IOException {
    TestingSettings settings = new TestingSettings();
    settings.setFanoutEnabled(false);
    settings.setRouterEnabled(false);
    settings.setOptimizerEnabled(false);
    settings.setJobTimeoutString("00:01:00");

    RoutingJob job = getRoutingJob("corney_island_wireless.dsn", settings);
    job = runRoutingJob(job);

    assertNotNull(job.board, "Board should load successfully");

    // 1. Verify library packages retain exact image IDs
    Package smallMhPkg = job.board.library.packages.get("ceoloide:mounting_hole_npth", true);
    Package largeMhPkg = job.board.library.packages.get("ceoloide:mounting_hole_npth::1", true);

    assertNotNull(smallMhPkg, "Base mounting hole package must exist");
    assertNotNull(largeMhPkg, "Variant mounting hole package ::1 must exist as distinct package");
    assertNotEquals(
        smallMhPkg.id, largeMhPkg.id, "Base and variant packages must have distinct IDs");
    assertEquals("ceoloide:mounting_hole_npth", smallMhPkg.name);
    assertEquals("ceoloide:mounting_hole_npth::1", largeMhPkg.name);

    // 2. Verify component package assignment
    Component mh1 = job.board.components.get("MH1");
    Component mh5 = job.board.components.get("MH5");
    assertNotNull(mh1, "MH1 must be placed on board");
    assertNotNull(mh5, "MH5 must be placed on board");

    assertEquals(smallMhPkg, mh1.getPackage(), "MH1 must reference small mounting hole package");
    assertEquals(
        largeMhPkg, mh5.getPackage(), "MH5 must reference large mounting hole package ::1");

    // 3. Verify actual keepout obstacle radii on board
    List<Circle> mh1Circles = getComponentKeepoutCircles(job, mh1.id);
    List<Circle> mh5Circles = getComponentKeepoutCircles(job, mh5.id);

    assertTrue(!mh1Circles.isEmpty(), "MH1 must have keepout circles");
    assertTrue(!mh5Circles.isEmpty(), "MH5 must have keepout circles");

    int mh1Radius = mh1Circles.getFirst().radius;
    int mh5Radius = mh5Circles.getFirst().radius;

    assertTrue(
        mh5Radius > mh1Radius,
        "MH5 keepout radius ("
            + mh5Radius
            + ") must be strictly larger than MH1 ("
            + mh1Radius
            + ")");
    // Ratio should match 4800 / 2700 ~ 1.777
    double radiusRatio = (double) mh5Radius / (double) mh1Radius;
    assertEquals(
        4800.0 / 2700.0, radiusRatio, 0.01, "Keepout radius ratio must match DSN dimensions");

    // 4. Verify back-side component with variant suffix retains its variant package
    Component switchS1 = job.board.components.get("S1");
    Component switchS4 = job.board.components.get("S4");
    assertNotNull(switchS1, "S1 must exist");
    assertNotNull(switchS4, "S4 must exist");
    assertEquals("ceoloide:switch_choc_v1_v2", switchS1.getPackage().name);
    assertEquals("ceoloide:switch_choc_v1_v2::1", switchS4.getPackage().name);

    // 5. Verify SES serialization retains exact component identifiers
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    SesWriter.write(job.board, out, "corney_island_wireless.dsn");
    String ses = out.toString(StandardCharsets.UTF_8);

    assertTrue(
        ses.contains("(component \"ceoloide:mounting_hole_npth\"")
            || ses.contains("(component ceoloide:mounting_hole_npth"),
        "SES must contain base mounting hole component scope");
    assertTrue(
        ses.contains("(component \"ceoloide:mounting_hole_npth::1\"")
            || ses.contains("(component ceoloide:mounting_hole_npth::1"),
        "SES must contain variant mounting hole component scope");
  }

  private List<Circle> getComponentKeepoutCircles(RoutingJob job, int componentId) {
    List<Circle> circles = new ArrayList<>();
    for (Item item : job.board.getItems()) {
      if (item instanceof ObstacleArea area && item.getComponentId() == componentId) {
        if (area.getArea() instanceof Circle circle) {
          circles.add(circle);
        }
      }
    }
    return circles;
  }
}
