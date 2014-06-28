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
 * TraceWidthWindow.java
 *
 * Created on 18. November 2004, 09:08
 */
package gui;

/**
 * Used for manual choice of trace widths in interactive routing.
 *
 * @author  Alfons Wirtz
 */
public class WindowManualRules extends BoardSavableSubWindow
{

    /** Creates a new instance of TraceWidthWindow */
    public WindowManualRules(BoardFrame p_board_frame)
    {
        this.board_handling = p_board_frame.board_panel.board_handling;
        java.util.ResourceBundle resources =
                java.util.ResourceBundle.getBundle("gui.resources.WindowManualRule", p_board_frame.get_locale());
        this.setTitle(resources.getString("title"));

        // create main panel

        final javax.swing.JPanel main_panel = new javax.swing.JPanel();
        getContentPane().add(main_panel);
        java.awt.GridBagLayout gridbag = new java.awt.GridBagLayout();
        main_panel.setLayout(gridbag);
        java.awt.GridBagConstraints gridbag_constraints = new java.awt.GridBagConstraints();
        gridbag_constraints.insets = new java.awt.Insets(5, 10, 5, 10);
        gridbag_constraints.anchor = java.awt.GridBagConstraints.WEST;

        javax.swing.JLabel via_rule_label = new javax.swing.JLabel(resources.getString("via_rule"));
        gridbag_constraints.gridwidth = 2;
        gridbag.setConstraints(via_rule_label, gridbag_constraints);
        main_panel.add(via_rule_label);

        board.RoutingBoard routing_board = this.board_handling.get_routing_board();
        this.via_rule_combo_box = new javax.swing.JComboBox(routing_board.rules.via_rules);
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridbag.setConstraints(this.via_rule_combo_box, gridbag_constraints);
        main_panel.add(this.via_rule_combo_box);
        this.via_rule_combo_box.addActionListener(new ViaRuleComboBoxListener());

        javax.swing.JLabel class_label = new javax.swing.JLabel(resources.getString("trace_clearance_class"));
        gridbag_constraints.gridwidth = 2;
        gridbag.setConstraints(class_label, gridbag_constraints);
        main_panel.add(class_label);

        this.clearance_combo_box = new ComboBoxClearance(routing_board.rules.clearance_matrix);
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridbag.setConstraints(this.clearance_combo_box, gridbag_constraints);
        main_panel.add(this.clearance_combo_box);
        this.clearance_combo_box.addActionListener(new ClearanceComboBoxListener());

        javax.swing.JLabel separator = new javax.swing.JLabel("  ----------------------------------------  ");
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridbag.setConstraints(separator, gridbag_constraints);
        main_panel.add(separator, gridbag_constraints);

        javax.swing.JLabel width_label = new javax.swing.JLabel(resources.getString("trace_width"));
        gridbag_constraints.gridwidth = 2;
        gridbag.setConstraints(width_label, gridbag_constraints);
        main_panel.add(width_label);
        java.text.NumberFormat number_format = java.text.NumberFormat.getInstance(p_board_frame.get_locale());
        number_format.setMaximumFractionDigits(7);
        this.trace_width_field = new javax.swing.JFormattedTextField(number_format);
        this.trace_width_field.setColumns(7);
        int curr_half_width = this.board_handling.settings.get_manual_trace_half_width(0);
        this.set_trace_width_field(curr_half_width);
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridbag.setConstraints(trace_width_field, gridbag_constraints);
        main_panel.add(trace_width_field);
        trace_width_field.addKeyListener(new TraceWidthFieldKeyListener());
        trace_width_field.addFocusListener(new TraceWidthFieldFocusListener());

        javax.swing.JLabel layer_label = new javax.swing.JLabel(resources.getString("on_layer"));
        gridbag_constraints.gridwidth = 2;
        gridbag.setConstraints(layer_label, gridbag_constraints);
        main_panel.add(layer_label);

        this.layer_combo_box =
                new ComboBoxLayer(this.board_handling.get_routing_board().layer_structure, p_board_frame.get_locale());
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridbag.setConstraints(this.layer_combo_box, gridbag_constraints);
        main_panel.add(this.layer_combo_box);
        this.layer_combo_box.addActionListener(new LayerComboBoxListener());

        javax.swing.JLabel empty_label = new javax.swing.JLabel();
        gridbag.setConstraints(empty_label, gridbag_constraints);
        main_panel.add(empty_label);

        p_board_frame.set_context_sensitive_help(this, "WindowManualRules");

        this.pack();
        this.setResizable(false);
    }

    /**
     * Recalculates the values in the trace width fields.
     */
    public void refresh()
    {
        board.RoutingBoard routing_board = board_handling.get_routing_board();
        javax.swing.ComboBoxModel new_model = new javax.swing.DefaultComboBoxModel(routing_board.rules.via_rules);
        this.via_rule_combo_box.setModel(new_model);
        rules.ClearanceMatrix clearance_matrix = board_handling.get_routing_board().rules.clearance_matrix;
        if (this.clearance_combo_box.get_class_count() != routing_board.rules.clearance_matrix.get_class_count())
        {
            this.clearance_combo_box.adjust(clearance_matrix);
        }
        this.clearance_combo_box.setSelectedIndex(board_handling.settings.get_manual_trace_clearance_class());
        int via_rule_index = board_handling.settings.get_manual_via_rule_index();
        if (via_rule_index < this.via_rule_combo_box.getItemCount())
        {
            this.via_rule_combo_box.setSelectedIndex(board_handling.settings.get_manual_via_rule_index());
        }
        this.set_selected_layer(this.layer_combo_box.get_selected_layer());
        this.repaint();
    }

    public void set_trace_width_field(int p_half_width)
    {
        if (p_half_width < 0)
        {
            this.trace_width_field.setText("");
        }
        else
        {
            Float trace_width = (float) board_handling.coordinate_transform.board_to_user(2 * p_half_width);
            this.trace_width_field.setValue(trace_width);
        }
    }

    /**
     * Sets the selected layer to p_layer.
     */
    private void set_selected_layer(ComboBoxLayer.Layer p_layer)
    {
        int curr_half_width;
        if (p_layer.index == ComboBoxLayer.ALL_LAYER_INDEX)
        {
            // check if the half width is layer_dependent.
            boolean trace_widths_layer_dependent = false;
            int first_half_width = this.board_handling.settings.get_manual_trace_half_width(0);
            for (int i = 1; i < this.board_handling.get_layer_count(); ++i)
            {
                if (this.board_handling.settings.get_manual_trace_half_width(i) != first_half_width)
                {
                    trace_widths_layer_dependent = true;
                    break;
                }
            }
            if (trace_widths_layer_dependent)
            {
                curr_half_width = -1;
            }
            else
            {
                curr_half_width = first_half_width;
            }
        }
        else if (p_layer.index == ComboBoxLayer.INNER_LAYER_INDEX)
        {
            // check if the half width is layer_dependent on the inner layers.
            boolean trace_widths_layer_dependent = false;
            int first_half_width = this.board_handling.settings.get_manual_trace_half_width(1);
            for (int i = 2; i < this.board_handling.get_layer_count() - 1; ++i)
            {
                if (this.board_handling.settings.get_manual_trace_half_width(i) != first_half_width)
                {
                    trace_widths_layer_dependent = true;
                    break;
                }
            }
            if (trace_widths_layer_dependent)
            {
                curr_half_width = -1;
            }
            else
            {
                curr_half_width = first_half_width;
            }
        }
        else
        {
            curr_half_width = this.board_handling.settings.get_manual_trace_half_width(p_layer.index);
        }
        set_trace_width_field(curr_half_width);
    }

    private final interactive.BoardHandling board_handling;
    private final ComboBoxLayer layer_combo_box;
    private final ComboBoxClearance clearance_combo_box;
    private final javax.swing.JComboBox via_rule_combo_box;
    private final javax.swing.JFormattedTextField trace_width_field;
    private boolean key_input_completed = true;
    private static final int max_slider_value = 15000;
    private static double scale_factor = 1;

    private class LayerComboBoxListener implements java.awt.event.ActionListener
    {

        public void actionPerformed(java.awt.event.ActionEvent evt)
        {
            ComboBoxLayer.Layer new_selected_layer = layer_combo_box.get_selected_layer();
            set_selected_layer(new_selected_layer);
        }
    }

    private class ClearanceComboBoxListener implements java.awt.event.ActionListener
    {

        public void actionPerformed(java.awt.event.ActionEvent evt)
        {
            int new_index = clearance_combo_box.get_selected_class_index();
            board_handling.settings.set_manual_trace_clearance_class(new_index);
        }
    }

    private class ViaRuleComboBoxListener implements java.awt.event.ActionListener
    {

        public void actionPerformed(java.awt.event.ActionEvent evt)
        {
            int new_index = via_rule_combo_box.getSelectedIndex();
            board_handling.settings.set_manual_via_rule_index(new_index);
        }
    }

    private class TraceWidthFieldKeyListener extends java.awt.event.KeyAdapter
    {

        public void keyTyped(java.awt.event.KeyEvent p_evt)
        {
            if (p_evt.getKeyChar() == '\n')
            {
                key_input_completed = true;
                Object input = trace_width_field.getValue();
                if (!(input instanceof Number))
                {
                    return;
                }
                double input_value = ((Number) input).doubleValue();
                if (input_value <= 0)
                {
                    return;
                }
                double board_value = board_handling.coordinate_transform.user_to_board(input_value);
                int new_half_width = (int) Math.round(0.5 * board_value);
                board_handling.set_manual_trace_half_width(layer_combo_box.get_selected_layer().index, new_half_width);
                set_trace_width_field(new_half_width);
            }
            else
            {
                key_input_completed = false;
            }
        }
    }

    private class TraceWidthFieldFocusListener implements java.awt.event.FocusListener
    {

        public void focusLost(java.awt.event.FocusEvent p_evt)
        {
            if (!key_input_completed)
            {
                // restore the text field.
                set_selected_layer(layer_combo_box.get_selected_layer());
                key_input_completed = true;
            }
        }

        public void focusGained(java.awt.event.FocusEvent p_evt)
        {
        }
    }
}
