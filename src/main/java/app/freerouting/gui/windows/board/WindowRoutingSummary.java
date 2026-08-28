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
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

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
    gbc.insets = new Insets(5, 12, 5, 12);
    gbc.fill = GridBagConstraints.HORIZONTAL;

    // Header label (centered horizontally, no emojis)
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    gbc.anchor = GridBagConstraints.CENTER;
    String headerText =
        summaryData.wasInterrupted()
            ? tm.getText("header_interrupted")
            : tm.getText("header_completed");
    JLabel headerLabel = new JLabel(headerText, SwingConstants.CENTER);
    headerLabel.setHorizontalAlignment(SwingConstants.CENTER);
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
            ? tm.getText("zero_incompletes", "0")
            : String.valueOf(summaryData.incompleteCount());
    addStatRow(panel, gbc, 3, tm.getText("connections_incomplete"), incompleteStr);

    // Clearance Violations (with max violation in brackets if > 0)
    String unitName =
        summaryData.displayUnit() != null ? summaryData.displayUnit().toString() : "mm";
    String violationsStr;
    if (summaryData.violationsCount() > 0) {
      violationsStr =
          String.format(
              Locale.US,
              "%d (max: %.2f %s)",
              summaryData.violationsCount(),
              summaryData.maxViolation(),
              unitName);
    } else {
      violationsStr = "0";
    }
    addStatRow(panel, gbc, 4, tm.getText("clearance_violations"), violationsStr);

    // Vias Added
    addStatRow(panel, gbc, 5, tm.getText("vias_added"), String.valueOf(summaryData.viaCount()));

    // Total Trace Length (lowercase unit)
    String lengthStr =
        String.format(Locale.US, "%,.2f %s", summaryData.totalTraceLength(), unitName);
    addStatRow(panel, gbc, 6, tm.getText("trace_length"), lengthStr);

    // Score (with 1000 = perfect explanation)
    String scoreStr = String.format(Locale.US, "%.2f / 1000 (1000 = perfect)", summaryData.score());
    addStatRow(panel, gbc, 7, tm.getText("score"), scoreStr);

    // Execution Time
    String timeStr = String.format(Locale.US, "%.2f s", summaryData.durationSeconds());
    addStatRow(panel, gbc, 8, tm.getText("execution_time"), timeStr);

    // Separator
    gbc.gridx = 0;
    gbc.gridy = 9;
    gbc.gridwidth = 2;
    panel.add(new JSeparator(), gbc);

    // Thank you header
    gbc.gridy = 10;
    gbc.anchor = GridBagConstraints.WEST;
    JLabel thankYouLabel = new JLabel(tm.getText("thank_you_title"));
    thankYouLabel.setFont(thankYouLabel.getFont().deriveFont(Font.BOLD, 13f));
    panel.add(thankYouLabel, gbc);

    // Word-wrapped sponsor message area (3 lines high, wraps nicely for long languages)
    gbc.gridy = 11;
    JTextArea sponsorMsgArea = new JTextArea(tm.getText("sponsor_message"));
    sponsorMsgArea.setFont(UIManager.getFont("Label.font"));
    sponsorMsgArea.setLineWrap(true);
    sponsorMsgArea.setWrapStyleWord(true);
    sponsorMsgArea.setOpaque(false);
    sponsorMsgArea.setEditable(false);
    sponsorMsgArea.setFocusable(false);
    sponsorMsgArea.setRows(3);
    sponsorMsgArea.setColumns(28);
    sponsorMsgArea.setPreferredSize(new Dimension(380, 52));
    panel.add(sponsorMsgArea, gbc);

    // Sponsor Button (no emojis)
    gbc.gridy = 12;
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.CENTER;
    JButton sponsorButton = new JButton(tm.getText("sponsor_button"));
    A11y.tag(sponsorButton, GuiLocators.ROUTING_SUMMARY_DONATE_BUTTON);
    A11y.describe(sponsorButton, sponsorButton.getText(), null);
    sponsorButton.setFont(sponsorButton.getFont().deriveFont(Font.BOLD, 13f));
    sponsorButton.setForeground(new Color(200, 16, 46));
    sponsorButton.setPreferredSize(new Dimension(200, sponsorButton.getPreferredSize().height + 4));
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
    gbc.gridy = 13;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(new JSeparator(), gbc);

    // Bottom Controls (Checkbox + Close Button)
    gbc.gridy = 14;
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
