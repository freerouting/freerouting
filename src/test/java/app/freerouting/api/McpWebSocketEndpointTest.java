package app.freerouting.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.api.mcp.McpApiKeyValidationService;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.McpServerSettings;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
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
    if (mcpServer != null) {
      mcpServer.stop();
      waitForServerStopped(mcpServer);
    }
    McpApiKeyValidationService.resetForTesting();
  }

  @Test
  void websocket_withValidHeaders_acceptsAndResponds() throws Exception {
    URI wsUri = startMcpServer(false);

    TestWebSocketListener listener = new TestWebSocketListener();
    WebSocket webSocket = HttpClient.newHttpClient().newWebSocketBuilder()
        .header("Freerouting-Profile-ID", "00000000-0000-0000-0000-000000000001")
        .header("Freerouting-Environment-Host", "TestClient/1.0")
        .connectTimeout(Duration.ofSeconds(5))
        .buildAsync(wsUri, listener)
        .join();

    webSocket.sendText("hello", true).join();

    String response = waitForMessageContaining(listener.messages, "Use POST /v1/mcp", 5);
    assertNotNull(response, "WebSocket should return MCP JSON-RPC usage hint");

    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
  }

  @Test
  void websocket_missingProfileHeader_isRejected() throws Exception {
    URI wsUri = startMcpServer(false);

    TestWebSocketListener listener = new TestWebSocketListener();
    HttpClient.newHttpClient().newWebSocketBuilder()
        .header("Freerouting-Environment-Host", "TestClient/1.0")
        .connectTimeout(Duration.ofSeconds(5))
        .buildAsync(wsUri, listener)
        .join();

    Integer status = listener.closeStatus.get(5, TimeUnit.SECONDS);
    assertEquals(1008, status.intValue());
  }

  @Test
  void websocket_missingEnvironmentHost_isRejected() throws Exception {
    URI wsUri = startMcpServer(false);

    TestWebSocketListener listener = new TestWebSocketListener();
    HttpClient.newHttpClient().newWebSocketBuilder()
        .header("Freerouting-Profile-ID", "00000000-0000-0000-0000-000000000001")
        .connectTimeout(Duration.ofSeconds(5))
        .buildAsync(wsUri, listener)
        .join();

    Integer status = listener.closeStatus.get(5, TimeUnit.SECONDS);
    assertEquals(1008, status.intValue());
  }

  @Test
  void websocket_authEnabled_missingAuthorization_isRejected() throws Exception {
    URI wsUri = startMcpServer(true);

    TestWebSocketListener listener = new TestWebSocketListener();
    HttpClient.newHttpClient().newWebSocketBuilder()
        .header("Freerouting-Profile-ID", "00000000-0000-0000-0000-000000000001")
        .header("Freerouting-Environment-Host", "TestClient/1.0")
        .connectTimeout(Duration.ofSeconds(5))
        .buildAsync(wsUri, listener)
        .join();

    Integer status = listener.closeStatus.get(5, TimeUnit.SECONDS);
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

    mcpServer = Freerouting.InitializeMCP(mcpSettings);
    waitForServerStarted(mcpServer);
    int mcpPort = ((ServerConnector) mcpServer.getConnectors()[0]).getLocalPort();
    return URI.create("ws://127.0.0.1:" + mcpPort + "/v1/mcp/ws");
  }

  private static String waitForMessageContaining(
      BlockingQueue<String> messages,
      String expectedText,
      int timeoutSeconds) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
    while (System.currentTimeMillis() < deadline) {
      String message = messages.poll(250, TimeUnit.MILLISECONDS);
      if (message != null && message.contains(expectedText)) {
        return message;
      }
    }
    return null;
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

  private static final class TestWebSocketListener implements WebSocket.Listener {

    private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    private final CompletableFuture<Integer> closeStatus = new CompletableFuture<>();

    @Override
    public void onOpen(WebSocket webSocket) {
      webSocket.request(1);
    }

    @Override
    public java.util.concurrent.CompletionStage<?> onText(
        WebSocket webSocket,
        CharSequence data,
        boolean last) {
      messages.offer(data.toString());
      webSocket.request(1);
      return null;
    }

    @Override
    public java.util.concurrent.CompletionStage<?> onClose(
        WebSocket webSocket,
        int statusCode,
        String reason) {
      closeStatus.complete(statusCode);
      return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      closeStatus.completeExceptionally(error);
    }
  }
}