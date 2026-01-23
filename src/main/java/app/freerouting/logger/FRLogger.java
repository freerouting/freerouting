package app.freerouting.logger;

import app.freerouting.Freerouting;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/// <summary> Provides logging functionality. </summary>
/**
 * Provides centralized logging functionality for the application.
 * Wraps Log4j2 and maintains an internal list of log entries for UI display.
 */
public class FRLogger {

  public static final DecimalFormat defaultFloatFormat = new DecimalFormat("0.00",
      new java.text.DecimalFormatSymbols(java.util.Locale.US));
  public static final DecimalFormat defaultSignedFloatFormat = new DecimalFormat("+0.00;-0.00",
      new java.text.DecimalFormatSymbols(java.util.Locale.US));
  private static final HashMap<Integer, Instant> perfData = new HashMap<>();
  private static final LogEntries logEntries = new LogEntries();
  public static boolean granularTraceEnabled = false;
  private static Logger logger;
  private static boolean enabled = true;

  private FRLogger() {
  }

  /**
   * Enables or disables logging globally.
   *
   * @param value true to enable logging, false to disable.
   */
  public static void setEnabled(boolean value) {
    enabled = value;
  }

  /**
   * Formats a duration in seconds into a human-readable string (hours, minutes,
   * seconds).
   *
   * @param totalSeconds The total duration in seconds.
   * @return A formatted string representing the duration.
   */
  public static String formatDuration(double totalSeconds) {
    double seconds = totalSeconds;
    double minutes = seconds / 60.0;
    double hours = minutes / 60.0;

    hours = Math.floor(hours);
    minutes = Math.floor(minutes % 60.0);
    seconds = seconds % 60.0;

    String hoursText = hours > 0 ? (int) hours + (hours == 1 ? " hour " : " hours ") : "";

    String minutesText = minutes > 0 ? (int) minutes + (minutes == 1 ? " minute " : " minutes ") : "";

    return hoursText + minutesText + defaultFloatFormat.format(seconds) + " seconds";
  }

  /**
   * Formats a score with details about incomplete items and violations.
   *
   * @param score      The routing score.
   * @param incomplete The number of unrouted items.
   * @param violations The number of design rule violations.
   * @return A formatted string representing the score and any issues.
   */
  public static String formatScore(float score, int incomplete, int violations) {
    StringBuilder sb = new StringBuilder(defaultFloatFormat.format(score));

    // Only include unrouted and violations if they exist
    if (incomplete > 0 || violations > 0) {
      sb.append(" (");

      // Add unrouted info only if there are any
      if (incomplete > 0) {
        sb
            .append(incomplete)
            .append(" unrouted");
      }

      // Add separator if both unrouted and violations exist
      if (incomplete > 0 && violations > 0) {
        sb.append(" and ");
      }

      // Add violations info only if there are any
      if (violations > 0) {
        sb
            .append(violations)
            .append(violations == 1 ? " violation" : " violations");
      }

      sb.append(")");
    }

    return sb.toString();
  }

  /**
   * Records the start time for a performance trace.
   *
   * @param perfId A unique identifier for the operation being traced (often the
   *               method name).
   */
  public static void traceEntry(String perfId) {
    if (!enabled) {
      return;
    }
    if (logger == null) {
      logger = LogManager.getLogger(Freerouting.class);
    }

    perfData.put(perfId.hashCode(), Instant.now());
  }

  /**
   * Records the end of a performance trace and logs the duration.
   *
   * @param perfId A unique identifier for the operation being traced.
   * @return The duration of the operation in seconds.
   */
  public static double traceExit(String perfId) {
    if (!enabled) {
      return 0.0;
    }
    if (logger == null) {
      logger = LogManager.getLogger(Freerouting.class);
    }

    return traceExit(perfId, null);
  }

  /**
   * Records the end of a performance trace with an optional result object and
   * logs the duration.
   *
   * @param perfId A unique identifier for the operation being traced.
   * @param result An optional result object to include in the log message.
   * @return The duration of the operation in seconds.
   */
  public static double traceExit(String perfId, Object result) {
    if (!enabled) {
      return 0.0;
    }
    if (logger == null) {
      logger = LogManager.getLogger(Freerouting.class);
    }

    long timeElapsed = 0;
    try {
      timeElapsed = Duration
          .between(perfData.get(perfId.hashCode()), Instant.now())
          .toMillis();
    } catch (Exception _) {
      // we can ignore this exception
    }

    perfData.remove(perfId.hashCode());
    if (timeElapsed < 0) {
      timeElapsed = 0;
    }

    String logMessage = "Method '" + perfId.replace("{}", result != null ? result.toString() : "(null)")
        + "' was performed in " + FRLogger.formatDuration(timeElapsed / 1000.0) + ".";

    FRLogger.trace(logMessage);

    return timeElapsed / 1000.0;
  }

  /**
   * Logs an INFO message.
   *
   * @param msg   The message to log.
   * @param topic An optional topic UUID associated with the message.
   * @return The created LogEntry.
   */
  public static LogEntry info(String msg, UUID topic) {
    LogEntry logEntry = logEntries.add(LogEntryType.Info, msg, topic);

    if (!enabled) {
      return null;
    }
    if (logger == null) {
      logger = LogManager.getLogger(Freerouting.class);
    }

    logger.info(msg);

    return logEntry;
  }

  /**
   * Logs an INFO message without a topic.
   *
   * @param msg The message to log.
   * @return The created LogEntry.
   */
  public static LogEntry info(String msg) {
    return info(msg, null);
  }

  /**
   * Logs a WARNING message.
   *
   * @param msg   The message to log.
   * @param topic An optional topic UUID associated with the message.
   * @return The created LogEntry.
   */
  public static LogEntry warn(String msg, UUID topic) {
    LogEntry logEntry = logEntries.add(LogEntryType.Warning, msg, topic);

    if (!enabled) {
      return null;
    }
    if (logger == null) {
      logger = LogManager.getLogger(Freerouting.class);
    }

    logger.warn(msg);

    return logEntry;
  }

  /**
   * Logs a WARNING message without a topic.
   *
   * @param msg The message to log.
   * @return The created LogEntry.
   */
  public static LogEntry warn(String msg) {
    return warn(msg, null);
  }

  /**
   * Logs a DEBUG message.
   *
   * @param msg   The message to log.
   * @param topic An optional topic UUID associated with the message.
   * @return The created LogEntry.
   */
  public static LogEntry debug(String msg, UUID topic) {
    if (!enabled) {
      return null;
    }
    if (logger == null) {
      logger = LogManager.getLogger(Freerouting.class);
    }

    logger.debug(msg);

    return null;
  }

  /**
   * Logs a DEBUG message without a topic.
   *
   * @param msg The message to log.
   * @return The created LogEntry.
   */
  public static LogEntry debug(String msg) {
    return debug(msg, null);
  }

  /**
   * Logs an ERROR message with an exception.
   *
   * @param msg       The message to log.
   * @param topic     An optional topic UUID associated with the message.
   * @param exception The exception to log.
   * @return The created LogEntry.
   */
  public static LogEntry error(String msg, UUID topic, Throwable exception) {
    LogEntry logEntry = logEntries.add(LogEntryType.Error, msg, topic, exception);

    if (!enabled) {
      return null;
    }
    if (logger == null) {
      logger = LogManager.getLogger(Freerouting.class);
    }

    if (exception == null) {
      logger.error(msg);
    } else {
      logger.error(msg, exception);
    }

    return logEntry;
  }

  /**
   * Logs an ERROR message with an exception, but without a topic.
   *
   * @param msg       The message to log.
   * @param exception The exception to log.
   * @return The created LogEntry.
   */
  public static LogEntry error(String msg, Throwable exception) {
    return error(msg, null, exception);
  }

  /**
   * Logs a TRACE message.
   *
   * @param msg The message to log.
   * @return The created LogEntry.
   */
  public static LogEntry trace(String msg) {
    if (!enabled) {
      return null;
    }
    if (logger == null) {
      logger = LogManager.getLogger(Freerouting.class);
    }

    return null;
  }

  /**
   * Logs a granular TRACE message and triggers a debug check.
   *
   * @param method        The method name where the log originates (e.g.
   *                      "InsertFoundConnectionAlgo").
   * @param operation     The operation type (e.g. "insertion", "removal").
   * @param message       The details of the log message.
   * @param impactedItems A string describing the impacted items (e.g. "Net #1,
   *                      Trace #123").
   *                      This string is used by DebugControl to filter execution.
   */
  public static void trace(String method, String operation, String message, String impactedItems) {
    if (enabled) {
      if (logger == null) {
        logger = LogManager.getLogger(Freerouting.class);
      }

      if (granularTraceEnabled && (impactedItems.isEmpty() || app.freerouting.debug.DebugControl.getInstance().isInterested(impactedItems))) {
        String formattedMessage = String.format("[%s] [%s] %s: %s", method, operation, message, impactedItems);
        logger.trace(formattedMessage);
      }
    }

    app.freerouting.debug.DebugControl.getInstance().check(impactedItems);
  }

  /**
   * Disables logging.
   */
  public static void disableLogging() {
    enabled = false;
  }

  /**
   * Gets the collection of log entries recorded by this logger.
   *
   * @return The LogEntries collection.
   */
  public static LogEntries getLogEntries() {
    return logEntries;
  }

  /**
   * Gets the underlying Log4j2 Logger instance.
   *
   * @return The Logger instance.
   */
  public static Logger getLogger() {
    if (logger == null) {
      logger = LogManager.getLogger(Freerouting.class);
    }

    return logger;
  }
}