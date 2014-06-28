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
 * WindowSnapshotSettings.java
 *
 * Created on 17. September 2005, 07:23
 *
 */

package gui;

/**
 * Window for the settinngs of interactive snapshots.
 *
 * @author Alfons Wirtz
 */
public class WindowSnapshotSettings extends BoardSavableSubWindow
{
    
    /** Creates a new instance of WindowSnapshotSettings */
    public WindowSnapshotSettings(BoardFrame p_board_frame)
    {
        this.board_handling = p_board_frame.board_panel.board_handling;
        
        java.util.ResourceBundle resources = 
                java.util.ResourceBundle.getBundle("gui.resources.WindowSnapshotSettings", p_board_frame.get_locale());
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
        
        // add check box for the object colors
        
        this.object_color_check_box = new javax.swing.JCheckBox(resources.getString("object_colors"));
        gridbag.setConstraints(object_color_check_box, gridbag_constraints);
        main_panel.add(object_color_check_box, gridbag_constraints);
        this.object_color_check_box.addActionListener(new ObjectColorListener());
        
        // add check box for the object visibility
        
        this.object_visibility_check_box = new javax.swing.JCheckBox(resources.getString("object_visibility"));
        gridbag.setConstraints(object_visibility_check_box, gridbag_constraints);
        main_panel.add(object_visibility_check_box, gridbag_constraints);
        this.object_visibility_check_box.addActionListener(new ObjectVisibilityListener());
        
        // add check box for the layer visibility
        
        this.layer_visibility_check_box = new javax.swing.JCheckBox(resources.getString("layer_visibility"));
        gridbag.setConstraints(layer_visibility_check_box, gridbag_constraints);
        main_panel.add(layer_visibility_check_box, gridbag_constraints);
        this.layer_visibility_check_box.addActionListener(new LayerVisibilityListener());
        
        // add check box for display region
        
        this.display_region_check_box = new javax.swing.JCheckBox(resources.getString("display_region"));
        gridbag.setConstraints(display_region_check_box, gridbag_constraints);
        main_panel.add(display_region_check_box, gridbag_constraints);
        this.display_region_check_box.addActionListener(new DisplayRegionListener());
        
        javax.swing.JLabel separator = new javax.swing.JLabel("  ----------------------------------------  ");
        gridbag.setConstraints(separator, gridbag_constraints);
        main_panel.add(separator, gridbag_constraints);
        
        
        // add check box for the interactive state
        
        this.interactive_state_check_box = new javax.swing.JCheckBox(resources.getString("interactive_state"));
        gridbag.setConstraints(interactive_state_check_box, gridbag_constraints);
        main_panel.add(interactive_state_check_box, gridbag_constraints);
        this.interactive_state_check_box.addActionListener(new InteractiveStateListener());
        
        separator = new javax.swing.JLabel("  ----------------------------------------  ");
        gridbag.setConstraints(separator, gridbag_constraints);
        main_panel.add(separator, gridbag_constraints);
        
        
        // add check box for the selection layers
        
        this.selection_layers_check_box = new javax.swing.JCheckBox(resources.getString("selection_layers"));
        gridbag.setConstraints(selection_layers_check_box, gridbag_constraints);
        main_panel.add(selection_layers_check_box, gridbag_constraints);
        this.selection_layers_check_box.addActionListener(new SelectionLayersListener());
        
        
        // add check box for the selectable items
        
        this.selectable_items_check_box = new javax.swing.JCheckBox(resources.getString("selectable_items"));
        gridbag.setConstraints(selectable_items_check_box, gridbag_constraints);
        main_panel.add(selectable_items_check_box, gridbag_constraints);
        this.selectable_items_check_box.addActionListener(new SelectableItemsListener());
        
        
        // add check box for the current layer
        
        this.current_layer_check_box = new javax.swing.JCheckBox(resources.getString("current_layer"));
        gridbag.setConstraints(current_layer_check_box, gridbag_constraints);
        main_panel.add(current_layer_check_box, gridbag_constraints);
        this.current_layer_check_box.addActionListener(new CurrentLayerListener());
        
        separator = new javax.swing.JLabel("  ----------------------------------------  ");
        gridbag.setConstraints(separator, gridbag_constraints);
        main_panel.add(separator, gridbag_constraints);
        
        
        // add check box for the rule selection
        
        this.rule_selection_check_box = new javax.swing.JCheckBox(resources.getString("rule_selection"));
        gridbag.setConstraints(rule_selection_check_box, gridbag_constraints);
        main_panel.add(rule_selection_check_box, gridbag_constraints);
        this.rule_selection_check_box.addActionListener(new RuleSelectionListener());
        
        
        // add check box for the manual rule settings
        
        this.manual_rule_settings_check_box = new javax.swing.JCheckBox(resources.getString("manual_rule_settings"));
        gridbag.setConstraints(manual_rule_settings_check_box, gridbag_constraints);
        main_panel.add(manual_rule_settings_check_box, gridbag_constraints);
        this.manual_rule_settings_check_box.addActionListener(new ManualRuleSettingsListener());
        
        // add check box for push and shove enabled
        
        this.push_and_shove_enabled_check_box = new javax.swing.JCheckBox(resources.getString("push&shove_enabled"));
        gridbag.setConstraints(push_and_shove_enabled_check_box, gridbag_constraints);
        main_panel.add(push_and_shove_enabled_check_box, gridbag_constraints);
        this.push_and_shove_enabled_check_box.addActionListener(new PushAndShoveEnabledListener());
        
        // add check box for drag components enabled
        
        this.drag_components_enabled_check_box = new javax.swing.JCheckBox(resources.getString("drag_components_enabled"));
        gridbag.setConstraints(drag_components_enabled_check_box, gridbag_constraints);
        main_panel.add(drag_components_enabled_check_box, gridbag_constraints);
        this.drag_components_enabled_check_box.addActionListener(new DragComponentsEnabledListener());
        
        // add check box for the pull tight region
        
        this.pull_tight_region_check_box = new javax.swing.JCheckBox(resources.getString("pull_tight_region"));
        gridbag.setConstraints(pull_tight_region_check_box, gridbag_constraints);
        main_panel.add(pull_tight_region_check_box, gridbag_constraints);
        this.pull_tight_region_check_box.addActionListener(new PullTightRegionListener());
        
        separator = new javax.swing.JLabel("  ----------------------------------------  ");
        gridbag.setConstraints(separator, gridbag_constraints);
        main_panel.add(separator, gridbag_constraints);
        
        // add check box for the component grid
        
        this.component_grid_check_box = new javax.swing.JCheckBox(resources.getString("component_grid"));
        gridbag.setConstraints(component_grid_check_box, gridbag_constraints);
        main_panel.add(component_grid_check_box, gridbag_constraints);
        this.component_grid_check_box.addActionListener(new ComponentGridListener());
        
        separator = new javax.swing.JLabel("  ----------------------------------------  ");
        gridbag.setConstraints(separator, gridbag_constraints);
        main_panel.add(separator, gridbag_constraints);
        
        // add check box for the info list filters
        
        this.info_list_filter_check_box = new javax.swing.JCheckBox(resources.getString("info_list_selections"));
        gridbag.setConstraints(info_list_filter_check_box, gridbag_constraints);
        main_panel.add(info_list_filter_check_box, gridbag_constraints);
        this.info_list_filter_check_box.addActionListener(new InfoListFilterListener());
        
        p_board_frame.set_context_sensitive_help(this, "WindowSnapshots_SnapshotSettings");
        
        
        this.refresh();
        this.pack();
        this.setResizable(false);
    }
    
    /**
     * Recalculates all displayed values
     */
    public void refresh()
    {
        interactive.SnapShot.Attributes attributes = this.board_handling.settings.get_snapshot_attributes();
        this.object_color_check_box.setSelected(attributes.object_colors);
        this.object_visibility_check_box.setSelected(attributes.object_visibility);
        this.layer_visibility_check_box.setSelected(attributes.layer_visibility);
        this.display_region_check_box.setSelected(attributes.display_region);
        this.interactive_state_check_box.setSelected(attributes.interactive_state);
        this.selection_layers_check_box.setSelected(attributes.selection_layers);
        this.selectable_items_check_box.setSelected(attributes.selectable_items);
        this.current_layer_check_box.setSelected(attributes.current_layer);
        this.rule_selection_check_box.setSelected(attributes.rule_selection);
        this.manual_rule_settings_check_box.setSelected(attributes.manual_rule_settings);
        this.push_and_shove_enabled_check_box.setSelected(attributes.push_and_shove_enabled);
        this.drag_components_enabled_check_box.setSelected(attributes.drag_components_enabled);
        this.pull_tight_region_check_box.setSelected(attributes.pull_tight_region);
        this.component_grid_check_box.setSelected(attributes.component_grid);
        info_list_filter_check_box.setSelected(attributes.info_list_selections);
    }
    
    private final interactive.BoardHandling board_handling;
    
    final javax.swing.JCheckBox object_color_check_box;
    final javax.swing.JCheckBox object_visibility_check_box;
    final javax.swing.JCheckBox layer_visibility_check_box;
    final javax.swing.JCheckBox display_region_check_box;
    final javax.swing.JCheckBox interactive_state_check_box;
    final javax.swing.JCheckBox selection_layers_check_box;
    final javax.swing.JCheckBox selectable_items_check_box;
    final javax.swing.JCheckBox current_layer_check_box;
    final javax.swing.JCheckBox rule_selection_check_box;
    final javax.swing.JCheckBox manual_rule_settings_check_box;
    final javax.swing.JCheckBox push_and_shove_enabled_check_box;
    final javax.swing.JCheckBox drag_components_enabled_check_box;
    final javax.swing.JCheckBox pull_tight_region_check_box;
    final javax.swing.JCheckBox component_grid_check_box;
    final javax.swing.JCheckBox info_list_filter_check_box;
    
    private class ObjectColorListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.get_snapshot_attributes().object_colors = object_color_check_box.isSelected();
        }
    }
    
    private class ObjectVisibilityListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.get_snapshot_attributes().object_visibility = object_visibility_check_box.isSelected();
        }
    }
    
    
    private class LayerVisibilityListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.get_snapshot_attributes().layer_visibility = layer_visibility_check_box.isSelected();
        }
    }
    
    private class DisplayRegionListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.get_snapshot_attributes().display_region = display_region_check_box.isSelected();
        }
    }
    
    private class InteractiveStateListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.get_snapshot_attributes().interactive_state = interactive_state_check_box.isSelected();
        }
    }
    
    private class SelectionLayersListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.get_snapshot_attributes().selection_layers = selection_layers_check_box.isSelected();
        }
    }
    
    private class SelectableItemsListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.get_snapshot_attributes().selectable_items = selectable_items_check_box.isSelected();
        }
    }
    
    private class CurrentLayerListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.get_snapshot_attributes().current_layer = current_layer_check_box.isSelected();
        }
    }
    
    private class RuleSelectionListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.get_snapshot_attributes().rule_selection = rule_selection_check_box.isSelected();
        }
    }
    
    private class ManualRuleSettingsListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.get_snapshot_attributes().manual_rule_settings = manual_rule_settings_check_box.isSelected();
        }
    }
    
    private class PushAndShoveEnabledListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.get_snapshot_attributes().push_and_shove_enabled = push_and_shove_enabled_check_box.isSelected();
        }
    }
    
    private class DragComponentsEnabledListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.get_snapshot_attributes().drag_components_enabled = drag_components_enabled_check_box.isSelected();
        }
    }
    
    private class PullTightRegionListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.get_snapshot_attributes().pull_tight_region = pull_tight_region_check_box.isSelected();
        }
    }
    
    private class ComponentGridListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.get_snapshot_attributes().component_grid = component_grid_check_box.isSelected();
        }
    }
    
    private class InfoListFilterListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.get_snapshot_attributes().info_list_selections = info_list_filter_check_box.isSelected();
        }
    }
}
