package app.freerouting.api;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.UUID;

/**
 * Base class for all Freerouting API controllers.
 *
 * <p>Provides shared authentication logic via {@link #authenticateUser()}, which resolves the
 * caller's UUID from the standard HTTP request headers. All protected controller methods must call
 * this method before performing any business logic.
 *
 * <h2>Authentication headers</h2>
 *
 * <ul>
 *   <li>{@code Freerouting-Profile-ID} — preferred; must be a valid RFC 4122 UUID string.
 *   <li>{@code Freerouting-Profile-Email} — fallback; email-to-UUID resolution is not yet
 *       implemented (see TODO in {@link #authenticateUser()}).
 * </ul>
 *
 * <p>Note: the method name intentionally uses PascalCase to match the original naming convention of
 * this code-base; a rename to camelCase is planned as a separate clean-up.
 */
public class BaseController {

  @Context private HttpHeaders httpHeaders;
  @Context private jakarta.ws.rs.core.SecurityContext securityContext;
  private UUID userIdOverride;

  /** Default constructor for BaseController. */
  public BaseController() {}

  /** Sets a user ID override (for testing without full HTTP context). */
  public void setUserIdOverride(UUID userIdOverride) {
    this.userIdOverride = userIdOverride;
  }

  /** Sets the HTTP headers (for testing). */
  public void setHttpHeaders(HttpHeaders httpHeaders) {
    this.httpHeaders = httpHeaders;
  }

  /**
   * Returns the authenticated principal from the request's {@link
   * jakarta.ws.rs.core.SecurityContext}, or {@code null} if unauthenticated.
   *
   * @return the caller's Principal or null
   */
  public java.security.Principal getAuthenticatedPrincipal() {
    return securityContext != null ? securityContext.getUserPrincipal() : null;
  }

  /**
   * Resolves and returns the authenticated caller's {@link UUID}.
   *
   * <p>Resolution order:
   *
   * <ol>
   *   <li>Use {@code userIdOverride} if set (e.g. in unit tests).
   *   <li>Parse {@code Freerouting-Profile-ID} header as a UUID.
   *   <li>If that header is absent or unparsable, fall back to {@code Freerouting-Profile-Email}
   *       (email-to-UUID look-up is not yet implemented).
   * </ol>
   *
   * @return the caller's UUID — never {@code null}.
   * @throws IllegalArgumentException if both headers are missing/empty, or if neither yields a
   *     resolvable UUID.
   */
  protected UUID authenticateUser() {
    if (userIdOverride != null) {
      return userIdOverride;
    }

    String userIdString =
        httpHeaders != null ? httpHeaders.getHeaderString("Freerouting-Profile-ID") : null;
    String userEmailString =
        httpHeaders != null ? httpHeaders.getHeaderString("Freerouting-Profile-Email") : null;

    if (((userIdString == null) || (userIdString.isEmpty()))
        && ((userEmailString == null) || (userEmailString.isEmpty()))) {
      throw new IllegalArgumentException(
          "Freerouting-Profile-ID or Freerouting-Profile-Email HTTP request header must be set in"
              + " order to get authenticated.");
    }

    UUID userId = null;

    // We need to get the userId from the e-mail address first
    if ((userIdString != null) && (!userIdString.isEmpty())) {
      try {
        userId = UUID.fromString(userIdString);
      } catch (IllegalArgumentException _) {
        // We couldn't parse the userId, so we fall back to e-mail address
      }
    }

    if ((userEmailString != null) && (!userEmailString.isEmpty())) {
      // TODO: get userId from e-mail address
    }

    if (userId == null) {
      throw new IllegalArgumentException(
          "The user couldn't be authenticated based on the Freerouting-Profile-ID or"
              + " Freerouting-Profile-Email HTTP request header values.");
    }

    // TODO: authenticate the user by calling the auth endpoint

    return userId;
  }
}
