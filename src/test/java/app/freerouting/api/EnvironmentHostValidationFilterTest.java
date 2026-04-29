package app.freerouting.api;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EnvironmentHostValidationFilter}.
 *
 * <p>Uses Mockito to isolate the filter's header-validation logic from the JAX-RS
 * runtime. All tests follow the same pattern as {@link app.freerouting.api.security.ApiKeyValidationFilterTest}:
 * mock a {@link ContainerRequestContext} with a specific path and header value, call
 * {@link EnvironmentHostValidationFilter#filter}, then assert whether
 * {@link ContainerRequestContext#abortWith} was called and — when it was — that the
 * response status is 400 and the body contains a useful error message.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("resource") // JAX-RS Response is AutoCloseable but holds no real resources in mock-based tests
class EnvironmentHostValidationFilterTest {

    private EnvironmentHostValidationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new EnvironmentHostValidationFilter();
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Builds a minimal mock {@link ContainerRequestContext} with the given path and
     * value for the {@code Freerouting-Environment-Host} header.
     *
     * @param path        the request path (without leading slash)
     * @param headerValue the header value, or {@code null} to simulate a missing header
     */
    private ContainerRequestContext mockRequest(String path, String headerValue) {
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn(path);
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        // Use lenient() so MockitoExtension's strict-stub mode does not raise
        // UnnecessaryStubbingException for tests that return before reading the header
        // (i.e. excluded-path tests).
        lenient().when(ctx.getHeaderString(EnvironmentHostValidationFilter.HEADER_NAME))
                .thenReturn(headerValue);
        return ctx;
    }

    /** Captures the {@link Response} passed to {@code abortWith} and returns it. */
    private Response captureAbortedResponse(ContainerRequestContext ctx) {
        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(ctx, times(1)).abortWith(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------------ valid header values

    @Test
    void validHeader_toolAndVersion_isAccepted() throws IOException {
        ContainerRequestContext ctx = mockRequest("v1/sessions/create", "KiCad/10.0");

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any(Response.class));
    }

    @ParameterizedTest(name = "header ''{0}'' is accepted")
    @ValueSource(strings = {"KiCad/10.0", "EasyEDA/1.0", "Postman/11.14", "MyScript/1.0", "Target3001!/22"})
    void validHeader_variousTools_areAccepted(String headerValue) throws IOException {
        ContainerRequestContext ctx = mockRequest("v1/jobs/enqueue", headerValue);

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any(Response.class));
    }

    // ------------------------------------------------------------------ missing header

    @Test
    void missingHeader_onProtectedPath_returns400() throws IOException {
        ContainerRequestContext ctx = mockRequest("v1/sessions/create", null);

        filter.filter(ctx);

        Response response = captureAbortedResponse(ctx);
        assertEquals(400, response.getStatus());
    }

    @Test
    void missingHeader_errorBodyMentionsHeaderName() throws IOException {
        ContainerRequestContext ctx = mockRequest("v1/sessions/create", null);

        filter.filter(ctx);

        Response response = captureAbortedResponse(ctx);
        String body = (String) response.getEntity();
        assertTrue(body.contains("Freerouting-Environment-Host"),
                "Error body should mention the header name");
    }

    @Test
    void missingHeader_errorBodyContainsExamples() throws IOException {
        ContainerRequestContext ctx = mockRequest("v1/sessions/create", null);

        filter.filter(ctx);

        Response response = captureAbortedResponse(ctx);
        String body = (String) response.getEntity();
        assertTrue(body.contains("KiCad/10.0"), "Error body should contain a KiCad example");
        assertTrue(body.contains("EasyEDA/1.0"), "Error body should contain an EasyEDA example");
        assertTrue(body.contains("Postman/11.14"), "Error body should contain a Postman example");
    }

    @Test
    void blankHeader_treatedAsMissing_returns400() throws IOException {
        ContainerRequestContext ctx = mockRequest("v1/sessions/create", "   ");

        filter.filter(ctx);

        Response response = captureAbortedResponse(ctx);
        assertEquals(400, response.getStatus());
    }

    // ------------------------------------------------------------------ invalid format

    @ParameterizedTest(name = "malformed header ''{0}'' is rejected with 400")
    @ValueSource(strings = {
            "KiCad",          // no slash at all
            "/10.0",          // empty tool name
            "KiCad/",         // empty version
            "/",              // both parts empty
            "KiCad/10.0/extra" // too many slashes
    })
    void malformedHeader_returns400(String headerValue) throws IOException {
        ContainerRequestContext ctx = mockRequest("v1/sessions/create", headerValue);

        filter.filter(ctx);

        Response response = captureAbortedResponse(ctx);
        assertEquals(400, response.getStatus());
    }

    @Test
    void malformedHeader_errorBodyMentionsActualValue() throws IOException {
        String badHeader = "BadHeaderNoSlash";
        ContainerRequestContext ctx = mockRequest("v1/sessions/create", badHeader);

        filter.filter(ctx);

        Response response = captureAbortedResponse(ctx);
        String body = (String) response.getEntity();
        assertTrue(body.contains(badHeader),
                "Error body should echo the invalid header value so the caller can fix it");
    }

    @Test
    void malformedHeader_errorBodyContainsFormatGuidance() throws IOException {
        ContainerRequestContext ctx = mockRequest("v1/jobs/enqueue", "NoSlashHere");

        filter.filter(ctx);

        Response response = captureAbortedResponse(ctx);
        String body = (String) response.getEntity();
        assertTrue(body.contains("<ToolName>/<Version>") || body.contains("format"),
                "Error body should explain the required format");
    }

    // ------------------------------------------------------------------ excluded paths

    @ParameterizedTest(name = "excluded path ''{0}'' is always public")
    @ValueSource(strings = {
            "v1/system/status",
            "v1/system/environment",
            "v1/analytics/track",
            "dev/anything",
            "openapi/openapi.json",
            "swagger-ui",
            "swagger-ui/index.html"
    })
    void excludedPaths_arePublic_headerNotRequired(String path) throws IOException {
        // No header stub needed because the filter returns before reading it.
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn(path);
        when(ctx.getUriInfo()).thenReturn(uriInfo);

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any(Response.class));
    }

    // ------------------------------------------------------------------ response format

    @Test
    void errorResponse_contentTypeIsJson() throws IOException {
        ContainerRequestContext ctx = mockRequest("v1/sessions/create", null);

        filter.filter(ctx);

        Response response = captureAbortedResponse(ctx);
        String mediaType = response.getMediaType() != null
                ? response.getMediaType().toString()
                : response.getHeaders().getFirst("Content-Type").toString();
        assertTrue(mediaType.contains("application/json"),
                "Error response Content-Type must be application/json");
    }

    @Test
    void errorResponse_bodyIsValidJsonObject() throws IOException {
        ContainerRequestContext ctx = mockRequest("v1/sessions/create", null);

        filter.filter(ctx);

        Response response = captureAbortedResponse(ctx);
        String body = (String) response.getEntity();
        assertTrue(body.trim().startsWith("{") && body.trim().endsWith("}"),
                "Error body must be a JSON object");
        assertTrue(body.contains("\"error\""),
                "Error body must have an 'error' key");
    }
}




