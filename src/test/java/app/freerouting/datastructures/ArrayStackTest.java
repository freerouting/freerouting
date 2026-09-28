package app.freerouting.datastructures;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class ArrayStackTest {

  @Test
  void popClearsTheReleasedSlot() throws ReflectiveOperationException {
    ArrayStack<String> stack = new ArrayStack<>(2);
    stack.push("first");
    stack.push("second");

    assertEquals("second", stack.pop());

    assertArrayEquals(new Object[] {"first", null}, backingArray(stack));
  }

  @Test
  void resetClearsAllLiveSlots() throws ReflectiveOperationException {
    ArrayStack<String> stack = new ArrayStack<>(2);
    stack.push("first");
    stack.push("second");

    stack.reset();

    assertArrayEquals(new Object[] {null, null}, backingArray(stack));
    assertNull(stack.pop());
  }

  @Test
  void growthStillPreservesElements() {
    ArrayStack<Integer> stack = new ArrayStack<>(1);
    stack.push(1);
    stack.push(2);
    stack.push(3);

    assertEquals(3, stack.pop());
    assertEquals(2, stack.pop());
    assertEquals(1, stack.pop());
    assertNull(stack.pop());
  }

  @Test
  void growthStopsAtTheMaximumDepth() {
    ArrayStack<Integer> stack = new ArrayStack<>(1);
    for (int i = 0; i < 40_000; i++) {
      stack.push(i);
    }

    assertThrows(IllegalStateException.class, () -> stack.push(40_000));
  }

  private static Object[] backingArray(ArrayStack<?> stack) throws ReflectiveOperationException {
    Field field = ArrayStack.class.getDeclaredField("nodeArr");
    field.setAccessible(true);
    return (Object[]) field.get(stack);
  }
}
