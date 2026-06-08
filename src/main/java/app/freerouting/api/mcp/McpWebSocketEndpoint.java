package app.freerouting.api.mcp;

import com.google.gson.JsonObject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lightweight WebSocket endpoint that mirrors MCP activity as push events.
 */
@ServerEndpoint(value = "/v1/mcp/ws", configurator = McpWebSocketConfigurator.class)
public class McpWebSocketEndpoint {

  private static final String ENVIRONMENT_HOST_HEADER = "Freerouting-Environment-Host";
  private static final String PROFILE_ID_HEADER = "Freerouting-Profile-ID";
  private static final String PROFILE_EMAIL_HEADER = "Freerouting-Profile-Email";
  private static final String AUTHORIZATION_HEADER = "authorization";
  private static final String BEARER_PREFIX = "bearer ";

  @OnOpen
  public void onOpen(Session session, EndpointConfig config) {
    Map<String, List<String>> headers = McpWebSocketConfigurator.getHeaders(config);

    if (!isAuthorized(headers)) {
      close(session, CloseReason.CloseCodes.VIOLATED_POLICY, "Unauthorized");
      return;
    }

    if (!hasProfile(headers)) {
      close(session, CloseReason.CloseCodes.VIOLATED_POLICY, "Missing Freerouting profile header");
      return;
    }

    String environmentHost = firstHeader(headers, ENVIRONMENT_HOST_HEADER);
    if (!isValidEnvironmentHost(environmentHost)) {
      close(session, CloseReason.CloseCodes.VIOLATED_POLICY, "Invalid Freerouting-Environment-Host");
      return;
    }

    McpRealtimeBridge.registerWsClient(session);

    JsonObject hello = new JsonObject();
    hello.addProperty("message", "MCP websocket stream connected");
    McpRealtimeBridge.broadcast("mcp.websocket.connected", hello);
  }

  @OnClose
  public void onClose(Session session) {
    McpRealtimeBridge.removeWsClient(session);
  }

  @OnError
  public void onError(Session session, Throwable throwable) {
    McpRealtimeBridge.removeWsClient(session);
  }

  @OnMessage
  public void onMessage(String message, Session session) {
    JsonObject payload = new JsonObject();
    payload.addProperty("message", message);
    payload.addProperty("hint", "Use POST /v1/mcp for JSON-RPC requests.");
    session.getAsyncRemote().sendText(payload.toString());
  }

  private boolean isAuthorized(Map<String, List<String>> headers) {
    McpApiKeyValidationService validationService = McpApiKeyValidationService.getInstance();
    if (!validationService.isAuthenticationEnabled()) {
      return true;
    }

    String authorization = firstHeader(headers, AUTHORIZATION_HEADER);
    if (authorization == null) {
      return false;
    }

    String normalized = authorization.toLowerCase(Locale.ROOT);
    if (!normalized.startsWith(BEARER_PREFIX)) {
      return false;
    }

    String apiKey = authorization.substring(7).trim();
    return !apiKey.isBlank() && validationService.validateApiKey(apiKey);
  }

  private boolean hasProfile(Map<String, List<String>> headers) {
    String profileId = firstHeader(headers, PROFILE_ID_HEADER);
    String profileEmail = firstHeader(headers, PROFILE_EMAIL_HEADER);
    return (profileId != null && !profileId.isBlank()) || (profileEmail != null && !profileEmail.isBlank());
  }

  private boolean isValidEnvironmentHost(String host) {
    if (host == null || host.isBlank()) {
      return false;
    }
    String[] parts = host.split("/", -1);
    return parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank();
  }

  private static String firstHeader(Map<String, List<String>> headers, String name) {
    for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
      if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
        List<String> values = entry.getValue();
        if (values != null && !values.isEmpty()) {
          return values.get(0);
        }
      }
    }
    return null;
  }

  private static void close(Session session, CloseReason.CloseCode closeCode, String reason) {
    try {
      session.close(new CloseReason(closeCode, reason));
    } catch (Exception ignored) {
      // no-op
    }
  }
}