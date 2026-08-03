package app.freerouting.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import app.freerouting.geometry.planar.FortyfiveDegreeBoundingDirections;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.io.specctra.DsnTestFixtures;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The drill-hole clearance inflation must apply to every drilled item (vias AND pins) in every
 * search-tree variant — including the 45-degree tree, which is the default autoroute tree and
 * historically skipped the delta entirely.
 */
class DrillHoleClearanceShapeTest {

  private static final String FIXTURE = "Issue575-drc_dev-board_4_hole_clearance_violations.dsn";
  private static final int HOLE_CLEARANCE_BOARD_UNITS = 200000;

  private static List<DrillItem> drilledItems(RoutingBoard board) {
    List<DrillItem> result = new ArrayList<>();
    for (Via via : board.get_vias()) {
      if (via.get_padstack() != null && via.get_padstack().get_drill_radius() > 0) {
        result.add(via);
        break;
      }
    }
    for (Pin pin : board.get_pins()) {
      if (pin.get_padstack() != null && pin.get_padstack().get_drill_radius() > 0
          && pin.tile_shape_count() > 0 && pin.get_shape(0) != null) {
        result.add(pin);
        break;
      }
    }
    return result;
  }

  private static long boxArea(TileShape shape) {
    IntBox box = shape.bounding_box();
    return (long) (box.ur.x - box.ll.x) * (box.ur.y - box.ll.y);
  }

  @Test
  void holeClearanceInflatesDrilledItemShapesInAllTreeVariants() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard(FIXTURE);
    List<DrillItem> items = drilledItems(board);
    assumeTrue(!items.isEmpty(), "fixture must contain drilled items");
    boolean sawVia = items.stream().anyMatch(it -> it instanceof Via);
    boolean sawPin = items.stream().anyMatch(it -> it instanceof Pin);
    assertTrue(sawVia && sawPin, "fixture must provide both a via and a drilled pin");

    for (DrillItem item : items) {
      board.rules.set_hole_clearance(0);
      TileShape[] base0 = new ShapeSearchTree(FortyfiveDegreeBoundingDirections.INSTANCE,
          board, 0).calculate_tree_shapes(item);
      TileShape[] tree45_0 = new ShapeSearchTree45Degree(board, 0).calculate_tree_shapes(item);
      TileShape[] tree90_0 = new ShapeSearchTree90Degree(board, 0).calculate_tree_shapes(item);

      board.rules.set_hole_clearance(HOLE_CLEARANCE_BOARD_UNITS);
      TileShape[] base1 = new ShapeSearchTree(FortyfiveDegreeBoundingDirections.INSTANCE,
          board, 0).calculate_tree_shapes(item);
      TileShape[] tree45_1 = new ShapeSearchTree45Degree(board, 0).calculate_tree_shapes(item);
      TileShape[] tree90_1 = new ShapeSearchTree90Degree(board, 0).calculate_tree_shapes(item);

      String label = item.getClass().getSimpleName();
      assertEquals(base0.length, base1.length, label);
      for (int i = 0; i < base0.length; i++) {
        if (base0[i] == null) {
          continue;
        }
        assertTrue(boxArea(base1[i]) > boxArea(base0[i]),
            label + " shape " + i + " must grow in the base tree");
        assertTrue(boxArea(tree45_1[i]) > boxArea(tree45_0[i]),
            label + " shape " + i + " must grow in the 45-degree tree");
        assertTrue(boxArea(tree90_1[i]) > boxArea(tree90_0[i]),
            label + " shape " + i + " must grow in the 90-degree tree");
      }
    }
    board.rules.set_hole_clearance(0);
  }

  @Test
  void nullShapeLayersGetSynthesizedHoleObstacles() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard(FIXTURE);
    Via via = null;
    int nullIdx = -1;
    for (Via v : board.get_vias()) {
      if (v.get_padstack() == null || v.get_padstack().get_drill_radius() <= 0) {
        continue;
      }
      for (int i = 0; i < v.tile_shape_count(); i++) {
        if (v.get_shape(i) == null) {
          via = v;
          nullIdx = i;
          break;
        }
      }
      if (via != null) {
        break;
      }
    }
    assumeTrue(via != null, "fixture must contain a via with a copper-less layer");

    board.rules.set_hole_clearance(0);
    TileShape[] legacy = new ShapeSearchTree45Degree(board, 0).calculate_tree_shapes(via);
    assertTrue(legacy[nullIdx] == null, "no obstacle without the hole-clearance rule");

    board.rules.set_hole_clearance(2500);
    TileShape[] holeAware = new ShapeSearchTree45Degree(board, 0).calculate_tree_shapes(via);
    assertTrue(holeAware[nullIdx] != null,
        "the drill hole must become an obstacle on copper-less layers");
    board.rules.set_hole_clearance(0);
  }

  @Test
  void zeroHoleClearanceKeepsLegacyShapes() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard(FIXTURE);
    List<DrillItem> items = drilledItems(board);
    assumeTrue(!items.isEmpty());
    board.rules.set_hole_clearance(0);
    for (DrillItem item : items) {
      TileShape[] a = new ShapeSearchTree45Degree(board, 0).calculate_tree_shapes(item);
      TileShape[] b = new ShapeSearchTree45Degree(board, 0).calculate_tree_shapes(item);
      for (int i = 0; i < a.length; i++) {
        if (a[i] != null) {
          assertEquals(boxArea(a[i]), boxArea(b[i]));
        }
      }
    }
  }
}
