package app.freerouting.api;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.UUID;

public class BaseController {

  @Context
  private HttpHeaders httpHeaders;

  public BaseController() {
  }

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