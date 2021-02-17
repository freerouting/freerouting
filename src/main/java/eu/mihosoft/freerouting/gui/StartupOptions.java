package eu.mihosoft.freerouting.gui;

import eu.mihosoft.freerouting.autoroute.BoardUpdateStrategy;
import eu.mihosoft.freerouting.logger.FRLogger;

import java.util.Locale;

/**
 * Andrey Belomutskiy
 * 6/28/2014
 */
public class StartupOptions {
    boolean single_design_option = false;
    boolean test_version_option = false;
    boolean session_file_option = false;
    boolean webstart_option = false;
    String design_input_filename = null;
    String design_output_filename = null;
    String design_rules_filename = null;
    String design_input_directory_name = null;
    int max_passes = 99999;
    int num_threads = 4; // default thread pool size
    BoardUpdateStrategy board_update_strategy = BoardUpdateStrategy.GREEDY; // default  
    java.util.Locale current_locale = java.util.Locale.ENGLISH;

    private StartupOptions() {
    }

    public Locale getCurrentLocale() {
        return current_locale;
    }

    public static StartupOptions parse(String[] p_args) {
        StartupOptions result = new StartupOptions();
        result.process(p_args);
        return result;
    }

    private void process(String[] p_args) {
        for (int i = 0; i < p_args.length; ++i) {
            try {
                if (p_args[i].startsWith("-de")) {
                    // the design file is provided
                    if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-")) {
                        single_design_option = true;
                        design_input_filename = p_args[i + 1];
                    }
                } else if (p_args[i].startsWith("-di")) {
                    // the design directory is provided
                    if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-")) {
                        design_input_directory_name = p_args[i + 1];
                    }
                } else if (p_args[i].startsWith("-do")) {
                    if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-")) {
                        design_output_filename = p_args[i + 1];
                    }
                } else if (p_args[i].startsWith("-dr")) {
                    if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-")) {
                        design_rules_filename = p_args[i + 1];
                    }
                } else if (p_args[i].startsWith("-mp")) {
                    if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-")) {
                        max_passes = Integer.decode(p_args[i + 1]);
                    }
                } else if (p_args[i].startsWith("-mt")) {
                    if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-")) {
                    	num_threads = Integer.decode(p_args[i + 1]);
                    }
                } else if (p_args[i].startsWith("-os")) {
                    if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-")) {
                    	board_update_strategy = p_args[i + 1].toLowerCase().contains("global") ?
                    			BoardUpdateStrategy.GLOBAL_OPTIMAL : BoardUpdateStrategy.GREEDY;
                    }                    
                } else if (p_args[i].startsWith("-l")) {
                    // the locale is provided
                    if (p_args.length > i + 1 && p_args[i + 1].startsWith("d")) {
                        current_locale = java.util.Locale.GERMAN;
                    }
                } else if (p_args[i].startsWith("-s")) {
                    session_file_option = true;
                } else if (p_args[i].startsWith("-w")) {
                    webstart_option = true;
                } else if (p_args[i].startsWith("-test")) {
                    test_version_option = true;
                }
            }
            catch (Exception e)
            {
                FRLogger.error("There was a problem parsing the '"+p_args[i]+"' parameter", e);
            }
        }
    }

    public boolean getWebstartOption() {
        return webstart_option;
    }

    public boolean isTestVersion() {
        return test_version_option;
    }

    public String getDesignDir() {
        return design_input_directory_name;
    }
    
    public int getMaxPasses()  { return max_passes;  }
    public int getNumThreads() { return num_threads; }
    public BoardUpdateStrategy getBoardUpdateStrategy()
    {
    	return board_update_strategy;
    }
}
