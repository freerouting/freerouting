package app.freerouting.logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;

/**
 * JUnit 5 extension that fails any test which produces one or more {@link LogEntryType#Error}
 * -level entries in {@link FRLogger} during its execution.
 *
 * <h2>Rationale</h2>
 * The application uses {@link FRLogger#error} for conditions that should never occur under
 * correct operation (e.g. failed trace normalisation, unexpected null board state, internal
 * algorithm invariant violations). Silently passing a test that triggers such conditions masks
 * regressions and hides latent bugs. This extension makes the contract explicit: a test that
 * produces an error log is a failing test.
 *
 * <h2>Scope</h2>
 * The check covers the full JUnit lifecycle of each test method:
 * <ol>
 *   <li>Extension {@code beforeEach} starts — start-time anchor is recorded</li>
 *   <li>Test class {@code @BeforeEach} methods run</li>
 *   <li>Test method body executes</li>
 *   <li>Test class {@code @AfterEach} methods run</li>
 *   <li>Extension {@code afterEach} fires — any error entries since step 1 cause failure</li>
 * </ol>
 *
 * <h2>Opt-out</h2>
 * Tests that intentionally exercise error-producing code paths should be annotated with
 * {@link AllowErrorLogs}.  The annotation can be placed on a method or on the whole class
 * (inherited by all methods in that class).
 *
 * <h2>Auto-registration</h2>
 * This extension is globally registered via the JUnit 5 service-loader mechanism
 * ({@code META-INF/services/org.junit.jupiter.api.extension.Extension}) together with
 * {@code junit.jupiter.extensions.autodetection.enabled=true} in
 * {@code junit-platform.properties}. No {@code @ExtendWith} annotation is required on
 * individual test classes.
 */
public class ErrorLogFailExtension implements BeforeEachCallback, AfterEachCallback {

  private static final Namespace NAMESPACE = Namespace.create(ErrorLogFailExtension.class);
  private static final String START_INSTANT_KEY = "startInstant";

  @Override
  public void beforeEach(ExtensionContext context) {
    getStore(context).put(START_INSTANT_KEY, Instant.now());
  }

  @Override
  public void afterEach(ExtensionContext context) {
    // Honour explicit opt-outs at the method or class level.
    if (isExempt(context)) {
      return;
    }

    Instant startInstant = getStore(context).get(START_INSTANT_KEY, Instant.class);
    if (startInstant == null) {
      return; // beforeEach did not run — nothing to check
    }

    LogEntry[] errorEntries = Arrays
        .stream(FRLogger.getLogEntries().getEntries(startInstant, null))
        .filter(e -> e.getType() == LogEntryType.Error)
        .toArray(LogEntry[]::new);

    if (errorEntries.length == 0) {
      return;
    }

    String errorDetails = Arrays
        .stream(errorEntries)
        .map(ErrorLogFailExtension::formatEntry)
        .collect(Collectors.joining("\n\n"));

    org.junit.jupiter.api.Assertions.fail(
        errorEntries.length + " error-level log entry(ies) were produced during this test — "
            + "tests must not produce ERROR logs. "
            + "If this error is intentional, annotate the test with @AllowErrorLogs.\n\n"
            + errorDetails);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static Store getStore(ExtensionContext context) {
    return context.getStore(NAMESPACE);
  }

  /**
   * Returns {@code true} when the test method or its enclosing class carries
   * {@link AllowErrorLogs}.
   */
  private static boolean isExempt(ExtensionContext context) {
    // Check method-level annotation first, then class-level.
    return context.getTestMethod()
               .map(m -> m.isAnnotationPresent(AllowErrorLogs.class))
               .orElse(false)
        || context.getTestClass()
               .map(c -> c.isAnnotationPresent(AllowErrorLogs.class))
               .orElse(false);
  }

  /**
   * Formats a single error entry for inclusion in the JUnit failure message.
   * Includes the stack trace of the attached exception when present.
   */
  private static String formatEntry(LogEntry entry) {
    StringBuilder sb = new StringBuilder();
    sb.append("  [ERROR] ").append(entry.getMessage());

    Throwable ex = entry.getException();
    if (ex != null) {
      StringWriter sw = new StringWriter();
      ex.printStackTrace(new PrintWriter(sw));
      sb.append("\n  Caused by: ").append(sw.toString().stripTrailing());
    }

    return sb.toString();
  }
}