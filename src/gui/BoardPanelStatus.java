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
 * BoardStatusPanel.java
 *
 * Created on 16. Februar 2005, 08:11
 */

package gui;

/**
 * Panel at the lower border of the board frame containing amongst others the message line
 * and the current layer and cursor position.
 *
 * @author Alfons Wirtz
 */
class BoardPanelStatus extends javax.swing.JPanel
{
    
    /** Creates a new instance of BoardStatusPanel */
    BoardPanelStatus(java.util.Locale p_locale)
    {
        java.util.ResourceBundle resources =
                java.util.ResourceBundle.getBundle("gui.resources.BoardPanelStatus", p_locale);
        this.setLayout(new java.awt.BorderLayout());
        this.setPreferredSize(new java.awt.Dimension(300, 20));
        
        javax.swing.JPanel left_message_panel = new javax.swing.JPanel();
        left_message_panel.setLayout(new java.awt.BorderLayout());
        
        status_message = new javax.swing.JLabel();
        status_message.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        status_message.setText(resources.getString("status_line"));
        left_message_panel.add(status_message, java.awt.BorderLayout.CENTER);
        
        add_message = new javax.swing.JLabel();
        add_message.setText(resources.getString("additional_text_field"));
        add_message.setMaximumSize(new java.awt.Dimension(300, 14));
        add_message.setMinimumSize(new java.awt.Dimension(140, 14));
        add_message.setPreferredSize(new java.awt.Dimension(180, 14));
        left_message_panel.add(add_message, java.awt.BorderLayout.EAST);
        
        this.add(left_message_panel, java.awt.BorderLayout.CENTER);
        
        javax.swing.JPanel right_message_panel = new javax.swing.JPanel();
        right_message_panel.setLayout(new java.awt.BorderLayout());
        
        right_message_panel.setMinimumSize(new java.awt.Dimension(200, 20));
        right_message_panel.setOpaque(false);
        right_message_panel.setPreferredSize(new java.awt.Dimension(450, 20));
        
        current_layer = new javax.swing.JLabel();
        current_layer.setText(resources.getString("current_layer"));
        right_message_panel.add(current_layer, java.awt.BorderLayout.CENTER);
        
        javax.swing.JPanel cursor_panel = new javax.swing.JPanel();
        cursor_panel.setLayout(new java.awt.BorderLayout());
        cursor_panel.setMinimumSize(new java.awt.Dimension(220, 14));
        cursor_panel.setPreferredSize(new java.awt.Dimension(220, 14));
        
        javax.swing.JLabel cursor = new javax.swing.JLabel();
        cursor.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        cursor.setText(resources.getString("cursor"));
        cursor.setMaximumSize(new java.awt.Dimension(100, 14));
        cursor.setMinimumSize(new java.awt.Dimension(50, 14));
        cursor.setPreferredSize(new java.awt.Dimension(50, 14));
        cursor_panel.add(cursor, java.awt.BorderLayout.WEST);
        
        mouse_position = new javax.swing.JLabel();
        mouse_position.setText("(0,0)");
        mouse_position.setMaximumSize(new java.awt.Dimension(170, 14));
        mouse_position.setPreferredSize(new java.awt.Dimension(170, 14));
        cursor_panel.add(mouse_position, java.awt.BorderLayout.EAST);
        
        right_message_panel.add(cursor_panel, java.awt.BorderLayout.EAST);
        
        this.add(right_message_panel, java.awt.BorderLayout.EAST);
    }
    
    final javax.swing.JLabel status_message;
    final javax.swing.JLabel add_message;
    final javax.swing.JLabel current_layer;
    final javax.swing.JLabel mouse_position;
}
