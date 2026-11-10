package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.management.BoardLoader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A deep copy takes the convex tiles of the planes from the original instead of splitting again.
 */
class DeepCopyPlaneTilesTest extends RoutingFixtureTest {

  @ParameterizedTest
  @ValueSource(strings = {"Issue367-Charger.dsn", "Issue756-tomu-fpga.dsn"})
  void copiedPlanesReuseTheSameTiles(String fixture) {
    var job = getRoutingJob(fixture);
    BoardLoader.loadBoardIfNeeded(job);
    RoutingBoard board = (RoutingBoard) job.board;
    RoutingBoard copy = board.deepCopy();
    assertNotNull(copy, fixture + ": no copy");

    int reused = 0;
    for (Item item : board.getItems()) {
      if (!(item instanceof ConductionArea plane)) {
        continue;
      }
      ConductionArea copiedPlane = (ConductionArea) copy.getItem(plane.getId());
      assertNotNull(copiedPlane, fixture + ", plane " + plane.getId() + " not copied");
      assertEquals(tiles(plane), tiles(copiedPlane), fixture + ", plane " + plane.getId());
      // The same array: the copy took the pieces of the original.
      assertSame(
          plane.getArea().splitToConvex(),
          copiedPlane.getArea().splitToConvex(),
          fixture + ", plane " + plane.getId());
      reused++;
    }
    assertTrue(reused > 0, fixture + ": no plane tiles reused");
  }

  private static List<List<Object>> tiles(ConductionArea plane) {
    List<List<Object>> result = new ArrayList<>();
    for (int i = 0; i < plane.tileShapeCount(); i++) {
      TileShape tile = plane.getTileShape(i);
      List<Object> lines = new ArrayList<>();
      for (int j = 0; j < tile.borderLineCount(); j++) {
        lines.add(tile.borderLine(j));
      }
      result.add(lines);
    }
    return result;
  }
}
