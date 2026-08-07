package app.freerouting.datastructures;

/** Implementation of a stack as an array */
@SuppressWarnings("unchecked")
public class ArrayStack<p_element_type> {

  private int level = -1;
  private p_element_type[] node_arr;

  /**
   * Creates a new instance of ArrayStack with an initial maximal capacity for p_max_stack_depth
   * elements.
   */
  public ArrayStack(int p_max_stack_depth) {
    node_arr = (p_element_type[]) new Object[p_max_stack_depth];
  }

  /** Sets the stack to empty. */
  public void reset() {
    level = -1;
  }

  /** Pushed p_element onto the stack. */
  public void push(p_element_type p_element) {

    ++level;

    if (level >= node_arr.length) {
      reallocate();
    }

    node_arr[level] = p_element;
  }

  /** Pops the next element from the top of the stack. Returns null, if the stack is exhausted. */
  public p_element_type pop() {
    if (level < 0) {
      return null;
    }
    p_element_type result = node_arr[level];
    --level;
    return result;
  }

  private void reallocate() {
    p_element_type[] new_arr = (p_element_type[]) new Object[4 * this.node_arr.length];
    System.arraycopy(node_arr, 0, new_arr, 0, node_arr.length);
    this.node_arr = new_arr;
  }
}
