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
 * BoardToolbarPanel.java
 *
 * Created on 15. Februar 2005, 09:44
 */

package gui;

/**
 * Implements the toolbar panel of the board frame.
 *
 * @author Alfons Wirtz
 */
class BoardToolbar extends javax.swing.JPanel
{
    
    /** Creates a new instance of BoardToolbarPanel */
    BoardToolbar(BoardFrame p_board_frame)
    {
        this.board_frame = p_board_frame;
        
        java.util.ResourceBundle resources = 
                java.util.ResourceBundle.getBundle("gui.resources.BoardToolbar", p_board_frame.get_locale());
        
        this.setLayout(new java.awt.BorderLayout());
        
        // create the left toolbar
        
        final javax.swing.JToolBar left_toolbar = new javax.swing.JToolBar();
        final javax.swing.ButtonGroup toolbar_button_group = new javax.swing.ButtonGroup();
        this.select_button = new javax.swing.JToggleButton();
        this.route_button = new javax.swing.JToggleButton();
        this.drag_button = new javax.swing.JToggleButton();
        final javax.swing.JLabel jLabel1 = new javax.swing.JLabel();
        
        left_toolbar.setMaximumSize(new java.awt.Dimension(1200, 23));
        toolbar_button_group.add(select_button);
        select_button.setSelected(true);
        select_button.setText(resources.getString("select_button"));
        select_button.setToolTipText(resources.getString("select_button_tooltip"));
        select_button.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                board_frame.board_panel.board_handling.set_select_menu_state();
            }
        });
        
        left_toolbar.add(select_button);
        
        toolbar_button_group.add(route_button);
        route_button.setText(resources.getString("route_button"));
        route_button.setToolTipText(resources.getString("route_button_tooltip"));
        route_button.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                board_frame.board_panel.board_handling.set_route_menu_state();
            }
        });
        
        left_toolbar.add(route_button);
        
        toolbar_button_group.add(drag_button);
        drag_button.setText(resources.getString("drag_button"));
        drag_button.setToolTipText(resources.getString("drag_button_tooltip"));
        drag_button.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                board_frame.board_panel.board_handling.set_drag_menu_state();
            }
        });
        
        left_toolbar.add(drag_button);
        
        jLabel1.setMaximumSize(new java.awt.Dimension(30, 10));
        jLabel1.setMinimumSize(new java.awt.Dimension(3, 10));
        jLabel1.setPreferredSize(new java.awt.Dimension(30, 10));
        left_toolbar.add(jLabel1);
        
        this.add(left_toolbar, java.awt.BorderLayout.WEST);
        
        // create the middle toolbar
        
        final javax.swing.JToolBar middle_toolbar = new javax.swing.JToolBar();
        
        final javax.swing.JButton autoroute_button = new javax.swing.JButton();
        autoroute_button.setText(resources.getString("autoroute_button"));
        autoroute_button.setToolTipText(resources.getString("autoroute_button_tooltip"));
        autoroute_button.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                board_frame.board_panel.board_handling.start_batch_autorouter();
            }
        });
        
        middle_toolbar.add(autoroute_button);
        
        final javax.swing.JLabel separator_2 = new javax.swing.JLabel();
        separator_2.setMaximumSize(new java.awt.Dimension(10, 10));
        separator_2.setPreferredSize(new java.awt.Dimension(10, 10));
        separator_2.setRequestFocusEnabled(false);
        middle_toolbar.add(separator_2);
        
        final javax.swing.JButton undo_button = new javax.swing.JButton();
        undo_button.setText(resources.getString("undo_button"));
        undo_button.setToolTipText(resources.getString("undo_button_tooltip"));
        undo_button.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                board_frame.board_panel.board_handling.cancel_state();
                board_frame.board_panel.board_handling.undo();
                board_frame.refresh_windows();
            }
        });
        
        middle_toolbar.add(undo_button);
        
        final javax.swing.JButton redo_button = new javax.swing.JButton();
        redo_button.setText(resources.getString("redo_button"));
        redo_button.setToolTipText(resources.getString("redo_button_tooltip"));
        redo_button.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                board_frame.board_panel.board_handling.redo();
            }
        });
        
        middle_toolbar.add(redo_button);
        
        final javax.swing.JLabel separator_1 = new javax.swing.JLabel();
        separator_1.setMaximumSize(new java.awt.Dimension(10, 10));
        separator_1.setPreferredSize(new java.awt.Dimension(10, 10));
        middle_toolbar.add(separator_1);
        
        final javax.swing.JButton incompletes_button = new javax.swing.JButton();
        incompletes_button.setText(resources.getString("incompletes_button"));
        incompletes_button.setToolTipText(resources.getString("incompletes_button_tooltip"));
        incompletes_button.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                board_frame.board_panel.board_handling.toggle_ratsnest();
            }
        });
        
        middle_toolbar.add(incompletes_button);
        
        final javax.swing.JButton violation_button = new javax.swing.JButton();
        violation_button.setText(resources.getString("violations_button"));
        violation_button.setToolTipText(resources.getString("violations_button_tooltip"));
        violation_button.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                board_frame.board_panel.board_handling.toggle_clearance_violations();
            }
        });
        
        middle_toolbar.add(violation_button);
        
        final javax.swing.JLabel separator_3 = new javax.swing.JLabel();
        separator_3.setMaximumSize(new java.awt.Dimension(10, 10));
        separator_3.setPreferredSize(new java.awt.Dimension(10, 10));
        separator_3.setRequestFocusEnabled(false);
        middle_toolbar.add(separator_3);
        
        final javax.swing.JButton display_all_button = new javax.swing.JButton();
        display_all_button.setText(resources.getString("display_all_button"));
        display_all_button.setToolTipText(resources.getString("display_all_button_tooltip"));
        display_all_button.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                board_frame.zoom_all();
            }
        });
        
        middle_toolbar.add(display_all_button);
        
        final javax.swing.JButton display_region_button = new javax.swing.JButton();
        display_region_button.setText(resources.getString("display_region_button"));
        display_region_button.setToolTipText(resources.getString("display_region_button_tooltip"));
        display_region_button.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                board_frame.board_panel.board_handling.zoom_region();
            }
        });
        
        middle_toolbar.add(display_region_button);
        
        this.add(middle_toolbar, java.awt.BorderLayout.CENTER);
        
        // create the right toolbar
        
        final javax.swing.JToolBar right_toolbar = new javax.swing.JToolBar();
        final javax.swing.JLabel unit_label = new javax.swing.JLabel();
        java.text.NumberFormat number_format = java.text.NumberFormat.getInstance(p_board_frame.get_locale());
        number_format.setMaximumFractionDigits(7);
        this.unit_factor_field = new javax.swing.JFormattedTextField(number_format);
        this.unit_combo_box = new javax.swing.JComboBox();
        final javax.swing.JLabel jLabel4 = new javax.swing.JLabel();
        
        right_toolbar.setAutoscrolls(true);
        unit_label.setText(resources.getString("unit_button"));
        unit_label.setMaximumSize(new java.awt.Dimension(30, 21));
        unit_label.setPreferredSize(new java.awt.Dimension(30, 21));
        right_toolbar.add(unit_label);
        
        unit_factor_field.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        unit_factor_field.setValue(1);
        unit_factor_field.setMaximumSize(new java.awt.Dimension(100, 18));
        unit_factor_field.setMinimumSize(new java.awt.Dimension(40, 18));
        unit_factor_field.setPreferredSize(new java.awt.Dimension(80, 18));
        unit_factor_field.addKeyListener(new java.awt.event.KeyAdapter()
        {
            public void keyTyped(java.awt.event.KeyEvent evt)
            {
                if (evt.getKeyChar() == '\n')
                {
                    Object input = unit_factor_field.getValue();
                    if (input instanceof Number)
                    {
                        double input_value = ((Number)input).doubleValue();
                        if (input_value > 0)
                        {
                            board_frame.board_panel.board_handling.change_user_unit_factor(input_value);
                        }
                    }
                    double unit_factor = board_frame.board_panel.board_handling.coordinate_transform.user_unit_factor;
                    unit_factor_field.setValue(unit_factor);
                    
                    board_frame.refresh_windows();
                }
            }
        });
        
        right_toolbar.add(unit_factor_field);
        
        unit_combo_box.setModel(new javax.swing.DefaultComboBoxModel(board.Unit.values()));
        unit_combo_box.setFocusTraversalPolicyProvider(true);
        unit_combo_box.setInheritsPopupMenu(true);
        unit_combo_box.setMaximumSize(new java.awt.Dimension(60, 18));
        unit_combo_box.setMinimumSize(new java.awt.Dimension(60, 18));
        unit_combo_box.setOpaque(false);
        unit_combo_box.setPreferredSize(new java.awt.Dimension(60, 18));
        unit_combo_box.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                board.Unit new_unit = (board.Unit) unit_combo_box.getSelectedItem();
                board_frame.board_panel.board_handling.change_user_unit(new_unit);
                board_frame.refresh_windows();
            }
        });
        
        right_toolbar.add(unit_combo_box);
        
        jLabel4.setMaximumSize(new java.awt.Dimension(30, 14));
        jLabel4.setPreferredSize(new java.awt.Dimension(30, 14));
        right_toolbar.add(jLabel4);
        
        this.add(right_toolbar, java.awt.BorderLayout.EAST);
    }
    
    /**
     * Sets the selected button in the menu button button group
     */
    void hilight_selected_button()
    {
        interactive.InteractiveState interactive_state = this.board_frame.board_panel.board_handling.get_interactive_state();
        if (interactive_state instanceof interactive.RouteMenuState)
        {
            this.route_button.setSelected(true);
        }
        else if (interactive_state instanceof interactive.DragMenuState)
        {
            this.drag_button.setSelected(true);
        }
        else if (interactive_state instanceof interactive.SelectMenuState)
        {
            this.select_button.setSelected(true);
        }
    }
    
    private final BoardFrame board_frame;
    private final javax.swing.JToggleButton select_button;
    private final javax.swing.JToggleButton route_button;
    private final javax.swing.JToggleButton drag_button;
    final javax.swing.JFormattedTextField unit_factor_field;
    final javax.swing.JComboBox unit_combo_box;
}
