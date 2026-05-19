package app.freerouting.gui;

import java.awt.Cursor;
import java.awt.Desktop;
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
 * Displays general information about the Freerouting software.
 */
public class WindowAbout extends BoardSavableSubWindow {

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

    this.pack();
  }

  /**
   * Splits strings like "Latest release: https://..." into plain text and a clickable link.
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
   * Helper method to create a clickable hyperlink JLabel.
   */
  private JLabel createHyperlinkLabel(final String urlText) {
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
            System.err.println("Could not open link: " + ex.getMessage());
          }
        }
      });
    }
    
    return label;
  }
}