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
    assertEquals("Freerouting MCP", payload.getAsJsonObject("result").getAsJsonObject("serverInfo").get("name").getAsString());
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
    assertTrue(containsTool(tools, "get_system_status"), "tools/list should expose get_system_status");

    JsonObject callRequest = new JsonObject();
    callRequest.addProperty("jsonrpc", "2.0");
    callRequest.addProperty("id", 3);
    callRequest.addProperty("method", "tools/call");

    JsonObject params = new JsonObject();
    params.addProperty("name", "get_system_status");
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
  void customTools_encodeAndDecodeBase64_runLocally() throws Exception {
    // 1. Verify tools exist in tools/list
    JsonObject listRequest = new JsonObject();
    listRequest.addProperty("jsonrpc", "2.0");
    listRequest.addProperty("id", 10);
    listRequest.addProperty("method", "tools/list");

    HttpResponse<String> listResponse = httpClient.send(authenticatedMcpRequest(listRequest), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, listResponse.statusCode());

    JsonObject listPayload = JsonParser.parseString(listResponse.body()).getAsJsonObject();
    JsonArray tools = listPayload.getAsJsonObject("result").getAsJsonArray("tools");
    assertTrue(containsTool(tools, "encode_base64"), "tools/list should expose encode_base64");
    assertTrue(containsTool(tools, "decode_base64"), "tools/list should expose decode_base64");

    // 2. Call encode_base64
    JsonObject encodeRequest = new JsonObject();
    encodeRequest.addProperty("jsonrpc", "2.0");
    encodeRequest.addProperty("id", 11);
    encodeRequest.addProperty("method", "tools/call");

    JsonObject encodeParams = new JsonObject();
    encodeParams.addProperty("name", "encode_base64");
    JsonObject encodeArgs = new JsonObject();
    encodeArgs.addProperty("text", "hello world");
    encodeParams.add("arguments", encodeArgs);
    encodeRequest.add("params", encodeParams);

    HttpResponse<String> encodeResponse = httpClient.send(authenticatedMcpRequest(encodeRequest), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, encodeResponse.statusCode());

    JsonObject encodePayload = JsonParser.parseString(encodeResponse.body()).getAsJsonObject();
    assertTrue(encodePayload.has("result"));
    assertFalse(encodePayload.getAsJsonObject("result").get("isError").getAsBoolean());

    String encodeText = encodePayload.getAsJsonObject("result")
        .getAsJsonArray("content")
        .get(0)
        .getAsJsonObject()
        .get("text")
        .getAsString();
    JsonObject encodeResultBody = JsonParser.parseString(encodeText).getAsJsonObject();
    assertEquals(200, encodeResultBody.get("status").getAsInt());
    String base64Value = encodeResultBody.getAsJsonObject("body").get("base64").getAsString();
    assertEquals("aGVsbG8gd29ybGQ=", base64Value);

    // 3. Call decode_base64
    JsonObject decodeRequest = new JsonObject();
    decodeRequest.addProperty("jsonrpc", "2.0");
    decodeRequest.addProperty("id", 12);
    decodeRequest.addProperty("method", "tools/call");

    JsonObject decodeParams = new JsonObject();
    decodeParams.addProperty("name", "decode_base64");
    JsonObject decodeArgs = new JsonObject();
    decodeArgs.addProperty("base64", "aGVsbG8gd29ybGQ=");
    decodeParams.add("arguments", decodeArgs);
    decodeRequest.add("params", decodeParams);

    HttpResponse<String> decodeResponse = httpClient.send(authenticatedMcpRequest(decodeRequest), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, decodeResponse.statusCode());

    JsonObject decodePayload = JsonParser.parseString(decodeResponse.body()).getAsJsonObject();
    assertTrue(decodePayload.has("result"));
    assertFalse(decodePayload.getAsJsonObject("result").get("isError").getAsBoolean());

    String decodeText = decodePayload.getAsJsonObject("result")
        .getAsJsonArray("content")
        .get(0)
        .getAsJsonObject()
        .get("text")
        .getAsString();
    JsonObject decodeResultBody = JsonParser.parseString(decodeText).getAsJsonObject();
    assertEquals(200, decodeResultBody.get("status").getAsInt());
    String textValue = decodeResultBody.getAsJsonObject("body").get("text").getAsString();
    assertEquals("hello world", textValue);
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
    params.addProperty("name", "get_system_status");
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

  @Test
  void initialize_extractsClientInfo_andCachesIt() throws Exception {
    JsonObject initializeRequest = new JsonObject();
    initializeRequest.addProperty("jsonrpc", "2.0");
    initializeRequest.addProperty("id", 101);
    initializeRequest.addProperty("method", "initialize");

    JsonObject params = new JsonObject();
    JsonObject clientInfo = new JsonObject();
    clientInfo.addProperty("name", "ClaudeDesktop");
    clientInfo.addProperty("version", "4.6.1");
    params.add("clientInfo", clientInfo);
    initializeRequest.add("params", params);

    HttpResponse<String> response = httpClient.send(authenticatedMcpRequest(initializeRequest), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());

    java.lang.reflect.Field field = app.freerouting.api.v1.McpControllerV1.class.getDeclaredField("detectedClientInfo");
    field.setAccessible(true);
    String detected = (String) field.get(null);
    assertEquals("ClaudeDesktop/4.6.1", detected);
  }

  @Test
  void customTools_fileUploadAndDownload_runLocally() throws Exception {
    boolean originalAuthEnabled = Freerouting.globalSettings.apiServerSettings.authentication.isEnabled;
    Freerouting.globalSettings.apiServerSettings.authentication.isEnabled = false;
    app.freerouting.api.security.ApiKeyValidationService.resetForTesting();

    java.nio.file.Path tempInput = null;
    java.nio.file.Path tempOutput = null;

    try {
      // Find valid empty_board.dsn test file
      java.nio.file.Path sourceDsn = java.nio.file.Path.of("fixtures/empty_board.dsn");
      if (!java.nio.file.Files.exists(sourceDsn)) {
        sourceDsn = java.nio.file.Path.of("../fixtures/empty_board.dsn");
      }
      if (!java.nio.file.Files.exists(sourceDsn)) {
        sourceDsn = java.nio.file.Path.of("C:/Work/freerouting/fixtures/empty_board.dsn");
      }

      tempInput = java.nio.file.Files.createTempFile("freerouting-test-input", ".dsn");
      java.nio.file.Files.copy(sourceDsn, tempInput, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

      // We need a session and job first to upload input to
      String sessionId = createTestSession();
      String jobId = enqueueTestJob(sessionId);

      // Call upload_job_input_from_local_file
      JsonObject uploadRequest = new JsonObject();
      uploadRequest.addProperty("jsonrpc", "2.0");
      uploadRequest.addProperty("id", 20);
      uploadRequest.addProperty("method", "tools/call");

      JsonObject uploadParams = new JsonObject();
      uploadParams.addProperty("name", "upload_job_input_from_local_file");
      JsonObject uploadArgs = new JsonObject();
      uploadArgs.addProperty("jobId", jobId);
      uploadArgs.addProperty("filePath", tempInput.toAbsolutePath().toString());
      uploadParams.add("arguments", uploadArgs);
      uploadRequest.add("params", uploadParams);

      HttpResponse<String> uploadResponse = httpClient.send(authenticatedMcpRequest(uploadRequest), HttpResponse.BodyHandlers.ofString());
      assertEquals(200, uploadResponse.statusCode());

      JsonObject uploadPayload = JsonParser.parseString(uploadResponse.body()).getAsJsonObject();
      assertFalse(uploadPayload.getAsJsonObject("result").get("isError").getAsBoolean());

      // 2. Call download_job_output_to_local_file (expecting 400 because job has not completed/run)
      tempOutput = java.nio.file.Path.of(System.getProperty("java.io.tmpdir")).resolve("freerouting-test-output-" + System.currentTimeMillis() + ".ses");

      JsonObject downloadRequest = new JsonObject();
      downloadRequest.addProperty("jsonrpc", "2.0");
      downloadRequest.addProperty("id", 21);
      downloadRequest.addProperty("method", "tools/call");

      JsonObject downloadParams = new JsonObject();
      downloadParams.addProperty("name", "download_job_output_to_local_file");
      JsonObject downloadArgs = new JsonObject();
      downloadArgs.addProperty("jobId", jobId);
      downloadArgs.addProperty("filePath", tempOutput.toAbsolutePath().toString());
      downloadParams.add("arguments", downloadArgs);
      downloadRequest.add("params", downloadParams);

      HttpResponse<String> downloadResponse = httpClient.send(authenticatedMcpRequest(downloadRequest), HttpResponse.BodyHandlers.ofString());
      assertEquals(200, downloadResponse.statusCode());

      JsonObject downloadPayload = JsonParser.parseString(downloadResponse.body()).getAsJsonObject();
      String downloadText = downloadPayload.getAsJsonObject("result")
          .getAsJsonArray("content")
          .get(0)
          .getAsJsonObject()
          .get("text")
          .getAsString();
      JsonObject downloadResultBody = JsonParser.parseString(downloadText).getAsJsonObject();
      assertEquals(400, downloadResultBody.get("status").getAsInt());
    } finally {
      if (tempInput != null) {
        java.nio.file.Files.deleteIfExists(tempInput);
      }
      if (tempOutput != null) {
        java.nio.file.Files.deleteIfExists(tempOutput);
      }
      Freerouting.globalSettings.apiServerSettings.authentication.isEnabled = originalAuthEnabled;
      app.freerouting.api.security.ApiKeyValidationService.resetForTesting();
    }
  }

  private String createTestSession() throws Exception {
    JsonObject request = new JsonObject();
    request.addProperty("jsonrpc", "2.0");
    request.addProperty("id", 50);
    request.addProperty("method", "tools/call");

    JsonObject params = new JsonObject();
    params.addProperty("name", "create_session");
    params.add("arguments", new JsonObject());
    request.add("params", params);

    HttpResponse<String> response = httpClient.send(authenticatedMcpRequest(request), HttpResponse.BodyHandlers.ofString());
    JsonObject payload = JsonParser.parseString(response.body()).getAsJsonObject();
    String text = payload.getAsJsonObject("result").getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
    JsonObject body = JsonParser.parseString(text).getAsJsonObject().getAsJsonObject("body");
    return body.get("id").getAsString();
  }

  private String enqueueTestJob(String sessionId) throws Exception {
    JsonObject request = new JsonObject();
    request.addProperty("jsonrpc", "2.0");
    request.addProperty("id", 51);
    request.addProperty("method", "tools/call");

    JsonObject params = new JsonObject();
    params.addProperty("name", "enqueue_job");
    JsonObject args = new JsonObject();
    JsonObject bodyObj = new JsonObject();
    bodyObj.addProperty("session_id", sessionId);
    args.add("body", bodyObj);
    params.add("arguments", args);
    request.add("params", params);

    HttpResponse<String> response = httpClient.send(authenticatedMcpRequest(request), HttpResponse.BodyHandlers.ofString());
    JsonObject payload = JsonParser.parseString(response.body()).getAsJsonObject();
    String text = payload.getAsJsonObject("result").getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
    JsonObject body = JsonParser.parseString(text).getAsJsonObject().getAsJsonObject("body");
    return body.get("id").getAsString();
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