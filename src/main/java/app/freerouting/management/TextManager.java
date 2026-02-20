package app.freerouting.management;

import app.freerouting.logger.FRLogger;
import app.freerouting.settings.GlobalSettings;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JToggleButton;

/**
 * Singleton class to manage the text resources for the application
 */
public class TextManager {

  // A key-value pair for Material Design icon names and their corresponding
  // Unicode characters
  private final Map<String, Integer> iconMap = new HashMap<>() {
    {
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
      put("play", 0xF040A);
      put("pause", 0xF03E4);
      put("step-forward", 0xF04D7);
      put("step-backward", 0xF04D5);
      put("fast-forward", 0xF0211);
      put("rewind", 0xF045F);
    }
  };
  private Locale currentLocale;
  private String currentBaseName;
  private ResourceBundle defaultMessages;
  private ResourceBundle classMessages;
  private ResourceBundle englishClassMessages;
  private Font materialDesignIcons;

  public TextManager(Class baseClass, Locale locale) {
    this.currentLocale = locale;
    loadResourceBundle(baseClass.getName());

    try {
      // Load the font
      materialDesignIcons = Font.createFont(Font.TRUETYPE_FONT,
          GlobalSettings.class.getResourceAsStream("/materialdesignicons-webfont.ttf"));

      // Register the font
      GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
      ge.registerFont(materialDesignIcons);
    } catch (IOException | FontFormatException e) {
      FRLogger.error("There was a problem loading the Material Design Icons font", e);
    }
  }

  public static String convertInstantToString(Instant instant) {
    return convertInstantToString(instant, "yyyyMMdd_HHmmss");
  }

  public static String convertInstantToString(Instant instant, String format) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
    LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    return localDateTime.format(formatter);
  }

  public static String generateRandomAlphanumericString(int length) {
    String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    StringBuilder randomString = new StringBuilder();
    for (int i = 0; i < length; i++) {
      int index = (int) (characters.length() * ThreadLocalRandom.current().nextDouble());
      randomString.append(characters.charAt(index));
    }
    return randomString.toString();
  }

  public static Long parseTimespanString(String timespanString) {
    try {
      // convert the string from "HH:mm:ss" or "mm:ss" or "ss" format to
      // "PnDTnHnMn.nS" format
      var durationString = convertFromTimespanToDurationFormat(timespanString);
      // parse the duration
      Duration duration = Duration.parse(durationString);
      return duration.getSeconds();
    } catch (DateTimeParseException _) {
      return null;
    }
  }

  public static String convertFromTimespanToDurationFormat(String timespanString) {
    String[] parts = timespanString.split(":");
    StringBuilder durationString = new StringBuilder("PT");

    if (parts.length == 3) {
      durationString
          .append(parts[0])
          .append("H")
          .append(parts[1])
          .append("M")
          .append(parts[2])
          .append("S");
    } else if (parts.length == 2) {
      durationString
          .append(parts[0])
          .append("M")
          .append(parts[1])
          .append("S");
    } else if (parts.length == 1) {
      durationString
          .append(parts[0])
          .append("S");
    }

    return durationString.toString();
  }

  /**
   * Shortens a string to a specified number of characters by replacing the middle
   * part with dots
   *
   * @param text               The text to shorten
   * @param peakCharacterCount The number of characters to keep at the beginning
   *                           and end of the text Example: shortenString("This is
   *                           a long text", 3) -> "Thi...ext" shortenString("This
   *                           is a long
   *                           text", 5) -> "This ... text" shortenString("This is
   *                           a long text", 10) -> "This is a long text"
   * @return The shortened text
   */
  public static String shortenString(String text, int peakCharacterCount) {
    String shortenedText = text;
    if (text.length() > peakCharacterCount * 2) {
      shortenedText = shortenedText.substring(0, peakCharacterCount) + "..."
          + text.substring(text.length() - peakCharacterCount);
    }
    return shortenedText;
  }

  /**
   * Removes quotes from the beginning and end of a string
   *
   * @param text The text to remove quotes from
   * @return The text without quotes
   */
  public static String removeQuotes(String text) {
    if ((text == null) || (text.length() < 2)) {
      return text;
    }

    if (text.startsWith("\"") && text.endsWith("\"")) {
      text = text.substring(1, text.length() - 1);
    }

    return text;
  }

  /**
   * Decrypts a string using AES-256-CBC with a passphrase
   *
   * @param encodedText The text to encrypt
   * @param passphrase  The passphrase to use for encryption
   * @return The encrypted text
   */
  public static byte[] decryptAes256Cbc(byte[] encodedText, String passphrase) {
    try {
      IvParameterSpec iv = new IvParameterSpec("freeroutingivpar".getBytes(StandardCharsets.UTF_8));
      SecretKeySpec skeySpec = new SecretKeySpec(passphrase.getBytes(StandardCharsets.UTF_8), "AES");

      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
      cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);
      byte[] original = cipher.doFinal(encodedText);

      return original;
    } catch (Exception ex) {
      FRLogger.error("There was a problem decrypting the text", ex);
    }

    return null;
  }

  /**
   * Unescapes unicode characters in a string
   *
   * @param text The text to unescape Example: unescapeUnicode("This is a
   *             \\u0063haracter") -> "This is a character"
   */
  public static String unescapeUnicode(String text) {
    Pattern pattern = Pattern.compile("\\\\u(\\p{XDigit}{4})");
    Matcher matcher = pattern.matcher(text);
    StringBuffer result = new StringBuffer();

    while (matcher.find()) {
      String hexCode = matcher.group(1);
      char unicode = (char) Integer.parseInt(hexCode, 16);
      matcher.appendReplacement(result, String.valueOf(unicode));
    }
    matcher.appendTail(result);

    return result.toString();
  }

  public static String longToHexadecimalString(Long longValue) {
    return "0x%016X".formatted(longValue);
  }

  public static Long hexadecimalStringToLong(String hexString) {
    if (hexString.startsWith("0x") || hexString.startsWith("0X")) {
      hexString = hexString.substring(2);
      return Long.parseUnsignedLong(hexString, 16);
    } else {
      return Long.parseUnsignedLong(hexString, 10);
    }
  }

  private void loadResourceBundle(String baseName) {
    this.currentBaseName = baseName;

    // Load the default messages that are common to all classes
    try {
      defaultMessages = ResourceBundle.getBundle("app.freerouting.Common", currentLocale);
    } catch (Exception _) {
      FRLogger.warn(
          "There was a problem loading the resource bundle 'app.freerouting.Common' of locale '" + currentLocale + "'");
      try {
        defaultMessages = ResourceBundle.getBundle("app.freerouting.Common", Locale.forLanguageTag("en-US"));
      } catch (Exception _) {
        defaultMessages = null;
        FRLogger.error("There was a problem loading the resource bundle 'app.freerouting.Common' of locale 'en-US'",
            null);
      }
    }

    // Load the class-specific messages
    try {
      classMessages = ResourceBundle.getBundle(currentBaseName, currentLocale);
    } catch (Exception _) {
      // FRLogger.warn("There was a problem loading the resource bundle '" +
      // currentBaseName + "' of locale '" + currentLocale + "'");
      try {
        classMessages = ResourceBundle.getBundle(currentBaseName, Locale.forLanguageTag("en-US"));
      } catch (Exception _) {
        classMessages = null;
        // FRLogger.error("There was a problem loading the resource bundle '" +
        // currentBaseName + "' of locale 'en-US'",null);
      }
    }

    // Load the fallback English messages
    try {
      englishClassMessages = ResourceBundle.getBundle(currentBaseName, Locale.forLanguageTag("en"));
    } catch (Exception _) {
      // FRLogger.warn("There was a problem loading the resource bundle '" +
      // currentBaseName + "' of locale 'en'");
    }
  }

  public String getText(String key, String... args) {
    String text;
    if ((classMessages != null) && (classMessages.containsKey(key))) {
      text = classMessages.getString(key);
    } else if ((defaultMessages != null) && (defaultMessages.containsKey(key))) {
      text = defaultMessages.getString(key);
    } else if ((englishClassMessages != null) && (englishClassMessages.containsKey(key))) {
      text = englishClassMessages.getString(key);
    } else {
      return key;
    }

    // Pattern to match {{variable_name}} placeholders
    Pattern pattern = Pattern.compile("\\{\\{(.+?)\\}\\}");
    Matcher matcher = pattern.matcher(text);

    // Find and replace all matches
    int argIndex = 0;
    while (matcher.find()) {
      // Entire match including {{ and }}
      String placeholder = matcher.group(0);

      if (!placeholder.startsWith("{{icon:") && argIndex < args.length) {
        // replace the placeholder with the value
        text = text.replace(placeholder, args[argIndex]);
        argIndex++;
      }
    }

    return text;
  }

  private String insertIcons(JComponent component, String text) {
    // Pattern to match {{variable_name}} placeholders
    Pattern pattern = Pattern.compile("\\{\\{icon:(.+?)\\}\\}");
    Matcher matcher = pattern.matcher(text);

    // Find all matches
    while (matcher.find()) {
      // Entire match including {{ and }}
      String placeholder = matcher.group(0);

      // Get the icon name
      String iconName = matcher.group(1);

      try {

        // Get the unicode code point for the icon
        int codePoint = iconMap.get(iconName);

        // Convert the code point to a String
        text = text.replace(placeholder, new String(Character.toChars(codePoint)));

        Font originalFont = component.getFont();
        component.setFont(materialDesignIcons.deriveFont(Font.PLAIN, originalFont.getSize() * 1.5f));
      } catch (Exception e) {
        FRLogger.error("There was a problem setting the icon for the component", e);
      }
    }

    return text;
  }

  // Add methods to set text for different GUI components
  public void setText(JComponent component, String key, String... args) {
    String text = getText(key, args);
    String tooltip = getText(key + "_tooltip", args);

    if (tooltip == null || tooltip.isEmpty() || tooltip.equals(key + "_tooltip")) {
      tooltip = null;
    }

    text = insertIcons(component, text);

    // Set the text for the component
    if (component instanceof JButton button1) {
      // Set the text for the button
      button1.setText(text);
      if (tooltip != null && !tooltip.isEmpty()) {
        // Set the tooltip text for the component
        component.setToolTipText(tooltip);
      }
    } else if (component instanceof JToggleButton button) {
      // Set the text for the toggle button
      button.setText(text);
      if (tooltip != null && !tooltip.isEmpty()) {
        // Set the tooltip text for the component
        component.setToolTipText(tooltip);
      }
    } else if (component instanceof JLabel label) {
      // Set the text for the toggle button
      label.setText(text);
      if (tooltip != null && !tooltip.isEmpty()) {
        // Set the tooltip text for the component
        component.setToolTipText(tooltip);
      }
    } else {
      // Handle other components like JLabel, JTextArea, etc.
      String componentType = component
          .getClass()
          .getName();
      FRLogger.warn("The component type '" + componentType + "' is not supported");
    }

    // Handle other components like JLabel, JTextArea, etc.
  }

  public Locale getLocale() {
    return currentLocale;
  }

  public void setLocale(Locale locale) {
    this.currentLocale = locale;
    loadResourceBundle(currentBaseName);
  }

}