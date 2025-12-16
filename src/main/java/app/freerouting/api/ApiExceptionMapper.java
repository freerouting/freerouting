package app.freerouting.api;

import static app.freerouting.management.gson.GsonProvider.GSON;

import com.google.gson.JsonObject;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<Throwable> {

  @Override
  @Produces(MediaType.APPLICATION_JSON)
  public Response toResponse(Throwable exception) {
    JsonObject errorMessage = new JsonObject();
    errorMessage.addProperty("error", exception.getMessage());
    errorMessage.addProperty("documentation", "https://github.com/freerouting/freerouting/blob/master/docs/API_v1.md");

    String prettyErrorMessage = GSON.toJson(errorMessage);

    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(prettyErrorMessage).build();
  }
}