package app.freerouting.analytics.model;

/** Represents the lifecycle progress or terminal status of a routing job. */
public enum JobLifecycleStatus {
  /** Job was enqueued and started execution. */
  STARTED,

  /** Job completed successfully with routing finished. */
  SUCCEEDED,

  /** Job failed due to parsing, DRC, algorithmic error, or unrouted incompletes. */
  FAILED,

  /** Job timed out before completing all passes. */
  TIMED_OUT,

  /** Job was cancelled by user or client request. */
  CANCELLED
}
