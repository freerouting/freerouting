package app.freerouting.autoroute;

import app.freerouting.autoroute.AutorouteControl.ExpansionCostFactor;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;

/**
 * Calculation of a good lower bound for the distance between a new MazeExpansionElement and the
 * destination set of the expansion.
 */
public class DestinationDistance {

  private final ExpansionCostFactor[] traceCosts;
  private final boolean[] layerActive;
  private final int layerCount;
  private final int activeLayerCount;
  private final double minCheapViaCost;
  double minComponentSideTraceCost;
  double maxComponentSideTraceCost;
  double minSolderSideTraceCost;
  double maxSolderSideTraceCost;
  double maxInnerSideTraceCost;
  // minimum of the maximal trace costs on each inner layer
  double minComponentInnerTraceCost;
  // minimum of minComponentSideTraceCost and
  // maxInnerSideTraceCost
  double minSolderInnerTraceCost;
  // minimum of minSolderSideTraceCost and maxInnerSideTraceCost
  double minComponentSolderInnerTraceCost;
  private double minNormalViaCost;
  // minimum of minComponentInnerTraceCost and
  // minSolderInnerTraceCost
  private IntBox componentSideBox = IntBox.EMPTY;
  private IntBox solderSideBox = IntBox.EMPTY;
  private IntBox innerSideBox = IntBox.EMPTY;
  private boolean boxIsEmpty = true;
  private boolean componentSideBoxIsEmpty = true;
  private boolean solderSideBoxIsEmpty = true;
  private boolean innerSideBoxIsEmpty = true;

  /**
   * Creates a new instance of DestinationDistance. p_trace_costs and p_layer_active are arrays of
   * dimension layerCount.
   */
  public DestinationDistance(
      ExpansionCostFactor[] p_trace_costs,
      boolean[] p_layer_active,
      double p_min_normal_via_cost,
      double p_min_cheap_via_cost) {
    traceCosts = p_trace_costs;
    layerActive = p_layer_active;
    layerCount = p_layer_active.length;
    minNormalViaCost = p_min_normal_via_cost;
    minCheapViaCost = p_min_cheap_via_cost;
    int currActiveLayerCount = 0;
    for (int ind = 0; ind < layerCount; ind++) {
      if (layerActive[ind]) {
        ++currActiveLayerCount;
      }
    }
    this.activeLayerCount = currActiveLayerCount;

    if (layerActive[0]) {
      if (traceCosts[0].horizontal < traceCosts[0].vertical) {
        minComponentSideTraceCost = traceCosts[0].horizontal;
        maxComponentSideTraceCost = traceCosts[0].vertical;
      } else {
        minComponentSideTraceCost = traceCosts[0].vertical;
        maxComponentSideTraceCost = traceCosts[0].horizontal;
      }
    }

    if (layerActive[layerCount - 1]) {
      ExpansionCostFactor currTraceCost = traceCosts[layerCount - 1];

      if (currTraceCost.horizontal < currTraceCost.vertical) {
        minSolderSideTraceCost = currTraceCost.horizontal;
        maxSolderSideTraceCost = currTraceCost.vertical;
      } else {
        minSolderSideTraceCost = currTraceCost.vertical;
        maxSolderSideTraceCost = currTraceCost.horizontal;
      }
    }

    // Note: for inner layers we assume, that cost in preferred direction is 1
    maxInnerSideTraceCost = Math.min(maxComponentSideTraceCost, maxSolderSideTraceCost);
    for (int ind2 = 1; ind2 < layerCount - 1; ind2++) {
      if (!layerActive[ind2]) {
        continue;
      }
      double currMaxCost = Math.max(traceCosts[ind2].horizontal, traceCosts[ind2].vertical);

      maxInnerSideTraceCost = Math.min(maxInnerSideTraceCost, currMaxCost);
    }
    minComponentInnerTraceCost = Math.min(minComponentSideTraceCost, maxInnerSideTraceCost);
    minSolderInnerTraceCost = Math.min(minSolderSideTraceCost, maxInnerSideTraceCost);
    minComponentSolderInnerTraceCost =
        Math.min(minComponentInnerTraceCost, minSolderInnerTraceCost);
  }

  public void join(IntBox p_box, int p_layer) {
    if (p_layer == 0) {
      componentSideBox = componentSideBox.union(p_box);
      componentSideBoxIsEmpty = false;
    } else if (p_layer == layerCount - 1) {
      solderSideBox = solderSideBox.union(p_box);
      solderSideBoxIsEmpty = false;
    } else {
      innerSideBox = innerSideBox.union(p_box);
      innerSideBoxIsEmpty = false;
    }
    boxIsEmpty = false;
  }

  public double calculate(FloatPoint p_point, int p_layer) {
    return calculate(p_point.boundingBox(), p_layer);
  }

  public double calculate(IntBox p_box, int p_layer) {
    if (boxIsEmpty) {
      return Integer.MAX_VALUE;
    }

    double componentSideDeltaX;
    double componentSideDeltaY;

    if (p_box.ll.x > componentSideBox.ur.x) {
      componentSideDeltaX = p_box.ll.x - componentSideBox.ur.x;
    } else if (p_box.ur.x < componentSideBox.ll.x) {
      componentSideDeltaX = componentSideBox.ll.x - p_box.ur.x;
    } else {
      componentSideDeltaX = 0;
    }

    if (p_box.ll.y > componentSideBox.ur.y) {
      componentSideDeltaY = p_box.ll.y - componentSideBox.ur.y;
    } else if (p_box.ur.y < componentSideBox.ll.y) {
      componentSideDeltaY = componentSideBox.ll.y - p_box.ur.y;
    } else {
      componentSideDeltaY = 0;
    }

    double solderSideDeltaX;
    double solderSideDeltaY;

    if (p_box.ll.x > solderSideBox.ur.x) {
      solderSideDeltaX = p_box.ll.x - solderSideBox.ur.x;
    } else if (p_box.ur.x < solderSideBox.ll.x) {
      solderSideDeltaX = solderSideBox.ll.x - p_box.ur.x;
    } else {
      solderSideDeltaX = 0;
    }

    if (p_box.ll.y > solderSideBox.ur.y) {
      solderSideDeltaY = p_box.ll.y - solderSideBox.ur.y;
    } else if (p_box.ur.y < solderSideBox.ll.y) {
      solderSideDeltaY = solderSideBox.ll.y - p_box.ur.y;
    } else {
      solderSideDeltaY = 0;
    }

    double innerSideDeltaX;
    double innerSideDeltaY;

    if (p_box.ll.x > innerSideBox.ur.x) {
      innerSideDeltaX = p_box.ll.x - innerSideBox.ur.x;
    } else if (p_box.ur.x < innerSideBox.ll.x) {
      innerSideDeltaX = innerSideBox.ll.x - p_box.ur.x;
    } else {
      innerSideDeltaX = 0;
    }

    if (p_box.ll.y > innerSideBox.ur.y) {
      innerSideDeltaY = p_box.ll.y - innerSideBox.ur.y;
    } else if (p_box.ur.y < innerSideBox.ll.y) {
      innerSideDeltaY = innerSideBox.ll.y - p_box.ur.y;
    } else {
      innerSideDeltaY = 0;
    }

    double componentSideMaxDelta;
    double componentSideMinDelta;

    if (componentSideDeltaX > componentSideDeltaY) {
      componentSideMaxDelta = componentSideDeltaX;
      componentSideMinDelta = componentSideDeltaY;
    } else {
      componentSideMaxDelta = componentSideDeltaY;
      componentSideMinDelta = componentSideDeltaX;
    }

    double solderSideMaxDelta;
    double solderSideMinDelta;

    if (solderSideDeltaX > solderSideDeltaY) {
      solderSideMaxDelta = solderSideDeltaX;
      solderSideMinDelta = solderSideDeltaY;
    } else {
      solderSideMaxDelta = solderSideDeltaY;
      solderSideMinDelta = solderSideDeltaX;
    }

    double innerSideMaxDelta;
    double innerSideMinDelta;

    if (innerSideDeltaX > innerSideDeltaY) {
      innerSideMaxDelta = innerSideDeltaX;
      innerSideMinDelta = innerSideDeltaY;
    } else {
      innerSideMaxDelta = innerSideDeltaY;
      innerSideMinDelta = innerSideDeltaX;
    }

    double result = Integer.MAX_VALUE;

    if (p_layer == 0)
    // calculate shortest distance to component side box
    {
      // calculate one layer distance

      if (!componentSideBoxIsEmpty) {
        result =
            p_box.weightedDistance(
                componentSideBox, traceCosts[0].horizontal, traceCosts[0].vertical);
      }

      if (activeLayerCount <= 1) {
        return result;
      }

      // calculate two layer distance on component and solder side

      double tmpDistance;
      if (minSolderSideTraceCost < minComponentSideTraceCost) {
        tmpDistance =
            minSolderSideTraceCost * solderSideMaxDelta
                + minComponentSideTraceCost * solderSideMinDelta
                + minNormalViaCost;
      } else {
        tmpDistance =
            minComponentSideTraceCost * solderSideMaxDelta
                + minSolderSideTraceCost * solderSideMinDelta
                + minNormalViaCost;
      }

      result = Math.min(result, tmpDistance);

      // calculate two layer distance on component and solde side
      // with two vias

      tmpDistance =
          componentSideMaxDelta
              + componentSideMinDelta * minComponentInnerTraceCost
              + 2 * minNormalViaCost;

      result = Math.min(result, tmpDistance);

      if (activeLayerCount == 2) {
        return result;
      }

      // calculate two layer distance on component side and an inner side

      tmpDistance =
          innerSideMaxDelta + innerSideMinDelta * minComponentInnerTraceCost + minNormalViaCost;

      result = Math.min(result, tmpDistance);

      // calculate three layer distance

      tmpDistance =
          solderSideMaxDelta
              + +minComponentSolderInnerTraceCost * solderSideMinDelta
              + 2 * minNormalViaCost;
      result = Math.min(result, tmpDistance);

      tmpDistance = componentSideMaxDelta + componentSideMinDelta + 2 * minNormalViaCost;
      result = Math.min(result, tmpDistance);

      if (activeLayerCount == 3) {
        return result;
      }

      tmpDistance = innerSideMaxDelta + innerSideMinDelta + 2 * minNormalViaCost;

      result = Math.min(result, tmpDistance);

      // calculate four layer distance

      tmpDistance = solderSideMaxDelta + solderSideMinDelta + 3 * minNormalViaCost;

      return Math.min(result, tmpDistance);
    }
    if (p_layer == layerCount - 1)
    // calculate the shortest distance to solder side box
    {
      // calculate one layer distance

      if (!solderSideBoxIsEmpty) {
        result =
            p_box.weightedDistance(
                solderSideBox, traceCosts[p_layer].horizontal, traceCosts[p_layer].vertical);
      }

      // calculate two layer distance
      double tmpDistance;
      if (minComponentSideTraceCost < minSolderSideTraceCost) {
        tmpDistance =
            minComponentSideTraceCost * componentSideMaxDelta
                + minSolderSideTraceCost * componentSideMinDelta
                + minNormalViaCost;
      } else {
        tmpDistance =
            minSolderSideTraceCost * componentSideMaxDelta
                + minComponentSideTraceCost * componentSideMinDelta
                + minNormalViaCost;
      }
      result = Math.min(result, tmpDistance);
      tmpDistance =
          solderSideMaxDelta + solderSideMinDelta * minSolderInnerTraceCost + 2 * minNormalViaCost;
      result = Math.min(result, tmpDistance);
      if (activeLayerCount <= 2) {
        return result;
      }
      tmpDistance =
          innerSideMinDelta * minSolderInnerTraceCost + innerSideMaxDelta + minNormalViaCost;
      result = Math.min(result, tmpDistance);

      // calculate three layer distance

      tmpDistance =
          componentSideMaxDelta
              + minComponentSolderInnerTraceCost * componentSideMinDelta
              + 2 * minNormalViaCost;
      result = Math.min(result, tmpDistance);
      tmpDistance = solderSideMaxDelta + solderSideMinDelta + 2 * minNormalViaCost;
      result = Math.min(result, tmpDistance);
      if (activeLayerCount == 3) {
        return result;
      }
      tmpDistance = innerSideMaxDelta + innerSideMinDelta + 2 * minNormalViaCost;
      result = Math.min(result, tmpDistance);

      // calculate four layer distance

      tmpDistance = componentSideMaxDelta + componentSideMinDelta + 3 * minNormalViaCost;
      return Math.min(result, tmpDistance);
    }

    // calculate distance to inner layer box

    // calculate one layer distance

    if (!innerSideBoxIsEmpty) {
      result =
          p_box.weightedDistance(
              innerSideBox, traceCosts[p_layer].horizontal, traceCosts[p_layer].vertical);
    }

    // calculate two layer distance

    double tmpDistance = innerSideMaxDelta + innerSideMinDelta + minNormalViaCost;

    result = Math.min(result, tmpDistance);
    tmpDistance =
        componentSideMaxDelta
            + componentSideMinDelta * minComponentInnerTraceCost
            + minNormalViaCost;
    result = Math.min(result, tmpDistance);
    tmpDistance =
        solderSideMaxDelta + solderSideMinDelta * minSolderInnerTraceCost + minNormalViaCost;
    result = Math.min(result, tmpDistance);

    // calculate three layer distance

    tmpDistance = componentSideMaxDelta + componentSideMinDelta + 2 * minNormalViaCost;
    result = Math.min(result, tmpDistance);
    tmpDistance = solderSideMaxDelta + solderSideMinDelta + 2 * minNormalViaCost;
    return Math.min(result, tmpDistance);
  }

  public double calculateCheapDistance(IntBox p_box, int p_layer) {
    double minNormalViaCostSave = minNormalViaCost;

    minNormalViaCost = minCheapViaCost;
    double result = calculate(p_box, p_layer);

    minNormalViaCost = minNormalViaCostSave;
    return result;
  }
}
