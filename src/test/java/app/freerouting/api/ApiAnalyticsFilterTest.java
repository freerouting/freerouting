package app.freerouting.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ApiAnalyticsFilterTest {

  private final ApiAnalyticsFilter filter = new ApiAnalyticsFilter();

  @Test
  void testSuccessfulResponsesAreSkipped() throws IOException {
    ContainerRequestContext req = mock(ContainerRequestContext.class);
    ContainerResponseContext res = mock(ContainerResponseContext.class);

    when(res.getStatus()).thenReturn(200);

    filter.filter(req, res);

    verifyNoInteractions(req);
    verify(res, never()).getEntity();
  }

  @Test
  void testAnalyticsEndpointsAreSkippedOnError() throws IOException {
    ContainerRequestContext req = mock(ContainerRequestContext.class);
    ContainerResponseContext res = mock(ContainerResponseContext.class);

    when(res.getStatus()).thenReturn(500);
    when(req.getProperty("app.freerouting.analytics.method")).thenReturn("POST");
    when(req.getProperty("app.freerouting.analytics.path")).thenReturn("v1/analytics/track");

    filter.filter(req, res);

    // Verify entity was not read because path contains "analytics"
    verify(res, never()).getEntity();
  }

  @Test
  void testAnalyticsIdentityEndpointIsSkippedOnError() throws IOException {
    ContainerRequestContext req = mock(ContainerRequestContext.class);
    ContainerResponseContext res = mock(ContainerResponseContext.class);

    when(res.getStatus()).thenReturn(400);
    when(req.getProperty("app.freerouting.analytics.method")).thenReturn("POST");
    when(req.getProperty("app.freerouting.analytics.path")).thenReturn("v1/analytics/identity");

    filter.filter(req, res);

    verify(res, never()).getEntity();
  }

  @Test
  void testMissingPropertiesAreHandledGracefully() throws IOException {
    ContainerRequestContext req = mock(ContainerRequestContext.class);
    ContainerResponseContext res = mock(ContainerResponseContext.class);

    when(res.getStatus()).thenReturn(404);
    when(req.getProperty("app.freerouting.analytics.method")).thenReturn(null);
    when(req.getProperty("app.freerouting.analytics.path")).thenReturn(null);

    assertDoesNotThrow(() -> filter.filter(req, res));
  }

  @Test
  void testRequestFilterSetsProperties() throws IOException {
    ContainerRequestContext req = mock(ContainerRequestContext.class);
    UriInfo uriInfo = mock(UriInfo.class);

    when(req.getMethod()).thenReturn("GET");
    when(req.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getPath(true)).thenReturn("v1/system/status");
    when(req.getHeaderString("Freerouting-Profile-ID"))
        .thenReturn("550e8400-e29b-41d4-a716-446655440000");

    assertDoesNotThrow(() -> filter.filter(req));
  }
}
