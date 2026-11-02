package app.freerouting.autoroute.decoupling;

import java.util.List;

/**
 * Immutable output of the decoupling capacitor identification pass.
 *
 * @param candidates unmodifiable list of evaluated candidates sorted by (score DESC, componentId
 *     ASC, pinIdA ASC)
 * @param summary summary statistics of the identification pass
 */
public record IdentificationResult(
    List<CapacitorCandidate> candidates, DiagnosticsSummary summary) {

  public IdentificationResult {
    candidates = candidates == null ? List.of() : List.copyOf(candidates);
  }
}
