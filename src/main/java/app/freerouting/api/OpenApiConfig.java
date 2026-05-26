package app.freerouting.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * OpenAPI 3.0 configuration for the Freerouting API.
 * This class provides comprehensive API documentation metadata and security
 * configuration.
 */
@ApplicationPath("/api")
@OpenAPIDefinition(info = @Info(title = "Freerouting API", version = "1.0", description = """
                The Freerouting API provides a comprehensive interface for automated PCB routing.

                This API enables developers and AI agents to:
                - Create and manage routing sessions
                - Submit PCB design files for automated routing
                - Monitor routing job progress in real-time
                - Retrieve routing results and DRC reports
                - Track usage analytics

                The API is fully compliant with OpenAPI 3.0 specification, providing standardized,
                clear, and machine-readable documentation for seamless integration.
                """, contact = @Contact(name = "Freerouting Team", url = "https://github.com/freerouting/freerouting", email = "support@freerouting.app"), license = @License(name = "GNU General Public License v3.0", url = "https://www.gnu.org/licenses/gpl-3.0.en.html")), servers = {
                @Server(description = "Production Server", url = "https://api.freerouting.app"),
                @Server(description = "Local Development Server", url = "http://localhost:37864")
}, tags = {
                @Tag(name = "OpenAPI Specification", description = "Endpoints for retrieving the OpenAPI specification in various formats"),
                @Tag(name = "API Documentation", description = "Interactive API documentation interface"),
                @Tag(name = "System", description = "System information and monitoring endpoints"),
                @Tag(name = "Sessions", description = "Session management endpoints"),
                @Tag(name = "Jobs", description = "Routing job management endpoints"),
                @Tag(name = "Analytics", description = "Analytics and tracking endpoints"),
                @Tag(name = "MCP", description = "Model Context Protocol and A2A discovery endpoints"),
                @Tag(name = "Dev - System", description = "Mock system endpoints for testing and development"),
                @Tag(name = "Dev - Sessions", description = "Mock session endpoints for testing and development"),
                @Tag(name = "Dev - Jobs", description = "Mock job endpoints for testing and development")
}, security = {
                @SecurityRequirement(name = "ApiKeyAuth")
})
@SecurityScheme(name = "ApiKeyAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", description = """
                API Key authentication for the Freerouting API.

                Pass your API key (a RFC 4122 GUID) as a Bearer token in the standard HTTP
                Authorization header:

                    Authorization: Bearer 550e8400-e29b-41d4-a716-446655440000

                API keys can be obtained by registering at https://www.freerouting.app.
                When running Freerouting locally (e.g. as a KiCad or EasyEDA plugin) with
                authentication disabled, this header is not required.
                """)
public class OpenApiConfig extends Application {
        // This class serves as the OpenAPI configuration holder
        // The actual API endpoints are defined in the controller classes
}