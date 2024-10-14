package app.freerouting.logger;

import app.freerouting.Freerouting;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;

/// <summary>
/// Provides logging functionality.
/// </summary>
public class FRLogger
{
  public static final DecimalFormat defaultFloatFormat = new DecimalFormat("0.00");
  private static final HashMap<Integer, Instant> perfData = new HashMap<>();
  private static final LogEntries logEntries = new LogEntries();
  private static Logger logger;
  private static boolean enabled = true;

  public static String formatDuration(double totalSeconds)
  {
    double seconds = totalSeconds;
    double minutes = seconds / 60.0;
    double hours = minutes / 60.0;

    hours = Math.floor(hours);
    minutes = Math.floor(minutes % 60.0);
    seconds = seconds % 60.0;

    return (hours > 0 ? (int) hours + " hour(s) " : "") + (minutes > 0 ? (int) minutes + " minute(s) " : "") + defaultFloatFormat.format(seconds) + " seconds";
  }

  public static void traceEntry(String perfId)
  {
    if (!enabled)
    {
      return;
    }
    if (logger == null)
    {
      logger = LogManager.getLogger(Freerouting.class);
    }

    perfData.put(perfId.hashCode(), Instant.now());
  }

  public static double traceExit(String perfId)
  {
    if (!enabled)
    {
      return 0.0;
    }
    if (logger == null)
    {
      logger = LogManager.getLogger(Freerouting.class);
    }

    return traceExit(perfId, null);
  }

  public static double traceExit(String perfId, Object result)
  {
    if (!enabled)
    {
      return 0.0;
    }
    if (logger == null)
    {
      logger = LogManager.getLogger(Freerouting.class);
    }

    long timeElapsed = 0;
    try
    {
      timeElapsed = Duration.between(perfData.get(perfId.hashCode()), Instant.now()).toMillis();
    } catch (Exception e)
    {
      // we can ignore this exception
    }

    perfData.remove(perfId.hashCode());
    if (timeElapsed < 0)
    {
      timeElapsed = 0;
    }

    String logMessage = "Method '" + perfId.replace("{}", result != null ? result.toString() : "(null)") + "' was performed in " + FRLogger.formatDuration(timeElapsed / 1000.0) + ".";

    FRLogger.trace(logMessage);

    return timeElapsed / 1000.0;
  }

  public static void info(String msg)
  {
    logEntries.add(LogEntryType.Info, msg);

    if (!enabled)
    {
      return;
    }
    if (logger == null)
    {
      logger = LogManager.getLogger(Freerouting.class);
    }

    logger.info(msg);
  }

  public static void warn(String msg)
  {
    logEntries.add(LogEntryType.Warning, msg);

    if (!enabled)
    {
      return;
    }
    if (logger == null)
    {
      logger = LogManager.getLogger(Freerouting.class);
    }

    logger.warn(msg);
  }

  public static void debug(String msg)
  {
    logEntries.add(LogEntryType.Debug, msg);

    if (!enabled)
    {
      return;
    }
    if (logger == null)
    {
      logger = LogManager.getLogger(Freerouting.class);
    }

    logger.debug(msg);
  }

  public static void error(String msg, Throwable t)
  {
    logEntries.add(LogEntryType.Error, msg);

    if (!enabled)
    {
      return;
    }
    if (logger == null)
    {
      logger = LogManager.getLogger(Freerouting.class);
    }


    if (t == null)
    {
      logger.error(msg);
    }
    else
    {
      logger.error(msg, t);
    }
  }

  public static void trace(String msg)
  {
    logEntries.add(LogEntryType.Trace, msg);

    if (!enabled)
    {
      return;
    }
    if (logger == null)
    {
      logger = LogManager.getLogger(Freerouting.class);
    }

    logger.trace(msg);
  }

  /// <summary>
  /// Disables the log4j logger.
  /// </summary>
  public static void disableLogging()
  {
    enabled = false;
  }

  public static LogEntries getLogEntries()
  {
    return logEntries;
  }

  public static void changeFileLogLevel(Level level)
  {
    // Obtain the LoggerContext
    var contextObject = LogManager.getContext(false);

    // Check if the contextObject is an instance of org.apache.logging.log4j.core.LoggerContext
    if (!(contextObject instanceof LoggerContext context))
    {
      FRLogger.warn("Failed to change the log level. The context object is not an instance of org.apache.logging.log4j.core.LoggerContext.");
      return;
    }

    // Get the Configuration
    Configuration config = context.getConfiguration();

    // Get the Root LoggerConfig
    LoggerConfig rootLoggerConfig = config.getRootLogger();

    // Create a new AppenderRef with the desired level
    List<AppenderRef> refList = rootLoggerConfig.getAppenderRefs();
    var refs = refList.toArray(new AppenderRef[0]);
    for (int i = 0; i < refs.length; i++)
    {
      if (refs[i].getRef().equals("Console"))
      {
        refs[i] = AppenderRef.createAppenderRef("Console", level, null);
      }
    }

    // Remove the existing AppenderRefs
    rootLoggerConfig.removeAppender("Console");

    // Add the modified AppenderRef back to the LoggerConfig
    for (AppenderRef ref : refs)
    {
      rootLoggerConfig.addAppender(config.getAppender(ref.getRef()), ref.getLevel(), ref.getFilter());
    }

    // Update the configuration
    context.updateLoggers();
  }

  public static void changeFileLogLevel(String level)
  {
    String logLevel = level.toUpperCase();

    if (logLevel.equals("OFF") || logLevel.equals("0"))
    {
      FRLogger.disableLogging();
    }
    else if (logLevel.equals("FATAL") || logLevel.equals("1"))
    {
      FRLogger.changeFileLogLevel(Level.FATAL);
    }
    else if (logLevel.equals("ERROR") || logLevel.equals("2"))
    {
      FRLogger.changeFileLogLevel(Level.ERROR);
    }
    else if (logLevel.equals("WARN") || logLevel.equals("3"))
    {
      FRLogger.changeFileLogLevel(Level.WARN);
    }
    else if (logLevel.equals("INFO") || logLevel.equals("4"))
    {
      FRLogger.changeFileLogLevel(Level.INFO);
    }
    else if (logLevel.equals("DEBUG") || logLevel.equals("5"))
    {
      FRLogger.changeFileLogLevel(Level.DEBUG);
    }
    else if (logLevel.equals("TRACE") || logLevel.equals("6"))
    {
      FRLogger.changeFileLogLevel(Level.TRACE);
    }
    else if (logLevel.equals("ALL") || logLevel.equals("7"))
    {
      FRLogger.changeFileLogLevel(Level.ALL);
    }
  }

  public static Logger getLogger()
  {
    if (logger == null)
    {
      logger = LogManager.getLogger(Freerouting.class);
    }

    return logger;
  }

  public static void changeFileLogLocation(Path userDataPath)
  {
    Path logFilePath = userDataPath.resolve("freerouting.log");

    // Obtain the LoggerContext
    LoggerContext context = (LoggerContext) LogManager.getContext(false);

    // Check if the contextObject is an instance of org.apache.logging.log4j.core.LoggerContext
    if (context == null)
    {
      FRLogger.warn("Failed to change the log file location. The context object is not an instance of org.apache.logging.log4j.core.LoggerContext.");
      return;
    }

    // Get the Configuration
    Configuration config = context.getConfiguration();

    // Get the Root LoggerConfig
    LoggerConfig rootLoggerConfig = config.getRootLogger();

    // Remove the existing File appender
    if (config.getAppender("File") != null)
    {
      config.getAppender("File").stop();
      rootLoggerConfig.removeAppender("File");
    }

    // Create a new FileAppender with the new log file path
    FileAppender newFileAppender = FileAppender.newBuilder().setName("File").withFileName(logFilePath.toString()).setLayout(PatternLayout.newBuilder().withPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %msg%n").build()).withImmediateFlush(true).setConfiguration(config).build();

    // Start the new appender
    newFileAppender.start();

    // Add the new FileAppender to the root logger
    rootLoggerConfig.addAppender(newFileAppender, rootLoggerConfig.getLevel(), null);

    // Update the loggers with the new configuration
    context.updateLoggers();
  }
}