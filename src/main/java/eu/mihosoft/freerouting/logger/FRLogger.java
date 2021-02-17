package eu.mihosoft.freerouting.logger;

import eu.FR;
import eu.mihosoft.freerouting.FreeRouting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;

public class FRLogger {
    private static Logger logger = LogManager.getLogger(FR.class);
    // Use a shorter class path, which appears in every line of the log 
    // It's meaningless since all are the same, occupies space and makes harder 
    // to read useful info on size-limited screen

    private static DecimalFormat performanceFormat = new DecimalFormat("0.00");

    private static HashMap<Integer, Instant> perfData = new HashMap<Integer, Instant>();

    public static void traceEntry(String perfId)
    {
        perfData.put(perfId.hashCode(), java.time.Instant.now());
    }

    public static void traceExit(String perfId)
    {
        traceExit(perfId, null);
    }

    public static void traceExit(String perfId, Object result)
    {
        long timeElapsed = Duration.between(perfData.get(perfId.hashCode()), java.time.Instant.now()).toMillis();

        perfData.remove(perfId.hashCode());
        if (timeElapsed < 0) {
            timeElapsed = 0;
        }
        logger.trace("Method '" + perfId.replace("{}", result != null ? result.toString() : "(null)") + "' was performed in " + performanceFormat.format(timeElapsed/1000.0) + " seconds.");
    }

    public static void info(String msg)
    {
        logger.info(msg);
    }

    public static void warn(String msg)
    {
        logger.warn(msg);
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
