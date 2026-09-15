package app.freerouting.api;

import static app.freerouting.api.EmbeddedServerTestSupport.HTTP_TIMEOUT;
import static app.freerouting.api.EmbeddedServerTestSupport.stopServerGracefully;
import static app.freerouting.api.EmbeddedServerTestSupport.waitForApiServerReady;
import static app.freerouting.api.EmbeddedServerTestSupport.waitForMcpServerReady;
import static app.freerouting.api.EmbeddedServerTestSupport.waitForServerStarted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import app.freerouting.Freerouting;
import app.freerouting.api.mcp.McpApiKeyValidationService;
import app.freerouting.api.mcp.McpControllerV1;
import app.freerouting.api.security.ApiKeyValidationService;
import app.freerouting.logger.AllowErrorLogs;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("serial")
class McpEndpointsTest {

  private static final String TEST_USER_ID = "00000000-0000-0000-0000-000000000001";

  private Server apiServer;
  private Server mcpServer;
  private URI mcpBaseUri;
  private HttpClient httpClient;

  private static boolean containsTool(JsonArray tools, String toolName) {
    for (int i = 0; i < tools.size(); i++) {
      JsonObject item = tools.get(i).getAsJsonObject();
      if (toolName.equals(item.get("name").getAsString())) {
        return true;
      }
    }
    return false;
  }

  @BeforeEach
  void setUp() throws Exception {
    ApiKeyValidationService.resetForTesting();
    McpApiKeyValidationService.resetForTesting();

    Freerouting.globalSettings = new GlobalSettings();

    ApiServerSettings apiSettings = new ApiServerSettings();
    apiSettings.isEnabled = true;
    apiSettings.isHttpAllowed = true;
    apiSettings.endpoints = new String[] {"http://127.0.0.1:0"};
    apiSettings.authentication.isEnabled = true;

    McpServerSettings mcpSettings = new McpServerSettings();
    mcpSettings.isEnabled = true;
    mcpSettings.isHttpAllowed = true;
    mcpSettings.endpoints = new String[] {"http://127.0.0.1:0"};
    mcpSettings.authentication.isEnabled = false;

    Freerouting.globalSettings.apiServerSettings.authentication.isEnabled = true;
    Freerouting.globalSettings.mcpServerSettings.authentication.isEnabled = false;

    apiServer = Freerouting.initializeAPI(apiSettings);
    waitForServerStarted(apiServer);
    int apiPort = ((ServerConnector) apiServer.getConnectors()[0]).getLocalPort();

    mcpSettings.targetApiBaseUrl = "http://127.0.0.1:" + apiPort;
    Freerouting.globalSettings.mcpServerSettings.targetApiBaseUrl = mcpSettings.targetApiBaseUrl;

    mcpServer = Freerouting.initializeMCP(mcpSettings);
    waitForServerStarted(mcpServer);
    int mcpPort = ((ServerConnector) mcpServer.getConnectors()[0]).getLocalPort();

    mcpBaseUri = URI.create("http://127.0.0.1:" + mcpPort);
    httpClient = HttpClient.newHttpClient();
    waitForApiServerReady(URI.create("http://127.0.0.1:" + apiPort));
    waitForMcpServerReady(mcpBaseUri);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (mcpServer != null) {
      stopServerGracefully(mcpServer);
    }
    if (apiServer != null) {
      stopServerGracefully(apiServer);
    }
    ApiKeyValidationService.resetForTesting();
    McpApiKeyValidationService.resetForTesting();
  }

  @Test
  void initializeReturnsStrictMcpShape() throws Exception {
    JsonObject request = new JsonObject();
    request.addProperty("jsonrpc", "2.0");
    request.addProperty("id", 1);
    request.addProperty("method", "initialize");

    HttpResponse<String> response =
        httpClient.send(authenticatedMcpRequest(request), HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());
    JsonObject payload = JsonParser.parseString(response.body()).getAsJsonObject();
    assertEquals("2.0", payload.get("jsonrpc").getAsString());
    assertTrue(payload.has("result"));
    assertEquals(
        "Freerouting MCP", payload.getAsJsonObject("result").get("serverName").getAsString());
    assertEquals(
        "Freerouting MCP",
        payload.getAsJsonObject("result").getAsJsonObject("serverInfo").get("name").getAsString());
  }

  @Test
  @AllowErrorLogs(
      "Jetty shutdown may interrupt in-flight MCP bridge HTTP calls under parallel test load")
  void toolsListAndToolsCallBridgeToApiRoutes() throws Exception {
    JsonObject listRequest = new JsonObject();
    listRequest.addProperty("jsonrpc", "2.0");
    listRequest.addProperty("id", 2);
    listRequest.addProperty("method", "tools/list");

    HttpResponse<String> listResponse =
        httpClient.send(authenticatedMcpRequest(listRequest), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, listResponse.statusCode());

    JsonObject listPayload = JsonParser.parseString(listResponse.body()).getAsJsonObject();
    JsonArray tools = listPayload.getAsJsonObject("result").getAsJsonArray("tools");
    assertTrue(
        containsTool(tools, "get_system_status"), "tools/list should expose get_system_status");

    JsonObject callRequest = new JsonObject();
    callRequest.addProperty("jsonrpc", "2.0");
    callRequest.addProperty("id", 3);
    callRequest.addProperty("method", "tools/call");

    JsonObject params = new JsonObject();
    params.addProperty("name", "get_system_status");
    params.add("arguments", new JsonObject());
    callRequest.add("params", params);

    HttpResponse<String> callResponse =
        httpClient.send(authenticatedMcpRequest(callRequest), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, callResponse.statusCode());

    JsonObject callPayload = JsonParser.parseString(callResponse.body()).getAsJsonObject();
    assertTrue(callPayload.has("result"));
    assertFalse(callPayload.getAsJsonObject("result").get("isError").getAsBoolean());

    String textPayload =
        callPayload
            .getAsJsonObject("result")
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
  void customToolsEncodeAndDecodeBase64RunLocally() throws Exception {
    // 1. Verify tools exist in tools/list
    JsonObject listRequest = new JsonObject();
    listRequest.addProperty("jsonrpc", "2.0");
    listRequest.addProperty("id", 10);
    listRequest.addProperty("method", "tools/list");

    HttpResponse<String> listResponse =
        httpClient.send(authenticatedMcpRequest(listRequest), HttpResponse.BodyHandlers.ofString());
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

    HttpResponse<String> encodeResponse =
        httpClient.send(
            authenticatedMcpRequest(encodeRequest), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, encodeResponse.statusCode());

    JsonObject encodePayload = JsonParser.parseString(encodeResponse.body()).getAsJsonObject();
    assertTrue(encodePayload.has("result"));
    assertFalse(encodePayload.getAsJsonObject("result").get("isError").getAsBoolean());

    String encodeText =
        encodePayload
            .getAsJsonObject("result")
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

    HttpResponse<String> decodeResponse =
        httpClient.send(
            authenticatedMcpRequest(decodeRequest), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, decodeResponse.statusCode());

    JsonObject decodePayload = JsonParser.parseString(decodeResponse.body()).getAsJsonObject();
    assertTrue(decodePayload.has("result"));
    assertFalse(decodePayload.getAsJsonObject("result").get("isError").getAsBoolean());

    String decodeText =
        decodePayload
            .getAsJsonObject("result")
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
  void agentCardIsPublicOnMcpServer() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(mcpBaseUri.resolve("/.well-known/agent.json"))
            .GET()
            .timeout(HTTP_TIMEOUT)
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
  @AllowErrorLogs(
      "Misconfigured target URL is rejected; MCP may log RPC interruption under parallel load")
  void toolsCallRejectsMcpTargetApiBaseUrlMisconfiguration() throws Exception {
    Freerouting.globalSettings.mcpServerSettings.targetApiBaseUrl = mcpBaseUri + "/v1/mcp";

    JsonObject callRequest = new JsonObject();
    callRequest.addProperty("jsonrpc", "2.0");
    callRequest.addProperty("id", 4);
    callRequest.addProperty("method", "tools/call");

    JsonObject params = new JsonObject();
    params.addProperty("name", "get_system_status");
    params.add("arguments", new JsonObject());
    callRequest.add("params", params);

    HttpResponse<String> response =
        httpClient.send(authenticatedMcpRequest(callRequest), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());

    JsonObject payload = JsonParser.parseString(response.body()).getAsJsonObject();
    assertTrue(payload.has("error"));
    assertEquals(-32602, payload.getAsJsonObject("error").get("code").getAsInt());
    assertTrue(
        payload
            .getAsJsonObject("error")
            .get("message")
            .getAsString()
            .contains("target_api_base_url"));
  }

  @Test
  void initializeExtractsClientInfoAndCachesIt() throws Exception {
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

    HttpResponse<String> response =
        httpClient.send(
            authenticatedMcpRequest(initializeRequest), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());

    java.lang.reflect.Field field = McpControllerV1.class.getDeclaredField("detectedClientInfo");
    field.setAccessible(true);
    String detected = (String) field.get(null);
    assertEquals("ClaudeDesktop/4.6.1", detected);
  }

  @Test
  void customToolsFileUploadAndDownloadRunLocally(@TempDir Path tempDir) throws Exception {
    // The MCP local-file tools bridge to the REST API without an Authorization header, and the
    // test servers are started with no API-key providers configured, so API-key validation must
    // stay disabled for the bridged upload/download calls below. Restored in finally.
    boolean originalAuthEnabled =
        Freerouting.globalSettings.apiServerSettings.authentication.isEnabled;
    Freerouting.globalSettings.apiServerSettings.authentication.isEnabled = false;
    app.freerouting.api.security.ApiKeyValidationService.resetForTesting();

    try {
      Path tempInput = copyEmptyBoardFixture(tempDir);

      String sessionId = createTestSession();
      String jobId = enqueueTestJob(sessionId);

      // 1. Upload the local DSN file into the job input.
      JsonObject uploadArgs = new JsonObject();
      uploadArgs.addProperty("jobId", jobId);
      uploadArgs.addProperty("filePath", tempInput.toAbsolutePath().toString());
      JsonObject uploadResult =
          callLocalFileTool("upload_job_input_from_local_file", 20, uploadArgs);
      JsonObject uploadBody =
          extractToolResultBody(uploadResult, "upload_job_input_from_local_file");
      assertEquals(
          200,
          uploadBody.get("status").getAsInt(),
          () -> "upload_job_input_from_local_file returned unexpected status: " + uploadBody);
      assertFalse(
          uploadResult.get("isError").getAsBoolean(),
          () -> "upload_job_input_from_local_file flagged isError: " + uploadBody);

      // 2. Download the output before the job started: no output bytes exist yet. A QUEUED job
      // reports 400 ("hasn't started yet"); if the scheduler ever picks the job up concurrently
      // it reports 204 instead. Both mean "no output available yet", so accept either.
      // @TempDir guarantees a fresh directory, so the output path does not exist beforehand and
      // no create-then-delete roundtrip (racy on Windows file locking) is needed.
      Path tempOutput = tempDir.resolve("freerouting-test-output.ses");

      JsonObject downloadArgs = new JsonObject();
      downloadArgs.addProperty("jobId", jobId);
      downloadArgs.addProperty("filePath", tempOutput.toAbsolutePath().toString());
      JsonObject downloadResult =
          callLocalFileTool("download_job_output_to_local_file", 21, downloadArgs);
      JsonObject downloadBody =
          extractToolResultBody(downloadResult, "download_job_output_to_local_file");
      int downloadStatus = downloadBody.get("status").getAsInt();
      assertTrue(
          downloadStatus == 400 || downloadStatus == 204,
          () ->
              "download_job_output_to_local_file should report no-output-yet"
                  + " (400 QUEUED or 204 RUNNING), but got: "
                  + downloadBody);
      if (downloadStatus == 400) {
        assertTrue(
            downloadResult.get("isError").getAsBoolean(),
            () -> "expected isError=true for HTTP 400 download status: " + downloadBody);
        assertTrue(
            downloadBody.getAsJsonObject("body").toString().contains("hasn't started"),
            () -> "expected 'hasn't started yet' error body, got: " + downloadBody);
      } else {
        assertFalse(
            downloadResult.get("isError").getAsBoolean(),
            () -> "expected isError=false for HTTP 204 download status: " + downloadBody);
      }
      assertFalse(
          Files.exists(tempOutput),
          () -> "no output file must be written when the job has no output: " + tempOutput);
    } finally {
      Freerouting.globalSettings.apiServerSettings.authentication.isEnabled = originalAuthEnabled;
      app.freerouting.api.security.ApiKeyValidationService.resetForTesting();
    }
  }

  @Test
  void mcpLocalFileToolsRejectSystemPaths() throws Exception {
    JsonObject uploadRequest = new JsonObject();
    uploadRequest.addProperty("jsonrpc", "2.0");
    uploadRequest.addProperty("id", 88);
    uploadRequest.addProperty("method", "tools/call");

    JsonObject uploadParams = new JsonObject();
    uploadParams.addProperty("name", "upload_job_input_from_local_file");
    JsonObject uploadArgs = new JsonObject();
    uploadArgs.addProperty("jobId", "test-job-id");
    uploadArgs.addProperty("filePath", "/etc/shadow");
    uploadParams.add("arguments", uploadArgs);
    uploadRequest.add("params", uploadParams);

    HttpResponse<String> response =
        httpClient.send(
            authenticatedMcpRequest(uploadRequest), HttpResponse.BodyHandlers.ofString());
    JsonObject payload = JsonParser.parseString(response.body()).getAsJsonObject();
    assertTrue(payload.has("error"), "Must return JSON-RPC error when accessing system path");
    assertEquals(-32603, payload.getAsJsonObject("error").get("code").getAsInt());
  }

  /**
   * Copies the empty-board DSN fixture into the JUnit-managed temp directory and verifies it is
   * non-empty, so a later upload failure can never be mistaken for a missing/empty fixture.
   */
  private Path copyEmptyBoardFixture(Path tempDir) throws Exception {
    Path tempInput = tempDir.resolve("freerouting-test-input.dsn");
    try (java.io.InputStream in = getClass().getResourceAsStream("/empty_board.dsn")) {
      if (in != null) {
        Files.copy(in, tempInput, StandardCopyOption.REPLACE_EXISTING);
      } else {
        Path sourceDsn = app.freerouting.TestFixtures.resolvePath("empty_board.dsn");
        Files.copy(sourceDsn, tempInput, StandardCopyOption.REPLACE_EXISTING);
      }
    }
    assertTrue(Files.exists(tempInput), () -> "fixture copy is missing after copy: " + tempInput);
    assertTrue(Files.size(tempInput) > 0, () -> "fixture copy is empty: " + tempInput);
    return tempInput;
  }

  /**
   * Calls a custom local-file MCP tool via {@code tools/call} and returns the {@code result} node.
   * Fails with the full transport/JSON-RPC payload when the HTTP status is unexpected or the tool
   * reports a JSON-RPC {@code error} (e.g. sandbox rejection or unreadable file).
   */
  private JsonObject callLocalFileTool(String toolName, int rpcId, JsonObject args)
      throws Exception {
    JsonObject request = new JsonObject();
    request.addProperty("jsonrpc", "2.0");
    request.addProperty("id", rpcId);
    request.addProperty("method", "tools/call");

    JsonObject params = new JsonObject();
    params.addProperty("name", toolName);
    params.add("arguments", args);
    request.add("params", params);

    HttpResponse<String> response =
        httpClient.send(authenticatedMcpRequest(request), HttpResponse.BodyHandlers.ofString());
    assertEquals(
        200,
        response.statusCode(),
        () ->
            "MCP HTTP transport failed for tool '"
                + toolName
                + "': HTTP "
                + response.statusCode()
                + " body="
                + response.body());

    JsonObject payload = JsonParser.parseString(response.body()).getAsJsonObject();
    if (payload.has("error")) {
      fail(
          "MCP tool '"
              + toolName
              + "' (rpc id "
              + rpcId
              + ") returned JSON-RPC error: "
              + payload.getAsJsonObject("error"));
    }
    assertTrue(
        payload.has("result"),
        () -> "MCP tool '" + toolName + "' response has neither result nor error: " + payload);
    return payload.getAsJsonObject("result");
  }

  /**
   * Unwraps the inner {@code {status, contentType, body}} envelope carried in {@code
   * result.content[0].text} for custom local-file tools.
   */
  private JsonObject extractToolResultBody(JsonObject result, String toolName) {
    assertTrue(
        result.has("content"),
        () -> "MCP tool '" + toolName + "' result has no content: " + result);
    JsonArray content = result.getAsJsonArray("content");
    assertTrue(
        content.size() > 0, () -> "MCP tool '" + toolName + "' result content is empty: " + result);
    String text = content.get(0).getAsJsonObject().get("text").getAsString();
    JsonObject body = JsonParser.parseString(text).getAsJsonObject();
    assertTrue(
        body.has("status"), () -> "MCP tool '" + toolName + "' result body has no status: " + body);
    return body;
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

    HttpResponse<String> response =
        httpClient.send(authenticatedMcpRequest(request), HttpResponse.BodyHandlers.ofString());
    JsonObject payload = JsonParser.parseString(response.body()).getAsJsonObject();
    if (payload.has("error")) {
      fail("create_session MCP tool call failed: " + payload.get("error"));
    }
    assertTrue(payload.has("result"), "Expected 'result' in response: " + payload);
    String text =
        payload
            .getAsJsonObject("result")
            .getAsJsonArray("content")
            .get(0)
            .getAsJsonObject()
            .get("text")
            .getAsString();
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

    HttpResponse<String> response =
        httpClient.send(authenticatedMcpRequest(request), HttpResponse.BodyHandlers.ofString());
    JsonObject payload = JsonParser.parseString(response.body()).getAsJsonObject();
    if (payload.has("error")) {
      fail("enqueue_job MCP tool call failed: " + payload.get("error"));
    }
    assertTrue(payload.has("result"), "Expected 'result' in response: " + payload);
    String text =
        payload
            .getAsJsonObject("result")
            .getAsJsonArray("content")
            .get(0)
            .getAsJsonObject()
            .get("text")
            .getAsString();
    JsonObject body = JsonParser.parseString(text).getAsJsonObject().getAsJsonObject("body");
    return body.get("id").getAsString();
  }

  private HttpRequest authenticatedMcpRequest(JsonObject requestBody) {
    return HttpRequest.newBuilder(mcpBaseUri.resolve("/v1/mcp"))
        .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
        .timeout(HTTP_TIMEOUT)
        .header("Content-Type", "application/json")
        .header("Freerouting-Profile-ID", TEST_USER_ID)
        .header("Freerouting-Environment-Host", "test/1.0")
        .build();
  }
}
