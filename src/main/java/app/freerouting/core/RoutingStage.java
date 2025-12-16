package app.freerouting.core;

/* The different stages a routing can be in. */
public enum RoutingStage {
  IDLE, // The job hasn't started yet
  FANOUT, // The components on the board are being fanned out (i.e. the components are being placed)
  ROUTING, // The board is currently being processed by the auto-router
  OPTIMIZATION // The board is routed, and it is currently being optimized
}