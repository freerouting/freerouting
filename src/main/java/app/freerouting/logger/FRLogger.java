package app.freerouting.logger;

import app.freerouting.Freerouting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;

public class FRLogger {
    private static Logger logger = LogManager.getLogger(Freerouting.class);

    private static DecimalFormat performanceFormat = new DecimalFormat("0.00");

    private static HashMap<Integer, Instant> perfData = new HashMap<Integer, Instant>();

    public static String formatDuration(double totalSeconds)
    {
        double seconds = totalSeconds;
        double minutes = seconds/60.0;
        double hours = minutes/60.0;

        hours = Math.floor(hours);
        minutes = Math.floor(minutes % 60.0);
        seconds = seconds % 60.0;

        return (hours > 0 ? (int)hours + " hour(s) " : "") + (minutes > 0 ? (int)minutes + " minute(s) " : "") + performanceFormat.format(seconds) + " seconds";
    }

    public static void traceEntry(String perfId)
    {
        perfData.put(perfId.hashCode(), java.time.Instant.now());
    }

    public static double traceExit(String perfId)
    {
        return traceExit(perfId, null);
    }

    public static double traceExit(String perfId, Object result)
    {
        long timeElapsed = Duration.between(perfData.get(perfId.hashCode()), java.time.Instant.now()).toMillis();

        perfData.remove(perfId.hashCode());
        if (timeElapsed < 0) {
            timeElapsed = 0;
        }

        logger.trace("Method '" + perfId.replace("{}", result != null ? result.toString() : "(null)") + "' was performed in " + FRLogger.formatDuration(timeElapsed/1000.0) + ".");

        return timeElapsed/1000.0;
    }

    public static void info(String msg)
    {
        logger.info(msg);
    }

    public static void warn(String msg)
    {
        logger.warn(msg);
    }

    public static void debug(String msg)
    {
        //logger.debug(msg);
    }

    public static void error(String msg, Throwable t)
    {
        if (t == null)
        {
            logger.error(msg);
        } else
        {
            logger.error(msg, t);
        }
    }
}
