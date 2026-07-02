package app.freerouting.api.v1;

import app.freerouting.api.BaseController;
import app.freerouting.api.CorrelationIdFilter;
import app.freerouting.api.mcp.McpRealtimeBridge;
import app.freerouting.api.mcp.OpenApiMcpToolRegistry;
import app.freerouting.Freerouting;
import app.freerouting.constants.Constants;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.util.gson.GsonProvider;
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
  private static volatile String detectedClientInfo = "MCP-Client/1.0";

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

    FRLogger.info("[mcp][cid=" + correlationId + "] request=" + requestBody);

    JsonObject request;
    try {
      request = JsonParser.parseString(requestBody).getAsJsonObject();
    } catch (Exception e) {
      FRLogger.warn("[mcp][cid=" + correlationId + "] Failed to parse JSON-RPC request: " + e.getMessage());
      return Response.ok(error(null, -32700, "Invalid JSON: " + e.getMessage()))
          .header(CorrelationIdFilter.HEADER_NAME, correlationId)
          .build();
    }

    JsonElement id = request.get("id");
    boolean isNotification = (id == null || id.isJsonNull());
    String method = request.has("method") ? request.get("method").getAsString() : null;
    JsonObject params = request.has("params") && request.get("params").isJsonObject()
        ? request.getAsJsonObject("params")
        : new JsonObject();

    UUID userId;
    try {
      userId = AuthenticateUser();
    } catch (Exception e) {
      FRLogger.warn("[mcp][cid=" + correlationId + "] Authentication failed for method '" + method + "': " + e.getMessage());
      if (isNotification) {
        return Response.noContent()
            .header(CorrelationIdFilter.HEADER_NAME, correlationId)
            .build();
      }
      JsonObject errResponse = error(id, -32602, "Authentication failed: " + e.getMessage());
      return Response.ok(errResponse.toString())
          .header(CorrelationIdFilter.HEADER_NAME, correlationId)
          .build();
    }

    JsonObject response;
    try {
      FRLogger.info("[mcp][cid=" + correlationId + "] method=" + method);
      response = switch (method == null ? "" : method) {
        case "initialize" -> handleInitialize(id, params);
        case "notifications/initialized" -> {
          // MCP lifecycle notification: connection fully established.
          yield success(id, new JsonObject());
        }
        case "tools/list" -> handleToolsList(id);
        case "tools/call" -> handleToolsCall(id, params, correlationId);
        default -> error(id, -32601, "Unknown method: " + method);
      };
      FRLogger.info("[mcp][cid=" + correlationId + "] response=" + response.toString());
    } catch (Exception e) {
      FRLogger.error("MCP RPC execution failed", e);
      response = error(id, -32603, "Internal error");
      FRLogger.info("[mcp][cid=" + correlationId + "] response (error)=" + response.toString());
    }

    FRAnalytics.apiEndpointCalled("POST v1/mcp", requestBody, response.toString(), userId);
    
    if (isNotification) {
      return Response.noContent()
          .header(CorrelationIdFilter.HEADER_NAME, correlationId)
          .build();
    }

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

  private JsonObject handleInitialize(JsonElement id, JsonObject params) {
    if (params != null && params.has("clientInfo")) {
      try {
        JsonObject clientInfo = params.getAsJsonObject("clientInfo");
        String name = clientInfo.has("name") ? clientInfo.get("name").getAsString() : "Unknown";
        String version = clientInfo.has("version") ? clientInfo.get("version").getAsString() : "1.0";
        detectedClientInfo = name + "/" + version;
      } catch (Exception e) {
        FRLogger.warn("Failed to parse clientInfo from initialize params: " + e.getMessage());
      }
    }

    JsonObject capabilities = new JsonObject();
    capabilities.add("tools", new JsonObject());

    JsonObject serverInfo = new JsonObject();
    serverInfo.addProperty("name", "Freerouting MCP");
    serverInfo.addProperty("version", Constants.FREEROUTING_VERSION);

    JsonObject result = new JsonObject();
    result.addProperty("protocolVersion", "2024-11-05");
    result.addProperty("serverName", "Freerouting MCP");
    result.addProperty("serverVersion", Constants.FREEROUTING_VERSION);
    result.add("serverInfo", serverInfo);
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

    if ("custom".equals(tool.method())) {
      return handleCustomToolCall(id, toolName, arguments, correlationId);
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
    copyHeaderOrEnvFallback("Freerouting-Profile-ID", "FREEROUTING_PROFILE_ID", "FREEROUTING__PROFILE__ID", builder);
    copyHeaderOrEnvFallback("Freerouting-Profile-Email", "FREEROUTING_PROFILE_EMAIL", "FREEROUTING__PROFILE__EMAIL", builder);

    // Resolve Freerouting-Environment-Host dynamically
    String envHost = headers.getHeaderString("Freerouting-Environment-Host");
    if (envHost == null || envHost.isBlank()) {
      envHost = System.getenv("FREEROUTING_ENVIRONMENT_HOST");
    }
    if (envHost == null || envHost.isBlank()) {
      envHost = System.getenv("FREEROUTING__ENVIRONMENT__HOST");
    }
    if (envHost == null || envHost.isBlank()) {
      envHost = McpControllerV1.detectedClientInfo;
    }
    if (envHost == null || envHost.isBlank()) {
      envHost = "MCP-Client/1.0";
    }
    builder.header("Freerouting-Environment-Host", envHost);

    builder.header(CorrelationIdFilter.HEADER_NAME, correlationId);
  }

  private void copyHeader(String name, HttpRequest.Builder builder) {
    String value = headers.getHeaderString(name);
    if (value != null && !value.isBlank()) {
      builder.header(name, value);
    }
  }

  private void copyHeaderOrEnvFallback(String headerName, String envVarNameSingle, String envVarNameDouble, HttpRequest.Builder builder) {
    String value = headers.getHeaderString(headerName);
    if (value == null || value.isBlank()) {
      value = System.getenv(envVarNameSingle);
    }
    if (value == null || value.isBlank()) {
      value = System.getenv(envVarNameDouble);
    }
    if (value != null && !value.isBlank()) {
      builder.header(headerName, value);
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

  private JsonObject handleCustomToolCall(JsonElement id, String toolName, JsonObject arguments, String correlationId) {
    JsonObject result = new JsonObject();
    JsonArray content = new JsonArray();
    JsonObject textObj = new JsonObject();
    textObj.addProperty("type", "text");

    JsonObject payload = new JsonObject();
    JsonObject body = new JsonObject();
    boolean isError = false;

    if ("encode_base64".equals(toolName)) {
      if (!arguments.has("text") || arguments.get("text").isJsonNull()) {
        return error(id, -32602, "Missing required parameter: text");
      }
      String text = arguments.get("text").getAsString();
      String base64 = java.util.Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
      body.addProperty("base64", base64);
      payload.addProperty("status", 200);
      payload.addProperty("contentType", "application/json");
      payload.add("body", body);
    } else if ("decode_base64".equals(toolName)) {
      if (!arguments.has("base64") || arguments.get("base64").isJsonNull()) {
        return error(id, -32602, "Missing required parameter: base64");
      }
      String base64 = arguments.get("base64").getAsString();
      try {
        byte[] decodedBytes = java.util.Base64.getDecoder().decode(base64);
        String decodedText = new String(decodedBytes, StandardCharsets.UTF_8);
        body.addProperty("text", decodedText);
        payload.addProperty("status", 200);
        payload.addProperty("contentType", "application/json");
        payload.add("body", body);
      } catch (IllegalArgumentException e) {
        return error(id, -32602, "Invalid base64 string: " + e.getMessage());
      }
    } else if ("upload_job_input_from_local_file".equals(toolName)) {
      if (!arguments.has("jobId") || arguments.get("jobId").isJsonNull()) {
        return error(id, -32602, "Missing required parameter: jobId");
      }
      if (!arguments.has("filePath") || arguments.get("filePath").isJsonNull()) {
        return error(id, -32602, "Missing required parameter: filePath");
      }
      String jobId = arguments.get("jobId").getAsString();
      String filePath = arguments.get("filePath").getAsString();

      try {
        byte[] fileBytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(filePath));
        String base64Data = java.util.Base64.getEncoder().encodeToString(fileBytes);

        URI uri = buildUriWithQuery("/v1/jobs/" + jobId + "/input", new JsonObject());
        JsonObject requestBodyObj = new JsonObject();
        requestBodyObj.addProperty("job_id", jobId);
        requestBodyObj.addProperty("data", base64Data);

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri);
        forwardHeaders(builder, correlationId);
        builder.header("Content-Type", MediaType.APPLICATION_JSON);
        builder.POST(HttpRequest.BodyPublishers.ofString(requestBodyObj.toString(), StandardCharsets.UTF_8));

        HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
        payload.addProperty("status", response.statusCode());
        payload.addProperty("contentType", "application/json");
        isError = response.statusCode() >= 400;
        if (isError) {
          payload.add("body", tryParseJson(response.body()));
        } else {
          body.addProperty("message", "Successfully uploaded input from file: " + filePath);
          payload.add("body", body);
        }
      } catch (Exception e) {
        return error(id, -32603, "Failed to upload local file input: " + e.getMessage());
      }
    } else if ("download_job_output_to_local_file".equals(toolName)) {
      if (!arguments.has("jobId") || arguments.get("jobId").isJsonNull()) {
        return error(id, -32602, "Missing required parameter: jobId");
      }
      if (!arguments.has("filePath") || arguments.get("filePath").isJsonNull()) {
        return error(id, -32602, "Missing required parameter: filePath");
      }
      String jobId = arguments.get("jobId").getAsString();
      String filePath = arguments.get("filePath").getAsString();

      try {
        URI uri = buildUriWithQuery("/v1/jobs/" + jobId + "/output", new JsonObject());
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri);
        forwardHeaders(builder, correlationId);
        builder.GET();

        HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
        payload.addProperty("status", response.statusCode());
        payload.addProperty("contentType", "application/json");
        isError = response.statusCode() >= 400;

        if (isError) {
          payload.add("body", tryParseJson(response.body()));
        } else if (response.statusCode() == 204) {
          body.addProperty("message", "Job is in progress but no output data is available yet.");
          payload.add("body", body);
        } else {
          JsonObject respObj = JsonParser.parseString(response.body()).getAsJsonObject();
          String base64Data = respObj.get("data").getAsString();
          byte[] sesBytes = java.util.Base64.getDecoder().decode(base64Data);

          java.nio.file.Path outputPath = java.nio.file.Path.of(filePath);
          if (outputPath.getParent() != null) {
            java.nio.file.Files.createDirectories(outputPath.getParent());
          }
          java.nio.file.Files.write(outputPath, sesBytes);

          body.addProperty("message", "Successfully downloaded output and saved to: " + filePath);
          payload.add("body", body);
        }
      } catch (Exception e) {
        return error(id, -32603, "Failed to download output to local file: " + e.getMessage());
      }
    } else {
      return error(id, -32601, "Unknown custom tool: " + toolName);
    }

    textObj.addProperty("text", GsonProvider.GSON.toJson(payload));
    content.add(textObj);
    result.add("content", content);
    result.addProperty("isError", isError);

    JsonObject eventPayload = new JsonObject();
    eventPayload.addProperty("tool", toolName);
    eventPayload.addProperty("status", payload.get("status").getAsInt());
    McpRealtimeBridge.broadcast("mcp.tool.called", eventPayload);

    return success(id, result);
  }

  private static JsonElement nullId() {
    return JsonParser.parseString("null");
  }
}
