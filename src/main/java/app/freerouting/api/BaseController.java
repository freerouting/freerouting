package app.freerouting.api;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.UUID;

/**
 * Base class for all Freerouting API controllers.
 *
 * <p>Provides shared authentication logic via {@link #AuthenticateUser()}, which resolves the
 * caller's UUID from the standard HTTP request headers. All protected controller methods must call
 * this method before performing any business logic.</p>
 *
 * <h2>Authentication headers</h2>
 * <ul>
 *   <li>{@code Freerouting-Profile-ID} — preferred; must be a valid RFC 4122 UUID string.</li>
 *   <li>{@code Freerouting-Profile-Email} — fallback; email-to-UUID resolution is not yet
 *       implemented (see TODO in {@link #AuthenticateUser()}).</li>
 * </ul>
 *
 * <p>Note: the method name intentionally uses PascalCase to match the original naming convention
 * of this code-base; a rename to camelCase is planned as a separate clean-up.</p>
 */
public class BaseController {

  @Context
  private HttpHeaders httpHeaders;

  public BaseController() {
  }

  /**
   * Resolves and returns the authenticated caller's {@link UUID}.
   *
   * <p>Resolution order:
   * <ol>
   *   <li>Parse {@code Freerouting-Profile-ID} header as a UUID.</li>
   *   <li>If that header is absent or unparseable, fall back to
   *       {@code Freerouting-Profile-Email} (email-to-UUID look-up is not yet implemented).</li>
   * </ol>
   *
   * @return the caller's UUID — never {@code null}.
   * @throws IllegalArgumentException if both headers are missing/empty, or if neither yields a
   *         resolvable UUID.
   */
  protected UUID AuthenticateUser() {
    String userIdString = httpHeaders.getHeaderString("Freerouting-Profile-ID");
    String userEmailString = httpHeaders.getHeaderString("Freerouting-Profile-Email");

    if (((userIdString == null) || (userIdString.isEmpty())) && ((userEmailString == null) || (userEmailString.isEmpty()))) {
      throw new IllegalArgumentException("Freerouting-Profile-ID or Freerouting-Profile-Email HTTP request header must be set in order to get authenticated.");
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
      throw new IllegalArgumentException("The user couldn't be authenticated based on the Freerouting-Profile-ID or Freerouting-Profile-Email HTTP request header values.");
    }

    // TODO: authenticate the user by calling the auth endpoint

    return userId;
  }
}
