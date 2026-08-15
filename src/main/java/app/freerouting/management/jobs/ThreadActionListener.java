package app.freerouting.management.jobs;

/** Receives lifecycle notifications from an autorouter thread. */
public interface ThreadActionListener {

  /** Invoked when autorouting starts. */
  void autorouterStarted();

  /** Invoked when autorouting is aborted. */
  void autorouterAborted();

  /** Invoked when autorouting finishes. */
  void autorouterFinished();
}
