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
 * BoardWindowsMenu.java
 *
 * Created on 12. Februar 2005, 06:08
 */

package gui;

/**
 * Creates the parameter menu of a board frame.
 *
 * @author Alfons Wirtz
 */
public class BoardMenuParameter extends javax.swing.JMenu
{
    /** Returns a new windows menu for the board frame. */
    public static BoardMenuParameter get_instance(BoardFrame p_board_frame)
    {
        final BoardMenuParameter parameter_menu = new BoardMenuParameter(p_board_frame);
        
        parameter_menu.setText(parameter_menu.resources.getString("parameter"));
        
        javax.swing.JMenuItem selectwindow = new javax.swing.JMenuItem();
        selectwindow.setText(parameter_menu.resources.getString("select"));
        selectwindow.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                parameter_menu.board_frame.select_parameter_window.setVisible(true);
            }
        });
        
        parameter_menu.add(selectwindow);
        
        javax.swing.JMenuItem routewindow = new javax.swing.JMenuItem();
        routewindow.setText(parameter_menu.resources.getString("route"));
        routewindow.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                parameter_menu.board_frame.route_parameter_window.setVisible(true);
            }
        });
        
        parameter_menu.add(routewindow);
        
        javax.swing.JMenuItem autoroutewindow = new javax.swing.JMenuItem();
        autoroutewindow.setText(parameter_menu.resources.getString("autoroute"));
        autoroutewindow.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                parameter_menu.board_frame.autoroute_parameter_window.setVisible(true);
            }
        });
        
        parameter_menu.add(autoroutewindow);
        
        javax.swing.JMenuItem movewindow = new javax.swing.JMenuItem();
        movewindow.setText(parameter_menu.resources.getString("move"));
        movewindow.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                parameter_menu.board_frame.move_parameter_window.setVisible(true);
            }
        });
        
        parameter_menu.add(movewindow);
        
        return parameter_menu;
    }
    
    /** Creates a new instance of BoardSelectMenu */
    private BoardMenuParameter(BoardFrame p_board_frame)
    {
        board_frame = p_board_frame;
        resources = java.util.ResourceBundle.getBundle("gui.resources.BoardMenuParameter", p_board_frame.get_locale());
    }
    
    private final BoardFrame board_frame;
    private final java.util.ResourceBundle resources;
}
