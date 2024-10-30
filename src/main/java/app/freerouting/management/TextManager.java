package app.freerouting.management;

import app.freerouting.logger.FRLogger;
import app.freerouting.settings.GlobalSettings;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Singleton class to manage the text resources for the application
 */
public class TextManager
{
  // A key-value pair for icon names and their corresponding unicode characters
  private final Map<String, Integer> iconMap = new HashMap<>()
  {{
    put("cog", 0xF0493);
    put("auto-fix", 0xF0068);
    put("cancel", 0xF073A);
    put("delete-sweep", 0xF05E9);
    put("undo", 0xF054C);
    put("redo", 0xF044E);
    put("spider-web", 0xF0BCA);
    put("order-bool-ascending-variant", 0xF098F);
    put("magnify-plus-cursor", 0xF0A63);
    put("magnify-minus", 0xF034A);
    put("alert", 0xF0026);
    put("close-octagon", 0xF015C);
  }};
  private Locale currentLocale;
  private String currentBaseName;
  private ResourceBundle defaultMessages;
  private ResourceBundle classMessages;
  private ResourceBundle englishClassMessages;
  private Font materialDesignIcons = null;

  public TextManager(Class baseClass, Locale locale)
  {
    this.currentLocale = locale;
    loadResourceBundle(baseClass.getName());

    try
    {
      // Load the font
      materialDesignIcons = Font.createFont(Font.TRUETYPE_FONT, GlobalSettings.class.getResourceAsStream("/materialdesignicons-webfont.ttf"));

      // Register the font
      GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
      ge.registerFont(materialDesignIcons);
    } catch (IOException | FontFormatException e)
    {
      FRLogger.error("There was a problem loading the Material Design Icons font", e);
    }
  }

  public static String convertInstantToString(Instant instant)
  {
    return convertInstantToString(instant, "yyyyMMdd_HHmmss");
  }

  public static String convertInstantToString(Instant instant, String format)
  {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
    LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    return localDateTime.format(formatter);
  }

  public static String generateRandomAlphanumericString(int length)
  {
    String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    StringBuilder randomString = new StringBuilder();
    for (int i = 0; i < length; i++)
    {
      int index = (int) (characters.length() * Math.random());
      randomString.append(characters.charAt(index));
    }
    return randomString.toString();
  }

  private void loadResourceBundle(String baseName)
  {
    this.currentBaseName = baseName;

    // Load the default messages that are common to all classes
    try
    {
      defaultMessages = ResourceBundle.getBundle("app.freerouting.Common", currentLocale);
    } catch (Exception e)
    {
      FRLogger.warn("There was a problem loading the resource bundle 'app.freerouting.Common' of locale '" + currentLocale + "'");
      try
      {
        defaultMessages = ResourceBundle.getBundle("app.freerouting.Common", Locale.forLanguageTag("en-US"));
      } catch (Exception ex)
      {
        defaultMessages = null;
        FRLogger.error("There was a problem loading the resource bundle 'app.freerouting.Common' of locale 'en-US'", null);
      }
    }

    // Load the class-specific messages
    try
    {
      classMessages = ResourceBundle.getBundle(currentBaseName, currentLocale);
    } catch (Exception e)
    {
      //FRLogger.warn("There was a problem loading the resource bundle '" + currentBaseName + "' of locale '" + currentLocale + "'");
      try
      {
        classMessages = ResourceBundle.getBundle(currentBaseName, Locale.forLanguageTag("en-US"));
      } catch (Exception ex)
      {
        classMessages = null;
        //FRLogger.error("There was a problem loading the resource bundle '" + currentBaseName + "' of locale 'en-US'",null);
      }
    }

    // Load the fallback English messages
    try
    {
      englishClassMessages = ResourceBundle.getBundle(currentBaseName, Locale.forLanguageTag("en"));
    } catch (Exception e)
    {
      //FRLogger.warn("There was a problem loading the resource bundle '" + currentBaseName + "' of locale 'en'");
    }
  }

  public String getText(String key, String... args)
  {
    String text;
    if ((classMessages != null) && (classMessages.containsKey(key)))
    {
      text = classMessages.getString(key);
    }
    else if ((defaultMessages != null) && (defaultMessages.containsKey(key)))
    {
      text = defaultMessages.getString(key);
    }
    else if ((englishClassMessages != null) && (englishClassMessages.containsKey(key)))
    {
      text = englishClassMessages.getString(key);
    }
    else
    {
      return key;
    }

    // Pattern to match {{variable_name}} placeholders
    Pattern pattern = Pattern.compile("\\{\\{(.+?)\\}\\}");
    Matcher matcher = pattern.matcher(text);

    // Find and replace all matches
    int argIndex = 0;
    while (matcher.find())
    {
      // Entire match including {{ and }}
      String placeholder = matcher.group(0);

      if (!placeholder.startsWith("{{icon:") && argIndex < args.length)
      {
        // replace the placeholder with the value
        text = text.replace(placeholder, args[argIndex]);
        argIndex++;
      }
    }

    return text;
  }

  private String insertIcons(JComponent component, String text)
  {
    // Pattern to match {{variable_name}} placeholders
    Pattern pattern = Pattern.compile("\\{\\{icon:(.+?)\\}\\}");
    Matcher matcher = pattern.matcher(text);

    // Find all matches
    while (matcher.find())
    {
      // Entire match including {{ and }}
      String placeholder = matcher.group(0);

      // Get the icon name
      String iconName = matcher.group(1);

      try
      {

        // Get the unicode code point for the icon
        int codePoint = iconMap.get(iconName);

        // Convert the code point to a String
        text = text.replace(placeholder, new String(Character.toChars(codePoint)));

        Font originalFont = component.getFont();
        component.setFont(materialDesignIcons.deriveFont(Font.PLAIN, originalFont.getSize() * 1.5f));
      } catch (Exception e)
      {
        FRLogger.error("There was a problem setting the icon for the component", e);
      }
    }

    return text;
  }

  // Add methods to set text for different GUI components
  public void setText(JComponent component, String key, String... args)
  {
    String text = getText(key, args);
    String tooltip = getText(key + "_tooltip", args);

    if (tooltip == null || tooltip.isEmpty() || tooltip.equals(key + "_tooltip"))
    {
      tooltip = null;
    }

    text = insertIcons(component, text);

    // Set the text for the component
    if (component instanceof JButton)
    {
      // Set the text for the button
      ((JButton) component).setText(text);
      if (tooltip != null && !tooltip.isEmpty())
      {
        // Set the tooltip text for the component
        component.setToolTipText(tooltip);
      }
    }
    else if (component instanceof JToggleButton)
    {
      // Set the text for the toggle button
      ((JToggleButton) component).setText(text);
      if (tooltip != null && !tooltip.isEmpty())
      {
        // Set the tooltip text for the component
        component.setToolTipText(tooltip);
      }
    }
    else if (component instanceof JLabel)
    {
      // Set the text for the toggle button
      ((JLabel) component).setText(text);
      if (tooltip != null && !tooltip.isEmpty())
      {
        // Set the tooltip text for the component
        component.setToolTipText(tooltip);
      }
    }
    else
    {
      // Handle other components like JLabel, JTextArea, etc.
      String componentType = component.getClass().getName();
      FRLogger.warn("The component type '" + componentType + "' is not supported");
    }

    // Handle other components like JLabel, JTextArea, etc.
  }

  public Locale getLocale()
  {
    return currentLocale;
  }

  public void setLocale(Locale locale)
  {
    this.currentLocale = locale;
    loadResourceBundle(currentBaseName);
  }

}