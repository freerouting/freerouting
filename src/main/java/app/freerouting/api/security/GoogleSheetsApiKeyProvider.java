package app.freerouting.api.security;

import app.freerouting.logger.FRLogger;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Google Sheets implementation of the API key provider.
 * <p>
 * This provider reads API keys from a publicly accessible Google Sheet and
 * validates them against the "API Key" and "Access granted?" columns. It
 * implements caching with a 5-minute refresh interval
 * to minimize API calls to Google Sheets.
 * </p>
 *
 * <h2>Google Sheet Structure</h2>
 * The Google Sheet must have the following columns (order doesn't matter):
 * <ul>
 * <li><b>API Key</b>: Contains valid GUID strings (e.g.,
 * 550e8400-e29b-41d4-a716-446655440000)</li>
 * <li><b>Access granted?</b>: Contains "Yes" for granted access, any other
 * value denies access</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * The Google Sheet URL must be configured via the environment variable:
 *
 * <pre>
 * <pre>FREEROUTING__API_SERVER__KEYS_LOCATION__GOOGLE_SHEETS
 * </pre>
 * </pre>
 *
 * <h2>Caching and Resilience</h2>
 * <ul>
 * <li>API keys are cached locally in a thread-safe ConcurrentHashMap</li>
 * <li>Cache refreshes every 5 minutes automatically</li>
 * <li>If refresh fails, cached data is maintained until next successful
 * refresh</li>
 * <li>Initial load happens synchronously during provider construction</li>
 * </ul>
 *
 * @see ApiKeyProvider
 */
public class GoogleSheetsApiKeyProvider implements ApiKeyProvider {

  private static final String APPLICATION_NAME = "Freerouting API Key Validator";
  private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
  private static final int CACHE_REFRESH_INTERVAL_MINUTES = 5;

  /**
   * GUID validation pattern (RFC 4122 compliant). Matches format:
   * xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx where x is a hex digit.
   */
  private static final Pattern GUID_PATTERN = Pattern.compile(
      "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

  private final String spreadsheetId;
  private final String googleApiKey;
  private final Sheets sheetsService;
  private final ScheduledExecutorService scheduler;
  private volatile ConcurrentHashMap<String, Boolean> apiKeyCache;
  private volatile String firstSheetName;
  private volatile boolean isHealthy;
  private volatile long lastSuccessfulRefresh;

  /**
   * Creates a new Google Sheets API key provider.
   *
   * @param spreadsheetUrl The full URL of the Google Sheet (e.g.,
   *                       https://docs.google.com/spreadsheets/d/SHEET_ID/...)
   * @param googleApiKey   The Google API key for authenticating Sheets API
   *                       requests
   * @throws IllegalArgumentException if the spreadsheet URL or API key is invalid
   * @throws RuntimeException         if the Google Sheets service cannot be
   *                                  initialized
   */
  public GoogleSheetsApiKeyProvider(String spreadsheetUrl, String googleApiKey) {
    if (spreadsheetUrl == null || spreadsheetUrl.trim().isEmpty()) {
      throw new IllegalArgumentException("Google Sheets URL cannot be null or empty");
    }
    if (googleApiKey == null || googleApiKey.trim().isEmpty()) {
      throw new IllegalArgumentException("Google API key cannot be null or empty");
    }

    this.spreadsheetId = extractSpreadsheetId(spreadsheetUrl);
    this.googleApiKey = googleApiKey;
    this.apiKeyCache = new ConcurrentHashMap<>();
    this.isHealthy = false;

    try {
      HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
      this.sheetsService = new Sheets.Builder(httpTransport, JSON_FACTORY, null)
          .setApplicationName(APPLICATION_NAME)
          .build();
    } catch (GeneralSecurityException | IOException e) {
      FRLogger.error("Failed to initialize Google Sheets service", null, e);
      throw new RuntimeException("Failed to initialize Google Sheets API client", e);
    }

    // Initial synchronous load
    refresh();

    // Schedule periodic refresh every 5 minutes
    this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread thread = new Thread(r, "GoogleSheetsApiKeyProvider-Refresh");
      thread.setDaemon(true);
      return thread;
    });

    scheduler.scheduleAtFixedRate(
        this::refresh,
        CACHE_REFRESH_INTERVAL_MINUTES,
        CACHE_REFRESH_INTERVAL_MINUTES,
        TimeUnit.MINUTES);

    FRLogger.info("Google Sheets API key provider initialized with " + apiKeyCache.size() + " keys");
  }

  /**
   * Extracts the spreadsheet ID from a Google Sheets URL.
   *
   * @param url The full Google Sheets URL
   * @return The spreadsheet ID
   * @throws IllegalArgumentException if the URL format is invalid
   */
  private String extractSpreadsheetId(String url) {
    // URL format: https://docs.google.com/spreadsheets/d/SPREADSHEET_ID/...
    String[] parts = url.split("/");
    for (int i = 0; i < parts.length - 1; i++) {
      if ("d".equals(parts[i])) {
        String id = parts[i + 1];
        if (id != null && !id.isEmpty()) {
          return id;
        }
      }
    }
    throw new IllegalArgumentException("Invalid Google Sheets URL format: " + url);
  }

  @Override
  public boolean validateApiKey(String apiKey) {
    if (apiKey == null || apiKey.trim().isEmpty()) {
      return false;
    }

    // Validate GUID format
    if (!GUID_PATTERN.matcher(apiKey.trim()).matches()) {
      FRLogger.debug("API key validation failed: invalid GUID format - " + apiKey);
      return false;
    }

    // Check cache
    Boolean isValid = apiKeyCache.get(apiKey.trim());
    if (isValid != null && isValid) {
      FRLogger.debug("API key validation successful: " + apiKey);
      return true;
    }

    FRLogger.debug("API key validation failed: key not found or access not granted - " + apiKey);
    return false;
  }

  @Override
  public void refresh() {
    try {
      FRLogger.debug("Refreshing API keys from Google Sheets...");

      // Get the first sheet name if not already cached
      if (firstSheetName == null) {
        firstSheetName = getFirstSheetName();
        FRLogger.debug("Using sheet: " + firstSheetName);
      }

      // Read all data from the first sheet
      // Google Sheets API will automatically detect the data range when using just
      // the sheet name
      ValueRange response = sheetsService.spreadsheets().values()
          .get(spreadsheetId, firstSheetName)
          .setKey(googleApiKey)
          .execute();

      List<List<Object>> values = response.getValues();
      if (values == null || values.isEmpty()) {
        FRLogger.warn("No data found in Google Sheet '" + firstSheetName + "'");
        return;
      }

      if (values.size() < 2) {
        FRLogger.warn("Google Sheet contains only headers, no API key data found");
        return;
      }

      // Find column indices for "API Key" and "Access granted?"
      List<Object> headers = values.get(0);
      int apiKeyColumnIndex = findColumnIndex(headers, "API Key");
      int accessGrantedColumnIndex = findColumnIndex(headers, "Access granted?");

      if (apiKeyColumnIndex == -1 || accessGrantedColumnIndex == -1) {
        FRLogger.error(
            "Required columns not found in Google Sheet. Expected 'API Key' and 'Access granted?' columns. Found: "
                + headers,
            null, null);
        return;
      }

      // Build new cache
      ConcurrentHashMap<String, Boolean> newCache = new ConcurrentHashMap<>();
      int validKeysCount = 0;
      int skippedRows = 0;

      // Process rows (skip header row)
      for (int i = 1; i < values.size(); i++) {
        List<Object> row = values.get(i);

        // Skip empty rows
        if (row.isEmpty()) {
          continue;
        }

        // Skip rows that don't have enough columns
        if (row.size() <= Math.max(apiKeyColumnIndex, accessGrantedColumnIndex)) {
          skippedRows++;
          continue;
        }

        String apiKey = row.get(apiKeyColumnIndex).toString().trim();
        String accessGranted = row.get(accessGrantedColumnIndex).toString().trim();

        // Skip empty API keys
        if (apiKey.isEmpty()) {
          continue;
        }

        // Validate GUID format
        if (!GUID_PATTERN.matcher(apiKey).matches()) {
          FRLogger.debug("Skipping invalid GUID in row " + (i + 1) + ": " + apiKey);
          skippedRows++;
          continue;
        }

        // Check if access is granted
        boolean isGranted = "Yes".equalsIgnoreCase(accessGranted);
        newCache.put(apiKey, isGranted);

        if (isGranted) {
          validKeysCount++;
        }
      }

      // Atomically replace the cache
      this.apiKeyCache = newCache;
      this.isHealthy = true;
      this.lastSuccessfulRefresh = System.currentTimeMillis();

      String message = String.format(
          "Successfully refreshed %d valid API keys from Google Sheets (total entries: %d, skipped: %d)",
          validKeysCount, newCache.size(), skippedRows);
      FRLogger.info(message);

    } catch (IOException e) {
      FRLogger.error("Failed to refresh API keys from Google Sheets. Using cached data.", null, e);
      this.isHealthy = false;
    }
  }

  /**
   * Gets the name of the first sheet in the spreadsheet.
   *
   * @return The name of the first sheet
   * @throws IOException if unable to retrieve sheet metadata
   */
  private String getFirstSheetName() throws IOException {
    Spreadsheet spreadsheet = sheetsService.spreadsheets()
        .get(spreadsheetId)
        .setKey(googleApiKey)
        .setFields("sheets.properties.title")
        .execute();

    if (spreadsheet.getSheets() == null || spreadsheet.getSheets().isEmpty()) {
      throw new IOException("No sheets found in spreadsheet");
    }

    String sheetName = spreadsheet.getSheets().get(0).getProperties().getTitle();
    if (sheetName == null || sheetName.isEmpty()) {
      throw new IOException("First sheet has no title");
    }

    return sheetName;
  }

  /**
   * Finds the column index for a given header name (case-insensitive).
   *
   * @param headers    The list of header values
   * @param headerName The header name to find
   * @return The column index, or -1 if not found
   */
  private int findColumnIndex(List<Object> headers, String headerName) {
    for (int i = 0; i < headers.size(); i++) {
      if (headers.get(i).toString().trim().equalsIgnoreCase(headerName)) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public String getProviderName() {
    return "Google Sheets";
  }

  @Override
  public boolean isHealthy() {
    return isHealthy;
  }

  /**
   * Gets the number of cached API keys.
   *
   * @return The cache size
   */
  public int getCacheSize() {
    return apiKeyCache.size();
  }

  /**
   * Gets the timestamp of the last successful refresh.
   *
   * @return The timestamp in milliseconds, or 0 if never refreshed successfully
   */
  public long getLastSuccessfulRefresh() {
    return lastSuccessfulRefresh;
  }

  /**
   * Shuts down the refresh scheduler. Should be called when the provider is no
   * longer needed.
   */
  public void shutdown() {
    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
          scheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        scheduler.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
    FRLogger.info("Google Sheets API key provider shut down");
  }
}