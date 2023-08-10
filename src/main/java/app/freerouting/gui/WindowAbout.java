package app.freerouting.gui;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Locale;
import java.util.ResourceBundle;

/** Displays general information about the freeroute software. */
public class WindowAbout extends BoardSavableSubWindow {
  public WindowAbout(Locale p_locale) {
    ResourceBundle resources =
        ResourceBundle.getBundle("app.freerouting.gui.WindowAbout", p_locale);
    this.setTitle(resources.getString("title"));

    final JPanel window_panel = new JPanel();
    this.add(window_panel);

    // Initialize gridbag layout.

    GridBagLayout gridbag = new GridBagLayout();
    window_panel.setLayout(gridbag);
    GridBagConstraints gridbag_constraints = new GridBagConstraints();
    gridbag_constraints.insets = new Insets(5, 10, 5, 10);
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;

    JLabel description_label =
        new JLabel(resources.getString("description"));
    gridbag.setConstraints(description_label, gridbag_constraints);
    window_panel.add(description_label, gridbag_constraints);

    String version_string =
        resources.getString("version") + " " + MainApplication.VERSION_NUMBER_STRING;
    JLabel version_label = new JLabel(version_string);
    gridbag.setConstraints(version_label, gridbag_constraints);
    window_panel.add(version_label, gridbag_constraints);

    JLabel warrenty_label = new JLabel(resources.getString("warranty"));
    gridbag.setConstraints(warrenty_label, gridbag_constraints);
    window_panel.add(warrenty_label, gridbag_constraints);

    JLabel homepage_label = new JLabel(resources.getString("homepage"));
    gridbag.setConstraints(homepage_label, gridbag_constraints);
    window_panel.add(homepage_label, gridbag_constraints);

    JLabel support_label = new JLabel(resources.getString("support"));
    gridbag.setConstraints(support_label, gridbag_constraints);
    window_panel.add(support_label, gridbag_constraints);

    this.add(window_panel);
    this.pack();
  }
}
