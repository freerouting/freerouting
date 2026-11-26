package app.freerouting.board.physics;

import app.freerouting.board.facade.BasicBoard;

/**
 * Tier 1 extension point that populates or smooths static per-layer {@link BoardFieldContext} grids
 * once at the start of a physics pass.
 */
public interface StaticFieldContributor {

  /** Returns a stable identifier for this static field contributor. */
  String id();

  /**
   * Contributes static field values (such as plane-void penalties, board-edge shielding, or global
   * channel potentials) into {@code context}.
   */
  void contribute(BasicBoard board, BoardFieldContext context);
}
