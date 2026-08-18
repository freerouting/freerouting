package app.freerouting.gui.workspace.ports;

import app.freerouting.gui.workspace.session.LoadGeneration;

/** Session-owned port for starting loads and rejecting stale load callbacks. */
public interface BoardLoadPort {

  /** Starts a new load generation. */
  public LoadGeneration beginBoardLoad();

  /** Returns whether a callback belongs to the current load. */
  public boolean isCurrent(LoadGeneration generation);
}
