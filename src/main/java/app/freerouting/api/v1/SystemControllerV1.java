package app.freerouting.api.v1;

import app.freerouting.Freerouting;
import app.freerouting.api.dto.SystemStatus;
import app.freerouting.management.SessionManager;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.management.gson.GsonProvider;
import app.freerouting.settings.GlobalSettings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

/**
 * JAX-RS controller for system-level monitoring and environment information endpoints.
 *
 * <p>This controller exposes two unauthenticated, publicly accessible endpoints:
 * <ul>
 *   <li>{@code GET /v1/system/status} — returns current CPU, RAM, storage and session-count
 *       metrics as a {@link app.freerouting.api.dto.SystemStatus} JSON object.</li>
 *   <li>{@code GET /v1/system/environment} — returns the deserialized
 *       {@code Freerouting.globalSettings.runtimeEnvironment} for diagnostics.</li>
 * </ul>
 *
 * <p>Neither endpoint requires an {@code Authorization} header or a
 * {@code Freerouting-Environment-Host} header; both are excluded from API-key validation
 * and environment-host validation filters.</p>
 */
@Path("/v1/system")
@Tag(name = "System", description = "System monitoring and environment information endpoints")
public class SystemControllerV1 {

  /**
   * Returns the current system-wide CPU load as a percentage in the range {@code [0, 100]}.
   *
   * <p>Uses {@link com.sun.management.OperatingSystemMXBean#getCpuLoad()} when the
   * {@code jdk.management} module is available. Falls back to {@code -1} on minimal JRE
   * builds where the module is absent, or if the JVM cannot determine CPU load.</p>
   *
   * @return a {@code double} in the range {@code [0, 100]} representing CPU utilisation,
   *         or {@code -1} if the value cannot be determined.
   */
  public static double getCpuLoad() {
    try {
      OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
      if (osBean instanceof com.sun.management.OperatingSystemMXBean bean) {
        return bean.getCpuLoad() * 100;
      }
    } catch (Throwable t) {
      // java.management or jdk.management module may not be available in minimal JRE builds
    }
    return -1;
  }

  @Operation(summary = "Get system status", description = "Retrieves comprehensive system status including CPU load, memory usage, storage availability, and active session count.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "System status retrieved successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SystemStatus.class), examples = @ExampleObject(name = "System Status Example", value = """
          {
            "status": "OK",
            "cpu_load": 45.5,
            "ram_used": 512,
            "ram_available": 1024,
            "storage_available": 10240,
            "session_count": 3
          }
          """)))
  })
  @GET
  @Path("/status")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getStatus() {
    Runtime runtime = Runtime.getRuntime();

    SystemStatus status = new SystemStatus();
    status.status = "OK";
    status.cpuLoad = getCpuLoad();
    status.ramUsed = (int) (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
    status.ramAvailable = (int) runtime.freeMemory() / 1024 / 1024;
    status.storageAvailable = (int) GlobalSettings.getUserDataPath().toFile().getFreeSpace() / 1024 / 1024;
    status.sessionCount = SessionManager.getInstance().getActiveSessionsCount();

    var response = GsonProvider.GSON.toJson(status);
    FRAnalytics.apiEndpointCalled("GET v1/system/status", "", response);
    return Response.ok(response).build();
  }

  @Operation(summary = "Get environment information", description = "Returns environment configuration and settings for the Freerouting application.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Environment information retrieved successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON))
  })
  @GET
  @Path("/environment")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getEnvironment() {
    // Serialize the collected environment information to JSON and return it
    var response = GsonProvider.GSON.toJson(Freerouting.globalSettings.runtimeEnvironment);
    FRAnalytics.apiEndpointCalled("GET v1/system/environment", "", response);
    return Response.ok(response).build();
  }

}
