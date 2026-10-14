package app.freerouting.autoroute.expansion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.TileShape;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpansionDoorCachingTest {

  private static class TestRoom implements ExpansionRoom {
    private TileShape shape;
    private final int id;

    TestRoom(TileShape shape, int id) {
      this.shape = shape;
      this.id = id;
    }

    void setShape(TileShape newShape) {
      this.shape = newShape;
    }

    @Override
    public TileShape getShape() {
      return shape;
    }

    @Override
    public void addDoor(ExpansionDoor door) {}

    @Override
    public List<ExpansionDoor> getDoors() {
      return new ArrayList<>();
    }

    @Override
    public void clearDoors() {}

    @Override
    public void resetDoors() {}

    @Override
    public boolean doorExists(ExpansionRoom other) {
      return false;
    }

    @Override
    public boolean removeDoor(ExpandableObject door) {
      return false;
    }

    @Override
    public int getLayer() {
      return 0;
    }

    @Override
    public int getId() {
      return id;
    }
  }

  @Test
  void testDoorShapeIsCachedUntilRoomShapeChanges() {
    TestRoom room1 = new TestRoom(new IntBox(0, 0, 100, 100), 1);
    TestRoom room2 = new TestRoom(new IntBox(50, 0, 150, 100), 2);

    ExpansionDoor door = new ExpansionDoor(room1, room2, 2);

    TileShape shapeFirst = door.getShape();
    TileShape shapeSecond = door.getShape();

    // Must return the identical cached instance
    assertSame(shapeFirst, shapeSecond);

    // Change room1's shape
    room1.setShape(new IntBox(20, 0, 120, 100));
    TileShape shapeThird = door.getShape();

    // After room change, a newly calculated shape is returned
    assertNotSame(shapeFirst, shapeThird);
    assertTrue(shapeThird instanceof IntBox);
    IntBox boxThird = (IntBox) shapeThird;
    assertEquals(50, boxThird.ll.x);
    assertEquals(0, boxThird.ll.y);
    assertEquals(120, boxThird.ur.x);
    assertEquals(100, boxThird.ur.y);
  }

  @Test
  void testSectionSegmentsAreCachedForSameOffset() {
    TestRoom room1 = new TestRoom(new IntBox(0, 0, 100, 100), 1);
    TestRoom room2 = new TestRoom(new IntBox(100, 0, 200, 100), 2);

    ExpansionDoor door = new ExpansionDoor(room1, room2, 1);

    FloatLine[] segs1 = door.getSectionSegments(10.0);
    FloatLine[] segs2 = door.getSectionSegments(10.0);

    // Exact array instance is cached and reused
    assertSame(segs1, segs2);

    // Different offset computes fresh sections
    FloatLine[] segs3 = door.getSectionSegments(20.0);
    assertEquals(segs1.length, segs3.length);
  }
}
