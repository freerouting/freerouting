package app.freerouting.autoroute;

/** Represents the execution state of an autorouting task. */
public enum TaskState {
  IDLE,
  STARTED,
  RUNNING,
  FINISHED,
  CANCELLED,
  TIMED_OUT
}
