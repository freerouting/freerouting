package app.freerouting.gui.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.logger.AllowErrorLogs;
import app.freerouting.logger.FRLogger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class GuiManagerEdtTest {

  @BeforeAll
  @Timeout(60)
  static void initializeDesktopTestEnvironment() {
    FRLogger.info("Initializing logging and EDT for GuiManagerEdtTest");
    // Cold Windows AWT/class loading is setup, not part of the five-second interrupt handshake.
    // Exercise the real entry point here; startup failure still fails the suite instead of skipping
    // it.
    assertTrue(GuiManager.invokeOnEdt(() -> true), "The desktop EDT must initialize successfully");
  }

  @Test
  void dispatchesInitializerToEdtWhenCalledOffEdt() {
    AtomicBoolean ranOnEdt = new AtomicBoolean();

    boolean initialized =
        GuiManager.invokeOnEdt(
            () -> {
              ranOnEdt.set(SwingUtilities.isEventDispatchThread());
              return true;
            });

    assertTrue(initialized);
    assertTrue(ranOnEdt.get());
  }

  @Test
  void runsInitializerDirectlyWhenAlreadyOnEdt() throws Exception {
    AtomicBoolean ranOnEdt = new AtomicBoolean();
    AtomicBoolean initialized = new AtomicBoolean();

    SwingUtilities.invokeAndWait(
        () ->
            initialized.set(
                GuiManager.invokeOnEdt(
                    () -> {
                      ranOnEdt.set(SwingUtilities.isEventDispatchThread());
                      return true;
                    })));

    assertTrue(initialized.get());
    assertTrue(ranOnEdt.get());
  }

  @Test
  @AllowErrorLogs("Verifies graceful false return when initializer throws on EDT")
  void returnsFalseWhenInitializerThrowsExceptionOnEdt() throws Exception {
    AtomicBoolean initialized = new AtomicBoolean(true);

    SwingUtilities.invokeAndWait(
        () ->
            initialized.set(
                GuiManager.invokeOnEdt(
                    () -> {
                      throw new RuntimeException("Simulated on-EDT initialization failure");
                    })));

    assertFalse(initialized.get());
  }

  @Test
  @AllowErrorLogs("Verifies graceful false return when initializer throws off EDT")
  void returnsFalseWhenInitializerThrowsExceptionOffEdt() {
    boolean initialized =
        GuiManager.invokeOnEdt(
            () -> {
              throw new RuntimeException("Simulated off-EDT initialization failure");
            });

    assertFalse(initialized);
  }

  @Test
  void returnsFalseWhenInitializerIsNull() {
    assertFalse(GuiManager.invokeOnEdt(null));
  }

  @RepeatedTest(5)
  @AllowErrorLogs(
      "Verifies graceful false return and interrupt restoration when thread is interrupted")
  void returnsFalseAndRestoresInterruptWhenInterrupted() throws Exception {
    CountDownLatch initializerStarted = new CountDownLatch(1);
    CountDownLatch allowInitializerToFinish = new CountDownLatch(1);
    AtomicBoolean interruptedState = new AtomicBoolean();
    AtomicBoolean result = new AtomicBoolean(true);
    CountDownLatch threadFinished = new CountDownLatch(1);

    Thread testThread =
        new Thread(
            () -> {
              try {
                result.set(
                    GuiManager.invokeOnEdt(
                        () -> {
                          initializerStarted.countDown();
                          try {
                            allowInitializerToFinish.await(5, TimeUnit.SECONDS);
                          } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                          }
                          return true;
                        }));
                interruptedState.set(Thread.currentThread().isInterrupted());
              } finally {
                threadFinished.countDown();
              }
            });

    testThread.start();
    try {
      assertTrue(initializerStarted.await(5, TimeUnit.SECONDS));
      testThread.interrupt();
      assertTrue(threadFinished.await(5, TimeUnit.SECONDS));
      assertFalse(result.get());
      assertTrue(interruptedState.get());
    } finally {
      // A failed assertion must not leave the EDT blocked or retain this test's worker.
      allowInitializerToFinish.countDown();
      testThread.interrupt();
      testThread.join(5000);
      SwingUtilities.invokeAndWait(() -> {});
      assertFalse(testThread.isAlive(), "The interrupted test worker must exit");
    }
  }
}
