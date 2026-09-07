package app.freerouting.analytics;

import app.freerouting.analytics.model.ActorType;
import app.freerouting.analytics.model.PipelineType;
import java.util.Locale;
import java.util.Optional;

/**
 * Inspects runtime environment characteristics, environment variables, and OS process hierarchy to
 * classify the active {@link PipelineType} and {@link ActorType}.
 */
public final class ProcessEnvironmentDetector {

  private ProcessEnvironmentDetector() {}

  /**
   * Identifies whether the current process is executing within a known CI/CD environment.
   *
   * @return {@code true} if a CI environment is detected, {@code false} otherwise
   */
  public static boolean isCiEnvironment() {
    String[] ciEnvVars = {
      "CI",
      "CONTINUOUS_INTEGRATION",
      "GITHUB_ACTIONS",
      "GITLAB_CI",
      "BITBUCKET_BUILD_NUMBER",
      "JENKINS_URL",
      "TRAVIS",
      "CIRCLECI",
      "BUILDKITE",
      "TF_BUILD"
    };

    for (String varName : ciEnvVars) {
      String val = System.getenv(varName);
      if (val != null && !val.isBlank() && !"false".equalsIgnoreCase(val) && !"0".equals(val)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Retrieves the parent process command line or executable name if available.
   *
   * @return lowercased parent command name or empty string if unavailable
   */
  public static String getParentProcessCommand() {
    try {
      Optional<ProcessHandle> parentOpt = ProcessHandle.current().parent();
      if (parentOpt.isPresent()) {
        ProcessHandle.Info info = parentOpt.get().info();
        String cmd = info.command().orElse(info.commandLine().orElse(""));
        if (!cmd.isBlank()) {
          return cmd.toLowerCase(Locale.ROOT);
        }
      }
    } catch (Throwable _) {
      // Ignored: OS permissions or security manager may prohibit process inspection
    }
    return "";
  }

  /**
   * Detects the {@link PipelineType} based on startup configuration and arguments.
   *
   * @param isMcpEnabled whether MCP server is enabled
   * @param isMcpStdio whether MCP runs in stdio bridge mode
   * @param isApiEnabled whether REST API server is enabled
   * @param isGuiEnabled whether GUI is enabled
   * @param hasInitialInputFile whether an initial input board file is passed
   * @return the resolved {@link PipelineType}
   */
  public static PipelineType detectPipelineType(
      boolean isMcpEnabled,
      boolean isMcpStdio,
      boolean isApiEnabled,
      boolean isGuiEnabled,
      boolean hasInitialInputFile) {
    if (isMcpEnabled && isMcpStdio) {
      return PipelineType.MCP;
    }
    if (isGuiEnabled) {
      return PipelineType.GUI;
    }
    if (isApiEnabled && !hasInitialInputFile) {
      return PipelineType.API;
    }
    if (isMcpEnabled && !hasInitialInputFile) {
      return PipelineType.MCP;
    }
    return PipelineType.CLI;
  }

  /**
   * Resolves the {@link ActorType} driving this Freerouting process.
   *
   * @param pipeline the resolved pipeline type
   * @param isGuiEnabled whether GUI is enabled
   * @param hasHeadlessDisplay whether display is headless or has zero dimension
   * @param commandLineArgs the raw command-line argument string
   * @return the resolved {@link ActorType}
   */
  public static ActorType detectActorType(
      PipelineType pipeline,
      boolean isGuiEnabled,
      boolean hasHeadlessDisplay,
      String commandLineArgs) {
    return detectActorType(
        pipeline, isGuiEnabled, hasHeadlessDisplay, commandLineArgs, isCiEnvironment());
  }

  /**
   * Resolves the {@link ActorType} driving this Freerouting process with explicit CI determination.
   *
   * @param pipeline the resolved pipeline type
   * @param isGuiEnabled whether GUI is enabled
   * @param hasHeadlessDisplay whether display is headless or has zero dimension
   * @param commandLineArgs the raw command-line argument string
   * @param isCi whether executing within a CI environment
   * @return the resolved {@link ActorType}
   */
  public static ActorType detectActorType(
      PipelineType pipeline,
      boolean isGuiEnabled,
      boolean hasHeadlessDisplay,
      String commandLineArgs,
      boolean isCi) {
    // 1. CI/CD environment takes priority when detected
    if (isCi) {
      return ActorType.CI_CD;
    }

    // 2. Explicit MCP pipeline is driven by an AI agent
    if (pipeline == PipelineType.MCP) {
      return ActorType.AGENT;
    }

    // 3. Inspect parent process binary/command if accessible
    String parentCmd = getParentProcessCommand();
    if (!parentCmd.isEmpty()) {
      // Agent frameworks and IDE MCP hosts
      if (parentCmd.contains("node")
          || parentCmd.contains("electron")
          || parentCmd.contains("claude")
          || parentCmd.contains("cursor")
          || parentCmd.contains("cline")
          || parentCmd.contains("windsurf")
          || parentCmd.contains("code")
          || parentCmd.contains("copilot")) {
        return ActorType.AGENT;
      }

      // Automated scripting runners and container runtimes
      if (parentCmd.contains("python")
          || parentCmd.contains("pytest")
          || parentCmd.contains("bash")
          || parentCmd.contains("sh")
          || parentCmd.contains("zsh")
          || parentCmd.contains("pwsh")
          || parentCmd.contains("powershell")
          || parentCmd.contains("docker")
          || parentCmd.contains("containerd")
          || parentCmd.contains("podman")
          || parentCmd.contains("perl")
          || parentCmd.contains("ruby")) {
        return ActorType.AUTOMATED_BATCH;
      }
    }

    // 4. Check for headless automation CLI flags
    String argsLower = commandLineArgs == null ? "" : commandLineArgs.toLowerCase(Locale.ROOT);
    if (argsLower.contains("-dct 0")
        || argsLower.contains("--gui.enabled=false")
        || argsLower.contains("--gui-enabled=false")
        || argsLower.contains("/data/")) {
      return ActorType.AUTOMATED_BATCH;
    }

    // 5. Check display capability
    boolean isHeadless =
        hasHeadlessDisplay || Boolean.getBoolean("java.awt.headless") || !isGuiEnabled;
    if (isHeadless) {
      return ActorType.AUTOMATED_BATCH;
    }

    return ActorType.HUMAN;
  }
}
