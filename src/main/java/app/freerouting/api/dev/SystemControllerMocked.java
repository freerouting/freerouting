package app.freerouting.api.dev;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/dev/system")
public class SystemControllerMocked
{
  public SystemControllerMocked()
  {
  }

  @GET
  @Path("/status")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getStatus()
  {
    return Response.ok("""
                       {
                           "status": "OK",
                           "cpu_load": 3.954818264180926,
                           "ram_used": 86,
                           "ram_available": 73,
                           "storage_available": 481,
                           "session_count": 1
                       }
                       """).build();
  }

  @GET
  @Path("/environment")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getEnvironment()
  {
    return Response.ok("""
                       {
                           "freerouting_version": "2.0.0,2024-10-14",
                           "app_started_at": "2024-10-14T10:56:26.730145900Z",
                           "command_line_arguments": "",
                           "architecture": "Windows 11,amd64,10.0",
                           "java": "21.0.2,Eclipse Adoptium",
                           "system_language": "en,en_US",
                           "cpu_cores": 12,
                           "ram": 8176
                       }
                       """).build();

  }
}