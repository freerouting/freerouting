package app.freerouting.api.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Context;

/**
 * Public A2A agent-card endpoint for MCP discovery.
 */
@Path("/.well-known")
@Tag(name = "MCP", description = "Model Context Protocol endpoints for LLM tool integration")
public class AgentCardController {

  /**
   * Returns the A2A agent card for Freerouting MCP.
   */
  @GET
  @Path("/agent.json")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Get A2A agent card", description = "Returns the MCP agent metadata at /.well-known/agent.json.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Agent card returned", content = @Content(mediaType = MediaType.APPLICATION_JSON))
  })
  public Response getAgentCard(@Context UriInfo uriInfo) {
    String base = uriInfo.getBaseUri().toString();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }

    JsonObject card = new JsonObject();
    card.addProperty("name", "Freerouting MCP");
    card.addProperty("description", "MCP server for Freerouting routing API tools");
    card.addProperty("version", "2.3");

    JsonArray endpoints = new JsonArray();
    endpoints.add(base + "/v1/mcp");
    card.add("endpoints", endpoints);

    JsonObject capabilities = new JsonObject();
    capabilities.addProperty("tools", true);
    capabilities.addProperty("sse", true);
    capabilities.addProperty("websocket", true);
    card.add("capabilities", capabilities);

    return Response.ok(card.toString()).type(MediaType.APPLICATION_JSON).build();
  }
}