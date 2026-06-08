package app.freerouting.gui;

import app.freerouting.logger.FRLogger;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.Locale;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * A dialog window that displays general information about the Freerouting application,
 * including its description, version, warranty information, and hyperlinks to its homepage and issue tracker.
 */
public class WindowAbout extends BoardSavableSubWindow {

  /**
   * Creates and initializes a new "About" dialog window.
   *
   * @param p_locale the locale to determine the language of the displayed texts.
   * @param freerouting_version the version string of the Freerouting application.
   */
  public WindowAbout(Locale p_locale, String freerouting_version) {
    setLanguage(p_locale);
    this.setTitle(tm.getText("title"));

    // Initialize panel and layout in one step
    final JPanel window_panel = new JPanel(new GridBagLayout());
    this.add(window_panel);

    GridBagConstraints gridbag_constraints = new GridBagConstraints();
    gridbag_constraints.insets = new Insets(5, 10, 5, 10);
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;

    // Inlining the JLabels reduces variable clutter. 
    // Passing constraints directly into add() handles the GridBag setup automatically.
    window_panel.add(new JLabel(tm.getText("description")), gridbag_constraints);

    String version_string = tm.getText("version") + " " + freerouting_version;
    window_panel.add(new JLabel(version_string), gridbag_constraints);

    window_panel.add(new JLabel(tm.getText("warranty")), gridbag_constraints);

    // Dynamically split and add the homepage/support panels
    window_panel.add(createMixedTextPanel(tm.getText("homepage")), gridbag_constraints);
    window_panel.add(createMixedTextPanel(tm.getText("support")), gridbag_constraints);

    this.setResizable(false);
    this.setMinimumSize(new Dimension(450, 220));
    this.pack();
  }

  /**
   * Splits a string containing a URL into plain text and a clickable hyperlink component.
   *
   * @param fullText the complete text string containing the label and the URL.
   * @return a JPanel layout with the formatted text and hyperlink components.
   */
  private JPanel createMixedTextPanel(String fullText) {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
    panel.setOpaque(false); 

    // Null safety check
    if (fullText == null || fullText.isEmpty()) {
      return panel;
    }

    int splitIndex = fullText.indexOf("http");

    if (splitIndex > 0) {
      panel.add(new JLabel(fullText.substring(0, splitIndex)));
      // Single parameter since display and URL are identical
      panel.add(createHyperlinkLabel(fullText.substring(splitIndex)));
    } else if (splitIndex == 0) {
      panel.add(createHyperlinkLabel(fullText));
    } else {
      panel.add(new JLabel(fullText));
    }

    return panel;
  }

  /**
   * Creates a clickable JLabel component that behaves as a hyperlink to the specified URL.
   *
   * @param url the destination URL.
   * @return a JLabel styled as a hyperlink.
   */
  private JLabel createHyperlinkLabel(String url) {
    final String urlText = url.trim();
    JLabel label = new JLabel("<html><a href=''>" + urlText + "</a></html>");
    label.setToolTipText("Open " + urlText + " in your browser"); 
    
    // Only format as a clickable link if the OS supports it
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
      label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      
      label.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          try {
            Desktop.getDesktop().browse(new URI(urlText));
          } catch (Exception ex) {
            FRLogger.error("Could not open link: " + urlText, ex);
          }
        }
      });
    }
    
    return label;
  }
}