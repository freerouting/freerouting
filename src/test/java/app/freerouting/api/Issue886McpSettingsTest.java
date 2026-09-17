package app.freerouting.api;

import static app.freerouting.util.gson.GsonProvider.GSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.api.mcp.OpenApiMcpToolRegistry;
import app.freerouting.api.v1.JobInputResource;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.Session;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.management.jobs.RoutingJobScheduler;
import app.freerouting.management.sessions.SessionManager;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.RouterSettings;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression and contract tests for Issue #886: 1. MCP registry exposes 'get_effective_settings'
 * mapped to GET /v1/jobs/{jobId}/settings. 2. GET /v1/jobs/{jobId}/settings returns resolved
 * RouterSettings including per-layer settings and validation warnings. 3. RouterSettings validates
 * against board, reporting unknown net classes and clamped parameters. 4. GSON serialization
 * properly includes transient layers and validation warnings.
 */
class Issue886McpSettingsTest {

  private JobInputResource resource;
  private UUID userId;
  private String sessionId;
  private String jobId;
  private RoutingJob job;
  private RoutingBoard board;

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
    Freerouting.globalSettings.apiServerSettings.authentication.isEnabled = false;

    userId = UUID.randomUUID();
    Session session = SessionManager.getInstance().createSession(userId, "test/1.0");
    sessionId = session.id.toString();

    job = new RoutingJob(session.id);
    RoutingJobScheduler.getInstance().enqueueJob(job);
    jobId = job.id.toString();

    resource = new JobInputResource();
    resource.setUserIdOverride(userId);

    Layer layer1 = new Layer("Top", true);
    Layer layer2 = new Layer("Bottom", true);
    LayerStructure layerStructure = new LayerStructure(new Layer[] {layer1, layer2});
    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.createDefaultNetClass();
    board =
        new RoutingBoard(
            new IntBox(0, 0, 2_000_000, 1_000_000),
            layerStructure,
            new PolylineShape[] {TileShape.getInstance(0, 0, 2_000_000, 1_000_000)},
            0,
            boardRules,
            new Communication());
  }

  @Test
  void mcpRegistryExposesGetEffectiveSettings() throws Exception {
    OpenApiMcpToolRegistry registry =
        OpenApiMcpToolRegistry.fromApplication(new FreeroutingApplication());
    OpenApiMcpToolRegistry.ToolOperation tool = registry.get("get_effective_settings");

    assertNotNull(tool, "Expected 'get_effective_settings' MCP tool to be registered");
    assertEquals("GET", tool.method());
    assertTrue(tool.path().endsWith("/settings"));
  }

  @Test
  void getEffectiveSettingsReturnsSettingsWithLayersAndValidationWarnings() {
    RouterSettings settings = new RouterSettings();
    settings.setLayerCount(2);
    settings.layers[0].preferredDirectionTraceCost = 0.05; // below 0.1, will be clamped
    settings.layers[1].bendCost = 99.0; // above MAX_BEND_COST (10.0), will be clamped
    job.routerSettings = settings;

    Response response = resource.getEffectiveSettings(jobId);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

    RouterSettings responseSettings =
        GSON.fromJson(response.getEntity().toString(), RouterSettings.class);
    assertNotNull(responseSettings.layers);
    assertEquals(2, responseSettings.layers.length);
    assertEquals(0.1, responseSettings.layers[0].preferredDirectionTraceCost, 1e-6);
    assertEquals(RouterSettings.MAX_BEND_COST, responseSettings.layers[1].bendCost, 1e-6);

    assertNotNull(responseSettings.validationWarnings);
    assertTrue(
        responseSettings.validationWarnings.stream()
            .anyMatch(w -> w.contains("preferredDirectionTraceCost")));
    assertTrue(responseSettings.validationWarnings.stream().anyMatch(w -> w.contains("bendCost")));
  }

  @Test
  void validationAgainstBoardReportsUnknownNetClass() {
    RouterSettings settings = new RouterSettings();
    settings.setLayerCount(2);
    settings.autorouter.ignoreNetClasses = new String[] {"NON_EXISTENT_CLASS"};

    settings.validateAgainstBoard(board);

    assertNotNull(settings.validationWarnings);
    assertTrue(
        settings.validationWarnings.stream()
            .anyMatch(
                w ->
                    w.contains("NON_EXISTENT_CLASS")
                        && w.contains("specified in ignoreNetClasses")));
  }

  @Test
  void routerSettingsSerializationIncludesLayersAndWarnings() {
    RouterSettings settings = new RouterSettings();
    settings.setLayerCount(2);
    settings.layers[0].preferredDirectionTraceCost = 0.75;
    settings.layers[0].undesiredDirectionTraceCost = 1.25;
    settings.validationWarnings = List.of("Warning: test warning");

    String json = GSON.toJson(settings);
    assertTrue(json.contains("\"layers\""), "JSON should contain serialized layers");
    assertTrue(json.contains("\"validation_warnings\""), "JSON should contain validation_warnings");
    assertTrue(json.contains("0.75"), "JSON should contain preferred trace cost");
    assertTrue(json.contains("1.25"), "JSON should contain undesired trace cost");
    assertTrue(json.contains("Warning: test warning"));
  }
}
