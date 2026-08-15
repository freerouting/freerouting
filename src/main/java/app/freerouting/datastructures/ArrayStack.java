package app.freerouting.datastructures;

/** Implementation of a stack as an array. */
@SuppressWarnings("unchecked")
public class ArrayStack<T> {

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
    --level;
    return result;
  }

  private void reallocate() {
    T[] newArray = (T[]) new Object[4 * this.nodeArr.length];
    System.arraycopy(nodeArr, 0, newArray, 0, nodeArr.length);
    this.nodeArr = newArray;
  }
}
