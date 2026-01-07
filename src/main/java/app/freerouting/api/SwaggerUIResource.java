package app.freerouting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

/**
 * Resource for serving Swagger UI.
 * Provides an interactive web interface for exploring and testing the API.
 */
@Path("/swagger-ui")
@Tag(name = "API Documentation", description = "Interactive API documentation interface")
public class SwaggerUIResource {

    @Operation(summary = "Swagger UI interface", description = "Serves the interactive Swagger UI interface for exploring and testing the Freerouting API. Provides a user-friendly way to view all endpoints, their parameters, and try out API calls directly from the browser.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Swagger UI page loaded successfully", content = @Content(mediaType = "text/html"))
    })
    @GET
    @Produces("text/html")
    public Response redirectToIndex() {
        // Serve Swagger UI using CDN resources
        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>Freerouting API Documentation</title>
                    <link rel="stylesheet" type="text/css" href="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5.11.0/swagger-ui.css">
                    <link rel="icon" type="image/png" href="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5.11.0/favicon-32x32.png" sizes="32x32">
                    <style>
                        html { box-sizing: border-box; overflow: -moz-scrollbars-vertical; overflow-y: scroll; }
                        *, *:before, *:after { box-sizing: inherit; }
                        body { margin:0; padding:0; }
                    </style>
                </head>
                <body>
                    <div id="swagger-ui"></div>
                    <script src="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5.11.0/swagger-ui-bundle.js"></script>
                    <script src="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5.11.0/swagger-ui-standalone-preset.js"></script>
                    <script>
                        window.onload = function() {
                            window.ui = SwaggerUIBundle({
                                url: "/openapi/openapi.json",
                                dom_id: '#swagger-ui',
                                deepLinking: true,
                                presets: [
                                    SwaggerUIBundle.presets.apis,
                                    SwaggerUIStandalonePreset
                                ],
                                plugins: [
                                    SwaggerUIBundle.plugins.DownloadUrl
                                ],
                                layout: "StandaloneLayout"
                            });
                        };
                    </script>
                </body>
                </html>
                """;

        return Response.ok(html).type("text/html").build();
    }
}
