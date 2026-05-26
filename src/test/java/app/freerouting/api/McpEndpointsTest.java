package app.freerouting.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.api.mcp.McpApiKeyValidationService;
import app.freerouting.api.security.ApiKeyValidationService;
import app.freerouting.settings.ApiServerSettings;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.McpServerSettings;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McpEndpointsTest {

  private static final String TEST_USER_ID = "00000000-0000-0000-0000-000000000001";

  private Server apiServer;
  private Server mcpServer;
  private URI mcpBaseUri;
  private HttpClient httpClient;

  @BeforeEach
  void setUp() throws Exception {
    ApiKeyValidationService.resetForTesting();
    McpApiKeyValidationService.resetForTesting();

    Freerouting.globalSettings = new GlobalSettings();

    ApiServerSettings apiSettings = new ApiServerSettings();
    apiSettings.isEnabled = true;
    apiSettings.isHttpAllowed = true;
    apiSettings.endpoints = new String[]{"http://127.0.0.1:0"};
    apiSettings.authentication.isEnabled = true;

    McpServerSettings mcpSettings = new McpServerSettings();
    mcpSettings.isEnabled = true;
    mcpSettings.isHttpAllowed = true;
    mcpSettings.endpoints = new String[]{"http://127.0.0.1:0"};
    mcpSettings.authentication.isEnabled = false;

    Freerouting.globalSettings.apiServerSettings.authentication.isEnabled = true;
    Freerouting.globalSettings.mcpServerSettings.authentication.isEnabled = false;

    apiServer = Freerouting.InitializeAPI(apiSettings);
    waitForServerStarted(apiServer);
    int apiPort = ((ServerConnector) apiServer.getConnectors()[0]).getLocalPort();

    mcpSettings.targetApiBaseUrl = "http://127.0.0.1:" + apiPort;
    Freerouting.globalSettings.mcpServerSettings.targetApiBaseUrl = mcpSettings.targetApiBaseUrl;

    mcpServer = Freerouting.InitializeMCP(mcpSettings);
    waitForServerStarted(mcpServer);
    int mcpPort = ((ServerConnector) mcpServer.getConnectors()[0]).getLocalPort();

    mcpBaseUri = URI.create("http://127.0.0.1:" + mcpPort);
    httpClient = HttpClient.newHttpClient();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (mcpServer != null) {
      mcpServer.stop();
      waitForServerStopped(mcpServer);
    }
    if (apiServer != null) {
      apiServer.stop();
      waitForServerStopped(apiServer);
    }
    ApiKeyValidationService.resetForTesting();
    McpApiKeyValidationService.resetForTesting();
  }

  @Test
  void initialize_returnsStrictMcpShape() throws Exception {
    JsonObject request = new JsonObject();
    request.addProperty("jsonrpc", "2.0");
    request.addProperty("id", 1);
    request.addProperty("method", "initialize");

    HttpResponse<String> response = httpClient.send(authenticatedMcpRequest(request), HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());
    JsonObject payload = JsonParser.parseString(response.body()).getAsJsonObject();
    assertEquals("2.0", payload.get("jsonrpc").getAsString());
    assertTrue(payload.has("result"));
    assertEquals("Freerouting MCP", payload.getAsJsonObject("result").get("serverName").getAsString());
  }

  @Test
  void toolsList_andToolsCall_bridgeToApiRoutes() throws Exception {
    JsonObject listRequest = new JsonObject();
    listRequest.addProperty("jsonrpc", "2.0");
    listRequest.addProperty("id", 2);
    listRequest.addProperty("method", "tools/list");

    HttpResponse<String> listResponse = httpClient.send(authenticatedMcpRequest(listRequest), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, listResponse.statusCode());

    JsonObject listPayload = JsonParser.parseString(listResponse.body()).getAsJsonObject();
    JsonArray tools = listPayload.getAsJsonObject("result").getAsJsonArray("tools");
    assertTrue(containsTool(tools, "get_v1_system_status"), "tools/list should expose GET /v1/system/status");

    JsonObject callRequest = new JsonObject();
    callRequest.addProperty("jsonrpc", "2.0");
    callRequest.addProperty("id", 3);
    callRequest.addProperty("method", "tools/call");

    JsonObject params = new JsonObject();
    params.addProperty("name", "get_v1_system_status");
    params.add("arguments", new JsonObject());
    callRequest.add("params", params);

    HttpResponse<String> callResponse = httpClient.send(authenticatedMcpRequest(callRequest), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, callResponse.statusCode());

    JsonObject callPayload = JsonParser.parseString(callResponse.body()).getAsJsonObject();
    assertTrue(callPayload.has("result"));
    assertFalse(callPayload.getAsJsonObject("result").get("isError").getAsBoolean());

    String textPayload = callPayload.getAsJsonObject("result")
        .getAsJsonArray("content")
        .get(0)
        .getAsJsonObject()
        .get("text")
        .getAsString();
    JsonObject forwarded = JsonParser.parseString(textPayload).getAsJsonObject();
    assertEquals(200, forwarded.get("status").getAsInt());
    assertNotNull(forwarded.get("body"));
  }

  @Test
  void agentCard_isPublicOnMcpServer() throws Exception {
    HttpRequest request = HttpRequest.newBuilder(mcpBaseUri.resolve("/.well-known/agent.json"))
        .GET()
        .timeout(Duration.ofSeconds(10))
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());

    JsonObject payload = JsonParser.parseString(response.body()).getAsJsonObject();
    assertEquals("Freerouting MCP", payload.get("name").getAsString());
    assertTrue(payload.has("endpoints"));
    assertTrue(payload.has("endpointMap"));
    assertTrue(payload.has("protocols"));
    assertTrue(payload.has("auth"));
    assertTrue(payload.has("contact"));
    assertTrue(payload.has("documentation"));
    assertTrue(payload.has("tools"));
    assertEquals("openapi", payload.getAsJsonObject("tools").get("source").getAsString());
    assertTrue(payload.getAsJsonObject("tools").getAsJsonArray("categories").size() >= 3);
  }

  @Test
  void toolsCall_rejectsMcpTargetApiBaseUrlMisconfiguration() throws Exception {
    Freerouting.globalSettings.mcpServerSettings.targetApiBaseUrl = mcpBaseUri + "/v1/mcp";

    JsonObject callRequest = new JsonObject();
    callRequest.addProperty("jsonrpc", "2.0");
    callRequest.addProperty("id", 4);
    callRequest.addProperty("method", "tools/call");

    JsonObject params = new JsonObject();
    params.addProperty("name", "get_v1_system_status");
    params.add("arguments", new JsonObject());
    callRequest.add("params", params);

    HttpResponse<String> response = httpClient.send(
        authenticatedMcpRequest(callRequest),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());

    JsonObject payload = JsonParser.parseString(response.body()).getAsJsonObject();
    assertTrue(payload.has("error"));
    assertEquals(-32602, payload.getAsJsonObject("error").get("code").getAsInt());
    assertTrue(payload.getAsJsonObject("error").get("message").getAsString().contains("target_api_base_url"));
  }

  private HttpRequest authenticatedMcpRequest(JsonObject requestBody) {
    return HttpRequest.newBuilder(mcpBaseUri.resolve("/v1/mcp"))
        .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
        .timeout(Duration.ofSeconds(10))
        .header("Content-Type", "application/json")
        .header("Freerouting-Profile-ID", TEST_USER_ID)
        .header("Freerouting-Environment-Host", "test/1.0")
        .build();
  }

  private static boolean containsTool(JsonArray tools, String toolName) {
    for (int i = 0; i < tools.size(); i++) {
      JsonObject item = tools.get(i).getAsJsonObject();
      if (toolName.equals(item.get("name").getAsString())) {
        return true;
      }
    }
    return false;
  }

  private static void waitForServerStarted(Server server) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 30_000;
    while (!server.isStarted()) {
      if (server.isFailed() || System.currentTimeMillis() > deadline) {
        throw new IllegalStateException("Server failed to start in time");
      }
      Thread.sleep(50);
    }
  }

  private static void waitForServerStopped(Server server) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 5_000;
    while (!server.isStopped() && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
  }
}