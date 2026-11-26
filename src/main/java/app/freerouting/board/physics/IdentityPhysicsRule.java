package app.freerouting.board.physics;

import app.freerouting.board.facade.BasicBoard;

/**
 * No-op identity {@link PhysicsRule} returning zero energy for all queries.
 *
 * <p>Used to verify end-to-end pipeline wiring, determinism, and DRC safety before specific
 * electromagnetic, thermal, or geometric relaxation rules are introduced.
 */
public final class IdentityPhysicsRule implements PhysicsRule {

  /** Singleton instance of the stateless identity rule. */
  public static final IdentityPhysicsRule INSTANCE = new IdentityPhysicsRule();

  private IdentityPhysicsRule() {}

  @Override
  public String id() {
    return "identity";
  }

  @Override
  public double evaluateSegment(
      BoardFieldContext context,
      int layer,
      int netNo,
      int halfWidth,
      double x1,
      double y1,
      double x2,
      double y2) {
    return 0.0;
  }

  @Override
  public double evaluateVertex(
      BoardFieldContext context, int layer, int netNo, double x, double y) {
    return 0.0;
  }

  @Override
  public double evaluateBoard(BasicBoard board, BoardFieldContext context) {
    return 0.0;
  }
}
