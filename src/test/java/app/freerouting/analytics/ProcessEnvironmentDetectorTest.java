package app.freerouting.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.analytics.model.ActorType;
import app.freerouting.analytics.model.JobLifecycleStatus;
import app.freerouting.analytics.model.PipelineType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcessEnvironmentDetectorTest {

  @Test
  void testDetectPipelineType() {
    assertEquals(
        PipelineType.MCP,
        ProcessEnvironmentDetector.detectPipelineType(true, true, true, false, false));

    assertEquals(
        PipelineType.GUI,
        ProcessEnvironmentDetector.detectPipelineType(false, false, false, true, false));

    assertEquals(
        PipelineType.API,
        ProcessEnvironmentDetector.detectPipelineType(false, false, true, false, false));

    assertEquals(
        PipelineType.CLI,
        ProcessEnvironmentDetector.detectPipelineType(false, false, false, false, true));
  }

  @Test
  void testDetectActorTypeForMcp() {
    assertEquals(
        ActorType.AGENT,
        ProcessEnvironmentDetector.detectActorType(PipelineType.MCP, false, true, "", false));
  }

  @Test
  void testDetectActorTypeForHeadlessFlags() {
    assertEquals(
        ActorType.AUTOMATED_BATCH,
        ProcessEnvironmentDetector.detectActorType(
            PipelineType.CLI, false, true, "-de board.dsn -do board.ses -mp 100 -dct 0", false));

    assertEquals(
        ActorType.AUTOMATED_BATCH,
        ProcessEnvironmentDetector.detectActorType(
            PipelineType.CLI, false, true, "--gui.enabled=false -de /data/board.dsn", false));
  }

  @Test
  void testDetectActorTypeForCi() {
    assertEquals(
        ActorType.CI_CD,
        ProcessEnvironmentDetector.detectActorType(
            PipelineType.CLI, false, true, "-de board.dsn", true));
  }

  @Test
  void testParentProcessDetectionSafeExecution() {
    // Verifies that getParentProcessCommand executes without throwing exceptions on any OS
    String cmd = ProcessEnvironmentDetector.getParentProcessCommand();
    assertNotNull(cmd);
  }

  @Test
  void testFRAnalyticsExecutionContextAndLifecycleMethods() {
    FRAnalytics.setExecutionContext(PipelineType.CLI, ActorType.AUTOMATED_BATCH, "KiCad", "9.0");

    assertEquals(PipelineType.CLI, FRAnalytics.getCurrentPipeline());
    assertEquals(ActorType.AUTOMATED_BATCH, FRAnalytics.getCurrentActorType());
    assertEquals("KiCad", FRAnalytics.getCurrentIntegrationTool());
    assertEquals("9.0", FRAnalytics.getCurrentIntegrationVersion());

    // Verify calling lifecycle methods does not throw exceptions when analytics is null/disabled
    FRAnalytics.recordBatchJobSummary(
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        "test_board.dsn",
        0,
        JobLifecycleStatus.SUCCEEDED,
        null,
        100,
        0,
        0,
        99.5f,
        10,
        12.5,
        8.0,
        512.0,
        "KiCad",
        "9.0");

    FRAnalytics.recordJobLifecycle(
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        JobLifecycleStatus.STARTED,
        PipelineType.API,
        ActorType.HUMAN,
        null,
        50,
        5,
        0,
        null,
        null,
        null,
        null,
        "EasyEDA",
        "1.0",
        UUID.randomUUID());

    FRAnalytics.recordSessionLifecycle(
        UUID.randomUUID().toString(),
        "SESSION_CREATED",
        PipelineType.MCP,
        ActorType.AGENT,
        "claude-desktop",
        UUID.randomUUID());

    FRAnalytics.recordStructuredError(
        "MCP",
        "TOOL_EXECUTION_FAILED",
        "Tool invocation error",
        new RuntimeException("Test exception"),
        "corr-12345");

    // Verify GUI pipeline job lifecycle events can be recorded cleanly
    FRAnalytics.setExecutionContext(PipelineType.GUI, ActorType.HUMAN, "Freerouting", "2.3.0");
    assertEquals(PipelineType.GUI, FRAnalytics.getCurrentPipeline());
    assertEquals(ActorType.HUMAN, FRAnalytics.getCurrentActorType());

    FRAnalytics.recordJobLifecycle(
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        JobLifecycleStatus.STARTED,
        PipelineType.GUI,
        ActorType.HUMAN,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "KiCad",
        null,
        null);

    FRAnalytics.recordJobLifecycle(
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        JobLifecycleStatus.SUCCEEDED,
        PipelineType.GUI,
        ActorType.HUMAN,
        null,
        150,
        0,
        0,
        98.5f,
        15.4,
        14.2,
        256.0,
        "KiCad",
        null,
        null);
  }
}
