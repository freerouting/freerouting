package eu.mihosoft.freerouting.gui;

import eu.mihosoft.freerouting.logger.FRLogger;

import java.util.Locale;

/**
 * Andrey Belomutskiy 6/28/2014
 */
public class StartupOptions {

    private boolean singleDesignOption = false;
    private boolean testVersionOption = false;
    private boolean sessionFileOption = false;
    private boolean demoOption = false;
    private String designInputFilename = null;
    private String designOutputFilename = null;
    private String designRulesFilename = null;
    private String designInputDirectoryName = null;
    int maxPasses = 99999;
    private Locale current_locale = Locale.ENGLISH;

    public StartupOptions(final String[] appArgs) {
        process(appArgs);
    }

    public boolean getDemoOption() {
        return demoOption;
    }

    public boolean isTestVersion() {
        return testVersionOption;
    }

    public String getDesignDir() {
        return designInputDirectoryName;
    }

    public Locale getCurrentLocale() {
        return current_locale;
    }

    /**
     * If application has an command line option
     *
     * -de [design input file],
     *
     * for instance, -de /home/user/freerouting/tests/pic_programmer.dsn
     *
     * then it loads up a Specctra .dsn file at startup
     *
     * @return
     */
    public boolean isSingleDesignOption() {
        return singleDesignOption;
    }

    public boolean isSessionFileOption() {
        return sessionFileOption;
    }

    /**
     * If application has an command line option
     *
     * -mp [number of passes]:
     *
     * then it sets the upper limit of the number of passes that will be performed
     * @return
     */
    public int getMaxPasses() {
        return maxPasses;
    }

    public String getDesignInputFilename() {
        return designInputFilename;
    }

    public String getDesignRulesFilename() {
        return designRulesFilename;
    }

    public String getDesignOutputFilename() {
        return designOutputFilename;
    }

    private void process(final String[] appArgs) {
        for (int i = 0; i < appArgs.length; ++i) {
            try {
                if (appArgs[i].startsWith("-de")) {
                    // the design file is provided
                    if (appArgs.length > i + 1 && !appArgs[i + 1].startsWith("-")) {
                        singleDesignOption = true;
                        designInputFilename = appArgs[i + 1];
                    }
                } else if (appArgs[i].startsWith("-di")) { //[design input directory]: if the GUI is used, this sets the default folder for the open design dialogs
                    // the design directory is provided
                    if (appArgs.length > i + 1 && !appArgs[i + 1].startsWith("-")) {
                        designInputDirectoryName = appArgs[i + 1];
                    }
                } else if (appArgs[i].startsWith("-do")) { //[design output file]: saves a Specctra board (.dsn), a Specctra session file (.ses) or Eagle session script file (.scr) when the routing is finished
                    if (appArgs.length > i + 1 && !appArgs[i + 1].startsWith("-")) {
                        designOutputFilename = appArgs[i + 1];
                    }
                } else if (appArgs[i].startsWith("-dr")) {//[design rules file]: reads the rules from a previously saved .rules file
                    if (appArgs.length > i + 1 && !appArgs[i + 1].startsWith("-")) {
                        designRulesFilename = appArgs[i + 1];
                    }
                } else if (appArgs[i].startsWith("-mp")) {
                    if (appArgs.length > i + 1 && !appArgs[i + 1].startsWith("-")) {
                        maxPasses = Integer.decode(appArgs[i + 1]);
                    }
                } else if (appArgs[i].startsWith("-l")) {//[language]: "de" for German, otherwise it's English
                    // the locale is provided
                    if (appArgs.length > i + 1 && appArgs[i + 1].startsWith("d")) {
                        current_locale = java.util.Locale.GERMAN;
                    }
                } else if (appArgs[i].startsWith("-s")) {
                    sessionFileOption = true;
                } else if (appArgs[i].startsWith("-d")) {//starts as Webstart
                    demoOption = true;
                } else if (appArgs[i].startsWith("-test")) {//test
                    testVersionOption = true;
                }
            } catch (Exception e) {
                FRLogger.error("There was a problem parsing the '" + appArgs[i] + "' parameter", e);
            }
        }
    }
}
