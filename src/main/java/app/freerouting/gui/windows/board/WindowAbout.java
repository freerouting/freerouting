package app.freerouting.gui.windows.board;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.gui.a11y.A11y;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.gui.board.BoardSavableSubWindow;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/**
 * A dialog window that displays general information about the Freerouting application, including
 * its description, version, warranty information, and community patrons showcase.
 */
public class WindowAbout extends BoardSavableSubWindow {

  private final JTabbedPane tabbedPane;

  /**
   * Creates and initializes a new "About" dialog window.
   *
   * @param locale the locale to determine the language of the displayed texts.
   * @param freeroutingVersion the version string of the Freerouting application.
   */
  public WindowAbout(Locale locale, String freeroutingVersion) {
    setLanguage(locale);
    this.setTitle(tm.getText("title"));

    this.tabbedPane =
        createComponentOnly(
            locale,
            freeroutingVersion,
            () -> {
              try {
                Desktop.getDesktop().browse(new URI("https://www.freerouting.app/donate.html"));
              } catch (Exception ex) {
                FRLogger.error("Failed to open patron link from About dialog", ex);
              }
            });

    this.add(this.tabbedPane);

    this.setResizable(false);
    this.setMinimumSize(new Dimension(560, 420));
    this.pack();
    clampWindowHeight(this, null);
  }

  /**
   * Creates the tabbed About and Community Patrons content without constructing a top-level window.
   *
   * <p>This component is deliberately independent of {@link WindowAbout}'s top-level frame. It is
   * suitable for forced-headless accessibility tests and embedded about panels.
   *
   * @param locale locale for translated text
   * @param freeroutingVersion application version string
   * @param patronActionListener optional listener invoked when the Become a Patron button is
   *     clicked
   * @return a tabbed pane containing About and Community Patrons tabs
   */
  public static JTabbedPane createComponentOnly(
      Locale locale, String freeroutingVersion, Runnable patronActionListener) {
    TextManager tm = new TextManager(WindowAbout.class, locale);
    JTabbedPane tabbedPane = new JTabbedPane();
    A11y.tag(tabbedPane, GuiLocators.WINDOW_ABOUT_TABBED_PANE);
    A11y.describe(tabbedPane, "About tabs", null);

    JPanel aboutPanel = createAboutPanel(tm, freeroutingVersion);
    JPanel patronsPanel = createPatronsPanel(tm, patronActionListener);

    tabbedPane.addTab(tm.getText("tab_about"), WindowBase.createScrollableContainer(aboutPanel));
    tabbedPane.addTab(
        tm.getText("tab_patrons"), WindowBase.createScrollableContainer(patronsPanel));

    return tabbedPane;
  }

  /** Selects the general About tab. */
  public void selectAboutTab() {
    if (this.tabbedPane != null && this.tabbedPane.getTabCount() > 0) {
      this.tabbedPane.setSelectedIndex(0);
    }
  }

  /** Selects the Community Patrons tab. */
  public void selectPatronsTab() {
    if (this.tabbedPane != null && this.tabbedPane.getTabCount() > 1) {
      this.tabbedPane.setSelectedIndex(1);
    }
  }

  /**
   * Returns the tabbed pane containing the dialog tabs.
   *
   * @return the tabbed pane
   */
  public JTabbedPane getTabbedPane() {
    return this.tabbedPane;
  }

  /**
   * Builds the general About information panel.
   *
   * @param tm TextManager for translations
   * @param freeroutingVersion application version string
   * @return panel containing version, warranty, and project links
   */
  private static JPanel createAboutPanel(TextManager tm, String freeroutingVersion) {
    final JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gridbagConstraints = new GridBagConstraints();
    gridbagConstraints.insets = new Insets(5, 10, 5, 10);
    gridbagConstraints.gridwidth = GridBagConstraints.REMAINDER;

    panel.add(new JLabel(tm.getText("description")), gridbagConstraints);

    String versionString = tm.getText("version") + " " + freeroutingVersion;
    panel.add(new JLabel(versionString), gridbagConstraints);

    panel.add(new JLabel(tm.getText("warranty")), gridbagConstraints);

    panel.add(createMixedTextPanel(tm.getText("homepage")), gridbagConstraints);
    panel.add(createMixedTextPanel(tm.getText("support")), gridbagConstraints);
    panel.add(createMixedTextPanel(tm.getText("donate")), gridbagConstraints);

    return panel;
  }

  /**
   * Builds the Community Patrons panel honoring backers and sponsors.
   *
   * @param tm TextManager for translations
   * @param patronActionListener listener invoked when the Become a Patron button is clicked
   * @return panel containing patron mission statement, tiers, and contribution button
   */
  private static JPanel createPatronsPanel(TextManager tm, Runnable patronActionListener) {
    final JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(6, 12, 6, 12);
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.WEST;

    JLabel headerLabel = new JLabel(tm.getText("patrons_header"));
    headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 14f));
    panel.add(headerLabel, gbc);

    panel.add(new JSeparator(), gbc);

    JTextArea descArea = new JTextArea(tm.getText("patrons_description"));
    descArea.setFont(UIManager.getFont("Label.font"));
    descArea.setLineWrap(true);
    descArea.setWrapStyleWord(true);
    descArea.setOpaque(false);
    descArea.setEditable(false);
    descArea.setFocusable(false);
    descArea.setColumns(38);
    panel.add(descArea, gbc);

    JLabel honorRollLabel = new JLabel(tm.getText("patrons_honor_roll"));
    honorRollLabel.setFont(honorRollLabel.getFont().deriveFont(Font.BOLD));
    panel.add(honorRollLabel, gbc);

    JLabel tier1Label = new JLabel(tm.getText("patrons_tier_sustaining"));
    tier1Label.setFont(tier1Label.getFont().deriveFont(Font.BOLD, 12f));
    tier1Label.setForeground(new Color(180, 100, 0));
    panel.add(tier1Label, gbc);

    JTextArea tier1Desc = new JTextArea(tm.getText("patrons_tier_sustaining_desc"));
    tier1Desc.setFont(UIManager.getFont("Label.font"));
    tier1Desc.setLineWrap(true);
    tier1Desc.setWrapStyleWord(true);
    tier1Desc.setOpaque(false);
    tier1Desc.setEditable(false);
    tier1Desc.setFocusable(false);
    tier1Desc.setColumns(38);
    panel.add(tier1Desc, gbc);

    JLabel tier2Label = new JLabel(tm.getText("patrons_tier_community"));
    tier2Label.setFont(tier2Label.getFont().deriveFont(Font.BOLD, 12f));
    tier2Label.setForeground(new Color(30, 110, 180));
    panel.add(tier2Label, gbc);

    JTextArea tier2Desc = new JTextArea(tm.getText("patrons_tier_community_desc"));
    tier2Desc.setFont(UIManager.getFont("Label.font"));
    tier2Desc.setLineWrap(true);
    tier2Desc.setWrapStyleWord(true);
    tier2Desc.setOpaque(false);
    tier2Desc.setEditable(false);
    tier2Desc.setFocusable(false);
    tier2Desc.setColumns(38);
    panel.add(tier2Desc, gbc);

    panel.add(new JSeparator(), gbc);

    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.CENTER;
    JButton patronBtn = new JButton(tm.getText("become_patron_button"));
    A11y.tag(patronBtn, GuiLocators.WINDOW_ABOUT_PATRON_BUTTON);
    A11y.describe(patronBtn, patronBtn.getText(), null);
    patronBtn.setFont(patronBtn.getFont().deriveFont(Font.BOLD, 13f));
    patronBtn.setPreferredSize(new Dimension(200, patronBtn.getPreferredSize().height + 4));
    patronBtn.addActionListener(
        _ -> {
          FRAnalytics.buttonClicked("window_about_patron_button", patronBtn.getText());
          if (patronActionListener != null) {
            patronActionListener.run();
          }
        });
    panel.add(patronBtn, gbc);

    return panel;
  }

  /**
   * Splits a string containing a URL into plain text and a clickable hyperlink component.
   *
   * @param fullText the complete text string containing the label and the URL.
   * @return a JPanel layout with the formatted text and hyperlink components.
   */
  private static JPanel createMixedTextPanel(String fullText) {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
    panel.setOpaque(false);

    if (fullText == null || fullText.isEmpty()) {
      return panel;
    }

    int splitIndex = fullText.indexOf("http");

    if (splitIndex > 0) {
      panel.add(new JLabel(fullText.substring(0, splitIndex)));
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
  private static JLabel createHyperlinkLabel(String url) {
    final String urlText = url.trim();
    JLabel label = new JLabel("<html><a href=''>" + urlText + "</a></html>");
    label.setToolTipText("Open " + urlText + " in your browser");

    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
      label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

      label.addMouseListener(
          new MouseAdapter() {
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
