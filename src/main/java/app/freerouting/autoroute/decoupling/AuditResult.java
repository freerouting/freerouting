package app.freerouting.autoroute.decoupling;

/** Result of cross-checking a topological verdict against the component's reference designator. */
public enum AuditResult {
  NOT_RUN,
  AGREE,
  CONFLICT,
  UNKNOWN
}

