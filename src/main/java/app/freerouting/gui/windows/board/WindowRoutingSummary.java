package app.freerouting.gui.windows.board;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.gui.a11y.A11y;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.gui.board.BoardFrame;
import app.freerouting.gui.workspace.progress.RoutingSummaryData;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.util.TextManager;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;

/**
 * Modal dialog displayed after autorouting completes, showing execution metrics and a donation
 * call-to-action.
 */
public final class WindowRoutingSummary {

  private WindowRoutingSummary() {}

  /**
   * Displays the routing summary and donation dialog.
   *
   * @param boardFrame parent board frame
   * @param summaryData captured routing statistics
   * @param globalSettings current application settings
   */
  public static void show(
      BoardFrame boardFrame, RoutingSummaryData summaryData, GlobalSettings globalSettings) {
    if (boardFrame == null || summaryData == null || globalSettings == null) {
      return;
    }

    Locale locale = boardFrame.getLocale();
    TextManager tm = new TextManager(WindowRoutingSummary.class, locale);

    JDialog dialog = new JDialog(boardFrame, tm.getText("title"), true);
    JPanel panel = createPanel(summaryData, globalSettings, locale, dialog::dispose);
    dialog.add(panel);
    dialog.pack();
    dialog.setResizable(false);
    dialog.setLocationRelativeTo(boardFrame);
    dialog.setVisible(true);
  }

  /**
   * Builds the routing summary content panel without constructing a top-level dialog.
   *
   * @param summaryData captured routing statistics
   * @param globalSettings current application settings
   * @param locale display locale
   * @param closeAction action to invoke when the close button is clicked
   * @return reusable JPanel component
   */
  public static JPanel createPanel(
      RoutingSummaryData summaryData,
      GlobalSettings globalSettings,
      Locale locale,
      Runnable closeAction) {
    TextManager tm = new TextManager(WindowRoutingSummary.class, locale);
    JPanel panel = new JPanel(new GridBagLayout());
    A11y.tag(panel, GuiLocators.ROUTING_SUMMARY_DIALOG);
    A11y.describe(panel, tm.getText("title"), null);

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(6, 12, 6, 12);
    gbc.fill = GridBagConstraints.HORIZONTAL;

    // Header label
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    String headerText =
        summaryData.wasInterrupted()
            ? tm.getText("header_interrupted")
            : tm.getText("header_completed");
    JLabel headerLabel = new JLabel("🎉 " + headerText);
    headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 15f));
    panel.add(headerLabel, gbc);

    // Separator
    gbc.gridy = 1;
    panel.add(new JSeparator(), gbc);

    // Statistics Rows
    gbc.gridwidth = 1;

    // Total Nets
    addStatRow(panel, gbc, 2, tm.getText("nets_total"), String.valueOf(summaryData.totalNets()));

    // Unrouted Connections
    String incompleteStr =
        summaryData.incompleteCount() == 0
            ? "0 (100% Routed)"
            : String.valueOf(summaryData.incompleteCount());
    addStatRow(panel, gbc, 3, tm.getText("connections_incomplete"), incompleteStr);

    // Clearance Violations
    addStatRow(
        panel,
        gbc,
        4,
        tm.getText("clearance_violations"),
        String.valueOf(summaryData.violationsCount()));

    // Vias Added
    addStatRow(panel, gbc, 5, tm.getText("vias_added"), String.valueOf(summaryData.viaCount()));

    // Total Trace Length
    String unitName = summaryData.displayUnit() != null ? summaryData.displayUnit().name() : "mm";
    String lengthStr =
        String.format(Locale.US, "%.2f %s", summaryData.totalTraceLength(), unitName);
    addStatRow(panel, gbc, 6, tm.getText("trace_length"), lengthStr);

    // Execution Time
    String timeStr = String.format(Locale.US, "%.2f s", summaryData.durationSeconds());
    addStatRow(panel, gbc, 7, tm.getText("execution_time"), timeStr);

    // Separator
    gbc.gridx = 0;
    gbc.gridy = 8;
    gbc.gridwidth = 2;
    panel.add(new JSeparator(), gbc);

    // Sponsor Message
    gbc.gridy = 9;
    JLabel sponsorMsgLabel = new JLabel(tm.getText("sponsor_message"));
    panel.add(sponsorMsgLabel, gbc);

    // Sponsor Button
    gbc.gridy = 10;
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.CENTER;
    JButton sponsorButton = new JButton("💖  " + tm.getText("sponsor_button") + "  💖");
    A11y.tag(sponsorButton, GuiLocators.ROUTING_SUMMARY_DONATE_BUTTON);
    A11y.describe(sponsorButton, sponsorButton.getText(), null);
    sponsorButton.setFont(sponsorButton.getFont().deriveFont(Font.BOLD, 14f));
    sponsorButton.setForeground(new Color(200, 16, 46));
    sponsorButton.setPreferredSize(new Dimension(260, sponsorButton.getPreferredSize().height + 6));
    sponsorButton.addActionListener(
        _ -> {
          FRAnalytics.buttonClicked("routing_summary_sponsor_button", sponsorButton.getText());
          try {
            Desktop.getDesktop().browse(new URI("https://www.freerouting.app/donate.html"));
          } catch (Exception ex) {
            FRLogger.error("Failed to open sponsor link from routing summary", ex);
          }
        });
    panel.add(sponsorButton, gbc);

    // Separator
    gbc.gridy = 11;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(new JSeparator(), gbc);

    // Bottom Controls (Checkbox + Close Button)
    gbc.gridy = 12;
    gbc.gridwidth = 1;
    gbc.anchor = GridBagConstraints.WEST;
    JCheckBox showSummaryCheckbox = new JCheckBox(tm.getText("checkbox_show_summary"));
    A11y.tag(showSummaryCheckbox, GuiLocators.ROUTING_SUMMARY_SHOW_CHECKBOX);
    A11y.describe(showSummaryCheckbox, showSummaryCheckbox.getText(), null);
    showSummaryCheckbox.setSelected(
        globalSettings.guiSettings.showRoutingSummary != null
            && globalSettings.guiSettings.showRoutingSummary);
    panel.add(showSummaryCheckbox, gbc);

    gbc.gridx = 1;
    gbc.anchor = GridBagConstraints.EAST;
    JButton closeButton = new JButton(tm.getText("close_button"));
    A11y.tag(closeButton, GuiLocators.ROUTING_SUMMARY_CLOSE_BUTTON);
    A11y.describe(closeButton, closeButton.getText(), null);
    closeButton.addActionListener(
        _ -> {
          boolean selected = showSummaryCheckbox.isSelected();
          if (globalSettings.guiSettings.showRoutingSummary != selected) {
            globalSettings.guiSettings.showRoutingSummary = selected;
            try {
              GlobalSettings.saveAsJson(globalSettings);
            } catch (IOException e) {
              FRLogger.warn("Failed to save showRoutingSummary setting: " + e.getMessage());
            }
          }
          if (closeAction != null) {
            closeAction.run();
          }
        });
    panel.add(closeButton, gbc);

    return panel;
  }

  private static void addStatRow(
      JPanel panel, GridBagConstraints gbc, int row, String labelText, String valueText) {
    gbc.gridy = row;
    gbc.gridx = 0;
    gbc.weightx = 0.0;
    gbc.anchor = GridBagConstraints.WEST;
    JLabel label = new JLabel(labelText);
    label.setFont(label.getFont().deriveFont(Font.BOLD));
    panel.add(label, gbc);

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.anchor = GridBagConstraints.EAST;
    JLabel val = new JLabel(valueText);
    panel.add(val, gbc);
  }
}
