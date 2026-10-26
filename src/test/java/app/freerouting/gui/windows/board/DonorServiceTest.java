package app.freerouting.gui.windows.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class DonorServiceTest {

  @Test
  void bundledFallbackLoadsSuccessfully() {
    DonorService service = new DonorService();
    DonorService.DonorPayload payload = service.getBundledFallback();

    assertNotNull(payload, "Bundled payload must not be null");
    assertNotNull(payload.tiers(), "Tiers list must not be null");
    assertFalse(payload.tiers().isEmpty(), "Bundled payload should have tiers");

    DonorService.DonorTier topTier = payload.tiers().getFirst();
    assertNotNull(topTier.name());
    assertFalse(topTier.donors().isEmpty(), "Top tier should contain initial donors");
    assertEquals("Foxy Laboratories", topTier.donors().getFirst().name());
    assertEquals("https://foxylabs.ee", topTier.donors().getFirst().url());
  }

  @Test
  void parseJsonWithStructuredObjects() {
    String json =
        """
        {
          "updated": "2026-10-26",
          "tiers": [
            {
              "name": "Gold Sponsors",
              "donors": [
                { "name": "Company X", "url": "https://example.com" },
                { "name": "Engineer Y" }
              ]
            }
          ]
        }
        """;

    DonorService service = new DonorService();
    DonorService.DonorPayload payload = service.parseJson(json);

    assertNotNull(payload);
    assertEquals("2026-10-26", payload.updated());
    assertEquals(1, payload.tiers().size());

    DonorService.DonorTier tier = payload.tiers().getFirst();
    assertEquals("Gold Sponsors", tier.name());
    assertEquals(2, tier.donors().size());
    assertEquals("Company X", tier.donors().get(0).name());
    assertEquals("https://example.com", tier.donors().get(0).url());
    assertEquals("Engineer Y", tier.donors().get(1).name());
    assertNull(tier.donors().get(1).url());
  }

  @Test
  void parseJsonWithPrimitiveStrings() {
    String json =
        """
        {
          "updated": "2026-10-26",
          "tiers": [
            {
              "name": "Supporters",
              "donors": ["Alice", "Bob"]
            }
          ]
        }
        """;

    DonorService service = new DonorService();
    DonorService.DonorPayload payload = service.parseJson(json);

    assertNotNull(payload);
    assertEquals(1, payload.tiers().size());
    DonorService.DonorTier tier = payload.tiers().getFirst();
    assertEquals("Supporters", tier.name());
    assertEquals(2, tier.donors().size());
    assertEquals("Alice", tier.donors().get(0).name());
    assertNull(tier.donors().get(0).url());
    assertEquals("Bob", tier.donors().get(1).name());
  }

  @Test
  void parseJsonGracefullyHandlesMalformedAndEmpty() {
    DonorService service = new DonorService();

    DonorService.DonorPayload pNull = service.parseJson(null);
    assertNotNull(pNull);

    DonorService.DonorPayload pEmpty = service.parseJson("   ");
    assertNotNull(pEmpty);

    DonorService.DonorPayload pBroken = service.parseJson("{ broken json ");
    assertNotNull(pBroken);
  }

  @Test
  @SuppressWarnings("unchecked")
  void fetchDonorsAsyncHandlesNetworkFailureGracefully() {
    HttpClient mockClient = mock(HttpClient.class);
    when(mockClient.sendAsync(any(), any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Simulated network down")));

    DonorService service = new DonorService("https://invalid.example.com/donors.json", mockClient);
    CompletableFuture<DonorService.DonorPayload> future = service.fetchDonorsAsync();

    assertNotNull(future);
    DonorService.DonorPayload result = future.join();
    assertNotNull(result);
    assertFalse(result.tiers().isEmpty(), "Must fall back to bundled data on network failure");
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void fetchDonorsAsyncSuccess() {
    HttpClient mockClient = mock(HttpClient.class);
    HttpResponse mockResponse = mock(HttpResponse.class);
    when(mockResponse.body())
        .thenReturn(
            """
            {
              "updated": "2026-10-27",
              "tiers": [
                {
                  "name": "Live Sponsors",
                  "donors": ["Remote Donor"]
                }
              ]
            }
            """);

    CompletableFuture futureResponse = CompletableFuture.completedFuture(mockResponse);
    when(mockClient.sendAsync(any(), any())).thenReturn(futureResponse);

    DonorService service = new DonorService("https://example.com/donors.json", mockClient);
    CompletableFuture<DonorService.DonorPayload> future = service.fetchDonorsAsync();

    DonorService.DonorPayload result = future.join();
    assertNotNull(result);
    assertEquals("2026-10-27", result.updated());
    assertEquals("Live Sponsors", result.tiers().getFirst().name());
    assertEquals("Remote Donor", result.tiers().getFirst().donors().getFirst().name());
  }
}
