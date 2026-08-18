package app.freerouting.gui.workspace.session;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import app.freerouting.core.RoutingJob;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.settings.GlobalSettings;

/**
 * Stores GUI-session options that configure batch routing without owning board or Swing state.
 *
 * <p>{@link GuiBoardManager} remains the public façade for these values. This class isolates the
 * session configuration slice so that future GUI collaborators do not need to depend on the
 * manager's rendering, persistence, or interaction responsibilities.
 */
public final class GuiBoardSessionState {

  private final GlobalSettings globalSettings;
  private final RoutingJob routingJob;
  private int numThreads;
  private BoardUpdateStrategy boardUpdateStrategy;
  private String hybridRatio;
  private ItemSelectionStrategy itemSelectionStrategy;

  public GuiBoardSessionState(GlobalSettings globalSettings, RoutingJob routingJob) {
    this.globalSettings = globalSettings;
    this.routingJob = routingJob;
  }

  /** Returns the board update strategy for batch operations. */
  public BoardUpdateStrategy getBoardUpdateStrategy() {
    return boardUpdateStrategy;
  }

  /** Sets the board update strategy for batch operations. */
  public void setBoardUpdateStrategy(BoardUpdateStrategy boardUpdateStrategy) {
    this.boardUpdateStrategy = boardUpdateStrategy;
  }

  /** Returns the hybrid routing ratio configuration. */
  public String getHybridRatio() {
    return hybridRatio;
  }

  /** Sets the hybrid routing ratio configuration. */
  public void setHybridRatio(String hybridRatio) {
    this.hybridRatio = hybridRatio;
  }

  /** Returns the item selection strategy for batch autorouting. */
  public ItemSelectionStrategy getItemSelectionStrategy() {
    return itemSelectionStrategy;
  }

  /** Sets the item selection strategy for batch autorouting. */
  public void setItemSelectionStrategy(ItemSelectionStrategy itemSelectionStrategy) {
    this.itemSelectionStrategy = itemSelectionStrategy;
  }

  /**
   * Returns the effective number of threads, respecting the global multi-threading feature flag.
   */
  public int getNumThreads() {
    if ((numThreads > 1) && (!globalSettings.featureFlags.multiThreading)) {
      routingJob.logInfo("Multi-threading is disabled in the settings. Using single thread.");
      numThreads = 1;
    }

    return numThreads;
  }

  /** Sets the configured number of threads for parallel routing operations. */
  public void setNumThreads(int value) {
    numThreads = value;
  }
}
