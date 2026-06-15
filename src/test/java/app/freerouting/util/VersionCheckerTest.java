package app.freerouting.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

class VersionCheckerTest {

  @Test
  void testConstructorVersionNormalization() {
    // Test case: starts with "v"
    VersionChecker v1 = new VersionChecker("v1.2.3");
    assertEquals("v1.2.3", v1.getCurrentVersion());

    // Test case: starts with "V"
    VersionChecker v2 = new VersionChecker("V1.2.3");
    assertEquals("v1.2.3", v2.getCurrentVersion());

    // Test case: does not start with "v" or "V"
    VersionChecker v3 = new VersionChecker("1.2.3");
    assertEquals("v1.2.3", v3.getCurrentVersion());

    // Test case: null version input
    VersionChecker v4 = new VersionChecker(null);
    assertEquals("v0.0.0", v4.getCurrentVersion());

    // Test case: empty/blank version input
    VersionChecker v5 = new VersionChecker("   ");
    assertEquals("v0.0.0", v5.getCurrentVersion());
  }

  @Test
  void testProcessResponseWithVariousVersionFormats() {
    HttpClient mockClient = mock(HttpClient.class);
    
    // Equal versions (matching tag_name)
    VersionChecker checker1 = new VersionChecker("1.2.3", mockClient);
    checker1.processResponse("{\"tag_name\":\"v1.2.3\"}"); // should log "No new version available"
    
    // Equal versions (both starting with v)
    VersionChecker checker2 = new VersionChecker("v1.2.3", mockClient);
    checker2.processResponse("{\"tag_name\":\"v1.2.3\"}");

    // Equal versions (GitHub tag has no 'v', constructor has 'v')
    VersionChecker checker3 = new VersionChecker("v1.2.3", mockClient);
    checker3.processResponse("{\"tag_name\":\"1.2.3\"}");

    // Equal versions (GitHub tag has 'v', constructor has no 'v')
    VersionChecker checker4 = new VersionChecker("1.2.3", mockClient);
    checker4.processResponse("{\"tag_name\":\"v1.2.3\"}");

    // Different versions (GitHub tag newer)
    VersionChecker checker5 = new VersionChecker("1.2.3", mockClient);
    checker5.processResponse("{\"tag_name\":\"v1.2.4\"}"); // should log "New version available"
  }

  @Test
  void testProcessResponseMalformedJson() {
    HttpClient mockClient = mock(HttpClient.class);
    VersionChecker checker = new VersionChecker("1.2.3", mockClient);
    // Should handle malformed JSON and null/empty input without throwing exceptions
    checker.processResponse(null);
    checker.processResponse("");
    checker.processResponse("{invalid-json}");
    checker.processResponse("{}");
  }
}

