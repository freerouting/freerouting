package app.freerouting.api.security;

import app.freerouting.Freerouting;
import app.freerouting.settings.ApiAuthenticationSettings;
import app.freerouting.settings.ApiServerSettings;
import app.freerouting.settings.GlobalSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ApiKeyValidationServiceTest {

    @BeforeEach
    void setUp() {
        ApiKeyValidationService.resetForTesting();
        Freerouting.globalSettings = new GlobalSettings();
        Freerouting.globalSettings.apiServerSettings = new ApiServerSettings();
    }

    @AfterEach
    void tearDown() {
        ApiKeyValidationService.resetForTesting();
        Freerouting.globalSettings = null;
    }

    @Test
    void testAuthenticationDisabled_ReturnsTrue() {
        Freerouting.globalSettings.apiServerSettings.authentication.isEnabled = false;
        ApiKeyValidationService service = ApiKeyValidationService.getInstance();

        assertTrue(service.validateApiKey("any-key"));
    }

    @Test
    void testAuthenticationEnabledButNoProviders_ReturnsFalse() {
        Freerouting.globalSettings.apiServerSettings.authentication.isEnabled = true;
        Freerouting.globalSettings.apiServerSettings.authentication.providers = ""; // No providers
        ApiKeyValidationService service = ApiKeyValidationService.getInstance();

        assertFalse(service.validateApiKey("any-key"));
    }

    @Test
    void testFallback_FirstProviderUndecided_SecondGrants() {
        ApiKeyValidationService service = configureMockProviders(
                ApiKeyValidationResult.UNDECIDED,
                ApiKeyValidationResult.ACCESS_GRANTED);
        assertTrue(service.validateApiKey("test-key"));
    }

    @Test
    void testFallback_FirstProviderDenies_ReturnsFalseImmediately() {
        ApiKeyValidationService service = configureMockProviders(
                ApiKeyValidationResult.ACCESS_DENIED,
                ApiKeyValidationResult.ACCESS_GRANTED // Should not be reached
        );
        assertFalse(service.validateApiKey("test-key"));
    }

    @Test
    void testFallback_AllProvidersUndecided_ReturnsFalse() {
        ApiKeyValidationService service = configureMockProviders(
                ApiKeyValidationResult.UNDECIDED,
                ApiKeyValidationResult.PROVIDER_FAILED);
        assertFalse(service.validateApiKey("test-key"));
    }

    @Test
    void testFallback_FirstProviderGrants_StopsEarly() {
        ApiKeyValidationService service = configureMockProviders(
                ApiKeyValidationResult.ACCESS_GRANTED,
                ApiKeyValidationResult.ACCESS_DENIED // Should not be reached
        );
        assertTrue(service.validateApiKey("test-key"));
    }

    /**
     * Helper that modifies the internal providers list to hold mocks for testing
     * the exact fallback logic.
     */
    private ApiKeyValidationService configureMockProviders(ApiKeyValidationResult... results) {
        Freerouting.globalSettings.apiServerSettings.authentication.isEnabled = true;
        Freerouting.globalSettings.apiServerSettings.authentication.providers = "Mock1, Mock2"; // Just to pass
                                                                                                // initialization checks
                                                                                                // if any

        ApiKeyValidationService service = ApiKeyValidationService.getInstance();
        List<ApiKeyProvider> providers = service.getProviders();
        providers.clear();

        for (int i = 0; i < results.length; i++) {
            final ApiKeyValidationResult result = results[i];
            final String name = "MockProvider_" + i;
            providers.add(new ApiKeyProvider() {
                @Override
                public ApiKeyValidationResult validateApiKey(String apiKey) {
                    return result;
                }

                @Override
                public void refresh() {
                }

                @Override
                public String getProviderName() {
                    return name;
                }
            });
        }
        return service;
    }
}
