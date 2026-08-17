package app.freerouting.gui.workspace;

/** Session-owned port for starting loads and rejecting stale load callbacks. */
public interface BoardLoadPort {

  /** Starts a new load generation. */
  LoadGeneration beginBoardLoad();

  /** Returns whether a callback belongs to the current load. */
  boolean isCurrent(LoadGeneration generation);
}
