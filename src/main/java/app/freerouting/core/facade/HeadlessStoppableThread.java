package app.freerouting.core.facade;

import app.freerouting.core.StoppableThread;

/**
 * Lightweight in-memory thread execution adapter bridging {@link CancellationToken} to
 * Freerouting's internal {@link StoppableThread} stop requests.
 */
final class HeadlessStoppableThread extends StoppableThread {

  private final CancellationToken cancellationToken;

  HeadlessStoppableThread(CancellationToken cancellationToken) {
    this.cancellationToken =
        cancellationToken != null ? cancellationToken : CancellationToken.none();
  }

  @Override
  public synchronized boolean isStopRequested() {
    return super.isStopRequested() || this.cancellationToken.isCancelled();
  }

  @Override
  public synchronized boolean isStopAutoRouterRequested() {
    return super.isStopAutoRouterRequested() || this.cancellationToken.isCancelled();
  }

  @Override
  protected void threadAction() {
    // In headless in-memory mode, execution runs synchronously on the calling thread.
  }
}
