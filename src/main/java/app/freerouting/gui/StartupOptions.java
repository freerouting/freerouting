package app.freerouting.gui;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import app.freerouting.logger.FRLogger;

import java.util.Arrays;
import java.util.Locale;

public class StartupOptions {
    boolean single_design_option = false;
    boolean test_version_option = false;
    boolean show_help_option = false;
    boolean session_file_option = false;
    boolean webstart_option = false;
    String design_input_filename = null;
    String design_output_filename = null;
    String design_rules_filename = null;
    String design_input_directory_name = null;
    int max_passes = 99999;
    int num_threads = Math.max(1 ,Runtime.getRuntime().availableProcessors() - 1);
    BoardUpdateStrategy board_update_strategy = BoardUpdateStrategy.GREEDY;
    String hybrid_ratio = "1:1";
    ItemSelectionStrategy item_selection_strategy = ItemSelectionStrategy.PRIORITIZED;
    String[] supported_languages = { "en", "de", "zh" };
    java.util.Locale current_locale = java.util.Locale.getDefault();
    boolean save_intermediate_stages = false;
    float optimization_improvement_threshold = 0.0025f;

    private StartupOptions() {
        if (!Arrays.stream(supported_languages).anyMatch(current_locale.getLanguage()::equals))
        {
            // the fallback language is English
            current_locale = Locale.ENGLISH;
        }
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

                        if (max_passes < 1)     { max_passes = 1; }
                        if (max_passes > 99998) { max_passes = 99998; }
                    }
                } else if (p_args[i].startsWith("-mt")) {
                    if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-")) {
                    	num_threads = Integer.decode(p_args[i + 1]);
                    	
                    	if (num_threads <= 0)   { num_threads = 0; }
                    	if (num_threads > 1024) { num_threads = 1024; }
                    }
                } else if (p_args[i].startsWith("-oit")) {
                    if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-")) {
                        optimization_improvement_threshold = Float.parseFloat(p_args[i + 1]) / 100;

                        if (optimization_improvement_threshold <= 0)   { optimization_improvement_threshold = 0; }
                    }
                } else if (p_args[i].startsWith("-us")) {
                    if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-")) {
                    	String op = p_args[i + 1].toLowerCase().trim();
                    	board_update_strategy = op.equals("global") ?
                 			  BoardUpdateStrategy.GLOBAL_OPTIMAL 
                		    : (op.equals("hybrid") ? BoardUpdateStrategy.HYBRID
                			  	                   : BoardUpdateStrategy.GREEDY);
                    }                    
                } else if (p_args[i].startsWith("-is")) {
                    if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-")) {
                    	String op = p_args[i + 1].toLowerCase().trim();
                    	item_selection_strategy = op.indexOf("seq") == 0 ? ItemSelectionStrategy.SEQUENTIAL
                    			: (op.indexOf("rand") == 0 ? ItemSelectionStrategy.RANDOM 
                    					                   : ItemSelectionStrategy.PRIORITIZED);
                    }                    
                } else if (p_args[i].startsWith("-hr")) {  // hybrid ratio
                    if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-")) {
                    	hybrid_ratio = p_args[i + 1].trim();
                    }                    
                } else if (p_args[i].startsWith("-l")) {
                    // the locale is provided
                    if (p_args.length > i + 1 && p_args[i + 1].startsWith("de")) {
                        current_locale = java.util.Locale.GERMAN;
                    } else if (p_args.length > i + 1 && p_args[i + 1].startsWith("zh")) {
                        current_locale = Locale.SIMPLIFIED_CHINESE;
                    }
                } else if (p_args[i].startsWith("-s")) {
                    session_file_option = true;
                } else if (p_args[i].startsWith("-im")) {
                    save_intermediate_stages = true;
                } else if (p_args[i].startsWith("-w")) {
                    webstart_option = true;
                } else if (p_args[i].startsWith("-test")) {
                    test_version_option = true;
                } else if (p_args[i].startsWith("-h")) {
                    show_help_option = true;
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
    
    public int getMaxPasses()      { return max_passes;   }
    public int getNumThreads()     { return num_threads;  }
    public String getHybridRatio() { return hybrid_ratio; }
    public BoardUpdateStrategy getBoardUpdateStrategy()
    {
    	return board_update_strategy;
    }
    public ItemSelectionStrategy getItemSelectionStrategy() 
    {
    	return item_selection_strategy;
    }
}
