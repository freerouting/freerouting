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
 * PopupMenuDisplay.java
 *
 * Created on 22. Mai 2005, 09:46
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package gui;

/**
 *
 * @author Alfons Wirtz
 */
public class PopupMenuDisplay extends javax.swing.JPopupMenu
{
    
    /** Creates a new instance of PopupMenuDisplay */
    public PopupMenuDisplay(BoardFrame p_board_frame)
    {
        this.board_panel = p_board_frame.board_panel;
        java.util.ResourceBundle resources = 
                java.util.ResourceBundle.getBundle("gui.resources.Default", p_board_frame.get_locale());
        javax.swing.JMenuItem center_display_item = new javax.swing.JMenuItem();
        center_display_item.setText(resources.getString("center_display"));
        center_display_item.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                board_panel.center_display(board_panel.right_button_click_location);
            }
        });
        
        this.add(center_display_item);
        
        javax.swing.JMenu zoom_menu = new javax.swing.JMenu();
        zoom_menu.setText(resources.getString("zoom"));
        
        javax.swing.JMenuItem zoom_in_item = new javax.swing.JMenuItem();
        zoom_in_item.setText(resources.getString("zoom_in"));
        zoom_in_item.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                board_panel.zoom_in(board_panel.right_button_click_location);
            }
        });
        
        zoom_menu.add(zoom_in_item);
        
        javax.swing.JMenuItem zoom_out_item = new javax.swing.JMenuItem();
        zoom_out_item.setText(resources.getString("zoom_out"));
        zoom_out_item.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                board_panel.zoom_out(board_panel.right_button_click_location);
            }
        });
        
        zoom_menu.add(zoom_out_item);
        
        this.add(zoom_menu);
    }
    
    protected final BoardPanel board_panel;
}
