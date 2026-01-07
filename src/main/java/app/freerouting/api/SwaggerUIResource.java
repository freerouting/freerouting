package app.freerouting.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

/**
 * Resource for serving Swagger UI static files.
 * Redirects the root path to the Swagger UI index page.
 */
@Path("/swagger-ui")
public class SwaggerUIResource {

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
