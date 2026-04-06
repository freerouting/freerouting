package app.freerouting.autoroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class MazeListElementTest {

  @Test
  void compareToReturnsZeroForSameInstance() {
    MazeListElement element = new MazeListElement(new TestDoor(1), 0, null, 0, 0.0, 1.0,
        null, null, false, null, false);

    assertEquals(0, compare(element, element));
  }

  @Test
  void compareToSortsBySortingValue() {
    MazeListElement lowerCost = new MazeListElement(new TestDoor(1), 0, null, 0, 0.0, 1.0,
        null, null, false, null, false);
    MazeListElement higherCost = new MazeListElement(new TestDoor(2), 0, null, 0, 0.0, 2.0,
        null, null, false, null, false);

    SortedSet<MazeListElement> queue = new TreeSet<>();
    queue.add(higherCost);
    queue.add(lowerCost);

    MazeListElement first = queue.first();
    assertSame(lowerCost, first, "Lower sorting_value must be expanded first");
  }

  private static final class TestDoor implements ExpandableObject {
    private final int id;

    private TestDoor(int id) {
      this.id = id;
    }

    @Override
    public app.freerouting.geometry.planar.TileShape get_shape() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int get_dimension() {
      return 1;
    }

    @Override
    public CompleteExpansionRoom other_room(CompleteExpansionRoom p_room) {
      return null;
    }

    @Override
    public int maze_search_element_count() {
      return 1;
    }

    @Override
    public MazeSearchElement get_maze_search_element(int p_no) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void reset() {
      // no-op for tests
    }

    @Override
    public int get_id_no() {
      return id;
    }
  }

  private static int compare(MazeListElement left, MazeListElement right) {
    return left.compareTo(right);
  }
}

