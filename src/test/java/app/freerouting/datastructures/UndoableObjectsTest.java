package app.freerouting.datastructures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

class UndoableObjectsTest {

  private static final class Stone implements UndoableObjects.Storable {
    final int id;

    Stone(int id) {
      this.id = id;
    }

    @Override
    public int compareTo(Object other) {
      if (other instanceof Stone stone) {
        return stone.id - this.id;
      }
      return 1;
    }

    @Override
    public Object clone() {
      return new Stone(this.id);
    }

    @Override
    public String toString() {
      return Integer.toString(this.id);
    }
  }

  @Test
  void testChangesSinceSnapshot() {
    UndoableObjects list = new UndoableObjects();
    List<Stone> stones = new ArrayList<>();
    for (int i = 1; i <= 6; i++) {
      Stone s = new Stone(i);
      stones.add(s);
      list.insert(s);
    }

    List<UndoableObjects.Storable> before = new ArrayList<>();
    List<UndoableObjects.Storable> after = new ArrayList<>();
    assertFalse(list.changesSinceSnapshot(before, after));

    list.generateSnapshot();
    list.delete(stones.get(1)); // id 2 deleted
    list.saveForUndo(stones.get(2)); // id 3 changed in place
    list.saveForUndo(stones.get(3)); // id 4 changed, then deleted
    list.delete(stones.get(3));
    Stone seven = new Stone(7);
    list.insert(seven); // id 7 new
    Stone eight = new Stone(8);
    list.insert(eight); // id 8 new, then deleted
    list.delete(eight);

    assertTrue(list.changesSinceSnapshot(before, after));
    List<Integer> beforeIds = before.stream().map(s -> ((Stone) s).id).sorted().toList();
    List<Integer> afterIds = after.stream().map(s -> ((Stone) s).id).sorted().toList();

    assertEquals(List.of(2, 3, 4), beforeIds);
    assertEquals(List.of(3, 7), afterIds);
  }

  @Test
  void testVersionAndContentVersionStamps() {
    UndoableObjects list = new UndoableObjects();
    assertEquals(0, list.getVersion());
    assertEquals(0, list.getContentVersion());

    Stone stone1 = new Stone(1);
    list.insert(stone1);
    assertEquals(1, list.getVersion());
    assertEquals(1, list.getContentVersion());

    list.generateSnapshot();
    assertEquals(2, list.getVersion());
    assertEquals(1, list.getContentVersion()); // snapshot alone leaves contentVersion unchanged

    list.saveForUndo(stone1);
    assertEquals(2, list.getVersion());
    assertEquals(2, list.getContentVersion());

    Stone stone2 = new Stone(2);
    list.insert(stone2);
    assertEquals(3, list.getVersion());
    assertEquals(3, list.getContentVersion());

    list.undo(null, null);
    assertEquals(4, list.getVersion());
    assertEquals(4, list.getContentVersion());

    list.redo(null, null); // verifies null restoredObjects does not throw NPE
    assertEquals(5, list.getVersion());
    assertEquals(5, list.getContentVersion());

    list.popSnapshot();
    assertEquals(6, list.getVersion());
    assertEquals(5, list.getContentVersion());

    list.noteContentChange();
    assertEquals(6, list.getVersion());
    assertEquals(6, list.getContentVersion());

    list.delete(stone2);
    assertEquals(7, list.getVersion());
    assertEquals(7, list.getContentVersion());
  }

  @Test
  void testConcurrentMutationsWhileReading() {
    UndoableObjects list = new UndoableObjects();
    for (int id = 1; id <= 50; id++) {
      list.insert(new Stone(id));
    }

    Iterator<UndoableObjects.UndoableObjectNode> it = list.startReadObject();
    Stone first = (Stone) list.readObject(it);
    assertNotNull(first);
    assertEquals(50, first.id);

    Stone second = (Stone) list.readObject(it);
    assertNotNull(second);
    assertEquals(49, second.id);

    // Insert new item during active iteration
    list.insert(new Stone(60));

    // Delete item ahead of cursor during active iteration
    Stone stone25 = null;
    Iterator<UndoableObjects.UndoableObjectNode> scan = list.startReadObject();
    for (UndoableObjects.Storable s; (s = list.readObject(scan)) != null; ) {
      if (((Stone) s).id == 25) {
        stone25 = (Stone) s;
        break;
      }
    }
    assertNotNull(stone25);
    assertTrue(list.delete(stone25));

    // Continue original iteration to completion without ConcurrentModificationException
    List<Integer> remainingIds = new ArrayList<>();
    for (UndoableObjects.Storable s; (s = list.readObject(it)) != null; ) {
      remainingIds.add(((Stone) s).id);
    }
    assertFalse(remainingIds.contains(25));
  }
}
