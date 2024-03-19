package app.freerouting.gui;

import app.freerouting.logger.FRLogger;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JToggleButton;

// Singleton class to manage the text resources for the application
public class TextManager {
  private Locale currentLocale;
  private String currentBaseName;
  private ResourceBundle messages;
  private Font materialDesignIcons = null;
  // A key-value pair for icon names and their corresponding unicode characters
  private Map<String, Integer> iconMap = new HashMap<>()
  {{
    put("auto-fix", 0xF0068);
    put("undo", 0xF054C);
    put("redo", 0xF044E);
  }};

  public TextManager(Class baseClass, Locale locale) {
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

  private void loadResourceBundle(String baseName) {
    this.currentBaseName = baseName;
    this.messages = ResourceBundle.getBundle(currentBaseName, currentLocale);
  }

  public void setLocale(Locale locale) {
    this.currentLocale = locale;
    loadResourceBundle(currentBaseName);
  }

  public String getText(String key, String... args) {
    // if the key is not found, return an empty string
    if (!messages.containsKey(key)) {
      return "";
    }

    String text = messages.getString(key);

    // Pattern to match {{variable_name}} placeholders
    Pattern pattern = Pattern.compile("\\{\\{(.+?)\\}\\}");
    Matcher matcher = pattern.matcher(text);

    // Find and print all matches
    int argIndex = 0;
    while (matcher.find()) {
      // entire match including {{ and }}
      String placeholder = matcher.group(0);

      if (!placeholder.startsWith("{{icon:") && argIndex < args.length - 1)
      {
        // replace the placeholder with the value
        text = text.replace(placeholder, args[argIndex]);
        argIndex++;
      }
    }

    return String.format(messages.getString(key), args);
  }

  // Add methods to set text for different GUI components
  public void setText(JComponent component, String key, String... args) {
    String text = getText(key, args);
    String tooltip = getText(key + "_tooltip", args);

    // If the text is in the format of {{icon:icon_name}}, set the icon instead
    if (text.startsWith("{{icon:") && text.endsWith("}}"))
    {
      try {
        String iconName = text.substring(7, text.length() - 2);

        // Get the unicode code point for the icon
        int codePoint = iconMap.get(iconName);

        // Convert the code point to a String
        text = new String(Character.toChars(codePoint));

        Font originalFont = component.getFont();
        component.setFont(materialDesignIcons.deriveFont(Font.PLAIN, originalFont.getSize()));
      } catch (Exception e) {
        FRLogger.error("There was a problem setting the icon for the component", e);
      }
    }

    // Set the text for the component
    if (component instanceof JButton) {
      // Set the text for the button
      ((JButton) component).setText(text);
      if (tooltip != null && !tooltip.isEmpty()) {
        // Set the tooltip text for the component
        ((JButton) component).setToolTipText(tooltip);
      }
    } else if (component instanceof JToggleButton) {
      // Set the text for the toggle button
      ((JToggleButton) component).setText(text);
      if (tooltip != null && !tooltip.isEmpty()) {
        // Set the tooltip text for the component
        ((JToggleButton) component).setToolTipText(tooltip);
      }
    } else if (component instanceof JLabel) {
      // Set the text for the toggle button
      ((JLabel) component).setText(text);
      if (tooltip != null && !tooltip.isEmpty())
      {
        // Set the tooltip text for the component
        ((JLabel) component).setToolTipText(tooltip);
      }
    } else {
      // Handle other components like JLabel, JTextArea, etc.
      String componentType = component.getClass().getName();
      FRLogger.warn("The component type '" + componentType + "' is not supported");
    }


    // Handle other components like JLabel, JTextArea, etc.
  }
}

