package app.freerouting.api;

import static app.freerouting.management.gson.GsonProvider.GSON;

import com.google.gson.JsonObject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * JAX-RS {@link jakarta.ws.rs.ext.ExceptionMapper} that converts a {@link jakarta.ws.rs.NotFoundException}
 * into an HTTP 404 Not Found response with a JSON body carrying a human-readable
 * {@code "error"} field and a {@code "documentation"} link.
 *
 * <p>Jersey throws {@code NotFoundException} when no resource method matches the request URI.
 * Without this mapper the container would return a plain-text or HTML 404 response; registering
 * this mapper ensures all 404 responses from the Freerouting API are consistently JSON-formatted.</p>
 */
@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {

  @Override
  @Produces(MediaType.APPLICATION_JSON)
  public Response toResponse(NotFoundException exception) {
    JsonObject errorMessage = new JsonObject();
    errorMessage.addProperty("error", "The requested resource was not found.");
    errorMessage.addProperty("documentation", "https://github.com/freerouting/freerouting/blob/master/docs/API/API_v1.md");

    String prettyErrorMessage = GSON.toJson(errorMessage);

    return Response.status(Response.Status.NOT_FOUND).entity(prettyErrorMessage).build();
  }
}
