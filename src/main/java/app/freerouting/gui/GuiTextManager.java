package app.freerouting.gui;

import app.freerouting.logger.FRLogger;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.util.TextManager;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JToggleButton;

/** GUI-owned text manager for Swing text mutation and Material Design icons. */
public class GuiTextManager extends TextManager {

  private static final Map<String, Integer> ICON_MAP =
      Map.ofEntries(
          Map.entry("cog", 0xF0493),
          Map.entry("auto-fix", 0xF0068),
          Map.entry("cancel", 0xF073A),
          Map.entry("delete-sweep", 0xF05E9),
          Map.entry("undo", 0xF054C),
          Map.entry("redo", 0xF044E),
          Map.entry("spider-web", 0xF0BCA),
          Map.entry("order-bool-ascending-variant", 0xF098F),
          Map.entry("magnify-plus-cursor", 0xF0A63),
          Map.entry("magnify-minus", 0xF034A),
          Map.entry("alert", 0xF0026),
          Map.entry("close-octagon", 0xF015C),
          Map.entry("play", 0xF040A),
          Map.entry("pause", 0xF03E4),
          Map.entry("step-forward", 0xF04D7),
          Map.entry("step-backward", 0xF04D5),
          Map.entry("fast-forward", 0xF0211),
          Map.entry("rewind", 0xF045F));

  private Font materialDesignIcons;

  /**
   * Creates a GUI text manager and loads the Material Design icon font.
   *
   * @param baseClass the class whose resource bundle names the lookup hierarchy
   * @param locale the locale for message lookup
   */
  public GuiTextManager(Class baseClass, Locale locale) {
    super(baseClass, locale);
    try {
      materialDesignIcons =
          Font.createFont(
              Font.TRUETYPE_FONT,
              GlobalSettings.class.getResourceAsStream("/materialdesignicons-webfont.ttf"));
      GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(materialDesignIcons);
    } catch (IOException | FontFormatException e) {
      FRLogger.error("There was a problem loading the Material Design Icons font", e);
    }
  }

  private String insertIcons(JComponent component, String text) {
    Matcher matcher = Pattern.compile("\\{\\{icon:(.+?)\\}\\}").matcher(text);
    while (matcher.find()) {
      String placeholder = matcher.group(0);
      String iconName = matcher.group(1);
      try {
        int codePoint = ICON_MAP.get(iconName);
        text = text.replace(placeholder, new String(Character.toChars(codePoint)));
        Font originalFont = component.getFont();
        component.setFont(
            materialDesignIcons.deriveFont(Font.PLAIN, originalFont.getSize() * 1.5f));
      } catch (Exception e) {
        FRLogger.error("There was a problem setting the icon for the component", e);
      }
    }
    return text;
  }

  /**
   * Sets localized text (and optional tooltip/icons) on a Swing component.
   *
   * @param component the target component
   * @param key the resource bundle key
   * @param args optional placeholder values
   */
  public void setText(JComponent component, String key, String... args) {
    String text = insertIcons(component, getText(key, args));
    String tooltip = getText(key + "_tooltip", args);
    if (tooltip.isEmpty() || tooltip.equals(key + "_tooltip")) {
      tooltip = null;
    }

    switch (component) {
      case JButton button -> {
        button.setText(text);
        setTooltipIfPresent(component, tooltip);
      }
      case JToggleButton button -> {
        button.setText(text);
        setTooltipIfPresent(component, tooltip);
      }
      case JLabel label -> {
        label.setText(text);
        setTooltipIfPresent(component, tooltip);
      }
      case javax.swing.text.JTextComponent textComponent -> {
        textComponent.setText(text);
        setTooltipIfPresent(component, tooltip);
      }
      case null, default ->
          FRLogger.warn(
              "The component type '" + component.getClass().getName() + "' is not supported");
    }
  }

  private static void setTooltipIfPresent(JComponent component, String tooltip) {
    if (tooltip != null && !tooltip.isEmpty()) {
      component.setToolTipText(tooltip);
    }
  }
}
