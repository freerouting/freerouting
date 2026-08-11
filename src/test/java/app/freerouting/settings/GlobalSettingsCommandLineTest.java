package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for command line argument parsing in GlobalSettings. Covers the -de argument (multiple file
 * types) and the general --key=value mechanism including string-array fields like
 * api_server.endpoints.
 */
class GlobalSettingsCommandLineTest {

  private GlobalSettings settings;

  @BeforeEach
  void setUp() {
    settings = new GlobalSettings();
  }

  @Test
  void singleDsnFile() {
    String[] args = {"-de", "myboard.dsn"};
    settings.applyCommandLineArguments(args);

    assertEquals("myboard.dsn", settings.initialInputFile);
    assertNull(settings.designSessionFilename);
    assertNull(settings.initialRulesFile);
  }

  @Test
  void dsnAndSesWithPlusSeparator() {
    String[] args = {"-de", "myboard.dsn+myboard.ses"};
    settings.applyCommandLineArguments(args);

    assertEquals("myboard.dsn", settings.initialInputFile);
    assertEquals("myboard.ses", settings.designSessionFilename);
    assertNull(settings.initialRulesFile);
  }

  @Test
  void dsnAndRulesWithPlusSeparator() {
    String[] args = {"-de", "myboard.dsn+myboard.rules"};
    settings.applyCommandLineArguments(args);

    assertEquals("myboard.dsn", settings.initialInputFile);
    assertNull(settings.designSessionFilename);
    assertEquals("myboard.rules", settings.initialRulesFile);
  }

  @Test
  void allThreeFilesWithPlusSeparator() {
    String[] args = {"-de", "myboard.dsn+myboard.ses+myboard.rules"};
    settings.applyCommandLineArguments(args);

    assertEquals("myboard.dsn", settings.initialInputFile);
    assertEquals("myboard.ses", settings.designSessionFilename);
    assertEquals("myboard.rules", settings.initialRulesFile);
  }

  @Test
  void filesInDifferentOrder() {
    String[] args = {"-de", "myboard.rules+myboard.dsn+myboard.ses"};
    settings.applyCommandLineArguments(args);

    assertEquals("myboard.dsn", settings.initialInputFile);
    assertEquals("myboard.ses", settings.designSessionFilename);
    assertEquals("myboard.rules", settings.initialRulesFile);
  }

  @Test
  void spaceSeparatedFiles() {
    String[] args = {"-de", "myboard.dsn", "myboard.ses", "myboard.rules"};
    settings.applyCommandLineArguments(args);

    assertEquals("myboard.dsn", settings.initialInputFile);
    assertEquals("myboard.ses", settings.designSessionFilename);
    assertEquals("myboard.rules", settings.initialRulesFile);
  }

  @Test
  void filenameWithSpaces() {
    String[] args = {"-de", "sonde xilinx.dsn"};
    settings.applyCommandLineArguments(args);

    assertEquals("sonde xilinx.dsn", settings.initialInputFile);
    assertNull(settings.designSessionFilename);
    assertNull(settings.initialRulesFile);
  }

  @Test
  void mixedSeparators() {
    String[] args = {"-de", "myboard.dsn+myboard.ses", "myboard.rules"};
    settings.applyCommandLineArguments(args);

    assertEquals("myboard.dsn", settings.initialInputFile);
    assertEquals("myboard.ses", settings.designSessionFilename);
    assertEquals("myboard.rules", settings.initialRulesFile);
  }

  @Test
  void filesWithPaths() {
    String[] args = {"-de", "/path/to/myboard.dsn+/path/to/myboard.ses"};
    settings.applyCommandLineArguments(args);

    assertEquals("/path/to/myboard.dsn", settings.initialInputFile);
    assertEquals("/path/to/myboard.ses", settings.designSessionFilename);
  }

  @Test
  void caseInsensitiveExtensions() {
    String[] args = {"-de", "myboard.DSN+myboard.SES+myboard.RULES"};
    settings.applyCommandLineArguments(args);

    assertEquals("myboard.DSN", settings.initialInputFile);
    assertEquals("myboard.SES", settings.designSessionFilename);
    assertEquals("myboard.RULES", settings.initialRulesFile);
  }

  @Test
  void multipleDsnFilesUsesLast() {
    String[] args = {"-de", "board1.dsn+board2.dsn"};
    settings.applyCommandLineArguments(args);

    // Should use the last DSN file
    assertEquals("board2.dsn", settings.initialInputFile);
  }

  @Test
  void onlySesFile() {
    String[] args = {"-de", "myboard.ses"};
    settings.applyCommandLineArguments(args);

    assertNull(settings.initialInputFile);
    assertEquals("myboard.ses", settings.designSessionFilename);
  }

  @Test
  void onlyRulesFile() {
    String[] args = {"-de", "myboard.rules"};
    settings.applyCommandLineArguments(args);

    assertNull(settings.initialInputFile);
    assertNull(settings.designSessionFilename);
    assertEquals("myboard.rules", settings.initialRulesFile);
  }

  @Test
  void emptyArgument() {
    String[] args = {"-de"};
    settings.applyCommandLineArguments(args);

    assertNull(settings.initialInputFile);
    assertNull(settings.designSessionFilename);
    assertNull(settings.initialRulesFile);
  }

  @Test
  void withOtherArguments() {
    String[] args = {"-de", "myboard.dsn+myboard.ses", "-do", "output.ses", "-mp", "10"};
    settings.applyCommandLineArguments(args);

    assertEquals("myboard.dsn", settings.initialInputFile);
    assertEquals("myboard.ses", settings.designSessionFilename);
    assertEquals("output.ses", settings.initialOutputFile);
    assertEquals(10, settings.getMaxPasses());
  }

  // -------------------------------------------------------------------------
  // Tests for string-array settings via the --key=value mechanism
  // -------------------------------------------------------------------------

  @Test
  void apiServerEndpointsSingleValue() {
    // Reproduces the Docker bug: --api_server-endpoints=http://0.0.0.0:37864
    // must be accepted and stored as a single-element String[] array.
    String[] args = {"--api_server-endpoints=http://0.0.0.0:37864"};
    settings.applyCommandLineArguments(args);

    String[] endpoints = settings.apiServerSettings.endpoints;
    assertNotNull(endpoints, "endpoints must not be null");
    assertEquals(1, endpoints.length, "expected exactly one endpoint");
    assertEquals("http://0.0.0.0:37864", endpoints[0]);
  }

  @Test
  void apiServerEndpointsMultipleValues() {
    // Multiple endpoints expressed as a comma-separated list.
    String[] args = {"--api_server-endpoints=http://0.0.0.0:37864,http://127.0.0.1:37865"};
    settings.applyCommandLineArguments(args);

    String[] endpoints = settings.apiServerSettings.endpoints;
    assertNotNull(endpoints, "endpoints must not be null");
    assertEquals(2, endpoints.length, "expected two endpoints");
    assertEquals("http://0.0.0.0:37864", endpoints[0]);
    assertEquals("http://127.0.0.1:37865", endpoints[1]);
  }

  @Test
  void apiServerEndpointsDotNotation() {
    // Verify that the dot notation also works alongside the dash notation.
    String[] args = {"--api_server.endpoints=http://0.0.0.0:37864"};
    settings.applyCommandLineArguments(args);

    String[] endpoints = settings.apiServerSettings.endpoints;
    assertNotNull(endpoints, "endpoints must not be null");
    assertEquals(1, endpoints.length);
    assertEquals("http://0.0.0.0:37864", endpoints[0]);
  }

  @Test
  void apiServerEndpointsWithSpacesAroundCommas() {
    // Leading/trailing whitespace around comma-separated tokens must be stripped.
    String[] args = {"--api_server-endpoints=http://0.0.0.0:37864 , http://127.0.0.1:37865"};
    settings.applyCommandLineArguments(args);

    String[] endpoints = settings.apiServerSettings.endpoints;
    assertNotNull(endpoints);
    assertEquals(2, endpoints.length);
    assertEquals("http://0.0.0.0:37864", endpoints[0]);
    assertEquals("http://127.0.0.1:37865", endpoints[1]);
  }

  @Test
  void valueContainingEqualSignIsNotTruncated() {
    // If a value contained a '=' (e.g. a URL query param), split("=", 2) must
    // preserve everything after the first '=' as the value.  Use a setting
    // whose value is a String to keep the assertion simple.
    String[] args = {"--api_server.corsOrigins=https://example.com?foo=bar"};
    settings.applyCommandLineArguments(args);

    assertEquals("https://example.com?foo=bar", settings.apiServerSettings.corsOrigins);
  }

  @Test
  void apiAndMcpRateLimitSettingsViaCli() {
    String[] args = {
      "--api_server.rate_limit.enabled=true",
      "--api_server.rate_limit.requests_per_window=15",
      "--api_server.rate_limit.window_seconds=20",
      "--mcp_server.rate_limit.enabled=true",
      "--mcp_server.rate_limit.requests_per_window=5",
      "--mcp_server.rate_limit.window_seconds=12"
    };

    settings.applyCommandLineArguments(args);

    assertTrue(settings.apiServerSettings.rateLimit.enabled);
    assertEquals(15, settings.apiServerSettings.rateLimit.requestsPerWindow);
    assertEquals(20, settings.apiServerSettings.rateLimit.windowSeconds);

    assertTrue(settings.mcpServerSettings.rateLimit.enabled);
    assertEquals(5, settings.mcpServerSettings.rateLimit.requestsPerWindow);
    assertEquals(12, settings.mcpServerSettings.rateLimit.windowSeconds);
  }
}
