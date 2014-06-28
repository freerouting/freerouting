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
 * SelectParameterWindow.java
 *
 * Created on 19. November 2004, 11:12
 */

package gui;

import board.ItemSelectionFilter;

/**
 * Window for the handling of the interactive selection parameters,
 *
 * @author  Alfons Wirtz
 */
public class WindowSelectParameter extends BoardSavableSubWindow
{
    
    /** Creates a new instance of SelectWindow */
    public WindowSelectParameter(BoardFrame p_board_frame)
    {
        this.board_handling = p_board_frame.board_panel.board_handling;
        
        java.util.ResourceBundle resources = 
                java.util.ResourceBundle.getBundle("gui.resources.WindowSelectParameter", p_board_frame.get_locale());
        this.setTitle(resources.getString("title"));
        
        // create main panel
        
        final javax.swing.JPanel main_panel = new javax.swing.JPanel();
        getContentPane().add(main_panel);
        java.awt.GridBagLayout gridbag = new java.awt.GridBagLayout();
        main_panel.setLayout(gridbag);
        java.awt.GridBagConstraints gridbag_constraints = new java.awt.GridBagConstraints();
        gridbag_constraints.anchor = java.awt.GridBagConstraints.WEST;
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridbag_constraints.insets = new java.awt.Insets(1, 10, 1, 10);
        
        // Create buttongroup for the selection layers
        
        javax.swing.JLabel selection_layer_label = new javax.swing.JLabel(resources.getString("selection_layers"));
        gridbag.setConstraints(selection_layer_label, gridbag_constraints);
        main_panel.add(selection_layer_label);
        
        this.all_visible_button = new javax.swing.JRadioButton(resources.getString("all_visible"));
        all_visible_button.setToolTipText(resources.getString("all_visible_tooltip"));
        this.current_only_button = new javax.swing.JRadioButton(resources.getString("current_only"));
        current_only_button.setToolTipText(resources.getString("current_only_tooltip"));
        
        all_visible_button.addActionListener(new AllVisibleListener());
        current_only_button.addActionListener(new CurrentOnlyListener());
        
        javax.swing.ButtonGroup selection_layer_button_group = new javax.swing.ButtonGroup();
        selection_layer_button_group.add(all_visible_button);
        selection_layer_button_group.add(current_only_button);
        gridbag_constraints.gridheight = 1;
        gridbag.setConstraints(all_visible_button, gridbag_constraints);
        main_panel.add(all_visible_button, gridbag_constraints);
        gridbag.setConstraints(current_only_button, gridbag_constraints);
        main_panel.add(current_only_button, gridbag_constraints);
        
        javax.swing.JLabel separator = new javax.swing.JLabel("  ----------------------------------------  ");
        gridbag.setConstraints(separator, gridbag_constraints);
        main_panel.add(separator, gridbag_constraints);
        
        // Create check boxes for selectable items:
        
        javax.swing.JLabel selectable_items_label = new javax.swing.JLabel(resources.getString("selectable_items"));
        gridbag.setConstraints(selectable_items_label, gridbag_constraints);
        main_panel.add(selectable_items_label);
        
        final ItemSelectionFilter.SelectableChoices[] filter_values = ItemSelectionFilter.SelectableChoices.values();
        
        this.item_selection_choices = new javax.swing.JCheckBox [filter_values.length];
        
        for (int i = 0; i < filter_values.length; ++i)
        {
            this.item_selection_choices[i] = new javax.swing.JCheckBox(resources.getString(filter_values[i].toString()));
            gridbag.setConstraints(this.item_selection_choices[i], gridbag_constraints);
            main_panel.add(this.item_selection_choices[i], gridbag_constraints);
            this.item_selection_choices[i].addActionListener(new ItemSelectionListener(i));
        }
        
        javax.swing.JLabel separator2 = new javax.swing.JLabel("  ----------------------------------------  ");
        gridbag.setConstraints(separator2, gridbag_constraints);
        main_panel.add(separator2, gridbag_constraints);
        
        // Create buttongroup for the current layer:
        
        board.LayerStructure layer_structure = this.board_handling.get_routing_board().layer_structure;
        int signal_layer_count = layer_structure.signal_layer_count();
        javax.swing.JLabel current_layer_label = new javax.swing.JLabel(resources.getString("current_layer"));
        current_layer_label.setToolTipText(resources.getString("current_layer_tooltip"));
        gridbag.setConstraints(current_layer_label, gridbag_constraints);
        main_panel.add(current_layer_label);
        
        this.layer_name_arr = new  javax.swing.JRadioButton [signal_layer_count];
        javax.swing.ButtonGroup current_layer_button_group = new javax.swing.ButtonGroup();
        gridbag_constraints.gridheight = 1;
        for (int i = 0; i <  signal_layer_count; ++i)
        {
            board.Layer curr_signal_layer = layer_structure.get_signal_layer(i);
            layer_name_arr[i] = new javax.swing.JRadioButton();
            layer_name_arr[i].setText(curr_signal_layer.name);
            gridbag.setConstraints(layer_name_arr[i], gridbag_constraints);
            main_panel.add(layer_name_arr[i]);
            current_layer_button_group.add(layer_name_arr[i]);
            int layer_no = layer_structure.get_no(curr_signal_layer);
            layer_name_arr[i].addActionListener(new CurrentLayerListener(i, layer_no));
        }
        javax.swing.JLabel empty_label = new javax.swing.JLabel();
        gridbag.setConstraints(empty_label, gridbag_constraints);
        main_panel.add(empty_label);
        
        p_board_frame.set_context_sensitive_help(this, "WindowSelectParameter");
        
        this.refresh();
        this.pack();
        this.setResizable(false);
    }
    
    /**
     * Refreshs the displayed values in this window.
     */
    public void refresh()
    {
        if (this.board_handling.settings.get_select_on_all_visible_layers())
        {
            all_visible_button.setSelected(true);
        }
        else
        {
            current_only_button.setSelected(true);
        }
        ItemSelectionFilter item_selection_filter = this.board_handling.settings.get_item_selection_filter();
        if (item_selection_filter == null)
        {
            System.out.println("SelectParameterWindow.refresh: item_selection_filter is null");
        }
        else
        {
            final ItemSelectionFilter.SelectableChoices[] filter_values = ItemSelectionFilter.SelectableChoices.values();
            for (int i = 0; i < filter_values.length; ++i)
            {
                this.item_selection_choices[i].setSelected(item_selection_filter.is_selected(filter_values[i])) ;
            }
        }
        board.LayerStructure layer_structure = this.board_handling.get_routing_board().layer_structure;
        board.Layer current_layer = layer_structure.arr[this.board_handling.settings.get_layer()];
        layer_name_arr[layer_structure.get_signal_layer_no(current_layer)].setSelected(true);
    }
    
    /**
     * Selects the layer with the input signal number.
     */
    public void select(int p_signal_layer_no)
    {
        layer_name_arr[p_signal_layer_no].setSelected(true);
    }
    
    private final interactive.BoardHandling board_handling;
    
    private final javax.swing.JRadioButton [] layer_name_arr;
    
    private final javax.swing.JCheckBox [] item_selection_choices;
    
    private final javax.swing.JRadioButton all_visible_button;
    
    private final javax.swing.JRadioButton current_only_button;
    
    private class CurrentLayerListener implements java.awt.event.ActionListener
    {
        public CurrentLayerListener(int p_signal_layer_no, int p_layer_no)
        {
            signal_layer_no = p_signal_layer_no;
            layer_no = p_layer_no;
        }
        
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.set_current_layer(layer_no);
        }
        
        public final int signal_layer_no;
        public final int layer_no;
    }
    
    private class AllVisibleListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.set_select_on_all_visible_layers(true);
        }
    }
    
    private class CurrentOnlyListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.set_select_on_all_visible_layers(false);
        }
    }
    
    private class ItemSelectionListener implements java.awt.event.ActionListener
    {
        public ItemSelectionListener(int p_item_no)
        {
            item_no = p_item_no;
        }
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            boolean is_selected = item_selection_choices[item_no].isSelected();
            
            ItemSelectionFilter.SelectableChoices item_type = ItemSelectionFilter.SelectableChoices.values()[item_no];
            
            board_handling.set_selectable(item_type, is_selected);
            
            // make shure that from fixed and unfixed items at least one type is selected.
            if (item_type == ItemSelectionFilter.SelectableChoices.FIXED)
            {
                int unfixed_no = ItemSelectionFilter.SelectableChoices.UNFIXED.ordinal();
                if (!is_selected && ! item_selection_choices[unfixed_no].isSelected())
                {
                    item_selection_choices[unfixed_no].setSelected(true);
                    board_handling.set_selectable(ItemSelectionFilter.SelectableChoices.UNFIXED, true);
                }
            }
            else if (item_type == ItemSelectionFilter.SelectableChoices.UNFIXED)
            {
                int fixed_no = ItemSelectionFilter.SelectableChoices.FIXED.ordinal();
                if (!is_selected && ! item_selection_choices[fixed_no].isSelected())
                {
                    item_selection_choices[fixed_no].setSelected(true);
                    board_handling.set_selectable(ItemSelectionFilter.SelectableChoices.FIXED, true);
                }
            }
        }
        
        private final int item_no;
    }
}
