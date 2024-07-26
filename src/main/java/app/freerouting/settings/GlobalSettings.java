package app.freerouting.settings;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import app.freerouting.constants.Constants;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.ReflectionUtil;
import app.freerouting.management.gson.GsonProvider;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

public class GlobalSettings implements Serializable
{
  private static final Path PATH = Paths.get(System.getProperty("java.io.tmpdir"), "freerouting", "freerouting.json");
  public final String version = Constants.FREEROUTING_VERSION;
  public transient final EnvironmentSettings environmentSettings = new EnvironmentSettings();
  @SerializedName("profile")
  public final UserProfileSettings userProfileSettings = new UserProfileSettings();
  @SerializedName("gui")
  public final GuiSettings guiSettings = new GuiSettings();
  @SerializedName("router")
  public final RouterSettings routerSettings = new RouterSettings();
  @SerializedName("usage_and_diagnostic_data")
  public final UsageAndDiagnosticDataSettings usageAndDiagnosticData = new UsageAndDiagnosticDataSettings();
  @SerializedName("disabled_features")
  public final DisabledFeaturesSettings disabledFeatures = new DisabledFeaturesSettings();
  @SerializedName("api_server")
  public final ApiServerSettings apiServerSettings = new ApiServerSettings();
  public transient boolean show_help_option = false;
  public transient String design_input_filename;
  public transient String design_output_filename;
  public transient String design_rules_filename;
  public transient String[] supported_languages = {"en", "de", "zh", "zh_TW", "hi", "es", "it", "fr", "ar", "bn", "ru", "pt", "ja", "ko"};
  public transient Locale current_locale = Locale.getDefault();

  public GlobalSettings()
  {
    // validate and set the current locale
    if (Arrays.stream(supported_languages).noneMatch(current_locale.getLanguage()::equals))
    {
      // the fallback language is English
      current_locale = Locale.ENGLISH;
    }
  }

  /*
   * Loads the settings from the default JSON settings file
   */
  public static GlobalSettings load() throws IOException
  {
    try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8))
    {
      return GsonProvider.GSON.fromJson(reader, GlobalSettings.class);
    }
  }

  /*
   * Saves the settings to the default JSON settings file
   */
  public static void save(GlobalSettings options) throws IOException
  {
    // Make sure that we have the directory structure in place, and create it if it doesn't exist
    Files.createDirectories(PATH.getParent());

    // Write the settings to the file
    try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8))
    {
      GsonProvider.GSON.toJson(options, writer);
    }
  }

  /*
   * Sets a property value in the settings, and it permanently saves it into the settings file.
   * Property names are in the format of "section.property" (eg. "router.max_passes" or "gui.input_directory").
   */
  public static Boolean setDefaultValue(String propertyName, String newValue)
  {
    try
    {
      var gs = load();
      gs.setValue(propertyName, newValue);
      save(gs);
      return true;
    } catch (Exception e)
    {
      FRLogger.error("Failed to save property value for: " + propertyName, e);
      return false;
    }
  }

  /*
   * Applies the environment variables to the settings
   */
  public void applyEnvironmentVariables()
  {
    // Read all the environment variables that begins with "FREEROUTING__"
    for (var entry : System.getenv().entrySet())
    {
      if (entry.getKey().startsWith("FREEROUTING__"))
      {
        String propertyName = entry.getKey().substring("FREEROUTING__".length()).toLowerCase().replace("__", ".");
        setValue(propertyName, entry.getValue());
      }
    }
  }

  /*
   * Sets a property value in the settings for the current process, but it does so without permanently saving it into the settings file.
   * For scenarios where the settings also needs to be saved in the settings file, use the save() method instead.
   * Property names are in the format of "section.property" (eg. "router.max_passes" or "gui.input_directory").
   */
  public Boolean setValue(String propertyName, String newValue)
  {
    try
    {
      ReflectionUtil.setFieldValue(this, propertyName, newValue);
      return true;
    } catch (Exception e)
    {
      FRLogger.error("Failed to set property value for: " + propertyName, e);
      return false;
    }
  }

  public Locale getCurrentLocale()
  {
    return current_locale;
  }

  public void applyCommandLineArguments(String[] p_args)
  {
    for (int i = 0; i < p_args.length; ++i)
    {
      try
      {
        if (p_args[i].startsWith("--"))
        {
          // it's a general settings value setter
          String[] parts = p_args[i].substring(2).split("=");
          if (parts.length == 2)
          {
            setValue(parts[0], parts[1]);
          }
        }
        else if (p_args[i].startsWith("-de"))
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
            guiSettings.inputDirectory = p_args[i + 1];
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
            routerSettings.maxPasses = Integer.decode(p_args[i + 1]);

            if (routerSettings.maxPasses < 1)
            {
              routerSettings.maxPasses = 1;
            }
            if (routerSettings.maxPasses > 99998)
            {
              routerSettings.maxPasses = 99998;
            }
          }
        }
        else if (p_args[i].startsWith("-mt"))
        {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            routerSettings.maxThreads = Integer.decode(p_args[i + 1]);

            if (routerSettings.maxThreads <= 0)
            {
              routerSettings.maxThreads = 0;
            }
            if (routerSettings.maxThreads > 1024)
            {
              routerSettings.maxThreads = 1024;
            }
          }
        }
        else if (p_args[i].startsWith("-oit"))
        {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            routerSettings.optimizationImprovementThreshold = Float.parseFloat(p_args[i + 1]) / 100;

            if (routerSettings.optimizationImprovementThreshold <= 0)
            {
              routerSettings.optimizationImprovementThreshold = 0;
            }
          }
        }
        else if (p_args[i].startsWith("-us"))
        {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            String op = p_args[i + 1].toLowerCase().trim();
            routerSettings.boardUpdateStrategy = op.equals("global") ? BoardUpdateStrategy.GLOBAL_OPTIMAL : (op.equals("hybrid") ? BoardUpdateStrategy.HYBRID : BoardUpdateStrategy.GREEDY);
          }
        }
        else if (p_args[i].startsWith("-is"))
        {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            String op = p_args[i + 1].toLowerCase().trim();
            routerSettings.itemSelectionStrategy = op.indexOf("seq") == 0 ? ItemSelectionStrategy.SEQUENTIAL : (op.indexOf("rand") == 0 ? ItemSelectionStrategy.RANDOM : ItemSelectionStrategy.PRIORITIZED);
          }
        }
        else if (p_args[i].startsWith("-hr"))
        { // hybrid ratio
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            routerSettings.hybridRatio = p_args[i + 1].trim();
          }
        }
        else if (p_args[i].equals("-l"))
        {
          String localeString = "";
          if (p_args.length > i + 1)
          {
            localeString = p_args[i + 1].toLowerCase().replace("-", "_");
          }

          // the locale is provided
          if (localeString.startsWith("en"))
          {
            current_locale = Locale.ENGLISH;
          }
          else if (localeString.startsWith("de"))
          {
            current_locale = Locale.GERMAN;
          }
          else if (localeString.startsWith("zh_tw"))
          {
            current_locale = Locale.TRADITIONAL_CHINESE;
          }
          else if (localeString.startsWith("zh"))
          {
            current_locale = Locale.SIMPLIFIED_CHINESE;
          }
          else if (localeString.startsWith("hi"))
          {
            //current_locale = Locale.HINDI;
            current_locale = Locale.forLanguageTag("hi-IN");
          }
          else if (localeString.startsWith("es"))
          {
            //current_locale = Locale.SPANISH;
            current_locale = Locale.forLanguageTag("es-ES");
          }
          else if (localeString.startsWith("it"))
          {
            //current_locale = Locale.ITALIAN;
            current_locale = Locale.forLanguageTag("it-IT");
          }
          else if (localeString.startsWith("fr"))
          {
            current_locale = Locale.FRENCH;
          }
          else if (localeString.startsWith("ar"))
          {
            //current_locale = Locale.ARABIC;
            current_locale = Locale.forLanguageTag("ar-EG");
          }
          else if (localeString.startsWith("bn"))
          {
            //current_locale = Locale.BENGALI;
            current_locale = Locale.forLanguageTag("bn-BD");
          }
          else if (localeString.startsWith("ru"))
          {
            //current_locale = Locale.RUSSIAN;
            current_locale = Locale.forLanguageTag("ru-RU");
          }
          else if (localeString.startsWith("pt"))
          {
            //current_locale = Locale.PORTUGUESE;
            current_locale = Locale.forLanguageTag("pt-PT");
          }
          else if (localeString.startsWith("ja"))
          {
            current_locale = Locale.JAPANESE;
          }
          else if (localeString.startsWith("ko"))
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
        else if (p_args[i].startsWith("-dl"))
        {
          disabledFeatures.logging = true;
        }
        else if (p_args[i].startsWith("-da"))
        {
          usageAndDiagnosticData.disableAnalytics = true;
        }
        else if (p_args[i].startsWith("-host"))
        {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            environmentSettings.host = p_args[i + 1].trim();
          }
        }
        else if (p_args[i].startsWith("-help"))
        {
          show_help_option = true;
        }
        else if (p_args[i].startsWith("-inc"))
        {
          // ignore net class(es)
          routerSettings.ignoreNetClassesByAutorouter = p_args[i + 1].split(",");
        }
        else if (p_args[i].startsWith("-dct"))
        {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            guiSettings.dialogConfirmationTimeout = Integer.parseInt(p_args[i + 1]);

            if (guiSettings.dialogConfirmationTimeout <= 0)
            {
              guiSettings.dialogConfirmationTimeout = 0;
            }
          }
        }
      } catch (Exception e)
      {
        FRLogger.error("There was a problem parsing the '" + p_args[i] + "' parameter", e);
      }
    }
  }

  public String getDesignDir()
  {
    return guiSettings.inputDirectory;
  }

  public int getMaxPasses()
  {
    return routerSettings.maxPasses;
  }

  public int getNumThreads()
  {
    return routerSettings.maxThreads;
  }

  public String getHybridRatio()
  {
    return routerSettings.hybridRatio;
  }

  public BoardUpdateStrategy getBoardUpdateStrategy()
  {
    return routerSettings.boardUpdateStrategy;
  }

  public ItemSelectionStrategy getItemSelectionStrategy()
  {
    return routerSettings.itemSelectionStrategy;
  }
}