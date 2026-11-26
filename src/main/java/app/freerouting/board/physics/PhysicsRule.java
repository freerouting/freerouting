package app.freerouting.board.physics;

import app.freerouting.board.facade.BasicBoard;

/**
 * Pluggable, component-agnostic physical energy rule operating strictly on copper geometry.
 *
 * <p>Implementations must be stateless (or strictly read-only during evaluation), deterministic,
 * and return non-negative scalar energy values.
 */
public interface PhysicsRule {

  /** Returns a stable identifier for this physics rule. */
  String id();

  /**
   * Evaluates the non-negative scalar energy of a copper segment on {@code layer}.
   *
   * @param context coarse per-layer field context
   * @param layer board layer index
   * @param netNo net number (or {@code -1} / {@code 0} if unassigned)
   * @param halfWidth trace half-width in board units
   * @param x1 start X coordinate in board units
   * @param y1 start Y coordinate in board units
   * @param x2 end X coordinate in board units
   * @param y2 end Y coordinate in board units
   * @return non-negative scalar energy contribution
   */
  double evaluateSegment(
      BoardFieldContext context,
      int layer,
      int netNo,
      int halfWidth,
      double x1,
      double y1,
      double x2,
      double y2);

  /**
   * Evaluates the non-negative scalar energy at a vertex neighborhood on {@code layer}.
   *
   * @param context coarse per-layer field context
   * @param layer board layer index
   * @param netNo net number
   * @param x X coordinate in board units
   * @param y Y coordinate in board units
   * @return non-negative scalar energy contribution
   */
  double evaluateVertex(BoardFieldContext context, int layer, int netNo, double x, double y);

  /**
   * Evaluates the total non-negative physical energy across the full board.
   *
   * @param board board model to inspect
   * @param context coarse per-layer field context
   * @return non-negative total scalar energy
   */
  double evaluateBoard(BasicBoard board, BoardFieldContext context);
}
