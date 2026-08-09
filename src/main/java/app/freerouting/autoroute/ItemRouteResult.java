package app.freerouting.autoroute;

/**
 * Represents the routing result of a single item, comparing metrics before and after routing.
 */
public class ItemRouteResult implements Comparable<ItemRouteResult> {

  private final int itemId;
  private final float improvementPercentage;
  private final int viaCountBefore;
  private final int viaCountAfter;
  private final double traceLengthBefore;
  private final double traceLengthAfter;
  private final int incompleteCountBefore;
  private final int incompleteCountAfter;
  private boolean improved;

  /** Constructs an unimproved ItemRouteResult for the given item ID. */
  public ItemRouteResult(int itemId) {
    this(itemId, 0, 0, 0, 0, 0, 1);
    this.improved = false;
  }

  /** Constructs an ItemRouteResult comparing metrics before and after routing. */
  public ItemRouteResult(
      int itemId,
      int viaCountBefore,
      int viaCountAfter,
      double traceLengthBefore,
      double traceLengthAfter,
      int incompleteCountBefore,
      int incompleteCountAfter) {
    this.itemId = itemId;
    this.viaCountBefore = viaCountBefore;
    this.viaCountAfter = viaCountAfter;
    this.traceLengthBefore = traceLengthBefore;
    this.traceLengthAfter = traceLengthAfter;
    this.incompleteCountBefore = incompleteCountBefore;
    this.incompleteCountAfter = incompleteCountAfter;

    if (incompleteCountAfter < incompleteCountBefore) {
      improved = true;
    } else if (incompleteCountAfter > incompleteCountBefore) {
      improved = false;
    } else { // incompleteCountAfter == incompleteCountBefore
      if (viaCountAfter < viaCountBefore) {
        improved = true;
      } else if (viaCountAfter > viaCountBefore) {
        improved = false;
      } else { // viaCountAfter == viaCountBefore
        if (traceLengthAfter < traceLengthBefore) {
          improved = true;
        } else if (traceLengthAfter > traceLengthBefore) {
          improved = false;
        } else {
          improved = false;
        }
      }
    }

    improvementPercentage =
        (float)
            (viaCountBefore != 0 && traceLengthBefore != 0
                ? 1.0
                    - (((viaCountAfter / viaCountBefore) + (traceLengthAfter / traceLengthBefore))
                        / 2)
                : 0);
  }

  @Override
  public int compareTo(ItemRouteResult r) {
    if (incompleteCountAfter < r.incompleteCountAfter) {
      return -1;
    } else if (incompleteCountAfter > r.incompleteCountAfter) {
      return 1;
    } else { // incompleteCountAfter == r.incompleteCountAfter
      if (viaCountAfter < r.viaCountAfter) {
        return -1;
      } else if (viaCountAfter > r.viaCountAfter) {
        return 1;
      } else { // viaCountAfter == r.viaCountAfter
        if (traceLengthAfter < r.traceLengthAfter) {
          return -1;
        } else if (traceLengthAfter > r.traceLengthAfter) {
          return 1;
        } else {
          return 0;
        }
      }
    }
  }

  /** Returns true if this result represents an improvement over r. */
  public boolean improvedOver(ItemRouteResult r) {
    return this.compareTo(r) < 0;
  }

  /** Returns the ID of the routed item. */
  public int itemId() {
    return this.itemId;
  }

  /** Returns true if the routing result was improved. */
  public boolean improved() {
    return this.improved;
  }

  /** Returns the calculated improvement percentage. */
  public float improvementPercentage() {
    return this.improvementPercentage;
  }

  /** Returns the via count after routing. */
  public int viaCount() {
    return viaCountAfter;
  }

  /** Returns the total trace length after routing. */
  public double traceLength() {
    return traceLengthAfter;
  }

  /** Returns the count of incomplete connections after routing. */
  public int incompleteCount() {
    return incompleteCountAfter;
  }

  /** Returns the net reduction in via count. */
  public int viaCountReduced() {
    return viaCountBefore - viaCountAfter;
  }

  /** Returns the net reduction in trace length. */
  public double lengthReduced() {
    return traceLengthBefore - traceLengthAfter;
  }

  /** Updates the improved flag for this result. */
  public void updateImproved(boolean improved) {
    this.improved = improved;
  }

  /** Returns the count of incomplete connections before routing. */
  public int incompleteCountBefore() {
    return incompleteCountBefore;
  }
}
