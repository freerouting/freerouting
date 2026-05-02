package app.freerouting.api.security;

import app.freerouting.Freerouting;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.ApiAuthenticationSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Singleton service that orchestrates API key validation across one or more configured
 * {@link ApiKeyProvider} implementations.
 *
 * <h2>Provider pipeline</h2>
 * Providers are evaluated in the order they are declared in the {@code providers} configuration
 * string (comma-separated). The first provider that returns
 * {@link ApiKeyValidationResult#ACCESS_GRANTED} or {@link ApiKeyValidationResult#ACCESS_DENIED}
 * short-circuits evaluation. If every provider returns {@link ApiKeyValidationResult#UNDECIDED}
 * or {@link ApiKeyValidationResult#PROVIDER_FAILED}, access is denied.
 *
 * <h2>Disabled mode</h2>
 * When {@code apiServerSettings.authentication.isEnabled} is {@code false} (or the settings
 * object is absent), {@link #isAuthenticationEnabled()} returns {@code false} and
 * {@link #validateApiKey(String)} unconditionally returns {@code true}. The
 * {@link ApiKeyValidationFilter} short-circuits on this flag before inspecting any header.
 *
 * <h2>Default configuration</h2>
 * Authentication is <strong>enabled by default</strong>. Operators running Freerouting locally
 * (e.g. as a KiCad plugin) must explicitly set {@code authentication.enabled=false} to allow
 * unauthenticated access.
 *
 * <h2>Singleton lifecycle</h2>
 * Obtain the instance via {@link #getInstance()}. Call {@link #resetForTesting()} in unit tests
 * to force re-initialization with a fresh configuration.
 */
public class ApiKeyValidationService {

    private static ApiKeyValidationService instance;
    private final List<ApiKeyProvider> providers;
    private final boolean isEnabled;

    private ApiKeyValidationService() {
        this.providers = new ArrayList<>();

        if (Freerouting.globalSettings == null || Freerouting.globalSettings.apiServerSettings == null) {
            this.isEnabled = false;
            return;
        }

        ApiAuthenticationSettings authSettings = Freerouting.globalSettings.apiServerSettings.authentication;
        this.isEnabled = authSettings != null && Boolean.TRUE.equals(authSettings.isEnabled);

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
                            providers.add(new GoogleSheetsApiKeyProvider(authSettings.googleSheets.sheetUrl,
                                    authSettings.googleSheets.googleApiKey));
                            FRLogger.debug("Added GoogleSheets API Key Provider");
                        } catch (Exception e) {
                            FRLogger.error("Failed to initialize Google Sheets API key provider.", null, e);
                        }
                    } else {
                        FRLogger.warn("GoogleSheets provider configured but sheetUrl or googleApiKey is missing.");
                    }
                }
            }
        }
    }

    /**
     * Returns the singleton {@code ApiKeyValidationService}, creating it lazily on first call.
     *
     * <p>Initialization reads {@link app.freerouting.Freerouting#globalSettings} to determine
     * whether authentication is enabled and which providers to instantiate. Subsequent calls
     * return the already-initialized instance.</p>
     *
     * @return the shared {@code ApiKeyValidationService} instance.
     */
    public static synchronized ApiKeyValidationService getInstance() {
        if (instance == null) {
            instance = new ApiKeyValidationService();
        }
        return instance;
    }

    /**
     * Resets the service instance (for testing purposes).
     */
    public static synchronized void resetForTesting() {
        instance = null;
    }

    /**
     * Returns the list of initialized {@link ApiKeyProvider} instances (for testing purposes).
     *
     * <p>Package-private visibility is intentional — only test classes in the same package
     * should inspect the provider list directly.</p>
     */
    List<ApiKeyProvider> getProviders() {
        return providers;
    }

    /**
     * Returns whether API key authentication is currently enabled.
     *
     * @return {@code true} if authentication is required, {@code false} if all requests are allowed through.
     */
    public boolean isAuthenticationEnabled() {
        return isEnabled;
    }

    /**
     * Validates the provided API key across the configured providers.
     *
     * @param apiKey The API key to validate
     * @return {@code true} if validation is disabled or the key is explicitly
     *         granted. {@code false} if denied or unresolved.
     */
    public boolean validateApiKey(String apiKey) {
        if (!isEnabled) {
            return true;
        }

        if (providers.isEmpty()) {
            FRLogger.warn("API authentication is enabled but no providers are correctly configured. Denying access.");
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
            // if UNDECIDED or PROVIDER_FAILED, continue to next provider
        }

        // If we've exhausted all providers without an explicit grant, deny access.
        return false;
    }
}
