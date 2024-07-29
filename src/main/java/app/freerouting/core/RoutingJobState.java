package app.freerouting.core;

public enum RoutingJobState
{
  INVALID, // The job is in an invalid state
  QUEUED, // The job is waiting to be processed
  RUNNING, // The job is currently being processed
  PAUSED, // The job is paused and can be resumed
  COMPLETED, // The job has been completed successfully
  CANCELLED, // The job has been cancelled by the user
  TERMINATED // The job has been terminated due to an error
}