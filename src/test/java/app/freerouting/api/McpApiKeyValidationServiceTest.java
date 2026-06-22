package app.freerouting.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.api.mcp.McpApiKeyValidationService;
import app.freerouting.settings.GlobalSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class McpApiKeyValidationServiceTest {

  @BeforeEach
  void setUp() {
    McpApiKeyValidationService.resetForTesting();
    Freerouting.globalSettings = new GlobalSettings();
  }

  @AfterEach
  void tearDown() {
    McpApiKeyValidationService.resetForTesting();
    Freerouting.globalSettings = null;
  }

  @Test
  void testAuthenticationDisabledWhenMcpDisabled() {
    Freerouting.globalSettings.apiServerSettings.authentication.isEnabled = true;
    Freerouting.globalSettings.mcpServerSettings.authentication.isEnabled = false;
    McpApiKeyValidationService service = McpApiKeyValidationService.getInstance();

    assertFalse(service.isAuthenticationEnabled());
    assertTrue(service.validateApiKey("any-key"));
  }

  @Test
  void testAuthenticationEnabledWhenOnlyMcpEnabled() {
    // MCP auth is independent of REST API auth; enabling only MCP auth is sufficient.
    Freerouting.globalSettings.apiServerSettings.authentication.isEnabled = false;
    Freerouting.globalSettings.mcpServerSettings.authentication.isEnabled = true;
    McpApiKeyValidationService service = McpApiKeyValidationService.getInstance();

    assertTrue(service.isAuthenticationEnabled());
  }

  @Test
  void testAuthenticationEnabledWhenBothEnabled() {
    Freerouting.globalSettings.apiServerSettings.authentication.isEnabled = true;
    Freerouting.globalSettings.mcpServerSettings.authentication.isEnabled = true;
    McpApiKeyValidationService service = McpApiKeyValidationService.getInstance();

    assertTrue(service.isAuthenticationEnabled());
  }
}
