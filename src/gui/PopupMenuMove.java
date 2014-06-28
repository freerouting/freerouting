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
 * PopupMenuMove.java
 *
 * Created on 15. Mai 2005, 11:21
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
public class PopupMenuMove extends PopupMenuDisplay
{
    
    /** Creates a new instance of PopupMenuMove */
    public PopupMenuMove(BoardFrame p_board_frame)
    {
        super(p_board_frame);
        java.util.ResourceBundle resources = 
                java.util.ResourceBundle.getBundle("gui.resources.PopupMenuMove", p_board_frame.get_locale());
        
        // Add menu for turning the items by a multiple of 90 degree
        
        javax.swing.JMenuItem rotate_menu = new javax.swing.JMenu();
        rotate_menu.setText(resources.getString("turn"));
        this.add(rotate_menu, 0);
        
        javax.swing.JMenuItem turn_90_item = new javax.swing.JMenuItem();
        turn_90_item.setText(resources.getString("90_degree"));
        turn_90_item.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                turn_45_degree(2);
            }
        });
        rotate_menu.add(turn_90_item);
        
        javax.swing.JMenuItem turn_180_item = new javax.swing.JMenuItem();
        turn_180_item.setText(resources.getString("180_degree"));
        turn_180_item.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                turn_45_degree(4);
            }
        });
        rotate_menu.add(turn_180_item);
        
        javax.swing.JMenuItem turn_270_item = new javax.swing.JMenuItem();
        turn_270_item.setText(resources.getString("-90_degree"));
        turn_270_item.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                turn_45_degree(6);
            }
        });
        rotate_menu.add(turn_270_item);
        
        javax.swing.JMenuItem turn_45_item = new javax.swing.JMenuItem();
        turn_45_item.setText(resources.getString("45_degree"));
        turn_45_item.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                turn_45_degree(1);
            }
        });
        rotate_menu.add(turn_45_item);
        
        javax.swing.JMenuItem turn_135_item = new javax.swing.JMenuItem();
        turn_135_item.setText(resources.getString("135_degree"));
        turn_135_item.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                turn_45_degree(3);
            }
        });
        rotate_menu.add(turn_135_item);
        
        javax.swing.JMenuItem turn_225_item = new javax.swing.JMenuItem();
        turn_225_item.setText(resources.getString("-135_degree"));
        turn_225_item.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                turn_45_degree(5);
            }
        });
        rotate_menu.add(turn_225_item);
        
        javax.swing.JMenuItem turn_315_item = new javax.swing.JMenuItem();
        turn_315_item.setText(resources.getString("-45_degree"));
        turn_315_item.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                turn_45_degree(7);
            }
        });
        rotate_menu.add(turn_315_item);
        
        javax.swing.JMenuItem change_side_item = new javax.swing.JMenuItem();
        change_side_item.setText(resources.getString("change_side"));
        change_side_item.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                board_panel.board_handling.change_placement_side();
            }
        });
        
        this.add(change_side_item, 1);
        
        javax.swing.JMenuItem reset_rotation_item = new javax.swing.JMenuItem();
        reset_rotation_item.setText(resources.getString("reset_rotation"));
        reset_rotation_item.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                interactive.InteractiveState interactive_state = board_panel.board_handling.get_interactive_state();
                if (interactive_state instanceof interactive.MoveItemState)
                {
                    ((interactive.MoveItemState)interactive_state).reset_rotation();
                }
            }
        });
        
        this.add(reset_rotation_item, 2);
        
        javax.swing.JMenuItem insert_item = new javax.swing.JMenuItem();
        insert_item.setText(resources.getString("insert"));
        insert_item.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                board_panel.board_handling.return_from_state();
            }
        });
        
        this.add(insert_item, 3);
        
        javax.swing.JMenuItem cancel_item = new javax.swing.JMenuItem();
        cancel_item.setText(resources.getString("cancel"));
        cancel_item.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                board_panel.board_handling.cancel_state();
            }
        });
        
        this.add(cancel_item, 4);
    }
    
    private void turn_45_degree(int p_factor)
    {
        board_panel.board_handling.turn_45_degree(p_factor);
        board_panel.move_mouse(board_panel.right_button_click_location);
    }
}
