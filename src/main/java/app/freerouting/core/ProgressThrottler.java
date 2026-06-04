package app.freerouting.core;

/**
 * Utility to throttle progress updates and notifications.
 */
public class ProgressThrottler {
  private final long intervalMs;
  private long lastUpdateMs;

  public ProgressThrottler(long intervalMs) {
    this.intervalMs = intervalMs;
    this.lastUpdateMs = 0;
  }

  /**
   * Returns true if the throttled action should run.
   */
  public boolean shouldUpdate() {
    long now = System.currentTimeMillis();
    if (lastUpdateMs == 0) {
      lastUpdateMs = now;
      return true;
    }
    if (now - lastUpdateMs >= intervalMs) {
      lastUpdateMs = now;
      return true;
    }
    return false;
  }

  /**
   * Resets the throttler timer.
   */
  public void reset() {
    this.lastUpdateMs = 0;
  }
}
