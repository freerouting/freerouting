package logger;

/**
 * Andrey Belomutskiy
 * 6/28/2014
 */
public class FRLogger {
    public static void warning(String message) {
        /**
         * there is a problem that errors are currently being written to standard console and thus not visible to the
         * user
         */
        System.out.println(message);
    }

    public static void error(Throwable e) {
        e.printStackTrace();
    }
}
