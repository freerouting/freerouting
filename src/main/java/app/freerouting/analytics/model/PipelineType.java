package app.freerouting.analytics.model;

/** Represents the execution pipeline or interface through which Freerouting is invoked. */
public enum PipelineType {
  /** Interactive graphical desktop user interface. */
  GUI,

  /** Headless command-line batch execution. */
  CLI,

  /** Headless REST API server execution. */
  API,

  /** Model Context Protocol (MCP) tool execution. */
  MCP
}
