package app.freerouting.api.dev;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/dev/system")
@Tag(name = "Dev - System", description = "Mock system endpoints for testing and development. Returns static test data.")
public class SystemControllerMocked {

  public SystemControllerMocked() {
  }

  @Operation(summary = "Get system status (mock)", description = "Returns mock system status data for testing purposes. This endpoint always returns the same static data.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Mock system status retrieved successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON))
  })
  @GET
  @Path("/status")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getStatus() {
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

  @Operation(summary = "Get environment information (mock)", description = "Returns mock environment information for testing purposes. This endpoint always returns the same static data.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Mock environment information retrieved successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON))
  })
  @GET
  @Path("/environment")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getEnvironment() {
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