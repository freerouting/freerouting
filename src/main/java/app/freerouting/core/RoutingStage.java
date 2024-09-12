package app.freerouting.core;

/* The different stages a routing can be in. */
public enum RoutingStage
{
  IDLE, // The job hasn't started yet
  FANOUT, // The components on the board are being fanned out (i.e. the components are being placed)
  ROUTING, // The board is currently being processed by the auto-router
  OPTIMIZATION, // The board is routed, and it is currently being optimized
  FINISHED, // The job has been completed successfully
  CANCELLED, // The job has been cancelled by the user
  ERROR; // The job has been terminated due to an error

  public static RoutingStage fromString(String text) {
    for (RoutingStage stage : RoutingStage.values()) {
      if (stage.name().equalsIgnoreCase(text)) {
        return stage;
      }
    }
    throw new IllegalArgumentException("No constant with text " + text + " found");
  }
}
