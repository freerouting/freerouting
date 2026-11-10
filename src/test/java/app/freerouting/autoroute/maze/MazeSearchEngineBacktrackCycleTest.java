package app.freerouting.autoroute.maze;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.expansion.CompleteExpansionRoom;
import app.freerouting.autoroute.expansion.ExpandableObject;
import app.freerouting.geometry.planar.TileShape;
import org.junit.jupiter.api.Test;

/**
 * A door section that the maze search left unoccupied can already be the backtrack door of the
 * elements expanded from it. Reaching it again from one of those descendants must not reassign its
 * backtrack door, or the backtrack chain becomes a cycle and backtracking never ends.
 */
class MazeSearchEngineBacktrackCycleTest {

  @Test
  void detectsReassignmentFromDescendant() {
    // start <- a <- drill <- b : a was left unoccupied, b is a descendant of a.
    TestDoor start = new TestDoor(1);
    TestDoor a = new TestDoor(2);
    TestDoor drill = new TestDoor(3);
    TestDoor b = new TestDoor(4);
    link(a, start);
    link(drill, a);
    link(b, drill);

    assertTrue(MazeSearchEngine.closesBacktrackCycle(elementTo(a, b), a.section));
  }

  @Test
  void acceptsReassignmentFromOtherBranch() {
    // start <- a and start <- c : reaching a from c keeps the chain acyclic.
    TestDoor start = new TestDoor(1);
    TestDoor a = new TestDoor(2);
    TestDoor c = new TestDoor(3);
    link(a, start);
    link(c, start);

    assertFalse(MazeSearchEngine.closesBacktrackCycle(elementTo(a, c), a.section));
  }

  @Test
  void terminatesOnExistingCycle() {
    // x <-> y already form a cycle that does not contain a.
    TestDoor a = new TestDoor(1);
    TestDoor x = new TestDoor(2);
    TestDoor y = new TestDoor(3);
    link(x, y);
    link(y, x);

    assertFalse(MazeSearchEngine.closesBacktrackCycle(elementTo(a, x), a.section));
  }

  private static void link(TestDoor door, TestDoor backtrackDoor) {
    door.section.backtrackDoor = backtrackDoor;
    door.section.sectionNoOfBacktrackDoor = 0;
  }

  private static MazeListElement elementTo(TestDoor door, TestDoor from) {
    return new MazeListElement(door, 0, from, 0, 0.0, 0.0, null, null, false, null, false);
  }

  private static final class TestDoor implements ExpandableObject {
    private final int id;
    private final MazeSearchElement section = new MazeSearchElement();

    private TestDoor(int id) {
      this.id = id;
    }

    @Override
    public TileShape getShape() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getDimension() {
      return 1;
    }

    @Override
    public CompleteExpansionRoom otherRoom(CompleteExpansionRoom room) {
      return null;
    }

    @Override
    public int mazeSearchElementCount() {
      return 1;
    }

    @Override
    public MazeSearchElement getMazeSearchElement(int index) {
      return section;
    }

    @Override
    public void reset() {
      section.reset();
    }

    @Override
    public int getId() {
      return id;
    }
  }
}
