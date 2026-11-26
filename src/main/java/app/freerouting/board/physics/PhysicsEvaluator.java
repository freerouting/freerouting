package app.freerouting.board.physics;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.datastructures.ShapeTree.TreeEntry;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified read-only scalar energy oracle aggregating Tier 1 ({@link PhysicsRule} / {@link
 * BoardFieldContext}) and Tier 2 ({@link NearFieldEnergyRule} / {@link ShapeSearchTree}) rules.
 */
public final class PhysicsEvaluator {

  private final PhysicsRule[] rules;
  private final double[] weights;
  private final List<TreeEntry> scratchEntries = new ArrayList<>(64);
  private final int[] scratchIgnoreNet = new int[1];

  /** Creates an evaluator with the supplied rules and per-rule scalar weights. */
  public PhysicsEvaluator(List<PhysicsRule> rules, double[] weights) {
    if (rules == null || rules.isEmpty()) {
      this.rules = new PhysicsRule[] {IdentityPhysicsRule.INSTANCE};
      this.weights = new double[] {1.0};
      return;
    }
    this.rules = rules.toArray(PhysicsRule[]::new);
    this.weights = new double[this.rules.length];
    for (int i = 0; i < this.rules.length; i++) {
      double w = (weights != null && i < weights.length) ? weights[i] : 1.0;
      this.weights[i] = (Double.isFinite(w) && w >= 0.0) ? w : 0.0;
    }
  }

  /** Creates a default evaluator wired with {@link IdentityPhysicsRule}. */
  public static PhysicsEvaluator identity(double weight) {
    return new PhysicsEvaluator(List.of(IdentityPhysicsRule.INSTANCE), new double[] {weight});
  }

  /**
   * Evaluates weighted local energy for a copper segment using Tier 2 exact near-field when
   * available.
   */
  public double evaluateLocalSegment(
      BasicBoard board,
      ShapeSearchTree tree,
      BoardFieldContext context,
      int layer,
      int netNo,
      int halfWidth,
      double x1,
      double y1,
      double x2,
      double y2,
      double cutoffUnits) {
    double total = 0.0;
    for (int i = 0; i < this.rules.length; i++) {
      double w = this.weights[i];
      if (w == 0.0) {
        continue;
      }
      double energy;
      if (this.rules[i] instanceof NearFieldEnergyRule nearField && tree != null) {
        energy =
            nearField.evaluateSegment(
                board,
                tree,
                this.scratchEntries,
                this.scratchIgnoreNet,
                layer,
                netNo,
                halfWidth,
                x1,
                y1,
                x2,
                y2,
                cutoffUnits);
      } else {
        energy = this.rules[i].evaluateSegment(context, layer, netNo, halfWidth, x1, y1, x2, y2);
      }
      if (Double.isFinite(energy) && energy > 0.0) {
        total += w * energy;
      }
    }
    return total;
  }

  /** Evaluates weighted local energy for a copper segment against Tier 1 field context only. */
  public double evaluateLocalSegment(
      BoardFieldContext context,
      int layer,
      int netNo,
      int halfWidth,
      double x1,
      double y1,
      double x2,
      double y2) {
    return evaluateLocalSegment(null, null, context, layer, netNo, halfWidth, x1, y1, x2, y2, 0.0);
  }

  /** Evaluates weighted local energy at a vertex neighborhood. */
  public double evaluateLocalVertex(
      BoardFieldContext context, int layer, int netNo, double x, double y) {
    double total = 0.0;
    for (int i = 0; i < this.rules.length; i++) {
      double w = this.weights[i];
      if (w == 0.0) {
        continue;
      }
      double energy = this.rules[i].evaluateVertex(context, layer, netNo, x, y);
      if (Double.isFinite(energy) && energy > 0.0) {
        total += w * energy;
      }
    }
    return total;
  }

  /** Evaluates total weighted physical energy across {@code board}. */
  public double evaluateBoard(BasicBoard board, BoardFieldContext context) {
    double total = 0.0;
    for (int i = 0; i < this.rules.length; i++) {
      double w = this.weights[i];
      if (w == 0.0) {
        continue;
      }
      double energy = this.rules[i].evaluateBoard(board, context);
      if (Double.isFinite(energy) && energy > 0.0) {
        total += w * energy;
      }
    }
    return total;
  }

  /** Returns the number of active rules registered in this evaluator. */
  public int getRuleCount() {
    return this.rules.length;
  }
}
