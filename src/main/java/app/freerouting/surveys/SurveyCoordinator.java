package app.freerouting.surveys;

import app.freerouting.settings.GlobalSettings;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/**
 * The headless brain of the micro-survey feature.
 *
 * <p>Decides whether a survey should be shown to this user at all — respecting the opt-out chain
 * ({@code usageAndDiagnosticData.disableAnalytics} + {@code userProfileSettings.allowSurveys}), the
 * local ask-once cache, the {@code min_client_version} guard, and the survey expiry. No Swing
 * dependencies; the GUI layer subscribes via the returned {@link CompletableFuture}.
 *
 * <p>At most one network poll per session: after the first poll the coordinator never asks the
 * server again, regardless of the outcome.
 */
public class SurveyCoordinator {

  private final SurveyClient client;
  private final SurveyCache cache;
  private final String userId;
  private final String clientVersion;
  private final BooleanSupplier surveysAllowed;

  private boolean polledThisSession;

  /**
   * Creates a coordinator.
   *
   * @param client the HTTP transport
   * @param cache the local answered/dismissed cache
   * @param userId the anonymous profile UUID ({@code userProfileSettings.userId})
   * @param clientVersion the desktop client version, e.g. {@code
   *     GlobalSettings.getReleaseSafeVersion()}
   * @param surveysAllowed supplier of the resolved opt-in decision; re-evaluated on each poll
   */
  public SurveyCoordinator(
      SurveyClient client,
      SurveyCache cache,
      String userId,
      String clientVersion,
      BooleanSupplier surveysAllowed) {
    this.client = client;
    this.cache = cache;
    this.userId = userId;
    this.clientVersion = clientVersion;
    this.surveysAllowed = surveysAllowed;
  }

  /**
   * Resets the single-poll session gate. Useful for tests or when dynamic configuration changes.
   */
  public synchronized void resetSession() {
    this.polledThisSession = false;
  }

  /**
   * Polls for an eligible survey. Completes with {@code null} when the user must not be asked —
   * surveys disabled, offline, nothing active, already handled, expired, unsupported schema, or a
   * survey requiring a newer client. Safe to call from any thread.
   */
  public synchronized CompletableFuture<SurveyDefinition> pollForSurvey() {
    if (polledThisSession || surveysAllowed == null || !surveysAllowed.getAsBoolean()) {
      return CompletableFuture.completedFuture(null);
    }
    polledThisSession = true;
    if (client == null) {
      return CompletableFuture.completedFuture(null);
    }
    return client
        .fetchActiveSurvey()
        .thenApply(this::filterEligible)
        // Defensive: never let an unexpected transport error propagate as a failed future.
        .exceptionally(error -> null);
  }

  private SurveyDefinition filterEligible(SurveyDefinition survey) {
    if (survey == null || !survey.isValid() || !survey.isSupportedSchema()) {
      return null;
    }
    if (survey.isExpired(Instant.now())) {
      return null;
    }
    if (cache != null && cache.isHandled(survey.id)) {
      return null;
    }
    if (survey.minClientVersion != null
        && !survey.minClientVersion.isBlank()
        && GlobalSettings.compareVersionStrings(clientVersion, survey.minClientVersion) < 0) {
      return null;
    }
    return survey;
  }

  /**
   * Submits the user's one-click answer. The local cache is marked immediately (ask-once holds even
   * if the network request fails); the POST is fire-and-forget and its outcome never blocks or
   * breaks the caller.
   */
  public void submitAnswer(SurveyDefinition survey, String option) {
    if (survey == null || option == null || option.isBlank()) {
      return;
    }
    if (cache != null) {
      cache.markAnswered(survey.id);
    }
    if (client != null) {
      SurveyResponsePayload payload =
          SurveyResponsePayload.of(survey.id, userId, option, clientVersion);
      // Fire-and-forget — the 400ms optimistic UI must not wait on the network.
      client.submitResponse(payload).exceptionally(error -> false);
    }
  }

  /** Marks the survey as dismissed without submitting an answer. */
  public void dismiss(SurveyDefinition survey) {
    if (survey != null && cache != null) {
      cache.markDismissed(survey.id);
    }
  }
}
