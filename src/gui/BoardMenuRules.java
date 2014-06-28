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
 * BoardRulesMenu.java
 *
 * Created on 20. Februar 2005, 06:00
 */

package gui;

/**
 * Creates the rules menu of a board frame.
 *
 * @author Alfons Wirtz
 */
public class BoardMenuRules extends javax.swing.JMenu
{
    
    /** Returns a new windows menu for the board frame. */
    public static BoardMenuRules get_instance(BoardFrame p_board_frame)
    {
        final BoardMenuRules rules_menu = new BoardMenuRules(p_board_frame);
        
        rules_menu.setText(rules_menu.resources.getString("rules"));
        
        javax.swing.JMenuItem clearance_window = new javax.swing.JMenuItem();
        clearance_window.setText(rules_menu.resources.getString("clearance_matrix"));
        clearance_window.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                rules_menu.board_frame.clearance_matrix_window.setVisible(true);
            }
        });
        rules_menu.add(clearance_window);
        
        javax.swing.JMenuItem via_window = new javax.swing.JMenuItem();
        via_window.setText(rules_menu.resources.getString("vias"));
        via_window.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                rules_menu.board_frame.via_window.setVisible(true);
            }
        });
        rules_menu.add(via_window);
        
        javax.swing.JMenuItem nets_window = new javax.swing.JMenuItem();
        nets_window.setText(rules_menu.resources.getString("nets"));
        nets_window.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                rules_menu.board_frame.net_info_window.setVisible(true);
            }
        });
        
        rules_menu.add(nets_window);
        
        javax.swing.JMenuItem net_class_window = new javax.swing.JMenuItem();
        net_class_window.setText(rules_menu.resources.getString("net_classes"));
        net_class_window.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                rules_menu.board_frame.edit_net_rules_window.setVisible(true);
            }
        });
        rules_menu.add(net_class_window);
        
        return rules_menu;
    }
    
    /** Creates a new instance of BoardRulesMenu */
    private BoardMenuRules(BoardFrame p_board_frame)
    {
        board_frame = p_board_frame;
        resources = java.util.ResourceBundle.getBundle("gui.resources.BoardMenuRules", p_board_frame.get_locale());
    }
    
    
    private final BoardFrame board_frame;
    private final java.util.ResourceBundle resources;
}
