package app.freerouting.autoroute;

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

  public ItemRouteResult(int pItemId) {
    this(pItemId, 0, 0, 0, 0, 0, 1);
    this.improved = false;
  }

  public ItemRouteResult(
      int pItemId,
      int pViaCountBefore,
      int pViaCountAfter,
      double pTraceLengthBefore,
      double pTraceLengthAfter,
      int pIncompleteCountBefore,
      int pIncompleteCountAfter) {
    itemId = pItemId;
    viaCountBefore = pViaCountBefore;
    viaCountAfter = pViaCountAfter;
    traceLengthBefore = pTraceLengthBefore;
    traceLengthAfter = pTraceLengthAfter;
    incompleteCountBefore = pIncompleteCountBefore;
    incompleteCountAfter = pIncompleteCountAfter;

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

  public boolean improvedOver(ItemRouteResult r) {
    return this.compareTo(r) < 0;
  }

  public int itemId() {
    return this.itemId;
  }

  public boolean improved() {
    return this.improved;
  }

  public float improvementPercentage() {
    return this.improvementPercentage;
  }

  public int viaCount() {
    return viaCountAfter;
  }

  public double traceLength() {
    return traceLengthAfter;
  }

  public int incompleteCount() {
    return incompleteCountAfter;
  }

  public int viaCountReduced() {
    return viaCountBefore - viaCountAfter;
  }

  public double lengthReduced() {
    return traceLengthBefore - traceLengthAfter;
  }

  public void updateImproved(boolean pImproved) {
    improved = pImproved;
  }

  public int incompleteCountBefore() {
    return incompleteCountBefore;
  }
}
