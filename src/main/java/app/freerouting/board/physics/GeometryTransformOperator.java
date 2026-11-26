package app.freerouting.board.physics;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.trace.PolylineTrace;

/**
 * Extension point for proposing and committing DRC-legal octilinear geometry transformations during
 * {@link PhysicsOptimizationPass}.
 */
public interface GeometryTransformOperator {

  /** Returns a stable identifier for this geometry transform operator. */
  String id();

  /**
   * Proposes candidate transformations for {@code trace} and commits the best DRC-clean move if
   * energy improves.
   */
  PhysicsOptimizationPass.MoveAttempt proposeAndApply(
      RoutingBoard board,
      BoardFieldContext context,
      PhysicsEvaluator evaluator,
      PolylineTrace trace,
      AngleRestriction angleRestriction,
      double cutoffUnits);
}
