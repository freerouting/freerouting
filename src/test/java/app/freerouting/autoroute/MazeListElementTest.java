package app.freerouting.autoroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class MazeListElementTest {

  @Test
  void compareToReturnsZeroForSameInstance() {
    MazeListElement element = MazeListElement.obtain(null, 0, null, 0, 0.0, 1.0,
        null, null, false, null, false);

    assertEquals(0, compare(element, element));

    MazeListElement.recycle(element);
  }

  @Test
  void compareToSortsBySortingValue() {
    MazeListElement lowerCost = MazeListElement.obtain(null, 0, null, 0, 0.0, 1.0,
        null, null, false, null, false);
    MazeListElement equalCostOlder = MazeListElement.obtain(null, 0, null, 0, 0.0, 2.0,
        null, null, false, null, false);
    MazeListElement equalCostNewer = MazeListElement.obtain(null, 0, null, 0, 0.0, 2.0,
        null, null, false, null, false);

    SortedSet<MazeListElement> queue = new TreeSet<>();
    queue.add(equalCostNewer);
    queue.add(lowerCost);
    queue.add(equalCostOlder);

    MazeListElement first = queue.first();
    assertSame(lowerCost, first, "Lower sorting_value must be expanded first");

    assertNotEquals(0, compare(equalCostOlder, equalCostNewer),
        "Distinct equal-cost entries must not compare as equal");

    MazeListElement.recycle(lowerCost);
    MazeListElement.recycle(equalCostOlder);
    MazeListElement.recycle(equalCostNewer);
  }

  private static int compare(MazeListElement left, MazeListElement right) {
    return left.compareTo(right);
  }
}

