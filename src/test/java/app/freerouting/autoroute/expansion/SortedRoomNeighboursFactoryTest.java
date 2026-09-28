package app.freerouting.autoroute.expansion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.board.searchtree.ShapeSearchTree45Degree;
import app.freerouting.board.searchtree.ShapeSearchTree90Degree;
import org.junit.jupiter.api.Test;

/** Verifies room-neighbour dispatch for each supported search-tree geometry. */
class SortedRoomNeighboursFactoryTest {

  @Test
  void selectsOrthogonalCalculationForOrthogonalSearchTree() {
    assertEquals(
        SortedRoomNeighbours.CalculationMode.ORTHOGONAL,
        SortedRoomNeighbours.selectCalculationMode(mock(ShapeSearchTree90Degree.class)));
  }

  @Test
  void selects45DegreeCalculationFor45DegreeSearchTree() {
    assertEquals(
        SortedRoomNeighbours.CalculationMode.DEGREE_45,
        SortedRoomNeighbours.selectCalculationMode(mock(ShapeSearchTree45Degree.class)));
  }

  @Test
  void selectsAnyAngleCalculationForOtherSearchTrees() {
    assertEquals(
        SortedRoomNeighbours.CalculationMode.ANY_ANGLE,
        SortedRoomNeighbours.selectCalculationMode(mock(ShapeSearchTree.class)));
  }
}
