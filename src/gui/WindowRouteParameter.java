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
 * RouteParameterWindow.java
 *
 * Created on 17. November 2004, 07:11
 */

package gui;

import java.util.Collection;

/**
 *  Window handling parameters of the interactive routing.
 *
 * @author  Alfons Wirtz
 */
public class WindowRouteParameter extends BoardSavableSubWindow
{
    
    /** Creates a new instance of RouteParameterWindow */
    public WindowRouteParameter(BoardFrame p_board_frame)
    {
        this.board_handling = p_board_frame.board_panel.board_handling;
        this.current_locale = p_board_frame.get_locale();
        this.detail_window = new WindowRouteDetail(p_board_frame);
        this.manual_rule_window = new WindowManualRules(p_board_frame);
        
        java.util.ResourceBundle resources = 
                java.util.ResourceBundle.getBundle("gui.resources.WindowRouteParameter", p_board_frame.get_locale());
        this.setTitle(resources.getString("title"));
        
        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE );
        
        // create main panel
        
        final javax.swing.JPanel main_panel = new javax.swing.JPanel();
        getContentPane().add(main_panel);
        java.awt.GridBagLayout gridbag = new java.awt.GridBagLayout();
        main_panel.setLayout(gridbag);
        java.awt.GridBagConstraints gridbag_constraints = new java.awt.GridBagConstraints();
        gridbag_constraints.anchor = java.awt.GridBagConstraints.WEST;
        gridbag_constraints.insets = new java.awt.Insets(1, 10, 1, 10);
        
        // add label and button group for the route snap angle.
        
        javax.swing.JLabel snap_angle_label = new javax.swing.JLabel(resources.getString("snap_angle"));
        snap_angle_label.setToolTipText(resources.getString("snap_angle_tooltip"));
        
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.RELATIVE;
        gridbag_constraints.gridheight = 3;
        gridbag.setConstraints(snap_angle_label, gridbag_constraints);
        main_panel.add(snap_angle_label);
        
        this.snap_angle_90_button = new javax.swing.JRadioButton(resources.getString("90_degree"));
        this.snap_angle_45_button = new javax.swing.JRadioButton(resources.getString("45_degree"));
        this.snap_angle_none_button = new javax.swing.JRadioButton(resources.getString("none"));
        
        snap_angle_90_button.addActionListener(new SnapAngle90Listener());
        snap_angle_45_button.addActionListener(new SnapAngle45Listener());
        snap_angle_none_button.addActionListener(new SnapAngleNoneListener());
        
        javax.swing.ButtonGroup snap_angle_button_group = new javax.swing.ButtonGroup();
        snap_angle_button_group.add(snap_angle_90_button);
        snap_angle_button_group.add(snap_angle_45_button);
        snap_angle_button_group.add(snap_angle_none_button);
        snap_angle_none_button.setSelected(true);
        
        
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridbag_constraints.gridheight = 1;
        gridbag.setConstraints(snap_angle_90_button, gridbag_constraints);
        main_panel.add(snap_angle_90_button, gridbag_constraints);
        
        gridbag.setConstraints(snap_angle_45_button, gridbag_constraints);
        main_panel.add(snap_angle_45_button, gridbag_constraints);
        gridbag.setConstraints(snap_angle_none_button, gridbag_constraints);
        main_panel.add(snap_angle_none_button, gridbag_constraints);
        
        javax.swing.JLabel separator = new javax.swing.JLabel("  ----------------------------------------  ");
        gridbag.setConstraints(separator, gridbag_constraints);
        main_panel.add(separator, gridbag_constraints);
        
        // add label and button group for the route mode.
        
        javax.swing.JLabel route_mode_label = new javax.swing.JLabel(resources.getString("route_mode"));
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.RELATIVE;
        gridbag_constraints.gridheight = 2;
        gridbag.setConstraints(route_mode_label, gridbag_constraints);
        main_panel.add(route_mode_label);
        
        this.dynamic_button = new javax.swing.JRadioButton(resources.getString("dynamic"));
        this.stitch_button = new javax.swing.JRadioButton(resources.getString("stitching"));
        
        dynamic_button.addActionListener(new DynamicRouteListener());
        stitch_button.addActionListener(new StitchRouteListener());
        
        javax.swing.ButtonGroup route_mode_button_group = new javax.swing.ButtonGroup();
        route_mode_button_group.add(dynamic_button);
        route_mode_button_group.add(stitch_button);
        dynamic_button.setSelected(true);
        
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridbag_constraints.gridheight = 1;
        gridbag.setConstraints(dynamic_button, gridbag_constraints);
        main_panel.add(dynamic_button, gridbag_constraints);
        gridbag.setConstraints(stitch_button, gridbag_constraints);
        main_panel.add(stitch_button, gridbag_constraints);
        
        separator = new javax.swing.JLabel("  ----------------------------------------  ");
        gridbag.setConstraints(separator, gridbag_constraints);
        main_panel.add(separator, gridbag_constraints);
        
        // add label and buttongroup for automatic or manual trace width selection.
        
        javax.swing.JLabel trace_widths_label = new javax.swing.JLabel(resources.getString("rule_selection"));
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.RELATIVE;
        gridbag_constraints.gridheight = 2;
        gridbag.setConstraints(trace_widths_label, gridbag_constraints);
        main_panel.add(trace_widths_label);
        
        this.automatic_button = new javax.swing.JRadioButton(resources.getString("automatic"));
        this.manual_button = new javax.swing.JRadioButton(resources.getString("manual"));
        
        automatic_button.addActionListener(new AutomaticTraceWidthListener());
        this.manual_trace_width_listener = new ManualTraceWidthListener();
        manual_button.addActionListener(manual_trace_width_listener);
        
        javax.swing.ButtonGroup trace_widths_button_group = new javax.swing.ButtonGroup();
        trace_widths_button_group.add(automatic_button);
        trace_widths_button_group.add(manual_button);
        automatic_button.setSelected(true);
        
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridbag_constraints.gridheight = 1;
        gridbag.setConstraints(automatic_button, gridbag_constraints);
        main_panel.add(automatic_button, gridbag_constraints);
        gridbag.setConstraints(manual_button, gridbag_constraints);
        main_panel.add(manual_button, gridbag_constraints);
        
        separator = new javax.swing.JLabel("  ----------------------------------------  ");
        gridbag.setConstraints(separator, gridbag_constraints);
        main_panel.add(separator, gridbag_constraints);
        
        // add check box for push enabled
        
        this.shove_check_box = new javax.swing.JCheckBox(resources.getString("push&shove_enabled"));
        this.shove_check_box.addActionListener(new ShoveListener());
        gridbag.setConstraints(shove_check_box, gridbag_constraints);
        this.shove_check_box.setToolTipText(resources.getString("push&shove_enabled_tooltip"));
        main_panel.add(shove_check_box, gridbag_constraints);
        
        // add check box for drag components enabled
        
        this.drag_component_check_box = new javax.swing.JCheckBox(resources.getString("drag_components_enabled"));
        this.drag_component_check_box.addActionListener(new DragComponentListener());
        gridbag.setConstraints(drag_component_check_box, gridbag_constraints);
        this.drag_component_check_box.setToolTipText(resources.getString("drag_components_enabled_tooltip"));
        main_panel.add(drag_component_check_box, gridbag_constraints);
        
        // add check box for via snap to smd center
        
        this.via_snap_to_smd_center_check_box = new javax.swing.JCheckBox(resources.getString("via_snap_to_smd_center"));
        this.via_snap_to_smd_center_check_box.addActionListener(new ViaSnapToSMDCenterListener());
        gridbag.setConstraints(via_snap_to_smd_center_check_box, gridbag_constraints);
        this.via_snap_to_smd_center_check_box.setToolTipText(resources.getString("via_snap_to_smd_center_tooltip"));
        main_panel.add(via_snap_to_smd_center_check_box, gridbag_constraints);
        
        // add check box for hilighting the routing obstacle
        
        this.hilight_routing_obstacle_check_box = new javax.swing.JCheckBox(resources.getString("hilight_routing_obstacle"));
        this.hilight_routing_obstacle_check_box.addActionListener(new HilightObstacleListener());
        gridbag.setConstraints(hilight_routing_obstacle_check_box, gridbag_constraints);
        this.hilight_routing_obstacle_check_box.setToolTipText(resources.getString("hilight_routing_obstacle_tooltip"));
        main_panel.add(hilight_routing_obstacle_check_box, gridbag_constraints);
        
        // add check box for ignore_conduction_areas
        
        this.ignore_conduction_check_box = new javax.swing.JCheckBox(resources.getString("ignore_conduction_areas"));
        this.ignore_conduction_check_box.addActionListener(new IgnoreConductionListener());
        gridbag.setConstraints(ignore_conduction_check_box, gridbag_constraints);
        this.ignore_conduction_check_box.setToolTipText(resources.getString("ignore_conduction_areas_tooltip"));
        main_panel.add(ignore_conduction_check_box, gridbag_constraints);
        
        // add check box for automatic neckdown
        
        this.neckdown_check_box = new javax.swing.JCheckBox(resources.getString("automatic_neckdown"));
        this.neckdown_check_box.addActionListener(new NeckDownListener());
        gridbag.setConstraints(neckdown_check_box, gridbag_constraints);
        this.neckdown_check_box.setToolTipText(resources.getString("automatic_neckdown_tooltip"));
        main_panel.add(neckdown_check_box, gridbag_constraints);
        
        // add labels and text field for restricting pin exit directions
        
        separator = new javax.swing.JLabel("  ----------------------------------------  ");
        gridbag.setConstraints(separator, gridbag_constraints);
        main_panel.add(separator, gridbag_constraints);
        
        this.restrict_pin_exit_directions_check_box = new javax.swing.JCheckBox(resources.getString("restrict_pin_exit_directions"));
        this.restrict_pin_exit_directions_check_box.addActionListener(new RestrictPinExitDirectionsListener());
        gridbag.setConstraints(restrict_pin_exit_directions_check_box, gridbag_constraints);
        this.restrict_pin_exit_directions_check_box.setToolTipText(resources.getString("restrict_pin_exit_directions_tooltip"));
        main_panel.add(restrict_pin_exit_directions_check_box, gridbag_constraints);
        
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.RELATIVE;
        javax.swing.JLabel pin_exit_edge_to_turn_label = new javax.swing.JLabel(resources.getString("pin_pad_to_turn_gap"));
        pin_exit_edge_to_turn_label.setToolTipText("pin_pad_to_turn_gap_tooltip");
        gridbag.setConstraints(pin_exit_edge_to_turn_label, gridbag_constraints);
        main_panel.add(pin_exit_edge_to_turn_label);
        java.text.NumberFormat number_format = java.text.NumberFormat.getInstance(p_board_frame.get_locale());
        number_format.setMaximumFractionDigits(7);
        this.edge_to_turn_dist_field = new javax.swing.JFormattedTextField(number_format);
        this.edge_to_turn_dist_field.setColumns(5);
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridbag.setConstraints(edge_to_turn_dist_field, gridbag_constraints);
        main_panel.add(edge_to_turn_dist_field);
        edge_to_turn_dist_field.addKeyListener(new EdgeToTurnDistFieldKeyListener());
        edge_to_turn_dist_field.addFocusListener(new EdgeToTurnDistFieldFocusListener());
        
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        separator = new javax.swing.JLabel("----------------------------------------  ");
        gridbag.setConstraints(separator, gridbag_constraints);
        main_panel.add(separator, gridbag_constraints);
        
        // add label and slider for the pull tight region around the cursor.
        
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.RELATIVE;
        javax.swing.JLabel pull_tight_region_label = new javax.swing.JLabel(resources.getString("pull_tight_region"));
        pull_tight_region_label.setToolTipText(resources.getString("pull_tight_region_tooltip"));
        gridbag.setConstraints(pull_tight_region_label, gridbag_constraints);
        main_panel.add(pull_tight_region_label);
        
        this.region_width_field = new javax.swing.JFormattedTextField(number_format);
        this.region_width_field.setColumns(3);
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridbag.setConstraints(region_width_field, gridbag_constraints);
        main_panel.add(region_width_field);
        region_width_field.addKeyListener(new RegionWidthFieldKeyListener());
        region_width_field.addFocusListener(new RegionWidthFieldFocusListener());
        
        this.region_slider = new javax.swing.JSlider();
        region_slider.setMaximum(c_max_slider_value);
        region_slider.addChangeListener(new SliderChangeListener());
        gridbag.setConstraints(region_slider, gridbag_constraints);
        main_panel.add(region_slider);
        
        separator = new javax.swing.JLabel("----------------------------------------  ");
        gridbag.setConstraints(separator, gridbag_constraints);
        main_panel.add(separator, gridbag_constraints);
        
        javax.swing.JButton detail_button = new javax.swing.JButton(resources.getString("detail_parameter"));
        this.detail_listener = new DetailListener();
        detail_button.addActionListener(detail_listener);
        gridbag.setConstraints(detail_button, gridbag_constraints);
        if (this.board_handling.get_routing_board().get_test_level() != board.TestLevel.RELEASE_VERSION)
        {
            main_panel.add(detail_button);
        }
        
        p_board_frame.set_context_sensitive_help(this, "WindowRouteParameter");
        
        this.refresh();
        this.pack();
        this.setResizable(false);
    }
    
    public void dispose()
    {
        detail_window.dispose();
        manual_rule_window.dispose();
        super.dispose();
    }
    
    /**
     * Reads the data of this frame from disk.
     * Returns false, if the reading failed.
     */
    public boolean read(java.io.ObjectInputStream p_object_stream)
    {
        
        boolean read_ok = super.read(p_object_stream);
        if(!read_ok)
        {
            return false;
        }
        read_ok = manual_rule_window.read(p_object_stream);
        if(!read_ok)
        {
            return false;
        }
        read_ok = detail_window.read(p_object_stream);
        if(!read_ok)
        {
            return false;
        }
        this.manual_trace_width_listener.first_time = false;
        this.detail_listener.first_time = false;
        this.refresh();
        return true;
        
    }
    
    /**
     * Saves this frame to disk.
     */
    public void save(java.io.ObjectOutputStream p_object_stream)
    {
        super.save(p_object_stream);
        manual_rule_window.save(p_object_stream);
        detail_window.save(p_object_stream);
    }
    
    /**
     * Recalculates all displayed values
     */
    public void refresh()
    {
        board.AngleRestriction snap_angle = this.board_handling.get_routing_board().rules.get_trace_angle_restriction();
        
        if (snap_angle == board.AngleRestriction.NINETY_DEGREE)
        {
            snap_angle_90_button.setSelected(true);
        }
        else if (snap_angle == board.AngleRestriction.FORTYFIVE_DEGREE)
        {
            snap_angle_45_button.setSelected(true);
        }
        else
        {
            snap_angle_none_button.setSelected(true);
        }
        
        if(this.board_handling.settings.get_is_stitch_route())
        {
            stitch_button.setSelected(true);
        }
        else
        {
            dynamic_button.setSelected(true);
        }
        
        if(this.board_handling.settings.get_manual_rule_selection())
        {
            manual_button.setSelected(true);
            if (this.manual_rule_window != null)
            {
                this.manual_rule_window.setVisible(true);
            }
        }
        else
        {
            automatic_button.setSelected(true);
        }
        
        this.shove_check_box.setSelected(this.board_handling.settings.get_push_enabled());
        this.drag_component_check_box.setSelected(this.board_handling.settings.get_drag_components_enabled());
        this.via_snap_to_smd_center_check_box.setSelected(this.board_handling.settings.get_via_snap_to_smd_center());
        this.ignore_conduction_check_box.setSelected(this.board_handling.get_routing_board().rules.get_ignore_conduction());
        this.hilight_routing_obstacle_check_box.setSelected(this.board_handling.settings.get_hilight_routing_obstacle());
        this.neckdown_check_box.setSelected(this.board_handling.settings.get_automatic_neckdown());
        
        double edge_to_turn_dist = this.board_handling.get_routing_board().rules.get_pin_edge_to_turn_dist();
        edge_to_turn_dist = this.board_handling.coordinate_transform.board_to_user(edge_to_turn_dist);
        this.edge_to_turn_dist_field.setValue(edge_to_turn_dist);
        this.restrict_pin_exit_directions_check_box.setSelected(edge_to_turn_dist > 0);
        
        int region_slider_value = this.board_handling.settings.get_trace_pull_tight_region_width() / c_region_scale_factor;
        region_slider_value = Math.min(region_slider_value, c_max_slider_value);
        region_slider.setValue(region_slider_value);
        region_width_field.setValue(region_slider_value);
        
        if (this.manual_rule_window != null)
        {
            this.manual_rule_window.refresh();
        }
        if (this.detail_window != null)
        {
            this.detail_window.refresh();
        }
    }
    
    public void parent_iconified()
    {
        manual_rule_window.parent_iconified();
        detail_window.parent_iconified();
        super.parent_iconified();
    }
    
    public void parent_deiconified()
    {
        manual_rule_window.parent_deiconified();
        detail_window.parent_deiconified();
        super.parent_deiconified();
    }
    
    private void set_pull_tight_region_width(int p_slider_value)
    {
        int slider_value = Math.max(p_slider_value, 0);
        slider_value = Math.min(p_slider_value, c_max_slider_value);
        int new_tidy_width;
        if (slider_value >= 0.9 * c_max_slider_value)
        {
            p_slider_value = c_max_slider_value;
            new_tidy_width = Integer.MAX_VALUE;
        }
        else
        {
            new_tidy_width = slider_value * c_region_scale_factor;
        }
        region_slider.setValue(slider_value);
        region_width_field.setValue(slider_value);
        board_handling.settings.set_current_pull_tight_region_width(new_tidy_width);
    }
    
    
    private final interactive.BoardHandling board_handling;
    private final java.util.Locale current_locale;
    final WindowManualRules manual_rule_window;
    final WindowRouteDetail detail_window;
    private final javax.swing.JSlider region_slider;
    private final javax.swing.JFormattedTextField region_width_field;
    private final javax.swing.JFormattedTextField edge_to_turn_dist_field;
    
    private final javax.swing.JRadioButton snap_angle_90_button;
    private final javax.swing.JRadioButton snap_angle_45_button;
    private final javax.swing.JRadioButton snap_angle_none_button;
    private final javax.swing.JRadioButton dynamic_button;
    private final javax.swing.JRadioButton stitch_button;
    private final javax.swing.JRadioButton automatic_button;
    private final javax.swing.JRadioButton manual_button ;
    private final javax.swing.JCheckBox shove_check_box;
    private final javax.swing.JCheckBox drag_component_check_box;
    private final javax.swing.JCheckBox ignore_conduction_check_box;
    private final javax.swing.JCheckBox via_snap_to_smd_center_check_box;
    private final javax.swing.JCheckBox hilight_routing_obstacle_check_box;
    private final javax.swing.JCheckBox neckdown_check_box;
    private final javax.swing.JCheckBox restrict_pin_exit_directions_check_box;
    
    private final DetailListener detail_listener;
    private final ManualTraceWidthListener manual_trace_width_listener;
    private boolean key_input_completed = true;
    
    private static final int c_max_slider_value = 999;
    private static final int c_region_scale_factor = 200;
    
    private class SnapAngle90Listener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            if (board_handling.get_routing_board().rules.get_trace_angle_restriction() == board.AngleRestriction.NINETY_DEGREE)
            {
                return;
            }
            Collection<board.Trace> trace_list = board_handling.get_routing_board().get_traces();
            boolean free_angle_traces_found = false;
            for (board.Trace curr_trace : trace_list)
            {
                if (curr_trace instanceof board.PolylineTrace)
                {
                    if (!((board.PolylineTrace)curr_trace).polyline().is_orthogonal())
                    {
                        free_angle_traces_found = true;
                        break;
                    }
                }
            }
            if (free_angle_traces_found)
            {
                java.util.ResourceBundle resources = 
                        java.util.ResourceBundle.getBundle("gui.resources.WindowRouteParameter", current_locale);
                String curr_message = resources.getString("change_snap_angle_90");
                if (!WindowMessage.confirm(curr_message))
                {
                    refresh();
                    return;
                }
            }
            board_handling.set_current_snap_angle(board.AngleRestriction.NINETY_DEGREE);
        }
    }
    
    private class SnapAngle45Listener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            if (board_handling.get_routing_board().rules.get_trace_angle_restriction() == board.AngleRestriction.FORTYFIVE_DEGREE)
            {
                return;
            }
            Collection<board.Trace> trace_list = board_handling.get_routing_board().get_traces();
            boolean free_angle_traces_found = false;
            for (board.Trace curr_trace : trace_list)
            {
                if (curr_trace instanceof board.PolylineTrace)
                {
                    if (!((board.PolylineTrace)curr_trace).polyline().is_multiple_of_45_degree())
                    {
                        free_angle_traces_found = true;
                        break;
                    }
                }
            }
            if (free_angle_traces_found)
            {
                java.util.ResourceBundle resources = 
                        java.util.ResourceBundle.getBundle("gui.resources.WindowRouteParameter", current_locale);
                String curr_message = resources.getString("change_snap_angle_45");
                if (!WindowMessage.confirm(curr_message))
                {
                    refresh();
                    return;
                }
            }
            board_handling.set_current_snap_angle(board.AngleRestriction.FORTYFIVE_DEGREE);
        }
    }
    
    private class SnapAngleNoneListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.set_current_snap_angle(board.AngleRestriction.NONE);
        }
    }
    
    private class DynamicRouteListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.set_stitch_route(false);
        }
    }
    
    private class StitchRouteListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.set_stitch_route(true);
        }
    }
    
    private class DetailListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            if (first_time)
            {
                java.awt.Point location = getLocation();
                detail_window.setLocation((int)location.getX() + 200, (int)location.getY() + 300);
                first_time = false;
            }
            detail_window.setVisible(true);
        }
        private boolean first_time = true;
    }
    
    private class AutomaticTraceWidthListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            manual_rule_window.setVisible(false);
            board_handling.settings.set_manual_tracewidth_selection(false);
        }
    }
    
    private class ManualTraceWidthListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            if (first_time)
            {
                java.awt.Point location = getLocation();
                manual_rule_window.setLocation((int)location.getX() + 200, (int)location.getY() + 200);
                first_time = false;
            }
            manual_rule_window.setVisible(true);
            board_handling.settings.set_manual_tracewidth_selection(true);
        }
        
        boolean first_time = true;
    }
    
    private class ShoveListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.set_push_enabled(shove_check_box.isSelected());
            refresh();
        }
    }
    
    private class ViaSnapToSMDCenterListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.set_via_snap_to_smd_center(via_snap_to_smd_center_check_box.isSelected());
        }
    }
    
    private class IgnoreConductionListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.set_ignore_conduction(ignore_conduction_check_box.isSelected());
        }
    }
    
    private class HilightObstacleListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.set_hilight_routing_obstacle(hilight_routing_obstacle_check_box.isSelected());
        }
    }
    
    private class DragComponentListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.set_drag_components_enabled(drag_component_check_box.isSelected());
            refresh();
        }
    }
    
    private class NeckDownListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.set_automatic_neckdown(neckdown_check_box.isSelected());
        }
    }
    
    
    
    private class RestrictPinExitDirectionsListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            if (restrict_pin_exit_directions_check_box.isSelected())
            {
                rules.BoardRules board_rules = board_handling.get_routing_board().rules;
                double edge_to_turn_dist =
                        board_handling.coordinate_transform.board_to_user(board_rules.get_min_trace_half_width());
                board_handling.set_pin_edge_to_turn_dist(edge_to_turn_dist);
            }
            else
            {
                board_handling.set_pin_edge_to_turn_dist(0);
            }
            refresh();
        }
    }
    
    private class EdgeToTurnDistFieldKeyListener extends java.awt.event.KeyAdapter
    {
        public void keyTyped(java.awt.event.KeyEvent p_evt)
        {
            if (p_evt.getKeyChar() == '\n')
            {
                key_input_completed = true;
                Object input = edge_to_turn_dist_field.getValue();
                if (!(input instanceof Number))
                {
                    return;
                }
                float input_value = ((Number)input).floatValue();
                board_handling.set_pin_edge_to_turn_dist(input_value);
                restrict_pin_exit_directions_check_box.setSelected(input_value > 0);
                refresh();
            }
            else
            {
                key_input_completed = false;
            }
        }
    }
    
    private class EdgeToTurnDistFieldFocusListener implements java.awt.event.FocusListener
    {
        public void focusLost(java.awt.event.FocusEvent p_evt)
        {
            if (!key_input_completed)
            {
                // restore the text field.
                double edge_to_turn_dist = board_handling.get_routing_board().rules.get_pin_edge_to_turn_dist();
                edge_to_turn_dist = board_handling.coordinate_transform.board_to_user(edge_to_turn_dist);
                edge_to_turn_dist_field.setValue(edge_to_turn_dist);
                key_input_completed = true;
            }
        }
        public void focusGained(java.awt.event.FocusEvent p_evt)
        {
        }
    }
    
    
    private class RegionWidthFieldKeyListener extends java.awt.event.KeyAdapter
    {
        public void keyTyped(java.awt.event.KeyEvent p_evt)
        {
            if (p_evt.getKeyChar() == '\n')
            {
                key_input_completed = true;
                Object input = region_width_field.getValue();
                if (!(input instanceof Number))
                {
                    return;
                }
                int input_value = ((Number)input).intValue();
                set_pull_tight_region_width(input_value);
            }
            else
            {
                key_input_completed = false;
            }
        }
    }
    
    private class RegionWidthFieldFocusListener implements java.awt.event.FocusListener
    {
        public void focusLost(java.awt.event.FocusEvent p_evt)
        {
            if (!key_input_completed)
            {
                // restore the text field.
                region_width_field.setValue(region_slider.getValue());
                key_input_completed = true;
            }
        }
        public void focusGained(java.awt.event.FocusEvent p_evt)
        {
        }
    }
    
    
    
    private class SliderChangeListener implements javax.swing.event.ChangeListener
    {
        public void stateChanged(javax.swing.event.ChangeEvent evt)
        {
            set_pull_tight_region_width(region_slider.getValue());
        }
    }
}
