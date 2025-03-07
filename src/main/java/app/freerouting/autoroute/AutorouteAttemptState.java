package app.freerouting.autoroute;

/**
 * The possible results of auto-routing a connection
 */
public enum AutorouteAttemptState
{
  UNKNOWN,              // Unknown result
  SKIPPED,              // Item was skipped
  NO_UNCONNECTED_NETS,  // Item has no unconnected nets
  CONNECTED_TO_PLANE,   // Item is connected to a conduction plane
  ALREADY_CONNECTED,    // Item is already connected
  NO_CONNECTIONS,       // The item has no connections to nets
  ROUTED,               // Item was successfully routed
  FAILED,               // Routing failed
  INSERT_ERROR          // Error inserting item
}