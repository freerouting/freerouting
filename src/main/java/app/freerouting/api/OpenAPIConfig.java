package app.freerouting.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenAPIConfig
{
  @Value("${app.freerouting.dev-url}")
  private String devUrl;

  @Value("${app.freerouting.prod-url}")
  private String prodUrl;

  public OpenAPIConfig()
  {
    //FRLogger.info("OpenApiConfig created");
  }

  /*
  @Bean
  public GroupedOpenApi apiV1()
  {
    return GroupedOpenApi.builder().group("v1").packagesToScan("app.freerouting.api.v1").build();
  }

  //  @Bean
  //  public GroupedOpenApi apiV2()
  //  {
  //    return GroupedOpenApi.builder().group("v2").packagesToScan("app.freerouting.api.v2").build();
  //  }
   */

  @Bean
  public OpenAPI myOpenAPI()
  {
    Server devServer = new Server();
    devServer.setUrl(devUrl);
    devServer.setDescription("Server URL in Development environment");

    Server prodServer = new Server();
    prodServer.setUrl(prodUrl);
    prodServer.setDescription("Server URL in Production environment");

    Contact contact = new Contact();
    contact.setEmail("andras@freerouting.app");
    contact.setName("Andras Fuchs");
    contact.setUrl("https://www.freerouting.app");

    License gpl30License = new License().name("GPL-3.0 License").url("https://choosealicense.com/licenses/gpl-3.0/");

    Info info = new Info().title("Freerouting API").version("1.0").contact(contact).description("This API exposes endpoints to use Freerouting, an open-source PCB auto-router.").license(gpl30License);

    return new OpenAPI().info(info).servers(List.of(devServer, prodServer));
  }
}