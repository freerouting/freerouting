package app.freerouting.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.api.dto.AutorouteRequest;
import app.freerouting.core.RoutingJob;
import app.freerouting.fixtures.RoutingFixtureTest;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.RouterSettings;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutorouteControllerV1Test extends RoutingFixtureTest {

  private AutorouteControllerV1 controller;

  @BeforeEach
  protected void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
    Freerouting.globalSettings.apiServerSettings.authentication.isEnabled = false;
    controller = new AutorouteControllerV1();
    controller.setUserIdOverride(UUID.randomUUID());
  }

  @Test
  void testAutorouteRejectsMissingPayload() {
    Response response = controller.autoroute(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
  }

  @Test
  void testAutorouteRejectsMissingFileContent() {
    AutorouteRequest req = new AutorouteRequest();
    Response response = controller.autoroute(req);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
  }

  @Test
  void testAutorouteExecutesSuccessfully() throws Exception {
    RoutingJob sampleJob = getRoutingJob("Issue508-DAC2020_bm01.dsn");
    assertNotNull(sampleJob);
    byte[] dsnBytes = sampleJob.input.getData().readAllBytes();
    String dsnContent = new String(dsnBytes, StandardCharsets.UTF_8);

    AutorouteRequest req = new AutorouteRequest();
    req.fileContent = dsnContent;
    req.outputFormats = List.of("SES", "DRC_SUMMARY");
    req.timeoutSeconds = 60;
    req.routerSettings = new RouterSettings();
    req.routerSettings.maxPasses = 1;
    req.routerSettings.maxItems = 10;

    Response response = controller.autoroute(req);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertNotNull(response.getEntity());
    String json = response.getEntity().toString();
    assertTrue(json.contains("\"job_id\"") || json.contains("\"jobId\""));
    assertTrue(json.contains("\"outputs\""));
  }
}
