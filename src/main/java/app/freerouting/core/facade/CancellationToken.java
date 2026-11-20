package app.freerouting.core.facade;

/**
 * Functional cooperative cancellation hook for headless routing execution.
 *
 * <p>Allows embedding runtimes (such as Web Workers or external thread pools) to signal
 * cancellation to an active routing pipeline without thread interrupts.
 */
@FunctionalInterface
public interface CancellationToken {

  /** Returns {@code true} if cancellation has been requested. */
  boolean isCancelled();

  /** Returns a cancellation token that is never cancelled. */
  static CancellationToken none() {
    return () -> false;
  }
}
