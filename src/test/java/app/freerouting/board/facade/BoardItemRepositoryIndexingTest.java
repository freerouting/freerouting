package app.freerouting.board.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.structure.BoardOutline;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class BoardItemRepositoryIndexingTest {

  @Test
  void testOutlineCachingAndItemLookups() {
    LayerStructure layerStructure =
        new LayerStructure(new Layer[] {new Layer("top", true), new Layer("bottom", true)});
    IntBox bounds = new IntBox(0, 0, 10000, 10000);
    TileShape tile = bounds.toSimplex();
    PolylineShape[] outlineShapes = new PolylineShape[] {tile};
    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules rules = new BoardRules(layerStructure, clearanceMatrix);
    rules.createDefaultNetClass();
    Communication communication = new Communication();

    BasicBoard board =
        new BasicBoard(bounds, layerStructure, outlineShapes, 0, rules, communication);

    // Outline cache test
    BoardOutline outline1 = board.getOutline();
    assertNotNull(outline1);
    BoardOutline outline2 = board.getOutline();
    assertSame(outline1, outline2);

    // getItem cache test
    Item fetchedOutline = board.getItem(outline1.getId());
    assertNotNull(fetchedOutline);
    assertSame(outline1, fetchedOutline);

    // Count tests
    assertEquals(board.getVias().size(), board.getViaCount());
    assertEquals(board.getPins().size(), board.getPinCount());

    // Connectable queries test
    Collection<Item> connectable = board.getConnectableItems(1);
    assertEquals(0, connectable.size());
    assertEquals(0, board.connectableItemCount(1));
  }
}
