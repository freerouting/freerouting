package app.freerouting.management;

import app.freerouting.core.Session;

import java.util.HashMap;
import java.util.Map;

/*
 * This class is responsible for maintaining the list of active sessions.
 * If the user start the GUI, they will be assigned to a new session until they close the GUI.
 * API users will be assigned to a new session when they authenticate by providing their e-mail address.
 * One Freerouting process can have multiple sessions at the same time.
 */
public class SessionManager
{
  private static final Map<String, Session> sessions = new HashMap<>();

  public static Session getSession(String sessionId)
  {
    return sessions.get(sessionId);
  }

  public static Session createSession()
  {
    Session session = new Session();
    sessions.put(session.id.toString(), session);
    return session;
  }

  public static void removeSession(String sessionId)
  {
    sessions.remove(sessionId);
  }

  public static int getActiveSessionsCount()
  {
    return sessions.size();
  }
}