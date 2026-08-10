package app.freerouting.api;

import static app.freerouting.api.EmbeddedServerTestSupport.HTTP_TIMEOUT;
import static app.freerouting.api.EmbeddedServerTestSupport.stopServerGracefully;
import static app.freerouting.api.EmbeddedServerTestSupport.waitForMcpServerReady;
import static app.freerouting.api.EmbeddedServerTestSupport.waitForServerStarted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.Freerouting;
import app.freerouting.api.mcp.McpApiKeyValidationService;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.McpServerSettings;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class McpWebSocketEndpointTest {

  private Server mcpServer;

  @AfterEach
  void tearDown() throws Exception {
    stopServerGracefully(mcpServer);
    McpApiKeyValidationService.resetForTesting();
  }

  @Test
  void websocketWithValidHeadersAcceptsAndResponds() throws Exception {
    URI wsUri = startMcpServer(false);

    TestWebSocketListener listener = new TestWebSocketListener();
    WebSocket webSocket =
        HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .header("Freerouting-Profile-ID", "00000000-0000-0000-0000-000000000001")
            .header("Freerouting-Environment-Host", "TestClient/1.0")
            .connectTimeout(HTTP_TIMEOUT)
            .buildAsync(wsUri, listener)
            .join();

    webSocket.sendText("hello", true).join();

    String response = waitForMessageContaining(listener.messages, "Use POST /v1/mcp", 30);
    assertNotNull(response, "WebSocket should return MCP JSON-RPC usage hint");

    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
  }

  @Test
  void websocketMissingProfileHeaderIsRejected() throws Exception {
    URI wsUri = startMcpServer(false);

    TestWebSocketListener listener = new TestWebSocketListener();
    HttpClient.newHttpClient()
        .newWebSocketBuilder()
        .header("Freerouting-Environment-Host", "TestClient/1.0")
        .connectTimeout(HTTP_TIMEOUT)
        .buildAsync(wsUri, listener)
        .join();

    Integer status = listener.closeStatus.get(30, TimeUnit.SECONDS);
    assertEquals(1008, status.intValue());
  }

  @Test
  void websocketMissingEnvironmentHostIsRejected() throws Exception {
    URI wsUri = startMcpServer(false);

    TestWebSocketListener listener = new TestWebSocketListener();
    HttpClient.newHttpClient()
        .newWebSocketBuilder()
        .header("Freerouting-Profile-ID", "00000000-0000-0000-0000-000000000001")
        .connectTimeout(HTTP_TIMEOUT)
        .buildAsync(wsUri, listener)
        .join();

    Integer status = listener.closeStatus.get(30, TimeUnit.SECONDS);
    assertEquals(1008, status.intValue());
  }

  @Test
  void websocketAuthEnabledMissingAuthorizationIsRejected() throws Exception {
    URI wsUri = startMcpServer(true);

    TestWebSocketListener listener = new TestWebSocketListener();
    HttpClient.newHttpClient()
        .newWebSocketBuilder()
        .header("Freerouting-Profile-ID", "00000000-0000-0000-0000-000000000001")
        .header("Freerouting-Environment-Host", "TestClient/1.0")
        .connectTimeout(HTTP_TIMEOUT)
        .buildAsync(wsUri, listener)
        .join();

    Integer status = listener.closeStatus.get(30, TimeUnit.SECONDS);
    assertEquals(1008, status.intValue());
  }

  private URI startMcpServer(boolean authenticationEnabled) throws Exception {
    McpApiKeyValidationService.resetForTesting();

    Freerouting.globalSettings = new GlobalSettings();
    Freerouting.globalSettings.mcpServerSettings.authentication.isEnabled = authenticationEnabled;

    McpServerSettings mcpSettings = new McpServerSettings();
    mcpSettings.isEnabled = true;
    mcpSettings.isHttpAllowed = true;
    mcpSettings.endpoints = new String[] {"http://127.0.0.1:0"};
    mcpSettings.authentication.isEnabled = authenticationEnabled;

    mcpServer = Freerouting.initializeMCP(mcpSettings);
    waitForServerStarted(mcpServer);
    int mcpPort = ((ServerConnector) mcpServer.getConnectors()[0]).getLocalPort();
    waitForMcpServerReady(URI.create("http://127.0.0.1:" + mcpPort));
    return URI.create("ws://127.0.0.1:" + mcpPort + "/v1/mcp/ws");
  }

  private static String waitForMessageContaining(
      BlockingQueue<String> messages, String expectedText, int timeoutSeconds)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
    while (System.currentTimeMillis() < deadline) {
      String message = messages.poll(250, TimeUnit.MILLISECONDS);
      if (message != null && message.contains(expectedText)) {
        return message;
      }
    }
    return null;
  }

  private static final class TestWebSocketListener implements WebSocket.Listener {

    private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    private final CompletableFuture<Integer> closeStatus = new CompletableFuture<>();

    @Override
    public void onOpen(WebSocket webSocket) {
      webSocket.request(1);
    }

    @Override
    // codespell:ignore
    public java.util.concurrent.CompletionStage<?> onText(
        WebSocket webSocket, CharSequence data, boolean last) {
      messages.offer(data.toString());
      webSocket.request(1);
      return null;
    }

    @Override
    public java.util.concurrent.CompletionStage<?> onClose(
        WebSocket webSocket, int statusCode, String reason) {
      closeStatus.complete(statusCode);
      return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      closeStatus.completeExceptionally(error);
    }
  }
}
