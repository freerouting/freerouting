package app.freerouting.api.security;

import app.freerouting.Freerouting;
import app.freerouting.settings.GlobalSettings;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ApiKeyValidationFilter}, focusing on the authentication-disabled
 * bypass (Issue 650 — sub-issue 2).
 *
 * <p>Uses {@link MockitoExtension} (JUnit 5 integration, Mockito docs section 45) to
 * wire Mockito into the JUnit lifecycle — validates mock setup, reports unused stubs
 * ({@code UnnecessaryStubbingException}), and resets internal Mockito state after each
 * test. The Mockito jar is also configured as a {@code -javaagent} in {@code build.gradle}
 * to satisfy the Java 21+ inline-mock-maker instrumentation requirement (Mockito docs
 * section 0.3), eliminating the self-attach warning.
 */
@ExtendWith(MockitoExtension.class)
public class ApiKeyValidationFilterTest {

    private ApiKeyValidationFilter filter;

    @BeforeEach
    void setUp() {
        ApiKeyValidationService.resetForTesting();
        Freerouting.globalSettings = new GlobalSettings();
        filter = new ApiKeyValidationFilter();
    }

    @AfterEach
    void tearDown() {
        ApiKeyValidationService.resetForTesting();
        Freerouting.globalSettings = null;
    }

    // ------------------------------------------------------------------ helpers

    private ContainerRequestContext mockRequest(String path, String authorizationHeader) {
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn(path);
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        // The Authorization header is only read when authentication is enabled.
        // Use lenient() so MockitoExtension's strict-stub mode does not raise
        // UnnecessaryStubbingException in tests that exercise the early-return path.
        lenient().when(ctx.getHeaderString("Authorization")).thenReturn(authorizationHeader);
        return ctx;
    }

    // ------------------------------------------------------------------ tests

    /**
     * Issue 650 – sub-issue 2: When authentication is explicitly disabled, requests that
     * carry NO Authorization header must be allowed through (no abort, no 401).
     */
    @Test
    void whenAuthDisabled_requestWithoutAuthHeader_isAllowed() throws IOException {
        Freerouting.globalSettings.apiServerSettings.authentication.isEnabled = false; // explicit opt-out

        ContainerRequestContext ctx = mockRequest("v1/sessions/create", null /* no auth header */);

        filter.filter(ctx);

        // abortWith must never have been called
        verify(ctx, never()).abortWith(any(Response.class));
    }

    /**
     * When authentication is disabled, even a request that would otherwise carry an
     * invalid/empty key must be allowed through.
     */
    @Test
    void whenAuthDisabled_requestWithEmptyBearerToken_isAllowed() throws IOException {
        Freerouting.globalSettings.apiServerSettings.authentication.isEnabled = false;

        ContainerRequestContext ctx = mockRequest("v1/jobs", "Bearer ");

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any(Response.class));
    }

    /**
     * When authentication is enabled (the default), a request without an Authorization
     * header must be rejected with 401.
     */
    @Test
    void whenAuthEnabled_requestWithoutAuthHeader_isRejected() throws IOException {
        // authentication.isEnabled defaults to true — no explicit set needed.
        ContainerRequestContext ctx = mockRequest("v1/sessions/create", null);

        filter.filter(ctx);

        verify(ctx, times(1)).abortWith(any(Response.class));
    }

    /**
     * Paths under v1/system/ are always public, regardless of authentication setting.
     * Only stubs that the filter actually reads are declared — MockitoExtension's
     * strict-stub mode raises {@code UnnecessaryStubbingException} for any unused stub.
     * The Authorization header is intentionally NOT stubbed here: the filter returns
     * before reaching that read for excluded paths.
     */
    @Test
    void systemStatusPath_isAlwaysPublic() throws IOException {
        // authentication.isEnabled defaults to true
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("v1/system/status");
        when(ctx.getUriInfo()).thenReturn(uriInfo);

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any(Response.class));
    }

    /**
     * {@link ApiKeyValidationService#isAuthenticationEnabled()} must accurately reflect
     * the configured setting. Note: authentication is ENABLED by default — the flag
     * must be explicitly set to false for unauthenticated local deployments.
     */
    @Test
    void isAuthenticationEnabled_reflectsConfiguration() {
        // Default is true — must return true without any explicit set
        assertTrue(ApiKeyValidationService.getInstance().isAuthenticationEnabled());

        ApiKeyValidationService.resetForTesting();

        Freerouting.globalSettings.apiServerSettings.authentication.isEnabled = false;
        assertFalse(ApiKeyValidationService.getInstance().isAuthenticationEnabled());
    }
}

