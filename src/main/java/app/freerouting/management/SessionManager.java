package app.freerouting.management;

import static app.freerouting.Freerouting.globalSettings;

import app.freerouting.core.Session;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maintains the active and historical sessions for one Freerouting process.
 *
 * <p>GUI users receive a session for the lifetime of the GUI, while API users receive a session
 * when they authenticate. Multiple sessions can be active at the same time.
 */
public final class SessionManager {

  private static final SessionManager instance = new SessionManager();
  private static final Map<String, Session> sessions = new HashMap<>();
  private volatile UUID monitoredSessionId;

  /** Returns the identifier of the session currently monitored by the GUI. */
  public UUID getMonitoredSessionId() {
    return this.monitoredSessionId;
  }

  /**
   * Sets the identifier of the session currently monitored by the GUI.
   *
   * @param sessionId the session identifier to monitor
   */
  public void setMonitoredSessionId(UUID sessionId) {
    this.monitoredSessionId = sessionId;
  }

  private SessionManager() {}

  /** Returns the process-wide session manager. */
  public static SessionManager getInstance() {
    return instance;
  }

  /**
   * Returns a session by identifier.
   *
   * @param sessionId the session identifier
   * @return the matching session, or {@code null} when it does not exist
   */
  public Session getSession(String sessionId) {
    return sessions.get(sessionId);
  }

  /**
   * Returns a session when it belongs to the specified user.
   *
   * @param sessionId the session identifier
   * @param userId the expected user identifier
   * @return the matching session, or {@code null} when it is absent or owned by another user
   */
  public Session getSession(String sessionId, UUID userId) {
    Session session = getSession(sessionId);

    if (session == null) {
      return null;
    }

    if (!session.userId.equals(userId)) {
      return null;
    }

    return session;
  }

  /**
   * Creates and registers a session for a user.
   *
   * @param userId the session owner's identifier
   * @param host the client host identifier
   * @return the newly created session
   */
  public Session createSession(UUID userId, String host) {
    Session session = new Session(userId, host);
    sessions.put(session.id.toString(), session);
    globalSettings.statistics.incrementSessionsTotal();
    return session;
  }

  /**
   * Removes a session from the registry.
   *
   * @param sessionId the session identifier to remove
   */
  public void removeSession(String sessionId) {
    sessions.remove(sessionId);
  }

  /** Returns the number of currently registered sessions. */
  public int getActiveSessionsCount() {
    return sessions.size();
  }

  /**
   * Lists the identifiers of all sessions owned by a user.
   *
   * @param userId the session owner's identifier
   * @return the matching session identifiers
   */
  public String[] listSessionIds(UUID userId) {
    return Arrays.stream(getSessions(null, userId))
        .map(s -> s.id.toString())
        .toArray(String[]::new);
  }

  /**
   * Returns the primary (GUI) session.
   *
   * @return the registered primary session
   * @throws IllegalArgumentException if no primary session is registered
   */
  public Session getPrimarySession() throws IllegalArgumentException {
    for (Session session : sessions.values()) {
      if (session.isGuiSession) {
        return session;
      }
    }

    throw new IllegalArgumentException("There is no GUI session.");
  }

  /**
   * Sets the session as the primary session.
   *
   * @param sessionId the identifier of the session to mark as the primary session
   * @throws IllegalArgumentException if another primary session exists, the session does not exist,
   *     or the session host is not a valid Freerouting GUI host
   */
  public void setPrimarySession(UUID sessionId) throws IllegalArgumentException {
    // Check if there are any other GUI sessions and if so, throw an exception because only one GUI
    // session is allowed
    for (Session session : sessions.values()) {
      if (session.isGuiSession) {
        throw new IllegalArgumentException("There is already a GUI session.");
      }
    }

    Session session = sessions.get(sessionId.toString());
    if (session != null) {
      session.isGuiSession = true;
    } else {
      throw new IllegalArgumentException("Session with id " + sessionId + " does not exist.");
    }

    if (!session.host.startsWith("Freerouting/")) {
      throw new IllegalArgumentException(
          "Session with id "
              + sessionId
              + " and host "
              + session.host
              + " is not a valid GUI session. GUI sessions must have the prefix "
              + "'Freerouting/' for their host value.");
    }
  }

  /**
   * Returns sessions visible to a user.
   *
   * @param sessionId a specific session identifier, or {@code null} to list all of the user's
   *     sessions
   * @param userId the session owner's identifier
   * @return the matching sessions
   */
  public Session[] getSessions(String sessionId, UUID userId) {
    if (sessionId == null) {
      return sessions.values().stream()
          .filter(s -> s.userId.equals(userId))
          .toArray(Session[]::new);
    } else {
      return new Session[] {sessions.get(sessionId)};
    }
  }
}
