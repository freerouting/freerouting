package app.freerouting.api.mcp;

import com.google.gson.JsonObject;
import jakarta.websocket.Session;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Broadcasts MCP activity events to both SSE and WebSocket subscribers.
 */
public final class McpRealtimeBridge {

  private static final Map<SseEventSink, Sse> SSE_CLIENTS = new ConcurrentHashMap<>();
  private static final Map<Session, Boolean> WS_CLIENTS = new ConcurrentHashMap<>();

  private McpRealtimeBridge() {
  }

  public static void registerSseClient(SseEventSink sink, Sse sse) {
    SSE_CLIENTS.put(sink, sse);
  }

  public static void removeSseClient(SseEventSink sink) {
    SSE_CLIENTS.remove(sink);
  }

  public static void registerWsClient(Session session) {
    WS_CLIENTS.put(session, Boolean.TRUE);
  }

  public static void removeWsClient(Session session) {
    WS_CLIENTS.remove(session);
  }

  public static void broadcast(String eventName, JsonObject payload) {
    JsonObject envelope = new JsonObject();
    envelope.addProperty("event", eventName);
    envelope.add("payload", payload);
    String json = envelope.toString();

    SSE_CLIENTS.forEach((sink, sse) -> {
      if (sink.isClosed()) {
        SSE_CLIENTS.remove(sink);
        return;
      }

      OutboundSseEvent event = sse.newEventBuilder()
          .name(eventName)
          .mediaType(jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE)
          .data(json)
          .build();
      sink.send(event);
    });

    WS_CLIENTS.forEach((session, present) -> {
      if (!session.isOpen()) {
        WS_CLIENTS.remove(session);
        return;
      }
      session.getAsyncRemote().sendText(json);
    });
  }
}