package app.freerouting.logger;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts a test method or test class out of the {@link ErrorLogFailExtension} check.
 *
 * <p>Apply this annotation when a test intentionally exercises code paths that produce
 * {@link LogEntryType#Error}-level log entries and the test is specifically verifying
 * that error-handling behaviour. In all other cases the extension will automatically
 * fail the test if any error-level entries appear in {@link FRLogger}'s log during the
 * test lifecycle.
 *
 * <p>Example — opting a single method out:
 * <pre>{@code
 * @Test
 * @AllowErrorLogs("intentionally triggers a parse error to verify recovery behaviour")
 * void myTest() { ... }
 * }</pre>
 *
 * <p>Example — opting an entire class out (all methods inherit the exemption):
 * <pre>{@code
 * @AllowErrorLogs("all methods in this class exercise error-handling paths")
 * class MyErrorHandlingTest { ... }
 * }</pre>
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AllowErrorLogs {

  /**
   * A mandatory human-readable justification explaining why error-level log output is
   * acceptable for this test or class. This is surfaced in CI output so reviewers can
   * quickly understand the exemption without reading the full test.
   */
  String value();
}