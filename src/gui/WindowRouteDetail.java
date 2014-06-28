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
 * RouteDetailWindow.java
 *
 * Created on 18. November 2004, 07:31
 */
package gui;

import board.BasicBoard;
import board.BoardOutline;

/**
 *  Window handling detail parameters of the interactive routing.
 *
 * @author  Alfons Wirtz
 */
public class WindowRouteDetail extends BoardSavableSubWindow
{

    /** Creates a new instance of RouteDetailWindow */
    public WindowRouteDetail(BoardFrame p_board_frame)
    {
        this.board_handling = p_board_frame.board_panel.board_handling;
        java.util.ResourceBundle resources =
                java.util.ResourceBundle.getBundle("gui.resources.WindowRouteDetail", p_board_frame.get_locale());
        this.setTitle(resources.getString("title"));

        // create main panel

        final javax.swing.JPanel main_panel = new javax.swing.JPanel();
        getContentPane().add(main_panel);
        java.awt.GridBagLayout gridbag = new java.awt.GridBagLayout();
        main_panel.setLayout(gridbag);
        java.awt.GridBagConstraints gridbag_constraints = new java.awt.GridBagConstraints();
        gridbag_constraints.anchor = java.awt.GridBagConstraints.WEST;
        gridbag_constraints.insets = new java.awt.Insets(5, 10, 5, 10);

        // add label and button group for the clearance compensation.

        javax.swing.JLabel clearance_compensation_label = new javax.swing.JLabel(resources.getString("clearance_compensation"));
        clearance_compensation_label.setToolTipText(resources.getString("clearance_compensation_tooltip"));

        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.RELATIVE;
        gridbag_constraints.gridheight = 2;
        gridbag.setConstraints(clearance_compensation_label, gridbag_constraints);
        main_panel.add(clearance_compensation_label);

        this.on_button = new javax.swing.JRadioButton(resources.getString("on"));
        this.off_button = new javax.swing.JRadioButton(resources.getString("off"));

        on_button.addActionListener(new CompensationOnListener());
        off_button.addActionListener(new CompensationOffListener());

        javax.swing.ButtonGroup clearance_compensation_button_group = new javax.swing.ButtonGroup();
        clearance_compensation_button_group.add(on_button);
        clearance_compensation_button_group.add(off_button);
        off_button.setSelected(true);

        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridbag_constraints.gridheight = 1;
        gridbag.setConstraints(on_button, gridbag_constraints);
        main_panel.add(on_button, gridbag_constraints);
        gridbag.setConstraints(off_button, gridbag_constraints);
        main_panel.add(off_button, gridbag_constraints);

        javax.swing.JLabel separator = new javax.swing.JLabel("  ----------------------------------------  ");
        gridbag.setConstraints(separator, gridbag_constraints);
        main_panel.add(separator, gridbag_constraints);

        // add label and slider for the pull tight accuracy.

        javax.swing.JLabel pull_tight_accuracy_label = new javax.swing.JLabel(resources.getString("pull_tight_accuracy"));
        pull_tight_accuracy_label.setToolTipText(resources.getString("pull_tight_accuracy_tooltip"));
        gridbag_constraints.insets = new java.awt.Insets(5, 10, 5, 10);
        gridbag.setConstraints(pull_tight_accuracy_label, gridbag_constraints);
        main_panel.add(pull_tight_accuracy_label);

        this.accuracy_slider = new javax.swing.JSlider();
        accuracy_slider.setMaximum(c_max_slider_value);
        accuracy_slider.addChangeListener(new SliderChangeListener());
        gridbag.setConstraints(accuracy_slider, gridbag_constraints);
        main_panel.add(accuracy_slider);

        separator = new javax.swing.JLabel("  ----------------------------------------  ");
        gridbag.setConstraints(separator, gridbag_constraints);
        main_panel.add(separator, gridbag_constraints);

        // add switch to define, if keepout is generated outside the outline.

        this.outline_keepout_check_box = new javax.swing.JCheckBox(resources.getString("keepout_outside_outline"));
        this.outline_keepout_check_box.setSelected(false);
        this.outline_keepout_check_box.addActionListener(new OutLineKeepoutListener());
        gridbag.setConstraints(outline_keepout_check_box, gridbag_constraints);
        this.outline_keepout_check_box.setToolTipText(resources.getString("keepout_outside_outline_tooltip"));
        main_panel.add(outline_keepout_check_box, gridbag_constraints);

        separator = new javax.swing.JLabel();
        gridbag.setConstraints(separator, gridbag_constraints);
        main_panel.add(separator, gridbag_constraints);

        this.refresh();
        this.pack();
        this.setResizable(false);
    }

    /**
     * Recalculates all displayed values
     */
    public void refresh()
    {
        if (this.board_handling.get_routing_board().search_tree_manager.is_clearance_compensation_used())
        {
            this.on_button.setSelected(true);
        }
        else
        {
            this.off_button.setSelected(true);
        }
        BoardOutline outline = this.board_handling.get_routing_board().get_outline();
        if (outline != null)
        {
            this.outline_keepout_check_box.setSelected(outline.keepout_outside_outline_generated());
        }
        int accuracy_slider_value = c_max_slider_value - this.board_handling.settings.get_trace_pull_tight_accuracy() / c_accuracy_scale_factor + 1;
        accuracy_slider.setValue(accuracy_slider_value);
    }
    private final interactive.BoardHandling board_handling;
    private final javax.swing.JSlider accuracy_slider;
    private final javax.swing.JRadioButton on_button;
    private final javax.swing.JRadioButton off_button;
    private final javax.swing.JCheckBox outline_keepout_check_box;
    private static final int c_max_slider_value = 100;
    private static final int c_accuracy_scale_factor = 20;

    private class CompensationOnListener implements java.awt.event.ActionListener
    {

        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.set_clearance_compensation(true);
        }
    }

    private class CompensationOffListener implements java.awt.event.ActionListener
    {

        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.set_clearance_compensation(false);
        }
    }

    private class SliderChangeListener implements javax.swing.event.ChangeListener
    {

        public void stateChanged(javax.swing.event.ChangeEvent evt)
        {
            int new_accurracy = (c_max_slider_value - accuracy_slider.getValue() + 1) * c_accuracy_scale_factor;
            board_handling.settings.set_current_pull_tight_accuracy(new_accurracy);
        }
    }

    private class OutLineKeepoutListener implements java.awt.event.ActionListener
    {

        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            if (board_handling.is_board_read_only())
            {
                return;
            }
            BoardOutline outline = board_handling.get_routing_board().get_outline();
            if (outline != null)
            {
                outline.generate_keepout_outside(outline_keepout_check_box.isSelected());
            }
        }
    }
}
