package app.freerouting.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StoppableThreadTest {

  private static class TestStoppableThread extends StoppableThread {
    @Override
    protected void thread_action() {
      // Do nothing
    }
  }

  @Test
  void testStopRequest() {
    TestStoppableThread thread = new TestStoppableThread();
    assertFalse(thread.isStopRequested());
    assertFalse(thread.is_stop_auto_router_requested());

    thread.requestStop();
    assertTrue(thread.isStopRequested());
    assertTrue(thread.is_stop_auto_router_requested());
  }

  @Test
  void testStopAutoRouterRequest() {
    TestStoppableThread thread = new TestStoppableThread();
    assertFalse(thread.isStopRequested());
    assertFalse(thread.is_stop_auto_router_requested());

    thread.request_stop_auto_router();
    assertFalse(thread.isStopRequested());
    assertTrue(thread.is_stop_auto_router_requested());
  }

  @Test
  void testStopRequestOverridesAutoRouterRequest() {
    TestStoppableThread thread = new TestStoppableThread();
    thread.request_stop_auto_router();
    thread.requestStop();
    assertTrue(thread.isStopRequested());
    assertTrue(thread.is_stop_auto_router_requested());
  }
}
