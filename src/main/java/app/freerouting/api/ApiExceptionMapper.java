package app.freerouting.api;

import static app.freerouting.management.gson.GsonProvider.GSON;

import com.google.gson.JsonObject;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * JAX-RS {@link jakarta.ws.rs.ext.ExceptionMapper} that catches any unhandled {@link Throwable}
 * thrown by a controller or filter and converts it into an HTTP 500 Internal Server Error
 * response with a JSON body containing an {@code "error"} field and a {@code "documentation"}
 * link.
 *
 * <p>This mapper acts as the last line of defence: structured error cases (e.g. 404, 400) are
 * handled directly inside the controller methods or by {@link NotFoundExceptionMapper}; this
 * mapper covers unexpected runtime exceptions.</p>
 */
@Provider
public class ApiExceptionMapper implements ExceptionMapper<Throwable> {

  @Override
  @Produces(MediaType.APPLICATION_JSON)
  public Response toResponse(Throwable exception) {
    JsonObject errorMessage = new JsonObject();
    errorMessage.addProperty("error", exception.getMessage());
    errorMessage.addProperty("documentation", "https://github.com/freerouting/freerouting/blob/master/docs/API/API_v1.md");

    String prettyErrorMessage = GSON.toJson(errorMessage);

    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(prettyErrorMessage).build();
  }
}
