package app.freerouting.api.mcp;

import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.ServerEndpointConfig;
import java.util.List;
import java.util.Map;

/**
 * Captures HTTP handshake headers so the endpoint can apply the same auth/header checks as REST.
 */
public class McpWebSocketConfigurator extends ServerEndpointConfig.Configurator {

  public static final String HEADERS_PROPERTY = "mcp.handshake.headers";

  @Override
  public void modifyHandshake(
      ServerEndpointConfig sec,
      HandshakeRequest request,
      HandshakeResponse response) {
    sec.getUserProperties().put(HEADERS_PROPERTY, request.getHeaders());
  }

  @SuppressWarnings("unchecked")
  public static Map<String, List<String>> getHeaders(EndpointConfig config) {
    Object value = config.getUserProperties().get(HEADERS_PROPERTY);
    if (value instanceof Map<?, ?> map) {
      return (Map<String, List<String>>) map;
    }
    return Map.of();
  }
}