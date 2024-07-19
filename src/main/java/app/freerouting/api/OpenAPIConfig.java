package app.freerouting.api;

import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationPath("/api")
public class OpenAPIConfig extends Application
{

  @Override
  public Set<Object> getSingletons()
  {
    OpenAPI openAPI = new OpenAPI().info(new Info().title("API Documentation").description("OpenAPI documentation for our API").version("v1"));

    SwaggerConfiguration oasConfig = new SwaggerConfiguration().openAPI(openAPI).prettyPrint(true).resourcePackages(Stream.of("app.freerouting.api.v1").collect(Collectors.toSet()));

    OpenApiResource openApiResource = new OpenApiResource();
    openApiResource.setOpenApiConfiguration(oasConfig);

    return Collections.singleton(openApiResource);
  }
}