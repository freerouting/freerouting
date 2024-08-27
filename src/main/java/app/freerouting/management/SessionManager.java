package app.freerouting.management;

import app.freerouting.core.Session;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/*
 * This class is responsible for maintaining the list of active sessions.
 * If the user start the GUI, they will be assigned to a new session until they close the GUI.
 * API users will be assigned to a new session when they authenticate by providing their e-mail address.
 * One Freerouting process can have multiple sessions at the same time.
 */
public class SessionManager
{
  private static final SessionManager instance = new SessionManager();
  private static final Map<String, Session> sessions = new HashMap<>();

  private SessionManager()
  {
  }

  public static SessionManager getInstance()
  {
    return instance;
  }

  public Session getSession(String sessionId)
  {
    return sessions.get(sessionId);
  }

  public Session createSession(UUID userId)
  {
    Session session = new Session(userId);
    sessions.put(session.id.toString(), session);
    return session;
  }

  public void removeSession(String sessionId)
  {
    sessions.remove(sessionId);
  }

  public int getActiveSessionsCount()
  {
    return sessions.size();
  }
}