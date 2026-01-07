package app.freerouting.api;

import io.swagger.v3.jaxrs2.integration.JaxrsOpenApiContextBuilder;
import io.swagger.v3.oas.integration.OpenApiConfigurationException;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.Set;

/**
 * Resource endpoint for serving the OpenAPI specification.
 * This endpoint generates and serves the OpenAPI spec in JSON format.
 */
@Path("/openapi")
public class OpenApiResource {

    @Context
    private Application application;

    @GET
    @Path("/openapi.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getOpenApiJson(@Context UriInfo uriInfo) {
        try {
            // Configure OpenAPI scanner to find all JAX-RS resources
            SwaggerConfiguration config = new SwaggerConfiguration()
                    .resourcePackages(Set.of("app.freerouting.api"))
                    .prettyPrint(true);

            // Build OpenAPI context and get the spec
            OpenAPI openAPI = new JaxrsOpenApiContextBuilder()
                    .application(application)
                    .openApiConfiguration(config)
                    .buildContext(true)
                    .read();

            if (openAPI != null) {
                // Serialize to JSON
                return Response.ok(io.swagger.v3.core.util.Json.pretty(openAPI)).build();
            } else {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("{\"error\":\"Failed to generate OpenAPI specification\"}")
                        .build();
            }
        } catch (OpenApiConfigurationException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    @GET
    @Path("/openapi.yaml")
    @Produces("application/yaml")
    public Response getOpenApiYaml(@Context UriInfo uriInfo) {
        try {
            // Configure OpenAPI scanner
            SwaggerConfiguration config = new SwaggerConfiguration()
                    .resourcePackages(Set.of("app.freerouting.api"))
                    .prettyPrint(true);

            // Build OpenAPI context and get the spec
            OpenAPI openAPI = new JaxrsOpenApiContextBuilder()
                    .application(application)
                    .openApiConfiguration(config)
                    .buildContext(true)
                    .read();

            if (openAPI != null) {
                // Serialize to YAML
                return Response.ok(io.swagger.v3.core.util.Yaml.pretty(openAPI)).build();
            } else {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("error: Failed to generate OpenAPI specification")
                        .build();
            }
        } catch (OpenApiConfigurationException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("error: " + e.getMessage())
                    .build();
        }
    }
}
