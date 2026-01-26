package app.freerouting.logger;

import java.io.File;
import java.net.URI;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Order;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.RootLoggerComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.core.config.plugins.Plugin;

/**
 * Custom Log4j2 ConfigurationFactory that programmatically builds the logging
 * configuration based on system properties set early in the application
 * startup.
 *
 * This eliminates the need for runtime configuration manipulation which causes
 * threading issues and exceptions.
 */
@Plugin(name = "FreeroutingConfigurationFactory", category = ConfigurationFactory.CATEGORY)
@Order(50)
public class Log4j2ConfigurationFactory extends ConfigurationFactory {

    private static final String PATTERN = "%d{yyyy-MM-dd HH:mm:ss.SSS} %-6level %msg%n";

    @Override
    protected String[] getSupportedTypes() {
        return new String[] { "*" };
    }

    @Override
    public Configuration getConfiguration(LoggerContext loggerContext, ConfigurationSource source) {
        return getConfiguration(loggerContext, source.toString(), null);
    }

    @Override
    public Configuration getConfiguration(LoggerContext loggerContext, String name, URI configLocation) {
        ConfigurationBuilder<BuiltConfiguration> builder = newConfigurationBuilder();

        // Read configuration from system properties
        boolean consoleEnabled = getBooleanProperty("freerouting.logging.console.enabled", true);
        String consoleLevel = getProperty("freerouting.logging.console.level", "INFO");

        boolean fileEnabled = getBooleanProperty("freerouting.logging.file.enabled", true);
        String fileLevel = getProperty("freerouting.logging.file.level", "DEBUG");
        String fileLocation = getProperty("freerouting.logging.file.location", null);
        String filePattern = getProperty("freerouting.logging.file.pattern", PATTERN);

        // Set configuration name and status
        builder.setConfigurationName("FreeroutingConfiguration");
        builder.setStatusLevel(Level.WARN);

        // Create Console appender if enabled
        if (consoleEnabled) {
            AppenderComponentBuilder consoleAppender = builder.newAppender("Console", "Console")
                    .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT)
                    .add(builder.newLayout("PatternLayout")
                            .addAttribute("pattern", "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-6level %msg%n"));
            builder.add(consoleAppender);
        }

        // Create File appender if enabled
        if (fileEnabled && fileLocation != null && !fileLocation.isBlank()) {
            // Ensure parent directory exists
            File logFile = new File(fileLocation);
            File parentDir = logFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            AppenderComponentBuilder fileAppender = builder.newAppender("File", "File")
                    .addAttribute("fileName", fileLocation)
                    .addAttribute("immediateFlush", true)
                    .addAttribute("bufferedIO", true)
                    .addAttribute("bufferSize", 8192)
                    .add(builder.newLayout("PatternLayout")
                            .addAttribute("pattern", filePattern));
            builder.add(fileAppender);
        }

        // Create stderr appender for errors
        AppenderComponentBuilder stderrAppender = builder.newAppender("stderr", "Console")
                .addAttribute("target", ConsoleAppender.Target.SYSTEM_ERR)
                .add(builder.newLayout("PatternLayout")
                        .addAttribute("pattern", filePattern)); // Use the same pattern for stderr
        builder.add(stderrAppender);

        // Configure root logger
        RootLoggerComponentBuilder rootLogger = builder.newRootLogger(Level.ALL);

        if (consoleEnabled) {
            rootLogger.add(builder.newAppenderRef("Console")
                    .addAttribute("level", parseLevel(consoleLevel)));
        }

        if (fileEnabled && fileLocation != null && !fileLocation.isBlank()) {
            rootLogger.add(builder.newAppenderRef("File")
                    .addAttribute("level", parseLevel(fileLevel)));
        }

        // Always add stderr for ERROR level
        rootLogger.add(builder.newAppenderRef("stderr")
                .addAttribute("level", Level.ERROR));

        builder.add(rootLogger);

        return builder.build();
    }

    private String getProperty(String key, String defaultValue) {
        String value = System.getProperty(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    private boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = System.getProperty(key);
        return (value != null) ? Boolean.parseBoolean(value) : defaultValue;
    }

    private Level parseLevel(String level) {
        try {
            return Level.valueOf(level.toUpperCase());
        } catch (Exception e) {
            return Level.INFO;
        }
    }
}