package app.freerouting.gui;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

/**
 * Displays general information about the Freerouting software.
 */
public class WindowAbout extends BoardSavableSubWindow
{
  public WindowAbout(Locale p_locale)
  {
    setLanguage(p_locale);
    this.setTitle(tm.getText("title"));

    final JPanel window_panel = new JPanel();
    this.add(window_panel);

    // Initialize gridbag layout.
    GridBagLayout gridbag = new GridBagLayout();
    window_panel.setLayout(gridbag);
    GridBagConstraints gridbag_constraints = new GridBagConstraints();
    gridbag_constraints.insets = new Insets(5, 10, 5, 10);
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;

    JLabel description_label = new JLabel(tm.getText("description"));
    gridbag.setConstraints(description_label, gridbag_constraints);
    window_panel.add(description_label, gridbag_constraints);

    String version_string = tm.getText("version") + " " + MainApplication.VERSION_NUMBER_STRING;
    JLabel version_label = new JLabel(version_string);
    gridbag.setConstraints(version_label, gridbag_constraints);
    window_panel.add(version_label, gridbag_constraints);

    JLabel warrenty_label = new JLabel(tm.getText("warranty"));
    gridbag.setConstraints(warrenty_label, gridbag_constraints);
    window_panel.add(warrenty_label, gridbag_constraints);

    JLabel homepage_label = new JLabel(tm.getText("homepage"));
    gridbag.setConstraints(homepage_label, gridbag_constraints);
    window_panel.add(homepage_label, gridbag_constraints);

    JLabel support_label = new JLabel(tm.getText("support"));
    gridbag.setConstraints(support_label, gridbag_constraints);
    window_panel.add(support_label, gridbag_constraints);

    this.add(window_panel);
    this.pack();
  }
}