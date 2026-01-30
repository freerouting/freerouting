package app.freerouting.autoroute;

import app.freerouting.Freerouting;
import app.freerouting.debug.DebugControl;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.GlobalSettings;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the DebugControl debugger functionality.
 */
public class BatchAutorouterDebugTest
{

  @BeforeEach
  void setUp()
  {
    Freerouting.globalSettings = new GlobalSettings();
    DebugControl.getInstance().resetDebugState();
    DebugControl.getInstance().resume();
  }

  @AfterEach
  void tearDown()
  {
    DebugControl.getInstance().resume(); // Ensure we don't leave it paused
    DebugControl.getInstance().resetDebugState();
    Freerouting.globalSettings = null;
  }

  @Test
  void testDebugControlPauseAndResume() throws InterruptedException
  {
    // Enable single step execution
    Freerouting.globalSettings.debugSettings.singleStepExecution = true;

    DebugControl control = DebugControl.getInstance();

    // Simulate a separate thread running the check loop
    AtomicBoolean checkCompleted = new AtomicBoolean(false);
    Thread runner = new Thread(() ->
    {
      control.check("insert_trace_segment", 1, "Net1");
      checkCompleted.set(true);
    });

    // Pause the control
    control.pause();
    Assertions.assertTrue(control.isPaused());

    // Start the runner
    runner.start();

    // Give it a bit of time to reach the check
    Thread.sleep(100);

    // It should be blocked (checkCompleted false)
    Assertions.assertFalse(checkCompleted.get(), "Thread should be paused at check()");

    // Now resume
    control.resume();
    Assertions.assertFalse(control.isPaused());

    // Wait for thread to finish
    runner.join(1000);

    Assertions.assertTrue(checkCompleted.get(), "Thread should have continued after resume");
  }

  @Test
  void testFilterByNet()
  {
    Freerouting.globalSettings.debugSettings.singleStepExecution = true;
    Freerouting.globalSettings.debugSettings.filterByNet.add("Net #1");

    DebugControl control = DebugControl.getInstance();
    control.pause();

    // Net2 should pass through even if paused because it's filtered out
    // We run this in main thread, if it blocks, test times out/fails
    long start = System.currentTimeMillis();
    control.check("insert_trace_segment", 2, "Net2");
    long end = System.currentTimeMillis();

    Assertions.assertTrue((end - start) < 500, "Net2 should not pause");

    // Net1 should pause. We test this by using a thread
    AtomicBoolean checkCompleted = new AtomicBoolean(false);
    Thread runner = new Thread(() ->
    {
      control.check("insert_trace_segment", 1, "Net1");
      checkCompleted.set(true);
    });

    runner.start();
    try
    {
      Thread.sleep(100);
      Assertions.assertFalse(checkCompleted.get(), "Net1 should be paused");
      control.resume();
      runner.join(1000);
      Assertions.assertTrue(checkCompleted.get(), "Net1 should complete after resume");
    }
    catch (InterruptedException e)
    {
      FRLogger.error("Test interrupted", e);
    }
  }

  @Test
  void testGranularSteppingWithTraceMessage()
  {
    // Enable single step execution
    Freerouting.globalSettings.debugSettings.singleStepExecution = true;
    Freerouting.globalSettings.debugSettings.filterByNet.add("Net #1");

    DebugControl control = DebugControl.getInstance();
    control.pause();

    // Net #2 should pass (filtered) - using the overload that takes String message
    long start = System.currentTimeMillis();
    control.check("insert_trace_segment", "Net #2, Trace #123");
    long end = System.currentTimeMillis();
    Assertions.assertTrue((end - start) < 500, "Net #2 should not pause");

    // Net #1 should pause
    AtomicBoolean checkCompleted = new AtomicBoolean(false);
    Thread runner = new Thread(() ->
    {
      // This should match filter
      control.check("insert_trace_segment", "Net #1, Trace #456");
      checkCompleted.set(true);
    });

    runner.start();
    try
    {
      Thread.sleep(100);
      Assertions.assertFalse(checkCompleted.get(),
          "Net #1 should be paused due to string match");
      control.resume();
      runner.join(1000);
      Assertions.assertTrue(checkCompleted.get(), "Net #1 should complete after resume");
    }
    catch (InterruptedException e)
    {
      FRLogger.error("Test interrupted", e);
    }
  }

  @Test
  void testSingleStepNext() throws InterruptedException
  {
    // Enable single step execution
    Freerouting.globalSettings.debugSettings.singleStepExecution = true;

    DebugControl control = DebugControl.getInstance();
    control.pause();
    Assertions.assertTrue(control.isPaused());

    AtomicBoolean checkCompleted = new AtomicBoolean(false);
    Thread runner = new Thread(() ->
    {
      control.check("insert_trace_segment", 1, "Net1");
      checkCompleted.set(true);
    });

    runner.start();
    Thread.sleep(100);
    Assertions.assertFalse(checkCompleted.get(), "Thread should be paused");

    // Trigger next() to execute one step
    control.next();
    runner.join(1000);

    Assertions.assertTrue(checkCompleted.get(), "Thread should complete after next()");
    Assertions.assertTrue(control.isPaused(), "Should remain paused after next()");
  }

  @Test
  void testMultipleStepsWithNext() throws InterruptedException
  {
    Freerouting.globalSettings.debugSettings.singleStepExecution = true;

    DebugControl control = DebugControl.getInstance();
    control.pause();

    AtomicBoolean step1Completed = new AtomicBoolean(false);
    AtomicBoolean step2Completed = new AtomicBoolean(false);

    Thread runner = new Thread(() ->
    {
      control.check("insert_trace_segment", 1, "Net1");
      step1Completed.set(true);
      control.check("insert_trace_segment", 2, "Net2");
      step2Completed.set(true);
    });

    runner.start();
    Thread.sleep(100);
    Assertions.assertFalse(step1Completed.get(), "Step 1 should be paused");

    // Execute first step
    control.next();
    Thread.sleep(100);
    Assertions.assertTrue(step1Completed.get(), "Step 1 should complete");
    Assertions.assertFalse(step2Completed.get(), "Step 2 should be paused");

    // Execute second step
    control.next();
    runner.join(1000);
    Assertions.assertTrue(step2Completed.get(), "Step 2 should complete");
  }

  @Test
  void testTraceInsertionDelay()
  {
    Freerouting.globalSettings.debugSettings.traceInsertionDelay = 100;

    DebugControl control = DebugControl.getInstance();

    long start = System.currentTimeMillis();
    control.check("insert_trace_segment", 1, "Net1");
    long end = System.currentTimeMillis();

    long elapsed = end - start;
    Assertions.assertTrue(elapsed >= 100, "Delay should be at least 100ms, was " + elapsed);
  }

  @Test
  void testOperationFiltering()
  {
    Freerouting.globalSettings.debugSettings.singleStepExecution = true;

    DebugControl control = DebugControl.getInstance();
    control.pause();

    // This operation is not in the default filter list, should return false
    long start = System.currentTimeMillis();
    boolean result = control.check("unknown_operation", 1, "Net1");
    long end = System.currentTimeMillis();

    Assertions.assertFalse(result, "Unknown operation should not be processed");
    Assertions.assertTrue((end - start) < 500, "Should not pause for unknown operation");
  }

  @Test
  void testIsInterestedMethod()
  {
    Freerouting.globalSettings.debugSettings.filterByNet.add("Net #5");

    DebugControl control = DebugControl.getInstance();

    Assertions.assertTrue(control.isInterested("Net #5, Trace #123"));
    Assertions.assertFalse(control.isInterested("Net #6, Trace #456"));
    Assertions.assertTrue(control.isInterested("Processing Net #5"));
  }

  @Test
  void testFastForward() throws InterruptedException
  {
    Freerouting.globalSettings.debugSettings.singleStepExecution = true;

    DebugControl control = DebugControl.getInstance();
    control.pause();
    control.convertToFastForward();

    Assertions.assertFalse(control.isPaused(),
        "Should be resumed after converting to fast forward");

    AtomicBoolean allCompleted = new AtomicBoolean(false);
    Thread runner = new Thread(() ->
    {
      // Same net multiple times - should continue
      control.check("insert_trace_segment", 1, "Net1");
      control.check("insert_trace_segment", 1, "Net1");
      control.check("insert_trace_segment", 1, "Net1");
      // Different net - should pause
      control.check("insert_trace_segment", 2, "Net2");
      allCompleted.set(true);
    });

    runner.start();
    Thread.sleep(200);

    // Should have paused at the net change
    Assertions.assertFalse(allCompleted.get(), "Should pause at net change");
    Assertions.assertTrue(control.isPaused(), "Should be paused after net change");

    // Resume to complete
    control.resume();
    runner.join(1000);
    Assertions.assertTrue(allCompleted.get(), "Should complete after resume");
  }

  @Test
  void testResetMethod()
  {
    Freerouting.globalSettings.debugSettings.singleStepExecution = true;

    DebugControl control = DebugControl.getInstance();
    control.resume();

    control.reset();
    Assertions.assertTrue(control.isPaused(),
        "Should be paused after reset when single step is enabled");

    Freerouting.globalSettings.debugSettings.singleStepExecution = false;
    control.reset();
    Assertions.assertFalse(control.isPaused(),
        "Should not be paused when single step is disabled");
  }

  @Test
  void testDebugStateListener() throws InterruptedException
  {
    DebugControl control = DebugControl.getInstance();

    AtomicBoolean listenerCalled = new AtomicBoolean(false);
    AtomicBoolean pauseState = new AtomicBoolean(false);

    control.addDebugStateListener(isPaused ->
    {
      listenerCalled.set(true);
      pauseState.set(isPaused);
    });

    control.pause();
    Thread.sleep(50);
    Assertions.assertTrue(listenerCalled.get(), "Listener should be called on pause");
    Assertions.assertTrue(pauseState.get(), "Listener should receive paused state");

    listenerCalled.set(false);
    control.resume();
    Thread.sleep(50);
    Assertions.assertTrue(listenerCalled.get(), "Listener should be called on resume");
    Assertions.assertFalse(pauseState.get(), "Listener should receive resumed state");
  }

  @Test
  void testNetHistoryOperations()
  {
    DebugControl control = DebugControl.getInstance();
    control.resetDebugState();

    // Test that empty stack returns -1
    Assertions.assertEquals(-1, control.peekLastStepNet(), "Initial peek should return -1");
    Assertions.assertEquals(-1, control.popLastStepNet(), "Initial pop should return -1");

    // The history is tested indirectly through the testFastForward test
    // which actually exercises the fast forward functionality that builds history
  }

  @Test
  void testDisabledDebugControl()
  {
    // When both single step and delay are disabled, check should return false immediately
    Freerouting.globalSettings.debugSettings.singleStepExecution = false;
    Freerouting.globalSettings.debugSettings.traceInsertionDelay = 0;

    DebugControl control = DebugControl.getInstance();

    long start = System.currentTimeMillis();
    boolean result = control.check("insert_trace_segment", 1, "Net1");
    long end = System.currentTimeMillis();

    Assertions.assertFalse(result, "Should return false when debug is disabled");
    Assertions.assertTrue((end - start) < 50, "Should return immediately");
  }

  @Test
  void testNetFilteringByNumber()
  {
    Freerouting.globalSettings.debugSettings.singleStepExecution = true;
    Freerouting.globalSettings.debugSettings.filterByNet.add("1");

    DebugControl control = DebugControl.getInstance();
    control.pause();

    // Net 1 should match the filter
    AtomicBoolean net1Completed = new AtomicBoolean(false);
    Thread runner1 = new Thread(() ->
    {
      control.check("insert_trace_segment", 1, "Net1");
      net1Completed.set(true);
    });

    runner1.start();
    try
    {
      Thread.sleep(100);
      Assertions.assertFalse(net1Completed.get(), "Net 1 should be paused");
      control.resume();
      runner1.join(1000);
      Assertions.assertTrue(net1Completed.get(), "Net 1 should complete after resume");
    }
    catch (InterruptedException e)
    {
      FRLogger.error("Test interrupted", e);
    }
  }

  @Test
  void testNetFilteringByName()
  {
    Freerouting.globalSettings.debugSettings.singleStepExecution = true;
    Freerouting.globalSettings.debugSettings.filterByNet.add("importantnet");

    DebugControl control = DebugControl.getInstance();
    control.pause();

    // Net with matching name should be filtered
    AtomicBoolean netCompleted = new AtomicBoolean(false);
    Thread runner = new Thread(() ->
    {
      control.check("insert_trace_segment", 1, "importantnet");
      netCompleted.set(true);
    });

    runner.start();
    try
    {
      Thread.sleep(100);
      Assertions.assertFalse(netCompleted.get(), "Net with matching name should be paused");
      control.resume();
      runner.join(1000);
      Assertions.assertTrue(netCompleted.get(), "Net should complete after resume");
    }
    catch (InterruptedException e)
    {
      FRLogger.error("Test interrupted", e);
    }
  }

  @Test
  void testShouldContinueRewindWithHistory()
  {
    DebugControl control = DebugControl.getInstance();
    control.resetDebugState();

    // With empty stack, should not continue
    Assertions.assertFalse(control.shouldContinueRewind(5),
        "Should not continue with empty stack");

    // The history building is tested indirectly through the testFastForward test
    // which exercises the functionality that actually builds and uses history
  }

  @Test
  void testMultipleListeners() throws InterruptedException
  {
    DebugControl control = DebugControl.getInstance();

    AtomicBoolean listener1Called = new AtomicBoolean(false);
    AtomicBoolean listener2Called = new AtomicBoolean(false);

    control.addDebugStateListener(isPaused -> listener1Called.set(true));
    control.addDebugStateListener(isPaused -> listener2Called.set(true));

    control.pause();
    Thread.sleep(50);

    Assertions.assertTrue(listener1Called.get(), "Listener 1 should be called");
    Assertions.assertTrue(listener2Called.get(), "Listener 2 should be called");
  }

  @Test
  void testOperationFilterWithMultipleOperations()
  {
    Freerouting.globalSettings.debugSettings.singleStepExecution = true;

    DebugControl control = DebugControl.getInstance();

    // Test valid operations from the default filter
    Assertions.assertTrue(control.check("insert_trace_segment", 1, "Net1"),
        "insert_trace_segment should be filtered");
    Assertions.assertTrue(control.check("remove_trace_segment", 1, "Net1"),
        "remove_trace_segment should be filtered");
    Assertions.assertTrue(control.check("insert_via", 1, "Net1"),
        "insert_via should be filtered");
    Assertions.assertTrue(control.check("remove_via", 1, "Net1"),
        "remove_via should be filtered");

    // Test invalid operation
    Assertions.assertFalse(control.check("invalid_operation", 1, "Net1"),
        "invalid_operation should not be filtered");
  }

  @Test
  void testEmptyNetFilter()
  {
    Freerouting.globalSettings.debugSettings.singleStepExecution = true;
    // Don't add any filters - empty filter means all nets are permitted
    Freerouting.globalSettings.debugSettings.filterByNet.clear();

    DebugControl control = DebugControl.getInstance();

    // All nets should be interested when filter is empty
    Assertions.assertTrue(control.isInterested("Net #1, Trace #123"));
    Assertions.assertTrue(control.isInterested("Net #999, Trace #456"));
    Assertions.assertTrue(control.isInterested("Any random string"));
  }

  @Test
  void testGetInstanceIsSingleton()
  {
    DebugControl instance1 = DebugControl.getInstance();
    DebugControl instance2 = DebugControl.getInstance();

    Assertions.assertSame(instance1, instance2, "getInstance should return the same instance");
  }

  @Test
  void testPauseDoesNotAffectDisabledDebug()
  {
    // Disable debugging
    Freerouting.globalSettings.debugSettings.singleStepExecution = false;
    Freerouting.globalSettings.debugSettings.traceInsertionDelay = 0;

    DebugControl control = DebugControl.getInstance();
    control.pause();

    // Even though paused, check should return false immediately when debug is disabled
    long start = System.currentTimeMillis();
    boolean result = control.check("insert_trace_segment", 1, "Net1");
    long end = System.currentTimeMillis();

    Assertions.assertFalse(result, "Should return false when debug is disabled");
    Assertions.assertTrue((end - start) < 50, "Should not block even when paused");
  }

  @Test
  void testNetFilterVariations()
  {
    Freerouting.globalSettings.debugSettings.filterByNet.add("Net #10");

    DebugControl control = DebugControl.getInstance();

    // Should match "Net #10"
    Assertions.assertTrue(control.isInterested("Net #10, some text"));

    // Also test that the filter checks different formats
    Freerouting.globalSettings.debugSettings.filterByNet.clear();
    Freerouting.globalSettings.debugSettings.filterByNet.add("10");

    // Should match when just the number is in the filter
    Assertions.assertTrue(control.isInterested("Net #10, Trace #5"));
  }
}