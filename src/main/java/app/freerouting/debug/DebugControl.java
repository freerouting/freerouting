package app.freerouting.debug;

import app.freerouting.Freerouting;
import app.freerouting.logger.FRLogger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the execution flow for debugging purposes.
 * Handles pausing, resuming, stepping, and delays.
 */
public class DebugControl {

    private static final DebugControl INSTANCE = new DebugControl();

    // Execution state
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean shouldStep = new AtomicBoolean(false);

    // Lock for synchronization
    private final Object lock = new Object();

    private DebugControl() {
        // Initialize state based on settings if needed, but usually settings are loaded
        // later.
        // We defer checking settings to the actual check() call or an explicit init.
    }

    public static DebugControl getInstance() {
        return INSTANCE;
    }

    /**
     * Called by the logging framework at potential breakpoints.
     * Parses the impactedItems string to extract net numbers for filtering.
     * 
     * @param impactedItems Description of items involved (e.g. "Net #1, Trace...")
     */
    public void check(String impactedItems) {
        if (!Freerouting.globalSettings.debugSettings.singleStepExecution &&
                Freerouting.globalSettings.debugSettings.traceInsertionDelay == 0) {
            return;
        }

        int netNo = -1;
        String netName = null;

        // Try to parse Net #<No>
        if (impactedItems != null) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("Net #(\\d+)").matcher(impactedItems);
            if (matcher.find()) {
                try {
                    netNo = Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }

        check(netNo, netName);
    }

    /**
     * Called by the engine at potential breakpoints.
     * Handles filtering, delays, and pausing.
     * 
     * @param netNo   The net number currently being processed
     * @param netName The net name currently being processed (optional, can be null)
     */
    public void check(int netNo, String netName) {
        if (!Freerouting.globalSettings.debugSettings.singleStepExecution &&
                Freerouting.globalSettings.debugSettings.traceInsertionDelay == 0) {
            return;
        }

        if (netNo >= 0 && !Freerouting.globalSettings.debugSettings.isNetPermitted(netNo, netName)) {
            return;
        }

        // Handle Delay
        if (Freerouting.globalSettings.debugSettings.traceInsertionDelay > 0) {
            try {
                Thread.sleep(Freerouting.globalSettings.debugSettings.traceInsertionDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Handle Single Stepping
        if (Freerouting.globalSettings.debugSettings.singleStepExecution) {
            synchronized (lock) {
                // If we were asked to step, we reset the flag now as we are about to "execute"
                // this step
                if (shouldStep.compareAndSet(true, false)) {
                    // We proceed execution for this step, but we remain paused for the next one
                    // effectively, step = run one iteration then pause again.
                }

                while (isPaused.get()) {
                    // We are paused.
                    // Check if we have a "step" command pending.
                    if (shouldStep.compareAndSet(true, false)) {
                        // Step command received. Break the wait loop and proceed.
                        // We stay paused for the next time.
                        break;
                    }

                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    // GUI Control Methods

    /**
     * Pauses the execution.
     */
    public void pause() {
        synchronized (lock) {
            isPaused.set(true);
            lock.notifyAll(); // Notify to check state (though logic is "wait while paused")
        }
        FRLogger.debug("DebugControl: Execution Paused");
    }

    /**
     * Resumes execution (Play).
     */
    public void resume() {
        synchronized (lock) {
            isPaused.set(false);
            shouldStep.set(false);
            lock.notifyAll(); // Wake up waiting threads
        }
        FRLogger.debug("DebugControl: Execution Resumed");
    }

    /**
     * Executes a single step (Next).
     * Must be paused to have effect.
     */
    public void next() {
        synchronized (lock) {
            if (isPaused.get()) {
                shouldStep.set(true);
                lock.notifyAll(); // Wake up waiting threads
            }
        }
        FRLogger.debug("DebugControl: Single Step Triggered");
    }

    public boolean isPaused() {
        return isPaused.get();
    }
}
