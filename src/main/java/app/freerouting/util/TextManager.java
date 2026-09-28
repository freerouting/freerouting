package app.freerouting.util;

import app.freerouting.logger.FRLogger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Headless-safe manager for the application's localized text resources and static utilities. */
public class TextManager {

  private static final Locale ENGLISH_LOCALE = Locale.forLanguageTag("en");
  private Locale currentLocale;
  private String currentBaseName;
  private ResourceBundle defaultMessages;
  private ResourceBundle englishDefaultMessages;
  private ResourceBundle classMessages;
  private ResourceBundle englishClassMessages;

  /**
   * Creates a text manager for the given base resource class and locale.
   *
   * @param baseClass the class whose resource bundle names the lookup hierarchy
   * @param locale the locale for message lookup
   */
  public TextManager(Class baseClass, Locale locale) {
    this.currentLocale = locale;
    loadResourceBundle(baseClass.getName());
  }

  /** Formats an instant using the default timestamp pattern. */
  public static String convertInstantToString(Instant instant) {
    return convertInstantToString(instant, "yyyyMMdd_HHmmss");
  }

  /**
   * Formats an instant using the given date-time pattern.
   *
   * @param instant the instant to format
   * @param format the {@link DateTimeFormatter} pattern
   * @return the formatted timestamp string
   */
  public static String convertInstantToString(Instant instant, String format) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
    LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    return localDateTime.format(formatter);
  }

  /**
   * Generates a random alphanumeric string of the requested length.
   *
   * @param length number of characters to generate
   * @return the random string
   */
  public static String generateRandomAlphanumericString(int length) {
    String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    StringBuilder randomString = new StringBuilder();
    for (int i = 0; i < length; i++) {
      int index = (int) (characters.length() * ThreadLocalRandom.current().nextDouble());
      randomString.append(characters.charAt(index));
    }
    return randomString.toString();
  }

  /**
   * Parses a human-readable timespan string into seconds.
   *
   * @param timespanString value in {@code HH:mm:ss}, {@code mm:ss}, or {@code ss} form
   * @return duration in seconds, or {@code null} if parsing fails
   */
  public static Long parseTimespanString(String timespanString) {
    if (timespanString == null || timespanString.isBlank()) {
      return null;
    }
    try {
      Duration duration = Duration.parse(convertFromTimespanToDurationFormat(timespanString));
      return duration.getSeconds();
    } catch (DateTimeParseException _) {
      return null;
    }
  }

  /**
   * Converts a colon-separated timespan into an ISO-8601 duration string.
   *
   * @param timespanString value in {@code HH:mm:ss}, {@code mm:ss}, or {@code ss} form
   * @return ISO-8601 duration text suitable for {@link Duration#parse(String)}
   */
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
      durationString.append(parts[0]).append("M").append(parts[1]).append("S");
    } else if (parts.length == 1) {
      durationString.append(parts[0]).append("S");
    }
    return durationString.toString();
  }

  /**
   * Shortens a string to a specified number of characters by replacing the middle part with dots.
   *
   * @param text the text to shorten
   * @param peakCharacterCount characters to keep at the beginning and end
   * @return the shortened text
   */
  public static String shortenString(String text, int peakCharacterCount) {
    if (text.length() > peakCharacterCount * 2) {
      return text.substring(0, peakCharacterCount)
          + "..."
          + text.substring(text.length() - peakCharacterCount);
    }
    return text;
  }

  /**
   * Removes quotes from the beginning and end of a string.
   *
   * @param text the text to remove quotes from
   * @return the text without quotes
   */
  public static String removeQuotes(String text) {
    if (text == null || text.length() < 2) {
      return text;
    }
    return text.startsWith("\"") && text.endsWith("\"")
        ? text.substring(1, text.length() - 1)
        : text;
  }

  /**
   * Decrypts a byte array using AES-256-CBC with a passphrase.
   *
   * @param encodedText the encrypted bytes
   * @param passphrase the passphrase to use for decryption
   * @return the decrypted bytes, or {@code null} on failure
   */
  public static byte[] decryptAes256Cbc(byte[] encodedText, String passphrase) {
    try {
      IvParameterSpec iv = new IvParameterSpec("freeroutingivpar".getBytes(StandardCharsets.UTF_8));
      SecretKeySpec keySpec = new SecretKeySpec(passphrase.getBytes(StandardCharsets.UTF_8), "AES");
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
      cipher.init(Cipher.DECRYPT_MODE, keySpec, iv);
      return cipher.doFinal(encodedText);
    } catch (Exception ex) {
      FRLogger.error("There was a problem decrypting the text", ex);
      return null;
    }
  }

  /**
   * Unescapes unicode characters in a string.
   *
   * @param text the text to unescape
   * @return the text with {@code \\uXXXX} sequences replaced by their characters
   */
  public static String unescapeUnicode(String text) {
    Matcher matcher = Pattern.compile("\\\\u(\\p{XDigit}{4})").matcher(text);
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
      matcher.appendReplacement(
          result, String.valueOf((char) Integer.parseInt(matcher.group(1), 16)));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  /** Formats a long value as a fixed-width uppercase hexadecimal string. */
  public static String longToHexadecimalString(Long longValue) {
    return "0x%016X".formatted(longValue);
  }

  /**
   * Parses a hexadecimal or decimal string into an unsigned long value.
   *
   * @param hexString value with optional {@code 0x} prefix
   * @return the parsed unsigned long
   */
  public static Long hexadecimalStringToLong(String hexString) {
    if (hexString.startsWith("0x") || hexString.startsWith("0X")) {
      return Long.parseUnsignedLong(hexString.substring(2), 16);
    }
    return Long.parseUnsignedLong(hexString, 10);
  }

  private void loadResourceBundle(String baseName) {
    currentBaseName = baseName;
    defaultMessages = loadBundle("app.freerouting.Common", currentLocale);
    englishDefaultMessages = loadBundle("app.freerouting.Common", ENGLISH_LOCALE);
    classMessages = loadBundle(currentBaseName, currentLocale);
    englishClassMessages = loadBundle(currentBaseName, ENGLISH_LOCALE);

    if (defaultMessages == null && !isEnglishLocale()) {
      FRLogger.warn(
          "There was a problem loading the resource bundle 'app.freerouting.Common' of locale '"
              + currentLocale
              + "'");
      defaultMessages = englishDefaultMessages;
    }
    if (classMessages == null && !isEnglishLocale()) {
      classMessages = englishClassMessages;
    }
  }

  private ResourceBundle loadBundle(String baseName, Locale locale) {
    try {
      return ResourceBundle.getBundle(baseName, locale);
    } catch (MissingResourceException _) {
      return null;
    }
  }

  private boolean isEnglishLocale() {
    return ENGLISH_LOCALE.equals(currentLocale)
        || ("en".equalsIgnoreCase(currentLocale.getLanguage())
            && currentLocale.getCountry().isEmpty());
  }

  private String getBundleString(ResourceBundle bundle, String key) {
    return bundle != null && bundle.containsKey(key) ? bundle.getString(key) : null;
  }

  /**
   * Looks up and formats a localized message for the given key.
   *
   * @param key the resource bundle key
   * @param args optional placeholder values
   * @return the localized text, or the key if no message is found
   */
  public String getText(String key, String... args) {
    String text = lookupMessage(key);
    if (text == null) {
      return key;
    }

    Matcher matcher = Pattern.compile("\\{\\{(.+?)\\}\\}").matcher(text);
    int argIndex = 0;
    while (matcher.find()) {
      String placeholder = matcher.group(0);
      if (!placeholder.startsWith("{{icon:") && argIndex < args.length) {
        text = text.replace(placeholder, args[argIndex++]);
      }
    }
    return text;
  }

  private String lookupMessage(String key) {
    String message = lookupMessageForKey(key);
    if (message != null) {
      return message;
    }
    String snakeCaseKey = toSnakeCase(key);
    return snakeCaseKey.equals(key) ? null : lookupMessageForKey(snakeCaseKey);
  }

  private String lookupMessageForKey(String key) {
    String message = getBundleString(classMessages, key);
    if (message != null) {
      return message;
    }
    if (!isEnglishLocale()) {
      message = getBundleString(englishClassMessages, key);
      if (message != null) {
        return message;
      }
    }
    message = lookupParentClassMessage(key, currentLocale);
    if (message != null) {
      return message;
    }
    if (!isEnglishLocale()) {
      message = lookupParentClassMessage(key, ENGLISH_LOCALE);
      if (message != null) {
        return message;
      }
    }
    message = getBundleString(defaultMessages, key);
    if (message != null) {
      return message;
    }
    return !isEnglishLocale() ? getBundleString(englishDefaultMessages, key) : null;
  }

  private static String toSnakeCase(String key) {
    return key.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
        .replaceAll("([a-z\\d])([A-Z])", "$1_$2")
        .toLowerCase(Locale.ROOT);
  }

  private String lookupParentClassMessage(String key, Locale locale) {
    try {
      Class<?> clazz = Class.forName(currentBaseName).getSuperclass();
      while (clazz != null && clazz.getName().startsWith("app.freerouting")) {
        String message = getBundleString(loadBundle(clazz.getName(), locale), key);
        if (message != null) {
          return message;
        }
        clazz = clazz.getSuperclass();
      }
    } catch (ClassNotFoundException _) {
      // currentBaseName is not a loadable class; skip parent lookup.
    }
    return null;
  }

  public Locale getLocale() {
    return currentLocale;
  }

  /** Switches the active locale and reloads the resource bundles. */
  public void setLocale(Locale locale) {
    currentLocale = locale;
    loadResourceBundle(currentBaseName);
  }
}
