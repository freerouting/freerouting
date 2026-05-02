package app.freerouting.api.security;

/**
 * Tri-state result returned by an {@link ApiKeyProvider} after validating an API key.
 *
 * <p>The {@link ApiKeyValidationService} evaluates providers in sequence and stops at the first
 * {@link #ACCESS_GRANTED} or {@link #ACCESS_DENIED} result. If all providers return
 * {@link #UNDECIDED} or {@link #PROVIDER_FAILED} access is ultimately denied.</p>
 */
public enum ApiKeyValidationResult {

  /** The API key was found and access is explicitly permitted. */
  ACCESS_GRANTED,

  /** The API key was found but access is explicitly revoked (e.g. "Access granted?" = "No"). */
  ACCESS_DENIED,

  /**
   * The API key could not be resolved by this provider — either because the key is not in the
   * provider's data source, or because the key format is invalid. The service will try the next
   * configured provider.
   */
  UNDECIDED,

  /**
   * The provider encountered an unrecoverable error (e.g. Google Sheets API unreachable with an
   * empty cache). The service treats this the same as {@link #UNDECIDED} and falls through to the
   * next provider.
   */
  PROVIDER_FAILED
}
