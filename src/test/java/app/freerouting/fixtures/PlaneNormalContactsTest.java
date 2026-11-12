package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.DrillItem;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.management.BoardLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The contacts of a plane, filtered by net and endpoint before the exact overlap test, are the ones
 * of the former query, in the same order.
 */
class PlaneNormalContactsTest extends RoutingFixtureTest {

  @ParameterizedTest
  @ValueSource(strings = {"Issue756-tomu-fpga.dsn", "Issue367-Charger.dsn", "Issue029-hw48na.dsn"})
  void planeContactsMatchTheTileByTileQuery(String fixture) {
    var job = getRoutingJob(fixture);
    BoardLoader.loadBoardIfNeeded(job);
    BasicBoard board = job.board;

    int planes = 0;
    int contacts = 0;
    for (Item item : board.getItems()) {
      if (item instanceof ConductionArea plane) {
        List<Item> expected = new ArrayList<>(tileByTileContacts(board, plane));
        // Only the traces and drill items are looked up tile by tile.
        List<Item> actual =
            plane.getNormalContacts().stream()
                .filter(contact -> !(contact instanceof ConductionArea))
                .toList();
        assertEquals(expected, actual, fixture + ", plane " + plane.getId());
        planes++;
        contacts += actual.size();
      }
    }
    assertTrue(planes > 0, fixture + " has no plane");
    assertTrue(contacts > 0, fixture + " has no plane contact: nothing was compared");
  }

  /** ConductionArea.getNormalContacts before it was batched. */
  private static Set<Item> tileByTileContacts(BasicBoard board, ConductionArea plane) {
    Set<Item> result = new TreeSet<>();
    for (int i = 0; i < plane.tileShapeCount(); i++) {
      TileShape currentShape = plane.getTileShape(i);
      Set<SearchTreeObject> overlaps = board.overlappingObjects(currentShape, plane.getLayer());
      for (SearchTreeObject currentObject : overlaps) {
        if (!(currentObject instanceof Item currentItem)) {
          continue;
        }
        if (currentItem != plane
            && currentItem.sharesNet(plane)
            && currentItem.sharesLayer(plane)) {
          if (currentItem instanceof Trace currentTrace) {
            if (currentShape.contains(currentTrace.firstCorner())
                || currentShape.contains(currentTrace.lastCorner())) {
              result.add(currentItem);
            }
          } else if (currentItem instanceof DrillItem currentDrillItem) {
            if (currentShape.contains(currentDrillItem.getCenter())) {
              result.add(currentItem);
            }
          }
        }
      }
    }
    return result;
  }
}
