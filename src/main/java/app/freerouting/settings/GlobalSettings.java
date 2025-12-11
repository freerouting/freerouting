package app.freerouting.settings;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import app.freerouting.constants.Constants;
import app.freerouting.core.BoardFileDetails;
import app.freerouting.gui.FileFormat;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.ReflectionUtil;
import app.freerouting.management.TextManager;
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
  private static Path userDataPath = Paths.get(System.getProperty("java.io.tmpdir"), "freerouting");
  private static Path configurationFilePath = userDataPath.resolve("freerouting.json");
  private static Boolean isUserDataPathLocked = false;
  public final transient EnvironmentSettings environmentSettings = new EnvironmentSettings();
  @SerializedName("profile")
  public final UserProfileSettings userProfileSettings = new UserProfileSettings();
  @SerializedName("gui")
  public final GuiSettings guiSettings = new GuiSettings();
  @SerializedName("router")
  public final RouterSettings routerSettings = new RouterSettings();
  @SerializedName("drc")
  public final DesignRulesCheckerSettings drcSettings = new DesignRulesCheckerSettings();
  @SerializedName("usage_and_diagnostic_data")
  public final UsageAndDiagnosticDataSettings usageAndDiagnosticData = new UsageAndDiagnosticDataSettings();
  @SerializedName("feature_flags")
  public final FeatureFlagsSettings featureFlags = new FeatureFlagsSettings();
  @SerializedName("api_server")
  public final ApiServerSettings apiServerSettings = new ApiServerSettings();
  @SerializedName("statistics")
  public final StatisticsSettings statistics = new StatisticsSettings();
  private final transient String[] supportedLanguages = {
      "en",
      "de",
      "zh",
      "zh_TW",
      "hi",
      "es",
      "it",
      "fr",
      "ar",
      "bn",
      "ru",
      "pt",
      "ja",
      "ko"
  };
  @SerializedName("version")
  public String version = Constants.FREEROUTING_VERSION;
  public transient boolean show_help_option = false;
  // DRC report file details that we got from the command line arguments.
  public transient BoardFileDetails drc_report_file = null;
  /**
   * The design_input_filename field is deprecated and should not be used. They are kept here for compatibility reasons.
   * Its function is now moved to the input.getFilename() method of RoutingJob object.
   */
  @Deprecated
  public transient String design_input_filename;
  /**
   * The design_output_filename field is deprecated and should not be used. They are kept here for compatibility reasons.
   * Its function is now moved to the output.getFilename() method of RoutingJob object.
   */
  @Deprecated
  public transient String design_output_filename;
  /**
   * The design_rules_filename field is deprecated and should not be used. They are kept here for compatibility reasons.
   * Its function is now removed, .rules files are considered to be deprecated, and other configuration methods should be used instead.
   */
  @Deprecated
  public transient String design_rules_filename;
  public transient Locale currentLocale = Locale.getDefault();

  public GlobalSettings()
  {
    // validate and set the current locale
    if (Arrays
        .stream(supportedLanguages)
        .noneMatch(currentLocale.getLanguage()::equals))
    {
      // the fallback language is English
      currentLocale = Locale.ENGLISH;
    }
  }

  public static void lockUserDataPath()
  {
    isUserDataPathLocked = true;
  }

  public static Path getUserDataPath()
  {
    return userDataPath;
  }

  public static void setUserDataPath(Path userDataPath)
  {
    if (!isUserDataPathLocked)
    {
      GlobalSettings.userDataPath = userDataPath;
      configurationFilePath = userDataPath.resolve("freerouting.json");
      FRLogger.changeFileLogLocation(userDataPath);
    }
  }

  /*
   * Loads the settings from the default JSON settings file
   */
  public static GlobalSettings load() throws IOException
  {
    GlobalSettings loadedSettings = null;
    try (Reader reader = Files.newBufferedReader(configurationFilePath, StandardCharsets.UTF_8))
    {
      loadedSettings = GsonProvider.GSON.fromJson(reader, GlobalSettings.class);
    }

    GlobalSettings defaultSettings = new GlobalSettings();
    if (loadedSettings != null)
    {
      // If the version numbers are different, we must save the file again to update it
      boolean isSaveNeeded = (!loadedSettings.version.equals(defaultSettings.version));

      // Apply all the loaded settings to the result if they are not null
      loadedSettings.version = null;
      ReflectionUtil.copyFields(defaultSettings, loadedSettings);

      if (isSaveNeeded)
      {
        saveAsJson(loadedSettings);
      }
    }

    return loadedSettings;
  }

  /*
   * Saves the settings to the default JSON settings file
   */
  public static void saveAsJson(GlobalSettings globalSettings) throws IOException
  {
    // Make sure that we have the directory structure in place, and create it if it doesn't exist
    Files.createDirectories(configurationFilePath.getParent());

    // Write the settings to the file
    try (Writer writer = Files.newBufferedWriter(configurationFilePath, StandardCharsets.UTF_8))
    {
      GsonProvider.GSON.toJson(globalSettings, writer);
    }
  }

  /*
   * Sets a property value in the settings, and it permanently saves it into the settings file.
   * Property names are in the format of "section.property" (eg. "router.max_passes", "gui:input_directory" or "profile-email").
   */
  public static Boolean setDefaultValue(String propertyName, String newValue)
  {
    try
    {
      var gs = load();
      gs.setValue(propertyName, newValue);
      saveAsJson(gs);
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
    for (var entry : System
        .getenv()
        .entrySet())
    {
      if (entry
          .getKey()
          .startsWith("FREEROUTING__"))
      {
        String propertyName = entry
            .getKey()
            .substring("FREEROUTING__".length())
            .toLowerCase()
            .replace("__", ".");
        setValue(propertyName, entry.getValue());
      }
    }
  }

  /*
   * Sets a property value in the settings for the current process, but it does so without permanently saving it into the settings file.
   * For scenarios where the settings also needs to be saved in the settings file, use the save() method instead.
   * Property names are in the format of "section.property" (eg. "router.max_passes", "gui:input_directory" or "profile-email").
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
    return currentLocale;
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
          String[] parts = p_args[i]
              .substring(2)
              .split("=");
          if ((parts.length == 2) && (!Objects.equals(parts[0], "user_data_path")))
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
        else if (p_args[i].startsWith("-drc"))
        {
          // DRC-only mode (must be checked before -dr)
          routerSettings.enabled = false;
          drcSettings.enabled = true;
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            drc_report_file = new BoardFileDetails();
            drc_report_file.format = FileFormat.DRC_JSON;
            drc_report_file.setFilename(p_args[i + 1]);
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
            routerSettings.optimizer.maxThreads = Integer.decode(p_args[i + 1]);

            if (routerSettings.optimizer.maxThreads <= 0)
            {
              routerSettings.optimizer.maxThreads = 0;
            }
            if (routerSettings.optimizer.maxThreads > 1024)
            {
              routerSettings.optimizer.maxThreads = 1024;
            }
          }
        }
        else if (p_args[i].startsWith("-oit"))
        {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            routerSettings.optimizer.optimizationImprovementThreshold = Float.parseFloat(p_args[i + 1]) / 100;

            if (routerSettings.optimizer.optimizationImprovementThreshold <= 0)
            {
              routerSettings.optimizer.optimizationImprovementThreshold = 0;
            }
          }
        }
        else if (p_args[i].startsWith("-us"))
        {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            String op = p_args[i + 1]
                .toLowerCase()
                .trim();
            routerSettings.optimizer.boardUpdateStrategy = op.equals("global") ? BoardUpdateStrategy.GLOBAL_OPTIMAL : (op.equals("hybrid") ? BoardUpdateStrategy.HYBRID : BoardUpdateStrategy.GREEDY);
          }
        }
        else if (p_args[i].startsWith("-is"))
        {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            String op = p_args[i + 1]
                .toLowerCase()
                .trim();
            routerSettings.optimizer.itemSelectionStrategy = op.indexOf("seq") == 0 ? ItemSelectionStrategy.SEQUENTIAL : (op.indexOf("rand") == 0 ? ItemSelectionStrategy.RANDOM : ItemSelectionStrategy.PRIORITIZED);
          }
        }
        else if (p_args[i].startsWith("-hr"))
        { // hybrid ratio
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            routerSettings.optimizer.hybridRatio = p_args[i + 1].trim();
          }
        }
        else if (p_args[i].equals("-l"))
        {
          String localeString = "";
          if (p_args.length > i + 1)
          {
            localeString = p_args[i + 1]
                .toLowerCase()
                .replace("-", "_");
          }

          // the locale is provided
          if (localeString.startsWith("en"))
          {
            currentLocale = Locale.ENGLISH;
          }
          else if (localeString.startsWith("de"))
          {
            currentLocale = Locale.GERMAN;
          }
          else if (localeString.startsWith("zh_tw"))
          {
            currentLocale = Locale.TRADITIONAL_CHINESE;
          }
          else if (localeString.startsWith("zh"))
          {
            currentLocale = Locale.SIMPLIFIED_CHINESE;
          }
          else if (localeString.startsWith("hi"))
          {
            //current_locale = Locale.HINDI;
            currentLocale = Locale.forLanguageTag("hi-IN");
          }
          else if (localeString.startsWith("es"))
          {
            //current_locale = Locale.SPANISH;
            currentLocale = Locale.forLanguageTag("es-ES");
          }
          else if (localeString.startsWith("it"))
          {
            //current_locale = Locale.ITALIAN;
            currentLocale = Locale.forLanguageTag("it-IT");
          }
          else if (localeString.startsWith("fr"))
          {
            currentLocale = Locale.FRENCH;
          }
          else if (localeString.startsWith("ar"))
          {
            //current_locale = Locale.ARABIC;
            currentLocale = Locale.forLanguageTag("ar-EG");
          }
          else if (localeString.startsWith("bn"))
          {
            //current_locale = Locale.BENGALI;
            currentLocale = Locale.forLanguageTag("bn-BD");
          }
          else if (localeString.startsWith("ru"))
          {
            //current_locale = Locale.RUSSIAN;
            currentLocale = Locale.forLanguageTag("ru-RU");
          }
          else if (localeString.startsWith("pt"))
          {
            //current_locale = Locale.PORTUGUESE;
            currentLocale = Locale.forLanguageTag("pt-PT");
          }
          else if (localeString.startsWith("ja"))
          {
            currentLocale = Locale.JAPANESE;
          }
          else if (localeString.startsWith("ko"))
          {
            currentLocale = Locale.KOREAN;
          }
        }
        else if (p_args[i].startsWith("-im"))
        {
          featureFlags.snapshots = true;
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            featureFlags.snapshots = !(Objects.equals(p_args[i + 1], "0"));
          }
        }
        else if (p_args[i].startsWith("-dl"))
        {
          featureFlags.logging = false;
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
          routerSettings.ignoreNetClasses = p_args[i + 1].split(",");
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
        else if (p_args[i].startsWith("-random_seed"))
        {
          if (p_args.length > i + 1 && !p_args[i + 1].startsWith("-"))
          {
            routerSettings.random_seed = TextManager.hexadecimalStringToLong(p_args[i + 1]);
            i++;
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
    return routerSettings.optimizer.maxThreads;
  }

  public String getHybridRatio()
  {
    return routerSettings.optimizer.hybridRatio;
  }

  public BoardUpdateStrategy getBoardUpdateStrategy()
  {
    return routerSettings.optimizer.boardUpdateStrategy;
  }

  public ItemSelectionStrategy getItemSelectionStrategy()
  {
    return routerSettings.optimizer.itemSelectionStrategy;
  }
}