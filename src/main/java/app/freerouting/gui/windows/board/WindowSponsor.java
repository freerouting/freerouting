package app.freerouting.gui.windows.board;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.gui.a11y.A11y;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.gui.board.BoardFrame;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.awt.Color;
import java.awt.Component;
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
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Dialog window displaying project sponsorship information, donation actions, and the dynamic honor
 * roll of community donors and sponsors.
 */
public final class WindowSponsor {

  private final JDialog sponsorDialog;

  private WindowSponsor(BoardFrame boardFrame) {
    TextManager tm = new TextManager(WindowSponsor.class, boardFrame.getLocale());
    this.sponsorDialog = new JDialog(boardFrame, tm.getText("title"), false);

    JComponent content =
        createComponentOnly(
            boardFrame.getLocale(),
            new DonorService(),
            () -> {
              try {
                Desktop.getDesktop().browse(new URI("https://www.freerouting.app/donate.html"));
              } catch (Exception ex) {
                FRLogger.error("Failed to open sponsor website link", ex);
              }
            });

    this.sponsorDialog.add(content);
    this.sponsorDialog.setResizable(true);
    this.sponsorDialog.setMinimumSize(new Dimension(480, 320));
    this.sponsorDialog.pack();
    this.sponsorDialog.setLocationRelativeTo(boardFrame);
    WindowBase.clampWindowHeight(this.sponsorDialog, boardFrame);
    this.sponsorDialog.setVisible(true);
  }

  /**
   * Displays the sponsor and donors dialog window.
   *
   * @param boardFrame the parent board frame
   * @return the created WindowSponsor instance
   */
  public static WindowSponsor show(BoardFrame boardFrame) {
    return new WindowSponsor(boardFrame);
  }

  /**
   * Creates the sponsorship and donors component without constructing a top-level window.
   *
   * <p>This component is independent of display frames and suitable for forced-headless testing and
   * embedded panels.
   *
   * @param locale active locale for localization
   * @param donorService service for fetching curated donor data
   * @param sponsorActionListener action invoked when the Donate button is clicked
   * @return a scrollable container with the complete sponsorship view
   */
  public static JComponent createComponentOnly(
      Locale locale, DonorService donorService, Runnable sponsorActionListener) {
    TextManager tm = new TextManager(WindowSponsor.class, locale);
    JPanel panel = new JPanel(new GridBagLayout());
    A11y.tag(panel, GuiLocators.WINDOW_SPONSOR);
    A11y.describe(panel, tm.getText("title"), null);

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(6, 14, 6, 14);
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.weightx = 1.0;

    // Header Title
    JLabel titleLabel = new JLabel(tm.getText("title"));
    titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
    panel.add(titleLabel, gbc);

    // Mission / Description message
    JTextArea descArea = new JTextArea(tm.getText("description"));
    descArea.setFont(UIManager.getFont("Label.font"));
    descArea.setLineWrap(true);
    descArea.setWrapStyleWord(true);
    descArea.setOpaque(false);
    descArea.setEditable(false);
    descArea.setFocusable(false);
    descArea.setColumns(34);
    panel.add(descArea, gbc);

    // Sponsor / Donate CTA Button
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.CENTER;
    gbc.insets = new Insets(10, 14, 10, 14);
    JButton sponsorBtn = new JButton(tm.getText("become_sponsor_button"));
    A11y.tag(sponsorBtn, GuiLocators.WINDOW_SPONSOR_DONATE_BUTTON);
    A11y.describe(sponsorBtn, sponsorBtn.getText(), null);
    sponsorBtn.setFont(sponsorBtn.getFont().deriveFont(Font.BOLD, 13f));
    sponsorBtn.setForeground(new Color(200, 16, 46));
    sponsorBtn.setPreferredSize(new Dimension(220, sponsorBtn.getPreferredSize().height + 4));
    sponsorBtn.addActionListener(
        _ -> {
          FRAnalytics.buttonClicked("window_sponsor_donate_button", sponsorBtn.getText());
          if (sponsorActionListener != null) {
            sponsorActionListener.run();
          }
        });
    panel.add(sponsorBtn, gbc);

    // Separator
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = new Insets(6, 14, 8, 14);
    panel.add(new JSeparator(), gbc);

    // Donors Honor Roll Header
    JLabel donorsHeader = new JLabel(tm.getText("header_donors"));
    donorsHeader.setFont(donorsHeader.getFont().deriveFont(Font.BOLD, 13f));
    panel.add(donorsHeader, gbc);

    // Dynamic Donors Container
    JPanel donorsContainer = new JPanel();
    donorsContainer.setLayout(new BoxLayout(donorsContainer, BoxLayout.Y_AXIS));
    donorsContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
    A11y.tag(donorsContainer, GuiLocators.WINDOW_SPONSOR_DONORS_LIST);
    A11y.describe(donorsContainer, tm.getText("header_donors"), null);

    JLabel loadingLabel = new JLabel(tm.getText("loading_donors"));
    loadingLabel.setFont(loadingLabel.getFont().deriveFont(Font.ITALIC));
    loadingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    donorsContainer.add(loadingLabel);
    panel.add(donorsContainer, gbc);

    // Vertical glue spacer to push all content firmly to the top
    GridBagConstraints glueGbc = new GridBagConstraints();
    glueGbc.gridwidth = GridBagConstraints.REMAINDER;
    glueGbc.weightx = 1.0;
    glueGbc.weighty = 1.0;
    glueGbc.fill = GridBagConstraints.BOTH;
    panel.add(Box.createGlue(), glueGbc);

    // Fetch and populate donors asynchronously
    if (donorService != null) {
      donorService
          .fetchDonorsAsync()
          .thenAccept(
              payload ->
                  SwingUtilities.invokeLater(() -> populateDonors(donorsContainer, payload, tm)));
    }

    return WindowBase.createScrollableContainer(panel);
  }

  private static void populateDonors(
      JPanel container, DonorService.DonorPayload payload, TextManager tm) {
    container.removeAll();

    if (payload == null || payload.tiers() == null || payload.tiers().isEmpty()) {
      JLabel emptyLabel = new JLabel(tm.getText("empty_tier"));
      emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC));
      emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
      container.add(emptyLabel);
      container.revalidate();
      container.repaint();
      return;
    }

    for (DonorService.DonorTier tier : payload.tiers()) {
      if (tier.donors() == null || tier.donors().isEmpty()) {
        continue;
      }

      JLabel tierTitle = new JLabel(tier.name());
      tierTitle.setFont(tierTitle.getFont().deriveFont(Font.BOLD, 12f));
      tierTitle.setForeground(new Color(180, 100, 0));
      tierTitle.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
      tierTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
      container.add(tierTitle);

      JPanel tierListPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
      tierListPanel.setOpaque(false);
      tierListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

      for (DonorService.Donor donor : tier.donors()) {
        if (donor.name() == null || donor.name().isBlank()) {
          continue;
        }

        if (donor.url() != null && !donor.url().isBlank()) {
          JLabel linkLabel = new JLabel("<html><u>" + escapeHtml(donor.name()) + "</u></html>");
          linkLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          linkLabel.setToolTipText(donor.url());
          linkLabel.setForeground(new Color(0, 102, 204));
          linkLabel.addMouseListener(
              new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                  try {
                    Desktop.getDesktop().browse(new URI(donor.url()));
                  } catch (Exception ex) {
                    FRLogger.error("Failed to open donor link: " + donor.url(), ex);
                  }
                }
              });
          tierListPanel.add(linkLabel);
        } else {
          JLabel nameLabel = new JLabel(donor.name());
          nameLabel.setBorder(
              BorderFactory.createCompoundBorder(
                  BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true),
                  BorderFactory.createEmptyBorder(2, 6, 2, 6)));
          tierListPanel.add(nameLabel);
        }
      }

      container.add(tierListPanel);
      container.add(Box.createVerticalStrut(6));
    }

    if (container.getComponentCount() == 0) {
      JLabel emptyLabel = new JLabel(tm.getText("empty_tier"));
      emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC));
      emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
      container.add(emptyLabel);
    }

    container.revalidate();
    container.repaint();
  }

  private static String escapeHtml(String input) {
    if (input == null) {
      return "";
    }
    return input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  /** Gets the underlying dialog instance. */
  public JDialog getDialog() {
    return sponsorDialog;
  }
}
