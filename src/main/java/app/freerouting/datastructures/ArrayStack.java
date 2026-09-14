package app.freerouting.datastructures;

import app.freerouting.logger.FRLogger;

/** Implementation of a stack as an array. */
@SuppressWarnings("unchecked")
public class ArrayStack<T> {

  private static final int MAX_STACK_DEPTH = 40_000;

  private int level = -1;
  private T[] nodeArr;

  /**
   * Creates a new instance of ArrayStack with an initial maximal capacity for maxStackDepth
   * elements.
   */
  public ArrayStack(int maxStackDepth) {
    nodeArr = (T[]) new Object[maxStackDepth];
  }

  /** Sets the stack to empty. */
  public void reset() {
    for (int i = 0; i <= level; i++) {
      nodeArr[i] = null;
    }
    level = -1;
  }

  /** Pushes element onto the stack. */
  public void push(T element) {

    ++level;

    if (level >= nodeArr.length) {
      reallocate();
    }

    nodeArr[level] = element;
  }

  /** Pops the next element from the top of the stack. Returns null, if the stack is exhausted. */
  public T pop() {
    if (level < 0) {
      return null;
    }
    T result = nodeArr[level];
    nodeArr[level] = null;
    --level;
    return result;
  }

  private void reallocate() {
    int oldLength = this.nodeArr.length;
    if (oldLength >= MAX_STACK_DEPTH) {
      throw new IllegalStateException(
          "ArrayStack maximum depth of " + MAX_STACK_DEPTH + " exceeded");
    }
    int newLength = Math.min(MAX_STACK_DEPTH, 4 * oldLength);
    FRLogger.debug("ArrayStack capacity grew from " + oldLength + " to " + newLength);
    T[] newArray = (T[]) new Object[newLength];
    System.arraycopy(nodeArr, 0, newArray, 0, nodeArr.length);
    this.nodeArr = newArray;
  }
}
