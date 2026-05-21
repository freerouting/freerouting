package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class McpServerSettingsTest {

  @Test
  void defaults_areInitializedForDedicatedMcpServer() {
    GlobalSettings settings = new GlobalSettings();

    assertNotNull(settings.mcpServerSettings);
    assertFalse(settings.mcpServerSettings.isEnabled);
    assertTrue(settings.mcpServerSettings.isHttpAllowed);
    assertEquals(1, settings.mcpServerSettings.endpoints.length);
    assertEquals("http://127.0.0.1:37964", settings.mcpServerSettings.endpoints[0]);
    assertEquals("http://127.0.0.1:37864", settings.mcpServerSettings.targetApiBaseUrl);
    assertTrue(settings.mcpServerSettings.authentication.isEnabled);
  }

  @Test
  void commandLine_overridesMcpSettings() {
    GlobalSettings settings = new GlobalSettings();

    String[] args = {
        "--mcp_server.enabled=true",
        "--mcp_server.endpoints=http://127.0.0.1:47000,http://127.0.0.1:47001",
        "--mcp_server.authentication.enabled=false",
        "--mcp_server.target_api_base_url=http://127.0.0.1:48000",
        "--mcp_server.cors_origins=http://example.com"
    };

    settings.applyCommandLineArguments(args);

    assertTrue(settings.mcpServerSettings.isEnabled);
    assertEquals(2, settings.mcpServerSettings.endpoints.length);
    assertEquals("http://127.0.0.1:47000", settings.mcpServerSettings.endpoints[0]);
    assertEquals("http://127.0.0.1:47001", settings.mcpServerSettings.endpoints[1]);
    assertFalse(settings.mcpServerSettings.authentication.isEnabled);
    assertEquals("http://127.0.0.1:48000", settings.mcpServerSettings.targetApiBaseUrl);
    assertEquals("http://example.com", settings.mcpServerSettings.cors_origins);
  }
}