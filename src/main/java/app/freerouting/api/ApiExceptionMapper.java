package app.freerouting.api;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<Throwable>
{
  @Override
  public Response toResponse(Throwable exception)
  {
    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("{\"error\":\"Internal server error: " + exception.getMessage() + "\"}").build();
  }
}