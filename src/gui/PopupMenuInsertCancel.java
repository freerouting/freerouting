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
 * CompleteCancelPopupMenu.java
 *
 * Created on 17. Februar 2005, 08:05
 */

package gui;

/**
 * Popup menu containing the 2 items complete and cancel.
 *
 * @author Alfons Wirtz
 */
class PopupMenuInsertCancel extends javax.swing.JPopupMenu
{
    
    /** Creates a new instance of CompleteCancelPopupMenu */
    PopupMenuInsertCancel(BoardFrame p_board_frame)
    {
        this.board_panel = p_board_frame.board_panel;
        java.util.ResourceBundle resources = 
                java.util.ResourceBundle.getBundle("gui.resources.Default", p_board_frame.get_locale());
        javax.swing.JMenuItem insert_item = new javax.swing.JMenuItem();
        insert_item.setText(resources.getString("insert"));
        insert_item.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                board_panel.board_handling.return_from_state();
            }
        });
        
        this.add(insert_item);
        
        javax.swing.JMenuItem cancel_item = new javax.swing.JMenuItem();
        cancel_item.setText(resources.getString("cancel"));
        cancel_item.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                board_panel.board_handling.cancel_state();
            }
        });
        
        this.add(cancel_item);
    }
    
    private final BoardPanel board_panel;
}
