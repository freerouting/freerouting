package app.freerouting.analytics.model;

/** Represents the actor or execution context driving the Freerouting process. */
public enum ActorType {
  /** Interactive human engineer operating the visual display. */
  HUMAN,

  /** Autonomous AI or LLM agent operating via MCP or agent orchestration. */
  AGENT,

  /** Automated batch script, training cluster, container worker, or parametric loop. */
  AUTOMATED_BATCH,

  /** Continuous integration or continuous delivery build runner. */
  CI_CD
}
