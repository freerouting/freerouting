package app.freerouting.api;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * MessageBodyWriter that writes raw JSON strings directly to the response output stream.
 *
 * <p>This prevents default JSON binders (like Eclipse Yasson / JSON-B) from intercepting
 * pre-serialized JSON strings and attempting to serialize the String object itself, which
 * can cause reflection errors on internal JVM fields in modern Java versions.</p>
 */
@Provider
@Priority(Priorities.USER - 1000)
@Produces(MediaType.APPLICATION_JSON)
public class JsonStringMessageBodyWriter implements MessageBodyWriter<String> {

  @Override
  public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return String.class.isAssignableFrom(type);
  }

  @Override
  public void writeTo(String t, Class<?> type, Type genericType, Annotation[] annotations,
                      MediaType mediaType, MultivaluedMap<String, Object> httpHeaders,
                      OutputStream entityStream) throws IOException, WebApplicationException {
    entityStream.write(t.getBytes(StandardCharsets.UTF_8));
  }
}
