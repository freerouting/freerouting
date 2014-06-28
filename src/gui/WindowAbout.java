/*
 *  Copyright (C) 2014  Alfons Wirtz  
 *   website www.freerouting.net
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License at <http://www.gnu.org/licenses/> 
 *   for more details.
 *
 * WindowAbout.java
 *
 * Created on 7. Juli 2005, 07:24
 *
 */

package gui;


/**
 * Displays general information about the freeroute software.
 *
 * @author Alfons Wirtz
 */
public class WindowAbout extends BoardSavableSubWindow
{
    public WindowAbout(java.util.Locale p_locale)
    {
        java.util.ResourceBundle resources =
                java.util.ResourceBundle.getBundle("gui.resources.WindowAbout", p_locale);
        this.setTitle(resources.getString("title"));
        
        final javax.swing.JPanel window_panel = new javax.swing.JPanel();
        this.add(window_panel);
        
        // Initialize gridbag layout.
        
        java.awt.GridBagLayout gridbag = new java.awt.GridBagLayout();
        window_panel.setLayout(gridbag);
        java.awt.GridBagConstraints gridbag_constraints = new java.awt.GridBagConstraints();
        gridbag_constraints.insets = new java.awt.Insets(5, 10, 5, 10);
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        
        javax.swing.JLabel description_label = new javax.swing.JLabel(resources.getString("description"));
        gridbag.setConstraints(description_label, gridbag_constraints);
        window_panel.add(description_label, gridbag_constraints);
        
        String version_string = resources.getString("version") + " " + MainApplication.VERSION_NUMBER_STRING;
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
