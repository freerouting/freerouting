package app.freerouting.settings;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import app.freerouting.constants.Constants;
import app.freerouting.logger.FRLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

public class GlobalSettings
{
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final Path PATH = Paths.get(System.getProperty("java.io.tmpdir"), "freerouting.json");
  public final String version = Constants.FREEROUTING_VERSION;
  @SerializedName("autorouter_settings")
  public final AutoRouterSettings autoRouterSettings = new AutoRouterSettings();
  @SerializedName("usage_and_diagnostic_data")
  public final UsageAndDiagnosticDataSettings usageAndDiagnosticData = new UsageAndDiagnosticDataSettings();
  @SerializedName("disabled_features")
  public final DisabledFeaturesSettings disabledFeatures = new DisabledFeaturesSettings();
  public transient boolean test_version_option = false;
  public transient boolean show_help_option = false;
  public transient String design_input_filename;
  public transient String design_output_filename;
  public transient String design_rules_filename;
  // add taiwnese language
  public transient String[] supported_languages = {"en", "de", "zh","zh_TW", "hi", "es", "it", "fr", "ar", "bn", "ru", "pt", "ja", "ko"};
  public transient Locale current_locale = Locale.getDefault();
  public transient String host = "N/A";
  public String input_directory = "";
  public int dialog_confirmation_timeout = 5;

  public GlobalSettings()
  {
    if (Arrays.stream(supported_languages).noneMatch(current_locale.getLanguage()::equals))
    {
      // the fallback language is English
      current_locale = Locale.ENGLISH;
    }
  }

  public static void save(GlobalSettings options) throws IOException
  {
    try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8))
    {
      GSON.toJson(options, writer);
    }
  }

  public static GlobalSettings load() throws IOException
  {
    try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8))
    {
      return GSON.fromJson(reader, GlobalSettings.class);
    }
  }

  public Locale getCurrentLocale()
  {
    return current_locale;
  }

  public void parseCommandLineArguments(String[] p_args)
  {
    for (int i = 0; i < p_args.length; ++i)
    {
      try
      {
        if (p_args[i].startsWith("-de"))
        {
          // the design file is provided
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            design_input_filename = p_args[i + 1];
          }
        }
        else if (p_args[i].startsWith("-di"))
        {
          // the design directory is provided
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            input_directory = p_args[i + 1];
          }
        }
        else if (p_args[i].startsWith("-do"))
        {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            design_output_filename = p_args[i + 1];
          }
        }
        else if (p_args[i].startsWith("-dr"))
        {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            design_rules_filename = p_args[i + 1];
          }
        }
        else if (p_args[i].startsWith("-mp"))
        {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            autoRouterSettings.max_passes = Integer.decode(p_args[i + 1]);

            if (autoRouterSettings.max_passes < 1)
            {
              autoRouterSettings.max_passes = 1;
            }
            if (autoRouterSettings.max_passes > 99998)
            {
              autoRouterSettings.max_passes = 99998;
            }
          }
        }
        else if (p_args[i].startsWith("-mt"))
        {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            autoRouterSettings.num_threads = Integer.decode(p_args[i + 1]);

            if (autoRouterSettings.num_threads <= 0)
            {
              autoRouterSettings.num_threads = 0;
            }
            if (autoRouterSettings.num_threads > 1024)
            {
              autoRouterSettings.num_threads = 1024;
            }
          }
        }
        else if (p_args[i].startsWith("-oit"))
        {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            autoRouterSettings.optimization_improvement_threshold = Float.parseFloat(p_args[i + 1]) / 100;

            if (autoRouterSettings.optimization_improvement_threshold <= 0)
            {
              autoRouterSettings.optimization_improvement_threshold = 0;
            }
          }
        }
        else if (p_args[i].startsWith("-us"))
        {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            String op = p_args[i + 1].toLowerCase().trim();
            autoRouterSettings.board_update_strategy = op.equals("global") ? BoardUpdateStrategy.GLOBAL_OPTIMAL : (op.equals("hybrid") ? BoardUpdateStrategy.HYBRID : BoardUpdateStrategy.GREEDY);
          }
        }
        else if (p_args[i].startsWith("-is"))
        {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            String op = p_args[i + 1].toLowerCase().trim();
            autoRouterSettings.item_selection_strategy = op.indexOf("seq") == 0 ? ItemSelectionStrategy.SEQUENTIAL : (op.indexOf("rand") == 0 ? ItemSelectionStrategy.RANDOM : ItemSelectionStrategy.PRIORITIZED);
          }
        }
        else if (p_args[i].startsWith("-hr"))
        { // hybrid ratio
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            autoRouterSettings.hybrid_ratio = p_args[i + 1].trim();
          }
        }
        else if (p_args[i].startsWith("-l"))
        {
          // the locale is provided
          if (p_args.length > i + 1 && p_args[i + 1].startsWith("en"))
          {
            current_locale = Locale.ENGLISH;
          }
          else if (p_args.length > i + 1 && p_args[i + 1].startsWith("de"))
          {
            current_locale = Locale.GERMAN;
          }
          else if (p_args.length > i + 1 && p_args[i + 1].startsWith("zh"))
          {
            current_locale = Locale.SIMPLIFIED_CHINESE;
          }
          else if (p_args.length > i + 1 && p_args[i + 1].startsWith("zh_TW"))
          {
            current_locale = Locale.TRADITIONAL_CHINESE;
          }
          else if (p_args.length > i + 1 && p_args[i + 1].startsWith("hi"))
          {
            //current_locale = Locale.HINDI;
            current_locale = Locale.forLanguageTag("hi-IN");
          }
          else if (p_args.length > i + 1 && p_args[i + 1].startsWith("es"))
          {
            //current_locale = Locale.SPANISH;
            current_locale = Locale.forLanguageTag("es-ES");
          }
          else if (p_args.length > i + 1 && p_args[i + 1].startsWith("it"))
          {
            //current_locale = Locale.ITALIAN;
            current_locale = Locale.forLanguageTag("it-IT");
          }
          else if (p_args.length > i + 1 && p_args[i + 1].startsWith("fr"))
          {
            current_locale = Locale.FRENCH;
          }
          else if (p_args.length > i + 1 && p_args[i + 1].startsWith("ar"))
          {
            //current_locale = Locale.ARABIC;
            current_locale = Locale.forLanguageTag("ar-EG");
          }
          else if (p_args.length > i + 1 && p_args[i + 1].startsWith("bn"))
          {
            //current_locale = Locale.BENGALI;
            current_locale = Locale.forLanguageTag("bn-BD");
          }
          else if (p_args.length > i + 1 && p_args[i + 1].startsWith("ru"))
          {
            //current_locale = Locale.RUSSIAN;
            current_locale = Locale.forLanguageTag("ru-RU");
          }
          else if (p_args.length > i + 1 && p_args[i + 1].startsWith("pt"))
          {
            //current_locale = Locale.PORTUGUESE;
            current_locale = Locale.forLanguageTag("pt-PT");
          }
          else if (p_args.length > i + 1 && p_args[i + 1].startsWith("ja"))
          {
            current_locale = Locale.JAPANESE;
          }
          else if (p_args.length > i + 1 && p_args[i + 1].startsWith("ko"))
          {
            current_locale = Locale.KOREAN;
          }
        }
        else if (p_args[i].startsWith("-im"))
        {
          disabledFeatures.snapshots = false;
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            disabledFeatures.snapshots = (Objects.equals(p_args[i + 1], "0"));
          }
        }
        else if (p_args[i].startsWith("-test"))
        {
          test_version_option = true;
        }
        else if (p_args[i].startsWith("-dl"))
        {
          disabledFeatures.logging = true;
        }
        else if (p_args[i].startsWith("-da"))
        {
          usageAndDiagnosticData.disable_analytics = true;
        }
        else if (p_args[i].startsWith("-host"))
        {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            host = p_args[i + 1].trim();
          }
        }
        else if (p_args[i].startsWith("-help"))
        {
          show_help_option = true;
        }
        else if (p_args[i].startsWith("-inc"))
        {
          // ignore net class(es)
          autoRouterSettings.ignore_net_classes_by_autorouter = p_args[i + 1].split(",");
        }
        else if (p_args[i].startsWith("-dct"))
        {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            dialog_confirmation_timeout = Integer.parseInt(p_args[i + 1]);

            if (dialog_confirmation_timeout <= 0)
            {
              dialog_confirmation_timeout = 0;
            }
          }
        }
      } catch (Exception e)
      {
        FRLogger.error("There was a problem parsing the '" + p_args[i] + "' parameter", e);
      }
    }
  }

  public boolean isTestVersion()
  {
    return test_version_option;
  }

  public String getDesignDir()
  {
    return input_directory;
  }

  public int getMaxPasses()
  {
    return autoRouterSettings.max_passes;
  }

  public int getNumThreads()
  {
    return autoRouterSettings.num_threads;
  }

  public String getHybridRatio()
  {
    return autoRouterSettings.hybrid_ratio;
  }

  public BoardUpdateStrategy getBoardUpdateStrategy()
  {
    return autoRouterSettings.board_update_strategy;
  }

  public ItemSelectionStrategy getItemSelectionStrategy()
  {
    return autoRouterSettings.item_selection_strategy;
  }
}