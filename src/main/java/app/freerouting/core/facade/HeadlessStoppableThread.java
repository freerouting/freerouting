package app.freerouting.core.facade;

import app.freerouting.core.StoppableThread;

/**
 * Lightweight in-memory thread execution adapter bridging {@link CancellationToken} to
 * Freerouting's internal {@link StoppableThread} stop requests.
 */
final class HeadlessStoppableThread extends StoppableThread {

  private final CancellationToken cancellationToken;
  private final Long deadlineMillis;
  private volatile boolean timedOut;

  HeadlessStoppableThread(CancellationToken cancellationToken) {
    this(cancellationToken, null);
  }

  HeadlessStoppableThread(CancellationToken cancellationToken, Long deadlineMillis) {
    this.cancellationToken =
        cancellationToken != null ? cancellationToken : CancellationToken.none();
    this.deadlineMillis = deadlineMillis;
  }

  boolean isTimedOut() {
    checkTimeout();
    return this.timedOut;
  }

  private boolean checkTimeout() {
    if (this.deadlineMillis != null && System.currentTimeMillis() >= this.deadlineMillis) {
      this.timedOut = true;
      return true;
    }
    return this.timedOut;
  }

  @Override
  public synchronized boolean isStopRequested() {
    return super.isStopRequested() || this.cancellationToken.isCancelled() || checkTimeout();
  }

  @Override
  public synchronized boolean isStopAutoRouterRequested() {
    return super.isStopAutoRouterRequested()
        || this.cancellationToken.isCancelled()
        || checkTimeout();
  }

  @Override
  protected void threadAction() {
    // In headless in-memory mode, execution runs synchronously on the calling thread.
  }
}
