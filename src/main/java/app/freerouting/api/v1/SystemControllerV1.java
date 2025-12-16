package app.freerouting.api.v1;

import app.freerouting.Freerouting;
import app.freerouting.api.dto.SystemStatus;
import app.freerouting.management.SessionManager;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.management.gson.GsonProvider;
import app.freerouting.settings.GlobalSettings;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

@Path("/v1/system")
public class SystemControllerV1
{
  public static double getCpuLoad()
  {
    OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    if (osBean instanceof com.sun.management.OperatingSystemMXBean bean)
    {
      return bean.getSystemCpuLoad() * 100;
    }
    return -1;
  }

  @GET
  @Path("/status")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getStatus()
  {
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

  @GET
  @Path("/environment")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getEnvironment()
  {
    // Serialize the collected environment information to JSON and return it
    var response = GsonProvider.GSON.toJson(Freerouting.globalSettings.environmentSettings);
    FRAnalytics.apiEndpointCalled("GET v1/system/environment", "", response);
    return Response.ok(response).build();
  }


}
