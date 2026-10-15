package app.freerouting.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.api.dto.AutorouteRequest;
import app.freerouting.api.mcp.OpenApiMcpToolRegistry;
import app.freerouting.cli.CliRunner;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.events.RoutingJobLogEntryAddedEventListener;
import app.freerouting.util.gson.GsonProvider;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Tests covering Headless CLI, API & MCP defects (FR-054, FR-055, FR-056, FR-072). */
public class ApiMcpCliDefectsTest {

  @Test
  void testRoutingJobStateIsTerminal_FR072() {
    assertTrue(RoutingJobState.COMPLETED.isTerminal());
    assertTrue(RoutingJobState.CANCELLED.isTerminal());
    assertTrue(RoutingJobState.TIMED_OUT.isTerminal());
    assertTrue(RoutingJobState.TERMINATED.isTerminal());
    assertTrue(RoutingJobState.INVALID.isTerminal());

    assertFalse(RoutingJobState.RUNNING.isTerminal());
    assertFalse(RoutingJobState.QUEUED.isTerminal());
    assertFalse(RoutingJobState.READY_TO_START.isTerminal());
    assertFalse(RoutingJobState.PAUSED.isTerminal());
    assertFalse(RoutingJobState.STOPPING.isTerminal());
  }

  @Test
  void testCliRunnerTerminalStateIncludesInvalid_FR072() {
    assertTrue(CliRunner.isCliTerminalState(RoutingJobState.INVALID));
    assertTrue(CliRunner.isCliTerminalState(RoutingJobState.COMPLETED));
    assertTrue(CliRunner.isCliTerminalState(RoutingJobState.TERMINATED));
    assertTrue(CliRunner.isCliTerminalState(RoutingJobState.TIMED_OUT));
    assertTrue(CliRunner.isCliTerminalState(RoutingJobState.CANCELLED));

    assertFalse(CliRunner.isCliTerminalState(RoutingJobState.RUNNING));
    assertFalse(CliRunner.isCliTerminalState(RoutingJobState.READY_TO_START));
    assertFalse(CliRunner.isCliTerminalState(null));
  }

  @Test
  void testOpenApiMcpToolRegistryBuildToolNameSessionList_FR054() throws Exception {
    Method buildToolNameMethod =
        OpenApiMcpToolRegistry.class.getDeclaredMethod("buildToolName", String.class, String.class);
    buildToolNameMethod.setAccessible(true);

    String sessionListToolName =
        (String) buildToolNameMethod.invoke(null, "GET", "/v1/sessions/list");
    assertEquals("list_sessions", sessionListToolName);

    String sessionsToolName = (String) buildToolNameMethod.invoke(null, "GET", "/v1/sessions");
    assertEquals("list_sessions", sessionsToolName);

    String sessionDetailsToolName =
        (String)
            buildToolNameMethod.invoke(
                null, "GET", "/v1/sessions/550e8400-e29b-41d4-a716-446655440000");
    assertEquals("get_session_details", sessionDetailsToolName);
  }

  @Test
  void testAutorouteRequestSnakeCaseAndCamelCaseDeserialization_FR055() {
    String snakeJson =
        """
        {
          "file_content": "(pcb test ...)",
          "rules_content": "(rules ...)",
          "timeout_seconds": 120
        }
        """;
    AutorouteRequest req1 = GsonProvider.GSON.fromJson(snakeJson, AutorouteRequest.class);
    assertNotNull(req1);
    assertEquals("(pcb test ...)", req1.fileContent);
    assertEquals("(rules ...)", req1.rulesContent);
    assertEquals(120, req1.timeoutSeconds);

    String camelJson =
        """
        {
          "fileContent": "(pcb test camel ...)",
          "rulesContent": "(rules camel ...)",
          "timeoutSeconds": 60
        }
        """;
    AutorouteRequest req2 = GsonProvider.GSON.fromJson(camelJson, AutorouteRequest.class);
    assertNotNull(req2);
    assertEquals("(pcb test camel ...)", req2.fileContent);
    assertEquals("(rules camel ...)", req2.rulesContent);
    assertEquals(60, req2.timeoutSeconds);
  }

  @Test
  void testRoutingJobListenerUnregistration_FR056() {
    RoutingJob job = new RoutingJob(UUID.randomUUID());
    AtomicInteger eventCount = new AtomicInteger(0);
    RoutingJobLogEntryAddedEventListener listener = event -> eventCount.incrementAndGet();

    job.addLogEntryAddedEventListener(listener);
    job.logInfo("test message");
    assertEquals(1, eventCount.get());

    job.removeLogEntryAddedEventListener(listener);
    job.logInfo("second message");
    assertEquals(1, eventCount.get());
  }
}
