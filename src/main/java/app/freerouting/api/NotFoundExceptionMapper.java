package app.freerouting.api;

import com.google.gson.JsonObject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import static app.freerouting.management.gson.GsonProvider.GSON;

@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException>
{
  @Override
  @Produces(MediaType.APPLICATION_JSON)
  public Response toResponse(NotFoundException exception)
  {
    JsonObject errorMessage = new JsonObject();
    errorMessage.addProperty("error", "The requested resource was not found.");
    errorMessage.addProperty("documentation", "https://github.com/freerouting/freerouting/blob/master/docs/API_v1.md");

    String prettyErrorMessage = GSON.toJson(errorMessage);

    return Response.status(Response.Status.NOT_FOUND).entity(prettyErrorMessage).build();
  }
}