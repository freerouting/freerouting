package app.freerouting.api.v1;

import app.freerouting.api.BaseController;
import app.freerouting.api.CorrelationIdFilter;
import app.freerouting.api.mcp.McpRealtimeBridge;
import app.freerouting.api.mcp.OpenApiMcpToolRegistry;
import app.freerouting.Freerouting;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.management.gson.GsonProvider;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * MCP endpoint for LLM clients. Uses JSON-RPC 2.0 over HTTP and exposes tools derived from OpenAPI.
 */
@Path("/v1/mcp")
@Tag(name = "MCP", description = "Model Context Protocol endpoints for LLM tool integration")
public class McpControllerV1 extends BaseController {

  private static final String JSONRPC_VERSION = "2.0";

  @Context
  private Application application;

  @Context
  private HttpHeaders headers;

  @Operation(summary = "MCP JSON-RPC endpoint", description = "Accepts MCP-compatible JSON-RPC requests including initialize, tools/list and tools/call.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "MCP request processed", content = @Content(mediaType = MediaType.APPLICATION_JSON)),
      @ApiResponse(responseCode = "500", description = "MCP execution failed")
  })
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response rpc(String requestBody) {
    String correlationId = CorrelationIdFilter.resolveOrCreate(
        headers.getHeaderString(CorrelationIdFilter.HEADER_NAME));

    JsonObject request;
    try {
      request = JsonParser.parseString(requestBody).getAsJsonObject();
    } catch (Exception e) {
      return Response.ok(error(null, -32700, "Invalid JSON"))
          .header(CorrelationIdFilter.HEADER_NAME, correlationId)
          .build();
    }

    JsonElement id = request.get("id");

    UUID userId;
    try {
      userId = AuthenticateUser();
    } catch (Exception e) {
      return Response.ok(error(id, -32602, "Authentication failed"))
          .header(CorrelationIdFilter.HEADER_NAME, correlationId)
          .build();
    }

    String method = request.has("method") ? request.get("method").getAsString() : null;
    JsonObject params = request.has("params") && request.get("params").isJsonObject()
        ? request.getAsJsonObject("params")
        : new JsonObject();

    JsonObject response;
    try {
      FRLogger.info("[mcp][cid=" + correlationId + "] method=" + method);
      response = switch (method == null ? "" : method) {
        case "initialize" -> handleInitialize(id);
        case "tools/list" -> handleToolsList(id);
        case "tools/call" -> handleToolsCall(id, params, correlationId);
        default -> error(id, -32601, "Unknown method: " + method);
      };
    } catch (Exception e) {
      FRLogger.error("MCP RPC execution failed", e);
      response = error(id, -32603, "Internal error");
    }

    FRAnalytics.apiEndpointCalled("POST v1/mcp", requestBody, response.toString(), userId);
    return Response.ok(response.toString())
        .header(CorrelationIdFilter.HEADER_NAME, correlationId)
        .build();
  }

  @Operation(summary = "MCP event stream (SSE)", description = "Streams MCP activity notifications to clients.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "SSE stream established", content = @Content(mediaType = MediaType.SERVER_SENT_EVENTS))
  })
  @GET
  @Path("/events")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  public void events(@Context SseEventSink sink, @Context Sse sse) {
    AuthenticateUser();

    McpRealtimeBridge.registerSseClient(sink, sse);
    JsonObject hello = new JsonObject();
    hello.addProperty("message", "MCP SSE stream connected");
    McpRealtimeBridge.broadcast("mcp.sse.connected", hello);
  }

  private JsonObject handleInitialize(JsonElement id) {
    JsonObject capabilities = new JsonObject();
    capabilities.add("tools", new JsonObject());

    JsonObject result = new JsonObject();
    result.addProperty("protocolVersion", "2025-03-26");
    result.addProperty("serverName", "Freerouting MCP");
    result.addProperty("serverVersion", "v2.3");
    result.add("capabilities", capabilities);

    return success(id, result);
  }

  private JsonObject handleToolsList(JsonElement id) throws Exception {
    OpenApiMcpToolRegistry registry = OpenApiMcpToolRegistry.fromApplication(application);

    JsonObject result = new JsonObject();
    result.add("tools", registry.toMcpToolsArray());

    return success(id, result);
  }

  private JsonObject handleToolsCall(JsonElement id, JsonObject params, String correlationId)
      throws Exception {
    if (!params.has("name") || params.get("name").isJsonNull()) {
      return error(id, -32602, "Missing required parameter: name");
    }

    String toolName = params.get("name").getAsString();
    JsonObject arguments = params.has("arguments") && params.get("arguments").isJsonObject()
        ? params.getAsJsonObject("arguments")
        : new JsonObject();

    OpenApiMcpToolRegistry registry = OpenApiMcpToolRegistry.fromApplication(application);
    OpenApiMcpToolRegistry.ToolOperation tool = registry.get(toolName);

    if (tool == null) {
      return error(id, -32601, "Unknown tool: " + toolName);
    }

    HttpResponse<String> response;
    try {
      response = invokeTool(tool, arguments, correlationId);
    } catch (IllegalArgumentException ex) {
      return error(id, -32602, ex.getMessage());
    }

    JsonObject payload = new JsonObject();
    payload.addProperty("status", response.statusCode());
    payload.addProperty("contentType", response.headers().firstValue("Content-Type").orElse("application/json"));

    String body = response.body() == null ? "" : response.body();
    payload.add("body", tryParseJson(body));

    JsonObject eventPayload = new JsonObject();
    eventPayload.addProperty("tool", tool.toolName());
    eventPayload.addProperty("status", response.statusCode());
    McpRealtimeBridge.broadcast("mcp.tool.called", eventPayload);

    JsonObject result = new JsonObject();
    JsonArray content = new JsonArray();
    JsonObject text = new JsonObject();
    text.addProperty("type", "text");
    text.addProperty("text", GsonProvider.GSON.toJson(payload));
    content.add(text);
    result.add("content", content);
    result.addProperty("isError", response.statusCode() >= 400);

    return success(id, result);
  }

  private HttpResponse<String> invokeTool(
      OpenApiMcpToolRegistry.ToolOperation tool,
      JsonObject arguments,
      String correlationId)
      throws IOException, InterruptedException {
    String resolvedPath = resolvePath(tool.path(), getObject(arguments, "path"));
    URI uri = buildUriWithQuery(resolvedPath, getObject(arguments, "query"));

    HttpRequest.Builder builder = HttpRequest.newBuilder(uri);
    forwardHeaders(builder, correlationId);

    JsonElement bodyElement = arguments.get("body");
    String method = tool.method();
    if (requiresBody(method)) {
      String body = bodyElement == null || bodyElement.isJsonNull() ? "{}" : GsonProvider.GSON.toJson(bodyElement);
      builder.header("Content-Type", MediaType.APPLICATION_JSON);
      builder.method(method, HttpRequest.BodyPublishers.ofString(body));
    } else {
      builder.method(method, HttpRequest.BodyPublishers.noBody());
    }

    return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private void forwardHeaders(HttpRequest.Builder builder, String correlationId) {
    // Forward only identity/auth headers required by the REST API contract.
    copyHeader("Authorization", builder);
    copyHeader("Freerouting-Profile-ID", builder);
    copyHeader("Freerouting-Profile-Email", builder);
    copyHeader("Freerouting-Environment-Host", builder);
    builder.header(CorrelationIdFilter.HEADER_NAME, correlationId);
  }

  private void copyHeader(String name, HttpRequest.Builder builder) {
    String value = headers.getHeaderString(name);
    if (value != null && !value.isBlank()) {
      builder.header(name, value);
    }
  }

  private URI buildUriWithQuery(String path, JsonObject query) {
    String baseUrl = Freerouting.globalSettings != null
        && Freerouting.globalSettings.mcpServerSettings != null
        && Freerouting.globalSettings.mcpServerSettings.targetApiBaseUrl != null
        && !Freerouting.globalSettings.mcpServerSettings.targetApiBaseUrl.isBlank()
        ? Freerouting.globalSettings.mcpServerSettings.targetApiBaseUrl
        : "http://127.0.0.1:37864";

    URI targetBaseUri;
    try {
      targetBaseUri = URI.create(baseUrl);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid mcp_server.target_api_base_url: " + baseUrl);
    }

    String scheme = targetBaseUri.getScheme() == null ? "" : targetBaseUri.getScheme().toLowerCase(Locale.ROOT);
    if (!"http".equals(scheme) && !"https".equals(scheme)) {
      throw new IllegalArgumentException("mcp_server.target_api_base_url must use http or https.");
    }

    String basePath = targetBaseUri.getPath() == null ? "" : targetBaseUri.getPath();
    if (basePath.startsWith("/v1/mcp") || basePath.contains("/.well-known")) {
      throw new IllegalArgumentException(
          "mcp_server.target_api_base_url points to MCP endpoints; it must point to the REST API base URL.");
    }

    UriBuilder builder = UriBuilder.fromUri(targetBaseUri).path(path.startsWith("/") ? path.substring(1) : path);
    for (Map.Entry<String, JsonElement> entry : query.entrySet()) {
      if (entry.getValue() == null || entry.getValue().isJsonNull()) {
        continue;
      }
      builder.queryParam(entry.getKey(), entry.getValue().getAsString());
    }
    URI result = builder.build();

    // Guard: ensure the final URI still targets the same host/port as the configured base URL.
    if (!targetBaseUri.getHost().equals(result.getHost())
        || targetBaseUri.getPort() != result.getPort()) {
      throw new IllegalArgumentException(
          "Resolved tool URI target does not match the configured mcp_server.target_api_base_url.");
    }

    return result;
  }

  private static String resolvePath(String rawPath, JsonObject pathArgs) {
    String resolved = rawPath;
    for (Map.Entry<String, JsonElement> entry : pathArgs.entrySet()) {
      String placeholder = "{" + entry.getKey() + "}";
      String encoded = URLEncoder.encode(entry.getValue().getAsString(), StandardCharsets.UTF_8)
          .replace("+", "%20");
      resolved = resolved.replace(placeholder, encoded);
    }
    return resolved;
  }

  private static boolean requiresBody(String method) {
    return "POST".equalsIgnoreCase(method)
        || "PUT".equalsIgnoreCase(method)
        || "PATCH".equalsIgnoreCase(method);
  }

  private static JsonObject getObject(JsonObject parent, String key) {
    if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) {
      return new JsonObject();
    }
    return parent.getAsJsonObject(key);
  }

  private static JsonElement tryParseJson(String body) {
    try {
      return JsonParser.parseString(body);
    } catch (Exception e) {
      JsonObject wrapped = new JsonObject();
      wrapped.addProperty("text", body);
      return wrapped;
    }
  }

  private static JsonObject success(JsonElement id, JsonObject result) {
    JsonObject response = new JsonObject();
    response.addProperty("jsonrpc", JSONRPC_VERSION);
    response.add("id", id == null ? nullId() : id);
    response.add("result", result);
    return response;
  }

  private static JsonObject error(JsonElement id, int code, String message) {
    JsonObject response = new JsonObject();
    response.addProperty("jsonrpc", JSONRPC_VERSION);
    response.add("id", id == null ? nullId() : id);

    JsonObject err = new JsonObject();
    err.addProperty("code", code);
    err.addProperty("message", message);

    response.add("error", err);
    return response;
  }

  private static JsonElement nullId() {
    return JsonParser.parseString("null");
  }
}