package app.freerouting.autoroute;

import app.freerouting.Freerouting;
import app.freerouting.debug.DebugControl;
import app.freerouting.settings.GlobalSettings;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BatchAutorouterDebugTest {

    @BeforeEach
    void setUp() {
        Freerouting.globalSettings = new GlobalSettings();
    }

    @AfterEach
    void tearDown() {
        DebugControl.getInstance().resume(); // Ensure we don't leave it paused
        Freerouting.globalSettings = null;
    }

    @Test
    void testDebugControlPauseAndResume() throws InterruptedException {
        // Enable single step execution
        Freerouting.globalSettings.debugSettings.singleStepExecution = true;

        DebugControl control = DebugControl.getInstance();

        // Simulate a separate thread running the check loop
        AtomicBoolean checkCompleted = new AtomicBoolean(false);
        Thread runner = new Thread(() -> {
            control.check(1, "Net1");
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
    void testFilterByNet() {
        Freerouting.globalSettings.debugSettings.singleStepExecution = true;
        Freerouting.globalSettings.debugSettings.filterByNet.add("net1");

        DebugControl control = DebugControl.getInstance();
        control.pause();

        // Net2 should pass through even if paused because it's filtered out
        // We run this in main thread, if it blocks, test times out/fails
        long start = System.currentTimeMillis();
        control.check(2, "Net2");
        long end = System.currentTimeMillis();

        Assertions.assertTrue((end - start) < 500, "Net2 should not pause");

        // Net1 should pause. We test this by using a thread
        AtomicBoolean checkCompleted = new AtomicBoolean(false);
        Thread runner = new Thread(() -> {
            control.check(1, "Net1");
            checkCompleted.set(true);
        });

        runner.start();
        try {
            Thread.sleep(100);
            Assertions.assertFalse(checkCompleted.get(), "Net1 should be paused");
            control.resume();
            runner.join(1000);
            Assertions.assertTrue(checkCompleted.get(), "Net1 should complete after resume");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    void testGranularSteppingWithTraceMessage() {
        // Enable single step execution
        Freerouting.globalSettings.debugSettings.singleStepExecution = true;
        Freerouting.globalSettings.debugSettings.filterByNet.add("Net #1");

        DebugControl control = DebugControl.getInstance();
        control.pause();

        // Net #2 should pass (filtered)
        long start = System.currentTimeMillis();
        control.check("Net #2, Trace #123");
        long end = System.currentTimeMillis();
        Assertions.assertTrue((end - start) < 500, "Net #2 should not pause");

        // Net #1 should pause
        AtomicBoolean checkCompleted = new AtomicBoolean(false);
        Thread runner = new Thread(() -> {
            control.check("Net #1, Trace #456"); // This should match filter
            checkCompleted.set(true);
        });

        runner.start();
        try {
            Thread.sleep(100);
            Assertions.assertFalse(checkCompleted.get(), "Net #1 should be paused due to string match");
            control.resume();
            runner.join(1000);
            Assertions.assertTrue(checkCompleted.get(), "Net #1 should complete after resume");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
