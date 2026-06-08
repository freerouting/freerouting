package app.freerouting.api.mcp;

import app.freerouting.Freerouting;
import app.freerouting.constants.Constants;
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

    String wsBase = base.startsWith("https://")
        ? "wss://" + base.substring("https://".length())
        : (base.startsWith("http://") ? "ws://" + base.substring("http://".length()) : base);

    JsonObject card = new JsonObject();
    card.addProperty("name", "Freerouting MCP");
    card.addProperty("description", "MCP server for Freerouting routing API tools");
    card.addProperty("version", Constants.FREEROUTING_VERSION);
    card.addProperty("agentCardVersion", "1.0");

    JsonArray endpoints = new JsonArray();
    endpoints.add(base + "/v1/mcp");
    card.add("endpoints", endpoints);

    JsonObject endpointMap = new JsonObject();
    endpointMap.addProperty("rpc", base + "/v1/mcp");
    endpointMap.addProperty("sse", base + "/v1/mcp/events");
    endpointMap.addProperty("websocket", wsBase + "/v1/mcp/ws");
    card.add("endpointMap", endpointMap);

    JsonObject protocols = new JsonObject();
    JsonObject mcp = new JsonObject();
    mcp.addProperty("version", "2025-03-26");
    JsonArray transports = new JsonArray();
    transports.add("http-jsonrpc");
    transports.add("sse");
    transports.add("websocket");
    mcp.add("transports", transports);
    protocols.add("mcp", mcp);
    JsonObject a2a = new JsonObject();
    a2a.addProperty("version", "1.0");
    protocols.add("a2a", a2a);
    card.add("protocols", protocols);

    JsonObject auth = new JsonObject();
    JsonArray schemes = new JsonArray();
    boolean authEnabled = Freerouting.globalSettings != null
        && Freerouting.globalSettings.mcpServerSettings != null
        && Freerouting.globalSettings.mcpServerSettings.authentication != null
        && Boolean.TRUE.equals(Freerouting.globalSettings.mcpServerSettings.authentication.isEnabled);
    JsonObject primaryScheme = new JsonObject();
    primaryScheme.addProperty("type", authEnabled ? "bearer" : "none");
    primaryScheme.addProperty("header", authEnabled ? "Authorization" : "n/a");
    primaryScheme.addProperty("description", authEnabled
        ? "Bearer API key sent in Authorization header"
        : "Authentication disabled for MCP server");
    schemes.add(primaryScheme);
    auth.add("schemes", schemes);
    JsonArray requiredHeaders = new JsonArray();
    requiredHeaders.add("Freerouting-Profile-ID");
    requiredHeaders.add("Freerouting-Environment-Host");
    auth.add("requiredHeaders", requiredHeaders);
    card.add("auth", auth);

    JsonObject contact = new JsonObject();
    contact.addProperty("name", "Freerouting Team");
    contact.addProperty("email", "support@freerouting.app");
    contact.addProperty("url", "https://github.com/freerouting/freerouting");
    card.add("contact", contact);

    JsonObject documentation = new JsonObject();
    documentation.addProperty("mcpSetup",
        "https://github.com/freerouting/freerouting/blob/master/docs/API/MCP_setup.md");
    documentation.addProperty("apiV1",
        "https://github.com/freerouting/freerouting/blob/master/docs/API/API_v1.md");
    documentation.addProperty("openApi", base + "/openapi/openapi.json");
    card.add("documentation", documentation);

    JsonObject tools = new JsonObject();
    tools.addProperty("source", "openapi");
    tools.addProperty("coverage", "all /v1/* routes except /v1/mcp*");
    JsonArray categories = new JsonArray();
    categories.add("system");
    categories.add("sessions");
    categories.add("jobs");
    categories.add("analytics");
    tools.add("categories", categories);
    card.add("tools", tools);

    JsonObject capabilities = new JsonObject();
    capabilities.addProperty("tools", true);
    capabilities.addProperty("sse", true);
    capabilities.addProperty("websocket", true);
    capabilities.addProperty("jsonrpc", true);
    capabilities.addProperty("streamingToolCalls", false);
    card.add("capabilities", capabilities);

    return Response.ok(card.toString()).type(MediaType.APPLICATION_JSON).build();
  }
}