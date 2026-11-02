package app.freerouting.autoroute.decoupling;

/**
 * Aggregate metrics and summary counts from a decoupling identification pass.
 *
 * @param components total components evaluated
 * @param twoPinCandidates two-pin SMD components meeting initial sanity criteria
 * @param decoupling count of candidates classified as DECOUPLING
 * @param review count of candidates classified as REVIEW
 * @param rejected count of candidates classified as REJECTED
 * @param auditConflicts count of candidates where topology and refdes naming diverged
 */
public record DiagnosticsSummary(
    int components,
    int twoPinCandidates,
    int decoupling,
    int review,
    int rejected,
    int auditConflicts) {}

