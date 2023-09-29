package app.freerouting.gui;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import app.freerouting.logger.FRLogger;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

public class StartupOptions {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final Path PATH = Paths.get(System.getProperty("java.io.tmpdir"), "freerouting.json");
  transient boolean single_design_option = false;
  transient boolean test_version_option = false;
  transient boolean show_help_option = false;
  transient boolean session_file_option = false;
  transient boolean webstart_option = false;
  transient String design_input_filename;
  transient String design_output_filename;
  transient String design_rules_filename;
  public String input_directory;
  public int max_passes = 99999;
  //int num_threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
  public int num_threads = 1;
  public BoardUpdateStrategy board_update_strategy = BoardUpdateStrategy.GREEDY;
  public String hybrid_ratio = "1:1";
  public ItemSelectionStrategy item_selection_strategy = ItemSelectionStrategy.PRIORITIZED;
  transient String[] supported_languages = {"en", "de", "zh", "hi", "es", "fr", "ar", "bn", "ru", "pt", "ja", "ko"};
  transient Locale current_locale = Locale.getDefault();
  public boolean save_intermediate_stages = true;
  // this value is equivalent to the setting of "-oit 0.001"
  public float optimization_improvement_threshold = 0.00001f;
  transient String[] ignore_net_classes_by_autorouter = new String[0];
  public boolean disable_logging = false;

  public StartupOptions() {
    if (Arrays.stream(supported_languages).noneMatch(current_locale.getLanguage()::equals)) {
      // the fallback language is English
      current_locale = Locale.ENGLISH;
    }
  }

  public Locale getCurrentLocale() {
    return current_locale;
  }

  public void parseCommandLineArguments(String[] p_args) {
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
            input_directory = p_args[i + 1];
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

            if (max_passes < 1) {
              max_passes = 1;
            }
            if (max_passes > 99998) {
              max_passes = 99998;
            }
          }
        } else if (p_args[i].startsWith("-mt")) {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-")) {
            num_threads = Integer.decode(p_args[i + 1]);

            if (num_threads <= 0) {
              num_threads = 0;
            }
            if (num_threads > 1024) {
              num_threads = 1024;
            }
          }
        } else if (p_args[i].startsWith("-oit")) {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-")) {
            optimization_improvement_threshold = Float.parseFloat(p_args[i + 1]) / 100;

            if (optimization_improvement_threshold <= 0) {
              optimization_improvement_threshold = 0;
            }
          }
        } else if (p_args[i].startsWith("-us")) {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-")) {
            String op = p_args[i + 1].toLowerCase().trim();
            board_update_strategy =
                op.equals("global")
                    ? BoardUpdateStrategy.GLOBAL_OPTIMAL
                    : (op.equals("hybrid")
                        ? BoardUpdateStrategy.HYBRID
                        : BoardUpdateStrategy.GREEDY);
          }
        } else if (p_args[i].startsWith("-is")) {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-")) {
            String op = p_args[i + 1].toLowerCase().trim();
            item_selection_strategy =
                op.indexOf("seq") == 0
                    ? ItemSelectionStrategy.SEQUENTIAL
                    : (op.indexOf("rand") == 0
                        ? ItemSelectionStrategy.RANDOM
                        : ItemSelectionStrategy.PRIORITIZED);
          }
        } else if (p_args[i].startsWith("-hr")) { // hybrid ratio
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-")) {
            hybrid_ratio = p_args[i + 1].trim();
          }
        } else if (p_args[i].startsWith("-l")) {
          // the locale is provided
          if (p_args.length > i + 1 && p_args[i + 1].startsWith("en")) {
            current_locale = Locale.ENGLISH;
          } else if (p_args.length > i + 1 && p_args[i + 1].startsWith("de")) {
            current_locale = Locale.GERMAN;
          } else if (p_args.length > i + 1 && p_args[i + 1].startsWith("zh")) {
            current_locale = Locale.SIMPLIFIED_CHINESE;
          } else if (p_args.length > i + 1 && p_args[i + 1].startsWith("hi")) {
            //current_locale = Locale.HINDI;
            current_locale = new Locale("hi", "IN");
          } else if (p_args.length > i + 1 && p_args[i + 1].startsWith("es")) {
            //current_locale = Locale.SPANISH;
            current_locale = new Locale("es", "ES");
          } else if (p_args.length > i + 1 && p_args[i + 1].startsWith("fr")) {
            current_locale = Locale.FRENCH;
          } else if (p_args.length > i + 1 && p_args[i + 1].startsWith("ar")) {
            //current_locale = Locale.ARABIC;
            current_locale = new Locale("ar", "EG");
          } else if (p_args.length > i + 1 && p_args[i + 1].startsWith("bn")) {
            //current_locale = Locale.BENGALI;
            current_locale = new Locale("bn", "BD");
          } else if (p_args.length > i + 1 && p_args[i + 1].startsWith("ru")) {
            //current_locale = Locale.RUSSIAN;
            current_locale = new Locale("ru", "RU");
          } else if (p_args.length > i + 1 && p_args[i + 1].startsWith("pt")) {
            //current_locale = Locale.PORTUGUESE;
            current_locale = new Locale("pt", "PT");
          } else if (p_args.length > i + 1 && p_args[i + 1].startsWith("ja")) {
            current_locale = Locale.JAPANESE;
          } else if (p_args.length > i + 1 && p_args[i + 1].startsWith("ko")) {
            current_locale = Locale.KOREAN;
          }
        } else if (p_args[i].startsWith("-s")) {
          session_file_option = true;
        } else if (p_args[i].startsWith("-im")) {
          save_intermediate_stages = true;
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-")) {
            save_intermediate_stages = !(Objects.equals(p_args[i + 1],"0"));
          }
        } else if (p_args[i].startsWith("-w")) {
          webstart_option = true;
        } else if (p_args[i].startsWith("-test")) {
          test_version_option = true;
        } else if (p_args[i].startsWith("-dl")) {
          disable_logging = true;
        } else if (p_args[i].startsWith("-h")) {
          show_help_option = true;
        } else if (p_args[i].startsWith("-inc")) {
          // ignore net class(es)
          ignore_net_classes_by_autorouter = p_args[i + 1].split(",");
        }
      } catch (Exception e) {
        FRLogger.error("There was a problem parsing the '" + p_args[i] + "' parameter", e);
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
    return input_directory;
  }

  public int getMaxPasses() {
    return max_passes;
  }

  public int getNumThreads() {
    return num_threads;
  }

  public String getHybridRatio() {
    return hybrid_ratio;
  }

  public BoardUpdateStrategy getBoardUpdateStrategy() {
    return board_update_strategy;
  }

  public ItemSelectionStrategy getItemSelectionStrategy() {
    return item_selection_strategy;
  }

  public static void save(StartupOptions options) throws IOException {
    try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
      GSON.toJson(options, writer);
    }
  }

  public static StartupOptions load() throws IOException {
    try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
      return GSON.fromJson(reader, StartupOptions.class);
    }
  }
}
