package app.freerouting.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.Freerouting;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.Session;
import app.freerouting.management.jobs.RoutingJobScheduler;
import app.freerouting.management.sessions.SessionManager;
import app.freerouting.settings.GlobalSettings;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobInputResourceTest {

  private JobInputResource resource;
  private UUID userId;
  private String sessionId;
  private String jobId;

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
    Freerouting.globalSettings.apiServerSettings.authentication.isEnabled = false;

    userId = UUID.randomUUID();
    Session session = SessionManager.getInstance().createSession(userId, "test/1.0");
    sessionId = session.id.toString();

    RoutingJob job = new RoutingJob(session.id);
    RoutingJobScheduler.getInstance().enqueueJob(job);
    jobId = job.id.toString();

    resource = new JobInputResource();
    resource.setUserIdOverride(userId);
  }

  @Test
  void uploadInputRejectsMalformedBase64() {
    String malformedPayload = "{\"data\":\"%%%not-valid-base64%%%\"}";
    Response response = resource.uploadInput(jobId, malformedPayload);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
  }

  @Test
  void uploadInputJsonRejectsEmptyPayload() {
    Response response = resource.uploadInputJson(jobId, "");
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
  }
}
