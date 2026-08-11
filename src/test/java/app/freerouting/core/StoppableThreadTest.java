package app.freerouting.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StoppableThreadTest {

  @Test
  void stopRequest() {
    TestStoppableThread thread = new TestStoppableThread();
    assertFalse(thread.isStopRequested());
    assertFalse(thread.isStopAutoRouterRequested());

    thread.requestStop();
    assertTrue(thread.isStopRequested());
    assertTrue(thread.isStopAutoRouterRequested());
  }

  @Test
  void stopAutoRouterRequest() {
    TestStoppableThread thread = new TestStoppableThread();
    assertFalse(thread.isStopRequested());
    assertFalse(thread.isStopAutoRouterRequested());

    thread.requestStopAutoRouter();
    assertFalse(thread.isStopRequested());
    assertTrue(thread.isStopAutoRouterRequested());
  }

  @Test
  void stopRequestOverridesAutoRouterRequest() {
    TestStoppableThread thread = new TestStoppableThread();
    thread.requestStopAutoRouter();
    thread.requestStop();
    assertTrue(thread.isStopRequested());
    assertTrue(thread.isStopAutoRouterRequested());
  }

  private static class TestStoppableThread extends StoppableThread {

    @Override
    protected void threadAction() {
      // Do nothing
    }
  }
}
