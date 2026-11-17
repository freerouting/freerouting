package app.freerouting.autoroute.scope;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.state.ChangedArea;

/**
 * Transactional checkpoint capturing board state before a Tier 2 connection attempt.
 *
 * <p>Snapshots {@code UndoableObjects} (via {@link RoutingBoard#generateSnapshot()}), {@link
 * ChangedArea} (via {@link RoutingBoard#getChangedAreaSnapshot()}), and the active {@link
 * RoutingScopeContext} foreign baseline bits. If foreign net recovery fails, rolls back all
 * modifications atomically.
 */
public final class ScopedRouteCheckpoint implements AutoCloseable {

  private final RoutingBoard board;
  private final ChangedArea changedAreaSnapshot;
  private final long[] foreignBaselineBitsSnapshot;
  private boolean rolledBack;
  private boolean committed;

  private ScopedRouteCheckpoint(
      RoutingBoard board, ChangedArea changedAreaSnapshot, long[] foreignBaselineBitsSnapshot) {
    this.board = board;
    this.changedAreaSnapshot = changedAreaSnapshot;
    this.foreignBaselineBitsSnapshot = foreignBaselineBitsSnapshot;
    this.rolledBack = false;
    this.committed = false;
  }

  /** Captures a transactional checkpoint on the board. */
  public static ScopedRouteCheckpoint capture(RoutingBoard board) {
    board.generateSnapshot();
    ChangedArea changedAreaSnapshot = board.getChangedAreaSnapshot();
    RoutingScopeContext scopeContext = board.getRoutingScopeContext();
    long[] foreignBaselineSnapshot =
        scopeContext != null ? scopeContext.snapshotForeignBaselineBits() : new long[0];
    return new ScopedRouteCheckpoint(board, changedAreaSnapshot, foreignBaselineSnapshot);
  }

  /** Rolls back all items, search trees, dirty areas, and scope bits to the checkpoint. */
  public void rollback() {
    if (this.rolledBack || this.committed) {
      return;
    }
    this.board.undo(null);
    this.board.restoreChangedAreaSnapshot(this.changedAreaSnapshot);
    RoutingScopeContext scopeContext = this.board.getRoutingScopeContext();
    if (scopeContext != null) {
      scopeContext.restoreForeignBaselineBits(this.foreignBaselineBitsSnapshot);
      scopeContext.clearAnchorShieldAndRecoveryMode();
    }
    this.rolledBack = true;
  }

  /** Commits the transaction, popping the undo snapshot so it cannot be undone. */
  public void commit() {
    if (this.rolledBack || this.committed) {
      return;
    }
    this.board.popSnapshot();
    RoutingScopeContext scopeContext = this.board.getRoutingScopeContext();
    if (scopeContext != null) {
      scopeContext.clearAnchorShieldAndRecoveryMode();
    }
    this.committed = true;
  }

  /** Returns whether this checkpoint has been rolled back. */
  public boolean isRolledBack() {
    return this.rolledBack;
  }

  /** Returns whether this checkpoint has been committed. */
  public boolean isCommitted() {
    return this.committed;
  }

  @Override
  public void close() {
    if (!this.committed && !this.rolledBack) {
      rollback();
    }
  }
}
