package app.freerouting.api.mcp;

import app.freerouting.Freerouting;
import app.freerouting.api.security.ApiKeyProvider;
import app.freerouting.api.security.ApiKeyValidationResult;
import app.freerouting.api.security.GoogleSheetsApiKeyProvider;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.ApiAuthenticationSettings;
import java.util.ArrayList;
import java.util.List;

/**
 * Authentication service for the dedicated MCP server. MCP authentication is controlled solely by
 * {@code mcp_server.authentication.enabled} and is independent of the REST API authentication
 * setting.
 */
public final class McpApiKeyValidationService {

  private static McpApiKeyValidationService instance;
  private final List<ApiKeyProvider> providers;
  private final boolean isEnabled;

  private McpApiKeyValidationService() {
    this.providers = new ArrayList<>();

    if (Freerouting.globalSettings == null
        || Freerouting.globalSettings.mcpServerSettings == null) {
      this.isEnabled = false;
      return;
    }

    ApiAuthenticationSettings authSettings =
        Freerouting.globalSettings.mcpServerSettings.authentication;
    boolean mcpAuthEnabled = authSettings != null && Boolean.TRUE.equals(authSettings.isEnabled);

    this.isEnabled = mcpAuthEnabled;

    if (this.isEnabled && authSettings != null && authSettings.providers != null) {
      String[] providerNames = authSettings.providers.split(",");
      for (String providerName : providerNames) {
        providerName = providerName.trim();
        if ("GoogleSheets".equalsIgnoreCase(providerName)) {
          if (authSettings.googleSheets != null
              && authSettings.googleSheets.sheetUrl != null
              && !authSettings.googleSheets.sheetUrl.isEmpty()
              && authSettings.googleSheets.googleApiKey != null
              && !authSettings.googleSheets.googleApiKey.isEmpty()) {
            try {
              providers.add(
                  new GoogleSheetsApiKeyProvider(
                      authSettings.googleSheets.sheetUrl, authSettings.googleSheets.googleApiKey));
            } catch (Exception e) {
              FRLogger.error("Failed to initialize MCP Google Sheets API key provider.", null, e);
            }
          } else {
            FRLogger.warn(
                "MCP GoogleSheets provider configured but sheetUrl or googleApiKey is missing.");
          }
        }
      }
    }
  }

  /** Returns the singleton instance of McpApiKeyValidationService. */
  public static synchronized McpApiKeyValidationService getInstance() {
    if (instance == null) {
      instance = new McpApiKeyValidationService();
    }
    return instance;
  }

  /** Resets the singleton instance for testing purposes. */
  public static synchronized void resetForTesting() {
    instance = null;
  }

  /** Returns whether MCP API key authentication is enabled. */
  public boolean isAuthenticationEnabled() {
    return isEnabled;
  }

  /**
   * Validates the provided API key against configured providers.
   *
   * @param apiKey API key to validate
   * @return {@code true} if valid or auth disabled; {@code false} otherwise
   */
  public boolean validateApiKey(String apiKey) {
    if (!isEnabled) {
      return true;
    }

    if (providers.isEmpty()) {
      FRLogger.warn(
          "MCP authentication is enabled but no providers are correctly configured. Denying"
              + " access.");
      return false;
    }

    for (ApiKeyProvider provider : providers) {
      ApiKeyValidationResult result = provider.validateApiKey(apiKey);
      if (result == ApiKeyValidationResult.ACCESS_GRANTED) {
        return true;
      }
      if (result == ApiKeyValidationResult.ACCESS_DENIED) {
        return false;
      }
    }

    return false;
  }
}
