package app.freerouting.board.physics;

import app.freerouting.board.facade.BasicBoard;
import java.util.List;

/**
 * Stateless, read-only scalar energy oracle aggregating active {@link PhysicsRule} instances.
 *
 * <p>Future routing or optimization code can query local or full-board physical energy through this
 * facade without coupling to specific field equations.
 */
public final class PhysicsEvaluator {

  private final PhysicsRule[] rules;
  private final double[] weights;

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

  /** Evaluates weighted local energy for a copper segment. */
  public double evaluateLocalSegment(
      BoardFieldContext context,
      int layer,
      int netNo,
      int halfWidth,
      double x1,
      double y1,
      double x2,
      double y2) {
    double total = 0.0;
    for (int i = 0; i < this.rules.length; i++) {
      double w = this.weights[i];
      if (w == 0.0) {
        continue;
      }
      double energy =
          this.rules[i].evaluateSegment(context, layer, netNo, halfWidth, x1, y1, x2, y2);
      if (Double.isFinite(energy) && energy > 0.0) {
        total += w * energy;
      }
    }
    return total;
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
