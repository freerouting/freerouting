package app.freerouting.autoroute.decoupling;

import java.util.List;

/**
 * Aggregated summary of the decoupling capacitor pre-routing pass.
 *
 * @param candidatesTotal total number of eligible decoupling candidates identified on the board
 * @param strapsRouted number of decoupling surface straps successfully routed
 * @param alreadyConnected number of decoupling connections already satisfied by existing copper
 * @param viasPlaced number of dedicated ground/plane vias placed adjacent to capacitor pads
 * @param failed number of candidates that failed routing or were skipped due to clearance rules
 * @param totalStrapLengthMm cumulative length of all routed decoupling straps in millimetres
 * @param metrics individual per-capacitor metrics
 * @param coupledViaPairs number of via pairs successfully coupled along the lateral flank
 * @param fallbackViaPairs number of via pairs falling back to end-fire or uncoordinated placement
 */
public record DecouplingRoutingSummary(
    int candidatesTotal,
    int strapsRouted,
    int alreadyConnected,
    int viasPlaced,
    int failed,
    double totalStrapLengthMm,
    List<DecouplingMetrics> metrics,
    int coupledViaPairs,
    int fallbackViaPairs) {

  public DecouplingRoutingSummary {
    metrics = metrics == null ? List.of() : List.copyOf(metrics);
  }

  /** Backward-compatible constructor for summaries without coupled pair counts. */
  public DecouplingRoutingSummary(
      int candidatesTotal,
      int strapsRouted,
      int alreadyConnected,
      int viasPlaced,
      int failed,
      double totalStrapLengthMm,
      List<DecouplingMetrics> metrics) {
    this(
        candidatesTotal,
        strapsRouted,
        alreadyConnected,
        viasPlaced,
        failed,
        totalStrapLengthMm,
        metrics,
        0,
        0);
  }
}
