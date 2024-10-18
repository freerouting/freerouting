package app.freerouting.management;

import app.freerouting.core.Session;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static app.freerouting.Freerouting.globalSettings;

/*
 * This class is responsible for maintaining the list of active and past sessions.
 * If the user start the GUI, they will be assigned to a new session until they close the GUI.
 * API users will be assigned to a new session when they authenticate by providing their e-mail address.
 * One Freerouting process can have multiple active sessions at the same time.
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

  public Session getSession(String sessionId, UUID userId)
  {
    Session session = getSession(sessionId);

    if (session == null)
    {
      return null;
    }

    if (!session.userId.equals(userId))
    {
      return null;
    }

    return session;
  }

  public Session createSession(UUID userId, String host)
  {
    Session session = new Session(userId, host);
    sessions.put(session.id.toString(), session);
    globalSettings.statistics.incrementSessionsTotal();
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

  public String[] listSessionIds(UUID userId)
  {
    return Arrays.stream(getSessions(null, userId)).map(s -> s.id.toString()).toArray(String[]::new);
  }

  public Session getGuiSession() throws IllegalArgumentException
  {
    for (Session session : sessions.values())
    {
      if (session.isGuiSession)
      {
        return session;
      }
    }

    throw new IllegalArgumentException("There is no GUI session.");
  }

  /**
   * Sets the session as a GUI session.
   *
   * @param sessionId
   * @throws IllegalArgumentException
   */
  public void setGuiSession(UUID sessionId) throws IllegalArgumentException
  {
    // Check if there are any other GUI sessions and if so, throw an exception because only one GUI session is allowed
    for (Session session : sessions.values())
    {
      if (session.isGuiSession)
      {
        throw new IllegalArgumentException("There is already a GUI session.");
      }
    }

    Session session = sessions.get(sessionId.toString());
    if (session != null)
    {
      session.isGuiSession = true;
    }
    else
    {
      throw new IllegalArgumentException("Session with id " + sessionId + " does not exist.");
    }

    if (!session.host.startsWith("Freerouting/"))
    {
      throw new IllegalArgumentException("Session with id " + sessionId + " and host " + session.host + " is not a valid GUI session. GUI sessions must have the prefix 'Freerouting/' for their host value.");
    }
  }

  public Session[] getSessions(String sessionId, UUID userId)
  {
    if (sessionId == null)
    {
      return sessions.values().stream().filter(s -> s.userId.equals(userId)).toArray(Session[]::new);
    }
    else
    {
      return new Session[]{sessions.get(sessionId)};
    }
  }
}