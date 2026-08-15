package app.freerouting.debug;

import app.freerouting.Freerouting;
import app.freerouting.logger.FRLogger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the execution flow for debugging purposes. Handles pausing, resuming, stepping, and
 * delays.
 */
public final class DebugControl {

  private static final DebugControl INSTANCE = new DebugControl();
  private static final java.util.regex.Pattern NET_NUMBER_PATTERN =
      java.util.regex.Pattern.compile("Net #(\\d+)");

  // Execution state
  private final AtomicBoolean isPaused = new AtomicBoolean(false);
  private final AtomicBoolean shouldStep = new AtomicBoolean(false);

  // Fast Forward / Rewind State
  private final AtomicBoolean isFastForwarding = new AtomicBoolean(false);
  private final java.util.Stack<Integer> stepNetHistory = new java.util.Stack<>();
  // Lock for synchronization
  private final Object lock = new Object();
  // Listeners
  private final java.util.List<DebugStateListener> listeners = new java.util.ArrayList<>();
  private volatile int currentNetNumber = -1;

  private DebugControl() {
    // Initialize state based on settings if needed, but usually settings are loaded
    // later.
    // We defer checking settings to the actual check() call or an explicit init.
  }

  public static DebugControl getInstance() {
    return INSTANCE;
  }

  /** Registers a listener for debug pause/resume state changes. */
  public void addDebugStateListener(DebugStateListener listener) {
    synchronized (listeners) {
      listeners.add(listener);
    }
  }

  private void notifyListeners() {
    boolean paused = isPaused.get();
    synchronized (listeners) {
      for (DebugStateListener listener : listeners) {
        listener.onDebugStateChanged(paused);
      }
    }
  }

  /** Resets the execution state. Starts in PAUSED mode if single stepping is enabled. */
  public void reset() {
    if (Freerouting.globalSettings.debugSettings.singleStepExecution) {
      pause();
    } else {
      resume();
    }
  }

  /** Resets the fast forward state and clears history. */
  public void resetDebugState() {
    isFastForwarding.set(false);
    currentNetNumber = -1;
    stepNetHistory.clear();
  }

  /** Sets the Fast Forward mode. Execution will continue until the net number changes. */
  public void convertToFastForward() {
    if (isPaused.get()) {
      isFastForwarding.set(true);
      resume();
    }
  }

  /**
   * Checks if we should continue rewinding based on the history.
   *
   * @param targetNetNo The net number we want to rewind back to the beginning of.
   */
  public boolean shouldContinueRewind(int targetNetNo) {
    if (stepNetHistory.isEmpty()) {
      return false;
    }
    // logic: we pop the last step's net.
    // If the stack is invalid or we shifted nets, we might stop.
    // For now, let's assume the caller manages the loop and popping.
    return !stepNetHistory.isEmpty() && stepNetHistory.peek() == targetNetNo;
  }

  /** Removes and returns the net number from the most recent debug step. */
  public int popLastStepNet() {
    if (stepNetHistory.isEmpty()) {
      return -1;
    }
    return stepNetHistory.pop();
  }

  /** Returns the net number from the most recent debug step without removing it. */
  public int peekLastStepNet() {
    if (stepNetHistory.isEmpty()) {
      return -1;
    }
    return stepNetHistory.peek();
  }

  /**
   * Checks if the debug control is interested in the given items based on the filter.
   *
   * @param impactedItems Description of items involved (e.g. "Net #1, Trace...")
   * @return true if the items should be processed/logged, false otherwise.
   */
  public boolean isInterested(String impactedItems) {
    int netNumber = getNetNumber(impactedItems);
    return Freerouting.globalSettings.debugSettings.isNetPermitted(netNumber, null);
  }

  /**
   * Parses the net number from an impacted-items description string.
   *
   * @param impactedItems Description of items involved (e.g. "Net #1, Trace...")
   * @return the parsed net number, or -1 if none was found
   */
  private int getNetNumber(String impactedItems) {
    int netNumber = -1;
    // Try to parse Net #<No>
    if (impactedItems != null) {
      java.util.regex.Matcher matcher = NET_NUMBER_PATTERN.matcher(impactedItems);
      if (matcher.find()) {
        try {
          netNumber = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
          // ignore
        }
      }
    }
    return netNumber;
  }

  /**
   * Called by the logging framework at potential breakpoints. Parses the impactedItems string to
   * extract net numbers for filtering.
   *
   * @param impactedItems Description of items involved (e.g. "Net #1, Trace...")
   */
  public boolean check(String operation, String impactedItems) {

    if (Freerouting.globalSettings == null || Freerouting.globalSettings.debugSettings == null) {
      return false;
    }

    // We defer to check(int, String) for checking enablement flags (step/delay).
    // BUT invalid optimization: we want to SKIP parsing if disabled.
    if (!Freerouting.globalSettings.debugSettings.singleStepExecution
        && Freerouting.globalSettings.debugSettings.traceInsertionDelay == 0) {
      return false;
    }

    int netNumber = getNetNumber(impactedItems);

    if (!Freerouting.globalSettings.debugSettings.isNetPermitted(netNumber, null)) {
      return false;
    }

    return check(operation, netNumber, null);
  }

  /**
   * Called by the engine at potential breakpoints. Handles filtering, delays, and pausing.
   *
   * @param operation The operation being performed (e.g. "insert_trace_segment")
   * @param netNumber The net number currently being processed
   * @param netName The net name currently being processed (optional, can be null)
   * @return true if the operation should be processed/logged, false otherwise.
   */
  public boolean check(String operation, int netNumber, String netName) {
    if (!Freerouting.globalSettings.debugSettings.singleStepExecution
        && Freerouting.globalSettings.debugSettings.traceInsertionDelay == 0) {
      return false;
    }

    if (netNumber >= 0
        && !Freerouting.globalSettings.debugSettings.isNetPermitted(netNumber, netName)) {
      return false;
    }

    if (operation == null || !isInterestedInOperation(operation)) {
      return false;
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

      // Logic for Fast Forwarding
      if (isFastForwarding.get()) {
        // If the net changed, pause!
        if (currentNetNumber != -1 && netNumber != -1 && currentNetNumber != netNumber) {
          FRLogger.debug(
              "FastForward Stopping: Net changed from " + currentNetNumber + " to " + netNumber);
          pause(); // Explicitly pause execution
          // Pause will happen below in the synchronized block logic
        } else {
          if (netNumber != -1) {
            currentNetNumber = netNumber;
          }
          stepNetHistory.push(netNumber);
          return true;
        }
      }

      synchronized (lock) {
        // If we were asked to step, we reset the flag now as we are about to "execute"
        // this step
        if (shouldStep.compareAndSet(true, false)) {
          // We are taking a step
          currentNetNumber = netNumber;
          stepNetHistory.push(netNumber);
        }

        while (isPaused.get()) {
          // We are paused.
          // Check if we have a "step" command pending.
          if (shouldStep.compareAndSet(true, false)) {
            // Step command received. Break the wait loop and proceed.
            // We stay paused for the next time.
            currentNetNumber = netNumber;
            stepNetHistory.push(netNumber);
            break;
          }

          // Check if we switched to fast forward while paused
          if (isFastForwarding.get()) {
            currentNetNumber = netNumber;
            stepNetHistory.push(netNumber);
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

    // Let's indicate that we had an event that we were interested in.
    return true;
  }

  private boolean isInterestedInOperation(String operation) {
    if (operation == null || operation.isEmpty()) {
      return false;
    }

    for (String filterOp : Freerouting.globalSettings.debugSettings.operationFilters) {
      if (operation.equalsIgnoreCase(filterOp)) {
        return true;
      }
    }

    return false;
  }

  /** Pauses the execution. */
  public void pause() {
    synchronized (lock) {
      isPaused.set(true);
      isFastForwarding.set(false);
      lock.notifyAll(); // Notify to check state (though logic is "wait while paused")
    }
    FRLogger.debug("DebugControl: Execution Paused");
    notifyListeners();
  }

  // GUI Control Methods

  /** Resumes execution (Play). */
  public void resume() {
    synchronized (lock) {
      isPaused.set(false);
      shouldStep.set(false);
      lock.notifyAll(); // Wake up waiting threads
    }
    FRLogger.debug("DebugControl: Execution Resumed");
    notifyListeners();
  }

  /** Executes a single step (Next). Must be paused to have effect. */
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

  /** Listener for debug pause/resume state changes. */
  public interface DebugStateListener {

    /** Called when the debug execution state changes. */
    void onDebugStateChanged(boolean isPaused);
  }
}
