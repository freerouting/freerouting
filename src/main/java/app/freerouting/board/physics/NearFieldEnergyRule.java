package app.freerouting.board.physics;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.datastructures.ShapeTree.TreeEntry;
import java.util.List;

/**
 * Tier 2 extension point for exact, continuous near-field energy kernels evaluated locally via
 * {@link ShapeSearchTree} with zero hot-loop allocations.
 */
public interface NearFieldEnergyRule {

  /** Returns a stable identifier for this near-field rule. */
  String id();

  /**
   * Evaluates the continuous near-field energy of a copper segment against nearby spatial-tree
   * items within {@code cutoffUnits}.
   */
  double evaluateSegment(
      BasicBoard board,
      ShapeSearchTree tree,
      List<TreeEntry> scratchEntries,
      int[] scratchIgnoreNet,
      int layer,
      int netNo,
      int halfWidth,
      double x1,
      double y1,
      double x2,
      double y2,
      double cutoffUnits);
}
