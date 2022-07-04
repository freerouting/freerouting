package app.freerouting.gui;

/** Displays general information about the freeroute software. */
public class WindowAbout extends BoardSavableSubWindow {
  public WindowAbout(java.util.Locale p_locale) {
    java.util.ResourceBundle resources =
        java.util.ResourceBundle.getBundle("app.freerouting.gui.WindowAbout", p_locale);
    this.setTitle(resources.getString("title"));

    final javax.swing.JPanel window_panel = new javax.swing.JPanel();
    this.add(window_panel);

    // Initialize gridbag layout.

    java.awt.GridBagLayout gridbag = new java.awt.GridBagLayout();
    window_panel.setLayout(gridbag);
    java.awt.GridBagConstraints gridbag_constraints = new java.awt.GridBagConstraints();
    gridbag_constraints.insets = new java.awt.Insets(5, 10, 5, 10);
    gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;

    javax.swing.JLabel description_label =
        new javax.swing.JLabel(resources.getString("description"));
    gridbag.setConstraints(description_label, gridbag_constraints);
    window_panel.add(description_label, gridbag_constraints);

    String version_string =
        resources.getString("version") + " " + MainApplication.VERSION_NUMBER_STRING;
    javax.swing.JLabel version_label = new javax.swing.JLabel(version_string);
    gridbag.setConstraints(version_label, gridbag_constraints);
    window_panel.add(version_label, gridbag_constraints);

    javax.swing.JLabel warrenty_label = new javax.swing.JLabel(resources.getString("warranty"));
    gridbag.setConstraints(warrenty_label, gridbag_constraints);
    window_panel.add(warrenty_label, gridbag_constraints);

    javax.swing.JLabel homepage_label = new javax.swing.JLabel(resources.getString("homepage"));
    gridbag.setConstraints(homepage_label, gridbag_constraints);
    window_panel.add(homepage_label, gridbag_constraints);

    javax.swing.JLabel support_label = new javax.swing.JLabel(resources.getString("support"));
    gridbag.setConstraints(support_label, gridbag_constraints);
    window_panel.add(support_label, gridbag_constraints);

    this.add(window_panel);
    this.pack();
  }
}
