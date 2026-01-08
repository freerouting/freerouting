package app.freerouting.api.security;

/**
 * Interface for API key validation providers.
 * <p>
 * This abstraction allows multiple implementations for different API key
 * storage mechanisms
 * (e.g., Google Sheets, database, file-based, web APIs). Implementations should
 * handle caching
 * and resilience for their specific data source.
 * </p>
 *
 * <h2>Implementation Guidelines</h2>
 * <ul>
 * <li>Implement caching to avoid excessive API calls to the data source</li>
 * <li>Handle errors gracefully and maintain cached data when the source is
 * unavailable</li>
 * <li>Validate API key format (e.g., GUID) before checking against the data
 * source</li>
 * <li>Thread-safe implementation is recommended for concurrent request
 * handling</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * ApiKeyProvider provider = new GoogleSheetsApiKeyProvider(sheetsUrl);
 * if (provider.validateApiKey("550e8400-e29b-41d4-a716-446655440000")) {
 *     // Grant access
 * } else {
 *     // Deny access
 * }
 * }</pre>
 *
 * @see GoogleSheetsApiKeyProvider
 */
public interface ApiKeyProvider {

    /**
     * Validates an API key against the provider's data source.
     * <p>
     * The implementation should:
     * <ol>
     * <li>Validate the key format (e.g., check if it's a valid GUID)</li>
     * <li>Check if the key exists in the data source</li>
     * <li>Verify that access is granted for the key</li>
     * </ol>
     * </p>
     *
     * @param apiKey The API key to validate (typically a GUID string)
     * @return {@code true} if the key is valid and has access granted,
     *         {@code false} otherwise
     */
    boolean validateApiKey(String apiKey);

    /**
     * Refreshes the cached API keys from the data source.
     * <p>
     * This method should be called periodically to update the local cache with the
     * latest
     * data from the source. Implementations should handle errors gracefully and
     * maintain
     * the existing cached data if the refresh fails.
     * </p>
     * <p>
     * The refresh operation should be non-blocking and should not throw exceptions
     * that
     * would interrupt normal API key validation.
     * </p>
     */
    void refresh();

    /**
     * Gets the provider name for logging and debugging purposes.
     * <p>
     * This should return a human-readable name that identifies the provider type,
     * such as "Google Sheets", "Database", or "File-based".
     * </p>
     *
     * @return The provider name
     */
    String getProviderName();

    /**
     * Checks if the provider is currently healthy and able to validate API keys.
     * <p>
     * This can be used for health checks and monitoring. A provider might be
     * unhealthy
     * if it cannot connect to its data source, but it may still be able to validate
     * keys using cached data.
     * </p>
     *
     * @return {@code true} if the provider is healthy, {@code false} otherwise
     */
    default boolean isHealthy() {
        return true;
    }
}
