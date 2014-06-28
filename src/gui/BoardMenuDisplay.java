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
 * BoardDisplayMenu.java
 *
 * Created on 12. Februar 2005, 05:42
 */

package gui;

/**
 * Creates the display menu of a board frame.
 *
 * @author Alfons Wirtz
 */
public class BoardMenuDisplay extends javax.swing.JMenu
{
    /** Returns a new display menu for the board frame. */
    public static BoardMenuDisplay get_instance(BoardFrame p_board_frame)
    {
        final BoardMenuDisplay display_menu = new BoardMenuDisplay(p_board_frame);
        display_menu.setText(display_menu.resources.getString("display"));
        
        javax.swing.JMenuItem itemvisibility = new javax.swing.JMenuItem();
        itemvisibility.setText(display_menu.resources.getString("object_visibility"));
        itemvisibility.setToolTipText(display_menu.resources.getString("object_visibility_tooltip"));
        itemvisibility.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                display_menu.board_frame.object_visibility_window.setVisible(true);
            }
        });
        
        display_menu.add(itemvisibility);
        
        javax.swing.JMenuItem layervisibility = new javax.swing.JMenuItem();
        layervisibility.setText(display_menu.resources.getString("layer_visibility"));
        layervisibility.setToolTipText(display_menu.resources.getString("layer_visibility_tooltip"));
        layervisibility.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                display_menu.board_frame.layer_visibility_window.setVisible(true);
            }
        });
        
        display_menu.add(layervisibility);
        
        javax.swing.JMenuItem colors = new javax.swing.JMenuItem();
        colors.setText(display_menu.resources.getString("colors"));
        colors.setToolTipText(display_menu.resources.getString("colors_tooltip"));
        colors.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                display_menu.board_frame.color_manager.setVisible(true);
            }
        });
        
        display_menu.add(colors);
        
        javax.swing.JMenuItem miscellanious = new javax.swing.JMenuItem();
        miscellanious.setText(display_menu.resources.getString("miscellaneous"));
        miscellanious.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                display_menu.board_frame.display_misc_window.setVisible(true);
            }
        });
        
        display_menu.add(miscellanious);
        
        return display_menu;
    }
    
    /** Creates a new instance of BoardDisplayMenu */
    private BoardMenuDisplay(BoardFrame p_board_frame)
    {
        board_frame = p_board_frame;
        resources = java.util.ResourceBundle.getBundle("gui.resources.BoardMenuDisplay", p_board_frame.get_locale());
    }
    
    private final BoardFrame board_frame;
    private final java.util.ResourceBundle resources;
}
