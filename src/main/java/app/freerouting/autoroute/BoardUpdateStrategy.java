package app.freerouting.autoroute;

/** Strategy for updating board state during routing passes. */
public enum BoardUpdateStrategy {
  GREEDY,
  GLOBAL_OPTIMAL,
  HYBRID
}
