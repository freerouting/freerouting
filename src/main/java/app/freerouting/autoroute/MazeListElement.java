package app.freerouting.autoroute;

import app.freerouting.geometry.planar.FloatLine;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Information for the maze expand Algorithm contained in expansion doors and
 * drills while the maze expanding algorithm is in progress.
 * Uses object pooling to reduce memory allocation during pathfinding.
 */
public class MazeListElement implements Comparable<MazeListElement> {

  // Thread-local pool for recycling instances (thread-safe without
  // synchronization)
  private static final ThreadLocal<Deque<MazeListElement>> POOL = ThreadLocal.withInitial(ArrayDeque::new);
  private static final int MAX_POOL_SIZE = 500; // Cap pool size to prevent unbounded growth
  private static final AtomicLong SEQUENCE_COUNTER = new AtomicLong();

  /**
   * The door or drill belonging to this MazeListElement
   */
  ExpandableObject door;
  /**
   * The section number of the door (or the layer of the drill)
   */
  int section_no_of_door;
  /**
   * The door, from which this door was expanded
   */
  ExpandableObject backtrack_door;
  /**
   * The section number of the backtrack door
   */
  int section_no_of_backtrack_door;
  /**
   * The weighted distance to the start of the expansion
   */
  double expansion_value;
  /**
   * The expansion value plus the shortest distance to a destination. The list is
   * sorted in ascending order by this value.
   */
  double sorting_value;
  /**
   * The next room, which will be expanded from this maze search element
   */
  CompleteExpansionRoom next_room;
  /**
   * Point of the region of the expansion door, which has the shortest distance to
   * the backtrack door.
   */
  FloatLine shape_entry;
  boolean room_ripped;
  MazeSearchElement.Adjustment adjustment;
  boolean already_checked;
  long sequence_no;

  /**
   * Private constructor for pooling
   */
  private MazeListElement() {
  }

  /**
   * Obtain a MazeListElement from the pool or create a new one if pool is empty
   */
  public static MazeListElement obtain(ExpandableObject p_door, int p_section_no_of_door,
      ExpandableObject p_backtrack_door, int p_section_no_of_backtrack_door,
      double p_expansion_value, double p_sorting_value,
      CompleteExpansionRoom p_next_room, FloatLine p_shape_entry,
      boolean p_room_ripped, MazeSearchElement.Adjustment p_adjustment, boolean p_already_checked) {

    Deque<MazeListElement> pool = POOL.get();
    MazeListElement element = pool.poll();

    if (element == null) {
      element = new MazeListElement();
    }

    // Initialize/reset fields
    element.door = p_door;
    element.section_no_of_door = p_section_no_of_door;
    element.backtrack_door = p_backtrack_door;
    element.section_no_of_backtrack_door = p_section_no_of_backtrack_door;
    element.expansion_value = p_expansion_value;
    element.sorting_value = p_sorting_value;
    element.next_room = p_next_room;
    element.shape_entry = p_shape_entry;
    element.room_ripped = p_room_ripped;
    element.adjustment = p_adjustment;
    element.already_checked = p_already_checked;
    element.sequence_no = SEQUENCE_COUNTER.getAndIncrement();

    return element;
  }

  /**
   * Recycle this element back to the pool for reuse
   */
  public static void recycle(MazeListElement element) {
    if (element == null) {
      return;
    }

    Deque<MazeListElement> pool = POOL.get();
    if (pool.size() < MAX_POOL_SIZE) {
      // Clear references to help GC
      element.door = null;
      element.backtrack_door = null;
      element.next_room = null;
      element.shape_entry = null;
      element.adjustment = null;

      pool.offer(element);
    }
    // If pool is full, let element be GC'd
  }

  @Override
  @SuppressWarnings("NullableProblems")
  public int compareTo(MazeListElement p_other) {
    if (this == p_other) {
      return 0;
    }
    int sortCompare = Double.compare(this.sorting_value, p_other.sorting_value);
    if (sortCompare != 0) {
      return sortCompare;
    }
    // Keep all same-cost entries in the TreeSet with deterministic order.
    return Long.compare(this.sequence_no, p_other.sequence_no);
  }
}