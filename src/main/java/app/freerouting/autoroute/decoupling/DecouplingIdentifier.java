package app.freerouting.autoroute.decoupling;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.logger.FRLogger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Main entry point for the read-only, deterministic decoupling capacitor identifier.
 *
 * <p>Executes Stage 0 identification over a {@link RoutingBoard} without mutating board state,
 * routing items, or locking copper.
 */
public final class DecouplingIdentifier {

  private DecouplingIdentifier() {}

  /**
   * Identifies IC supply decoupling capacitors using default configuration parameters.
   *
   * @param board the routing board to analyze
   * @return immutable identification result
   */
  public static IdentificationResult identify(RoutingBoard board) {
    return identify(board, IdentifierConfig.defaultConfig());
  }

  /**
   * Identifies IC supply decoupling capacitors on a routing board.
   *
   * @param board the routing board to analyze
   * @param config identification settings and thresholds
   * @return immutable identification result
   */
  public static IdentificationResult identify(RoutingBoard board, IdentifierConfig config) {
    if (board == null) {
      return new IdentificationResult(List.of(), new DiagnosticsSummary(0, 0, 0, 0, 0, 0));
    }

    long startTimeNanos = System.nanoTime();
    IdentifierConfig activeConfig = config != null ? config : IdentifierConfig.defaultConfig();

    // 1. Determine internal coordinate resolution (board units per mm)
    double resolutionMm =
        (board.communication != null) ? board.communication.getResolution(Unit.MM) : 1000.0;
    if (resolutionMm <= 0.0) {
      resolutionMm = 1000.0;
    }

    // 2. Single-pass board indexing and component typing (Phases 1 & 3)
    BoardIndex boardIndex = BoardIndex.build(board, activeConfig);

    // 3. Hard gates and Pass A relative scoring (Phase 4)
    List<CapacitorScorer.EvaluationState> states =
        CapacitorScorer.evaluatePassA(boardIndex, activeConfig, resolutionMm);

    // 4. Pass B filtered-island bonus (Phase 5)
    CapacitorScorer.applyPassB(states, boardIndex, activeConfig);

    // 5. Spatial owner lookup (Phase 6)
    OwnerFinder.findOwners(states, boardIndex, activeConfig, resolutionMm);

    // 6. Reference designator audit cross-check (Phase 6)
    int auditConflicts = RefDesAudit.audit(states, board, activeConfig);

    // 7. Assemble final immutable candidate list and summary
    List<CapacitorCandidate> candidates = new ArrayList<>(states.size());
    int decouplingCount = 0;
    int reviewCount = 0;
    int rejectedCount = 0;

    for (CapacitorScorer.EvaluationState state : states) {
      CapacitorCandidate candidate = state.toCandidate();
      candidates.add(candidate);
      switch (candidate.verdict()) {
        case DECOUPLING -> decouplingCount++;
        case REVIEW -> reviewCount++;
        case REJECTED -> rejectedCount++;
      }
    }

    // Sort deterministically by (score DESC, componentId ASC, pinIdA ASC)
    Collections.sort(
        candidates,
        (c1, c2) -> {
          int cmp = Integer.compare(c2.score(), c1.score());
          if (cmp != 0) {
            return cmp;
          }
          cmp = Integer.compare(c1.componentId(), c2.componentId());
          if (cmp != 0) {
            return cmp;
          }
          return Integer.compare(c1.pinIdA(), c2.pinIdA());
        });

    DiagnosticsSummary summary =
        new DiagnosticsSummary(
            boardIndex.totalComponents(),
            states.size(),
            decouplingCount,
            reviewCount,
            rejectedCount,
            auditConflicts);

    long elapsedMs = (System.nanoTime() - startTimeNanos) / 1_000_000L;
    FRLogger.info(
        String.format(
            "Decoupling identification: %d components evaluated, %d candidates (%d decoupling, %d review, %d rejected, %d audit conflicts) in %d ms",
            summary.components(),
            summary.twoPinCandidates(),
            summary.decoupling(),
            summary.review(),
            summary.rejected(),
            summary.auditConflicts(),
            elapsedMs));

    return new IdentificationResult(candidates, summary);
  }
}
