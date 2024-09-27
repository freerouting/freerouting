package app.freerouting.management;

import app.freerouting.core.Session;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SessionManagerTest
{
  @Test
  void testGetInstance()
  {
    SessionManager sessionManager1 = SessionManager.getInstance();
    SessionManager sessionManager2 = SessionManager.getInstance();
    assertSame(sessionManager1, sessionManager2, "SessionManager should be a singleton.");
  }

  @Test
  void testCreateAndGetSession()
  {
    SessionManager sessionManager = SessionManager.getInstance();
    UUID userId = UUID.randomUUID();
    Session session = sessionManager.createSession(userId);

    assertNotNull(session, "Created session should not be null.");
    assertNotNull(session.id, "Session ID should be generated.");
    assertEquals(userId, session.userId, "Session should be associated with the correct user ID.");

    Session retrievedSession = sessionManager.getSession(session.id.toString());
    assertEquals(session, retrievedSession, "Retrieved session should match the created session.");
  }

  @Test
  void testRemoveSession()
  {
    SessionManager sessionManager = SessionManager.getInstance();
    UUID userId = UUID.randomUUID();
    Session session = sessionManager.createSession(userId);

    sessionManager.removeSession(session.id.toString());
    Session retrievedSession = sessionManager.getSession(session.id.toString());
    assertNull(retrievedSession, "Removed session should not be retrievable.");
  }

  @Test
  void testGetActiveSessionsCount()
  {
    SessionManager sessionManager = SessionManager.getInstance();
    int initialCount = sessionManager.getActiveSessionsCount();

    sessionManager.createSession(UUID.randomUUID());
    sessionManager.createSession(UUID.randomUUID());
    assertEquals(initialCount + 2, sessionManager.getActiveSessionsCount(), "Active session count should be incremented.");
  }

  @Test
  void testListSessionIds()
  {
    SessionManager sessionManager = SessionManager.getInstance();
    sessionManager.createSession(UUID.randomUUID());
    sessionManager.createSession(UUID.randomUUID());

    String[] sessionIds = sessionManager.listSessionIds();
    assertTrue(sessionIds.length >= 2, "Session ID list should contain at least two IDs.");
  }

  @Test
  void testGetAndSetGuiSession()
  {
    SessionManager sessionManager = SessionManager.getInstance();
    UUID userId = UUID.randomUUID();
    Session session = sessionManager.createSession(userId);

    assertThrows(IllegalArgumentException.class, sessionManager::getGuiSession, "Getting GUI session without setting it should throw an exception.");

    sessionManager.setGuiSession(session.id);
    Session guiSession = sessionManager.getGuiSession();
    assertEquals(session, guiSession, "Retrieved GUI session should match the set session.");
  }
}