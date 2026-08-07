package app.freerouting.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApiUsageFilterTest {

  @Test
  void normalizeRoute_replacesUuidSegments() {
    String route =
        ApiUsagePaths.normalizeRoute("GET", "/v1/jobs/550e8400-e29b-41d4-a716-446655440000/output");
    assertEquals("GET v1/jobs/{id}/output", route);
  }

  @Test
  void isUsageTrackingExcluded_skipsAnalyticsAndDocs() {
    assertTrue(ApiUsagePaths.isUsageTrackingExcluded("/v1/analytics/track"));
    assertTrue(ApiUsagePaths.isUsageTrackingExcluded("/openapi/openapi.json"));
    assertTrue(ApiUsagePaths.isUsageTrackingExcluded("/swagger-ui/index.html"));
    assertFalse(ApiUsagePaths.isUsageTrackingExcluded("/v1/jobs/enqueue"));
    assertFalse(ApiUsagePaths.isUsageTrackingExcluded("/v1/system/status"));
  }

  @Test
  void hashBearerToken_returnsStableSha256Hex() {
    String hash = ApiUsageFilter.hashBearerToken("Bearer 550e8400-e29b-41d4-a716-446655440000");
    assertNotNull(hash);
    assertEquals(64, hash.length());
    assertEquals(
        hash, ApiUsageFilter.hashBearerToken("Bearer 550e8400-e29b-41d4-a716-446655440000"));
  }

  @Test
  void hashBearerToken_returnsNullWhenMissing() {
    assertNull(ApiUsageFilter.hashBearerToken(null));
    assertNull(ApiUsageFilter.hashBearerToken("Basic abc"));
  }
}
