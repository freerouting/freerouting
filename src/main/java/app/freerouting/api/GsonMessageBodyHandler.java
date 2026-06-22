package app.freerouting.api;

import app.freerouting.util.gson.GsonProvider;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * Custom JAX-RS provider that routes all application/json serialization and deserialization
 * through Gson instead of Yasson/JSON-B.
 *
 * <p>This prevents reflection-based serialization issues in modern Java environments, particularly
 * when returning already-serialized JSON String objects or complex GSON model classes.</p>
 */
@Provider
@Priority(Priorities.USER - 1000)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GsonMessageBodyHandler implements MessageBodyReader<Object>, MessageBodyWriter<Object> {

  @Override
  public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return true;
  }

  @Override
  public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                         MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
      throws IOException, WebApplicationException {
    try (InputStreamReader reader = new InputStreamReader(entityStream, StandardCharsets.UTF_8)) {
      return GsonProvider.GSON.fromJson(reader, genericType != null ? genericType : type);
    }
  }

  @Override
  public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return true;
  }

  @Override
  public void writeTo(Object t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                      MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
      throws IOException, WebApplicationException {
    if (t instanceof String stringVal) {
      entityStream.write(stringVal.getBytes(StandardCharsets.UTF_8));
      return;
    }
    try (OutputStreamWriter writer = new OutputStreamWriter(entityStream, StandardCharsets.UTF_8)) {
      GsonProvider.GSON.toJson(t, genericType != null ? genericType : type, writer);
    }
  }
}
