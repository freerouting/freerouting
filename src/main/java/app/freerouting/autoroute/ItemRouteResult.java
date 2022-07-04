package app.freerouting.autoroute;

public class ItemRouteResult implements Comparable<ItemRouteResult> {
  private final int item_id;
  private boolean improved;
  private final float improvement_percentage;
  private final int via_count_before;
  private final int via_count_after;
  private final double trace_length_before;
  private final double trace_length_after;
  private final int incomplete_count_before;
  private final int incomplete_count_after;

  public ItemRouteResult(int p_item_id) {
    this(p_item_id, 0, 0, 0, 0, 0, 1);
    this.improved = false;
  }

  public ItemRouteResult(
      int p_item_id,
      int p_via_count_before,
      int p_via_count_after,
      double p_trace_length_before,
      double p_trace_length_after,
      int p_incomplete_count_before,
      int p_incomplete_count_after) {
    item_id = p_item_id;
    via_count_before = p_via_count_before;
    via_count_after = p_via_count_after;
    trace_length_before = p_trace_length_before;
    trace_length_after = p_trace_length_after;
    incomplete_count_before = p_incomplete_count_before;
    incomplete_count_after = p_incomplete_count_after;

    if (incomplete_count_after < incomplete_count_before) {
      improved = true;
    } else if (incomplete_count_after > incomplete_count_before) {
      improved = false;
    } else { // incomplete_count_after == incomplete_count_before
      if (via_count_after < via_count_before) {
        improved = true;
      } else if (via_count_after > via_count_before) {
        improved = false;
      } else { // via_count_after == via_count_before
        if (trace_length_after < trace_length_before) {
          improved = true;
        } else if (trace_length_after > trace_length_before) {
          improved = false;
        } else {
          improved = false;
        }
      }
    }

    improvement_percentage =
        (float)
            ((via_count_before != 0 && trace_length_before != 0)
                ? 1.0
                    - ((((via_count_after / via_count_before)
                            + (trace_length_after / trace_length_before))
                        / 2))
                : 0);
  }

  public int compareTo(ItemRouteResult r) {
    if (incomplete_count_after < r.incomplete_count_after) {
      return -1;
    } else if (incomplete_count_after > r.incomplete_count_after) {
      return 1;
    } else { // incomplete_count_after == r.incomplete_count_after
      if (via_count_after < r.via_count_after) {
        return -1;
      } else if (via_count_after > r.via_count_after) {
        return 1;
      } else { // via_count_after == r.via_count_after
        if (trace_length_after < r.trace_length_after) {
          return -1;
        } else if (trace_length_after > r.trace_length_after) {
          return 1;
        } else {
          return 0;
        }
      }
    }
  }

  public boolean improved_over(ItemRouteResult r) {
    return this.compareTo(r) < 0;
  }

  public int item_id() {
    return this.item_id;
  }

  public boolean improved() {
    return this.improved;
  }

  public float improvement_percentage() {
    return this.improvement_percentage;
  }

  public int via_count() {
    return via_count_after;
  }

  public double trace_length() {
    return trace_length_after;
  }

  public int incomplete_count() {
    return incomplete_count_after;
  }

  public int via_count_reduced() {
    return via_count_before - via_count_after;
  }

  public double length_reduced() {
    return trace_length_before - trace_length_after;
  }

  public void update_improved(boolean p_improved) {
    improved = p_improved;
  }

  public int incomplete_count_before() {
    return incomplete_count_before;
  }
}
