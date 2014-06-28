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
 * WindowAutorouteDetailParameter.java
 *
 * Created on 25. Juli 2006, 08:17
 *
 */
package gui;

/**
 *
 * @author Alfons Wirtz
 */
public class WindowAutorouteDetailParameter extends BoardSavableSubWindow
{

    /** Creates a new instance of WindowAutorouteDetailParameter */
    public WindowAutorouteDetailParameter(BoardFrame p_board_frame)
    {
        this.board_handling = p_board_frame.board_panel.board_handling;
        java.util.ResourceBundle resources =
                java.util.ResourceBundle.getBundle("gui.resources.WindowAutorouteParameter", p_board_frame.get_locale());
        this.setTitle(resources.getString("detail_autoroute_parameter"));

        // create main panel

        final javax.swing.JPanel main_panel = new javax.swing.JPanel();
        getContentPane().add(main_panel);
        java.awt.GridBagLayout gridbag = new java.awt.GridBagLayout();
        main_panel.setLayout(gridbag);
        java.awt.GridBagConstraints gridbag_constraints = new java.awt.GridBagConstraints();
        gridbag_constraints.anchor = java.awt.GridBagConstraints.WEST;
        gridbag_constraints.insets = new java.awt.Insets(5, 10, 5, 10);

        // add label and number field for the via costs.

        gridbag_constraints.gridwidth = 2;
        javax.swing.JLabel via_cost_label = new javax.swing.JLabel(resources.getString("via_costs"));
        gridbag.setConstraints(via_cost_label, gridbag_constraints);
        main_panel.add(via_cost_label);

        java.text.NumberFormat number_format = java.text.NumberFormat.getIntegerInstance(p_board_frame.get_locale());
        this.via_cost_field = new javax.swing.JFormattedTextField(number_format);
        this.via_cost_field.setColumns(3);
        this.via_cost_field.addKeyListener(new ViaCostFieldKeyListener());
        this.via_cost_field.addFocusListener(new ViaCostFieldFocusListener());
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridbag.setConstraints(via_cost_field, gridbag_constraints);
        main_panel.add(via_cost_field);

        this.plane_via_cost_field = new javax.swing.JFormattedTextField(number_format);
        this.plane_via_cost_field.setColumns(3);
        this.plane_via_cost_field.addKeyListener(new PlaneViaCostFieldKeyListener());
        this.plane_via_cost_field.addFocusListener(new PlaneViaCostFieldFocusListener());

        gridbag_constraints.gridwidth = 2;
        javax.swing.JLabel plane_via_cost_label = new javax.swing.JLabel(resources.getString("plane_via_costs"));
        gridbag.setConstraints(plane_via_cost_label, gridbag_constraints);
        main_panel.add(plane_via_cost_label);
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridbag.setConstraints(plane_via_cost_field, gridbag_constraints);
        main_panel.add(plane_via_cost_field);

        // add label and number field for the start pass no.

        gridbag_constraints.gridwidth = 2;
        javax.swing.JLabel start_pass_label = new javax.swing.JLabel(resources.getString("start_pass"));
        gridbag.setConstraints(start_pass_label, gridbag_constraints);
        main_panel.add(start_pass_label);

        start_pass_no = new javax.swing.JFormattedTextField(number_format);
        start_pass_no.setColumns(2);
        this.start_pass_no.addKeyListener(new StartPassFieldKeyListener());
        this.start_pass_no.addFocusListener(new StartPassFieldFocusListener());
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridbag.setConstraints(start_pass_no, gridbag_constraints);
        main_panel.add(start_pass_no);

        // add label and number field for the start ripup costs.

        gridbag_constraints.gridwidth = 2;
        javax.swing.JLabel start_ripup_costs_label = new javax.swing.JLabel();
        start_ripup_costs_label.setText(resources.getString("start_ripup_costs"));
        gridbag.setConstraints(start_ripup_costs_label, gridbag_constraints);
        main_panel.add(start_ripup_costs_label);

        start_ripup_costs = new javax.swing.JFormattedTextField(number_format);
        start_ripup_costs.setColumns(3);
        this.start_ripup_costs.addKeyListener(new StartRipupCostFieldKeyListener());
        this.start_ripup_costs.addFocusListener(new StartRipupCostFieldFocusListener());
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridbag.setConstraints(start_ripup_costs, gridbag_constraints);
        main_panel.add(start_ripup_costs);



        // add label and combo box for the router speed (if the speed is set to slow, free angle geometry
        // is used also in the 45 and 90 degree modes.
        this.speed_fast = resources.getString("fast");
        this.speed_slow = resources.getString("slow");
        speed_combo_box = new javax.swing.JComboBox();
        speed_combo_box.addItem(this.speed_fast);
        speed_combo_box.addItem(this.speed_slow);
        speed_combo_box.addActionListener(new SpeedListener());

        if (this.board_handling.get_routing_board().get_test_level() != board.TestLevel.RELEASE_VERSION)
        {
            gridbag_constraints.gridwidth = 2;
            javax.swing.JLabel speed_label = new javax.swing.JLabel();
            speed_label.setText(resources.getString("speed"));
            gridbag.setConstraints(speed_label, gridbag_constraints);
            main_panel.add(speed_label);

            gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
            gridbag.setConstraints(speed_combo_box, gridbag_constraints);
            main_panel.add(speed_combo_box);
        }


        javax.swing.JLabel separator = new javax.swing.JLabel("----------------------------------------------------------------  ");
        gridbag.setConstraints(separator, gridbag_constraints);
        main_panel.add(separator, gridbag_constraints);

        // add label and number field for the trace costs on each layer.

        gridbag_constraints.gridwidth = 3;
        javax.swing.JLabel layer_label = new javax.swing.JLabel(resources.getString("trace_costs_on_layer"));
        gridbag.setConstraints(layer_label, gridbag_constraints);
        main_panel.add(layer_label);

        javax.swing.JLabel pref_dir_label = new javax.swing.JLabel(resources.getString("in_preferred_direction"));
        gridbag.setConstraints(pref_dir_label, gridbag_constraints);
        main_panel.add(pref_dir_label);

        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        javax.swing.JLabel against_pref_dir_label = new javax.swing.JLabel(resources.getString("against_preferred_direction"));
        gridbag.setConstraints(against_pref_dir_label, gridbag_constraints);
        main_panel.add(against_pref_dir_label);

        board.LayerStructure layer_structure = this.board_handling.get_routing_board().layer_structure;
        int signal_layer_count = layer_structure.signal_layer_count();
        layer_name_arr = new javax.swing.JLabel[signal_layer_count];
        preferred_direction_trace_cost_arr = new javax.swing.JFormattedTextField[signal_layer_count];
        against_preferred_direction_trace_cost_arr = new javax.swing.JFormattedTextField[signal_layer_count];
        preferred_direction_trace_costs_input_completed = new boolean[signal_layer_count];
        against_preferred_direction_trace_costs_input_completed = new boolean[signal_layer_count];
        number_format = java.text.NumberFormat.getInstance(p_board_frame.get_locale());
        number_format.setMaximumFractionDigits(2);
        final int TEXT_FIELD_LENGTH = 2;
        for (int i = 0; i < signal_layer_count; ++i)
        {
            layer_name_arr[i] = new javax.swing.JLabel();
            board.Layer curr_signal_layer = layer_structure.get_signal_layer(i);
            layer_name_arr[i].setText(curr_signal_layer.name);
            gridbag_constraints.gridwidth = 3;
            gridbag.setConstraints(layer_name_arr[i], gridbag_constraints);
            main_panel.add(layer_name_arr[i]);
            preferred_direction_trace_cost_arr[i] = new javax.swing.JFormattedTextField(number_format);
            preferred_direction_trace_cost_arr[i].setColumns(TEXT_FIELD_LENGTH);
            preferred_direction_trace_cost_arr[i].addKeyListener(new PreferredDirectionTraceCostKeyListener(i));
            preferred_direction_trace_cost_arr[i].addFocusListener(new PreferredDirectionTraceCostFocusListener(i));
            gridbag.setConstraints(preferred_direction_trace_cost_arr[i], gridbag_constraints);
            main_panel.add(preferred_direction_trace_cost_arr[i]);
            against_preferred_direction_trace_cost_arr[i] = new javax.swing.JFormattedTextField(number_format);
            against_preferred_direction_trace_cost_arr[i].setColumns(TEXT_FIELD_LENGTH);
            against_preferred_direction_trace_cost_arr[i].addKeyListener(new AgainstPreferredDirectionTraceCostKeyListener(i));
            against_preferred_direction_trace_cost_arr[i].addFocusListener(new AgainstPreferredDirectionTraceCostFocusListener(i));
            gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
            gridbag.setConstraints(against_preferred_direction_trace_cost_arr[i], gridbag_constraints);
            main_panel.add(against_preferred_direction_trace_cost_arr[i]);
            preferred_direction_trace_costs_input_completed[i] = true;
            against_preferred_direction_trace_costs_input_completed[i] = true;
        }

        p_board_frame.set_context_sensitive_help(this, "WindowAutorouteDetailParameter");

        this.refresh();
        this.pack();
        this.setResizable(false);
    }

    /**
     * Recalculates all displayed values
     */
    public void refresh()
    {
        interactive.AutorouteSettings settings = this.board_handling.settings.autoroute_settings;
        board.LayerStructure layer_structure = this.board_handling.get_routing_board().layer_structure;
        this.via_cost_field.setValue(settings.get_via_costs());
        this.plane_via_cost_field.setValue(settings.get_plane_via_costs());
        this.start_ripup_costs.setValue(settings.get_start_ripup_costs());
        this.start_pass_no.setValue(settings.get_pass_no());
        for (int i = 0; i < preferred_direction_trace_cost_arr.length; ++i)
        {
            this.preferred_direction_trace_cost_arr[i].setValue(settings.get_preferred_direction_trace_costs(layer_structure.get_layer_no(i)));
        }
        for (int i = 0; i < against_preferred_direction_trace_cost_arr.length; ++i)
        {
            this.against_preferred_direction_trace_cost_arr[i].setValue(settings.get_against_preferred_direction_trace_costs(layer_structure.get_layer_no(i)));
        }
    }
    private final interactive.BoardHandling board_handling;
    private final javax.swing.JFormattedTextField via_cost_field;
    private final javax.swing.JFormattedTextField plane_via_cost_field;
    private final javax.swing.JFormattedTextField start_ripup_costs;
    private final javax.swing.JFormattedTextField start_pass_no;
    private final javax.swing.JComboBox speed_combo_box;
    private final String speed_fast;
    private final String speed_slow;
    private final javax.swing.JLabel[] layer_name_arr;
    private final javax.swing.JFormattedTextField[] preferred_direction_trace_cost_arr;
    private final javax.swing.JFormattedTextField[] against_preferred_direction_trace_cost_arr;
    private boolean via_cost_input_completed = true;
    private boolean plane_via_cost_input_completed = true;
    private boolean start_ripup_cost_input_completed = true;
    private final boolean[] preferred_direction_trace_costs_input_completed;
    private final boolean[] against_preferred_direction_trace_costs_input_completed;

    private class ViaCostFieldKeyListener extends java.awt.event.KeyAdapter
    {

        public void keyTyped(java.awt.event.KeyEvent p_evt)
        {
            if (p_evt.getKeyChar() == '\n')
            {
                int old_value = board_handling.settings.autoroute_settings.get_via_costs();
                Object input = via_cost_field.getValue();
                int input_value;
                if (input instanceof Number)
                {
                    input_value = ((Number) input).intValue();
                    if (input_value <= 0)
                    {
                        input_value = 1;
                        via_cost_field.setValue(input_value);
                    }
                } else
                {
                    input_value = old_value;
                    via_cost_field.setValue(old_value);
                }
                board_handling.settings.autoroute_settings.set_via_costs(input_value);
                via_cost_field.setValue(input_value);
                via_cost_input_completed = true;

            } else
            {
                via_cost_input_completed = false;
            }
        }
    }

    private class ViaCostFieldFocusListener implements java.awt.event.FocusListener
    {

        public void focusLost(java.awt.event.FocusEvent p_evt)
        {
            if (!via_cost_input_completed)
            {
                via_cost_input_completed = true;
                refresh();
            }
        }

        public void focusGained(java.awt.event.FocusEvent p_evt)
        {
        }
    }

    private class PlaneViaCostFieldKeyListener extends java.awt.event.KeyAdapter
    {

        public void keyTyped(java.awt.event.KeyEvent p_evt)
        {
            if (p_evt.getKeyChar() == '\n')
            {
                int old_value = board_handling.settings.autoroute_settings.get_plane_via_costs();
                Object input = plane_via_cost_field.getValue();
                int input_value;
                if (input instanceof Number)
                {
                    input_value = ((Number) input).intValue();
                    if (input_value <= 0)
                    {
                        input_value = 1;
                        plane_via_cost_field.setValue(input_value);
                    }
                } else
                {
                    input_value = old_value;
                    plane_via_cost_field.setValue(old_value);
                }
                board_handling.settings.autoroute_settings.set_plane_via_costs(input_value);
                plane_via_cost_field.setValue(input_value);
                plane_via_cost_input_completed = true;

            } else
            {
                plane_via_cost_input_completed = false;
            }
        }
    }

    private class PlaneViaCostFieldFocusListener implements java.awt.event.FocusListener
    {

        public void focusLost(java.awt.event.FocusEvent p_evt)
        {
            if (!plane_via_cost_input_completed)
            {
                plane_via_cost_input_completed = true;
                refresh();
            }
        }

        public void focusGained(java.awt.event.FocusEvent p_evt)
        {
        }
    }

    private class StartRipupCostFieldKeyListener extends java.awt.event.KeyAdapter
    {

        public void keyTyped(java.awt.event.KeyEvent p_evt)
        {
            if (p_evt.getKeyChar() == '\n')
            {
                int old_value = board_handling.settings.autoroute_settings.get_start_ripup_costs();
                Object input = start_ripup_costs.getValue();
                int input_value;
                if (input instanceof Number)
                {
                    input_value = ((Number) input).intValue();
                    if (input_value <= 0)
                    {
                        input_value = 1;
                    }
                } else
                {
                    input_value = old_value;
                }
                board_handling.settings.autoroute_settings.set_start_ripup_costs(input_value);
                start_ripup_costs.setValue(input_value);
                start_ripup_cost_input_completed = true;
            } else
            {
                start_ripup_cost_input_completed = false;
            }
        }
    }

    private class StartRipupCostFieldFocusListener implements java.awt.event.FocusListener
    {

        public void focusLost(java.awt.event.FocusEvent p_evt)
        {
            if (!start_ripup_cost_input_completed)
            {
                start_ripup_cost_input_completed = true;
                refresh();
            }
        }

        public void focusGained(java.awt.event.FocusEvent p_evt)
        {
        }
    }

    private class StartPassFieldKeyListener extends java.awt.event.KeyAdapter
    {

        public void keyTyped(java.awt.event.KeyEvent p_evt)
        {
            if (p_evt.getKeyChar() == '\n')
            {
                int old_value = board_handling.settings.autoroute_settings.get_pass_no();
                Object input = start_pass_no.getValue();
                int input_value;
                if (input instanceof Number)
                {
                    input_value = ((Number) input).intValue();
                    if (input_value < 1)
                    {
                        input_value = 1;
                    }
                    if (input_value > 99)
                    {
                        input_value = 99;
                    }
                } else
                {
                    input_value = old_value;
                }
                board_handling.settings.autoroute_settings.set_pass_no(input_value);
                start_pass_no.setValue(input_value);

            }
        }
    }

    private class StartPassFieldFocusListener implements java.awt.event.FocusListener
    {

        public void focusLost(java.awt.event.FocusEvent p_evt)
        {
            if (!start_ripup_cost_input_completed)
            {
                refresh();
            }
        }

        public void focusGained(java.awt.event.FocusEvent p_evt)
        {
        }
    }

    private class SpeedListener implements java.awt.event.ActionListener
    {

        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            boolean old_is_slow = board_handling.get_routing_board().rules.get_slow_autoroute_algorithm();
            boolean new_is_slow = speed_combo_box.getSelectedItem() == speed_slow;
            if (old_is_slow != new_is_slow)
            {
                board_handling.get_routing_board().rules.set_slow_autoroute_algorithm(new_is_slow);
                board_handling.get_routing_board().search_tree_manager.reset_compensated_trees();
            }
        }
    }

    private class PreferredDirectionTraceCostKeyListener extends java.awt.event.KeyAdapter
    {

        public PreferredDirectionTraceCostKeyListener(int p_layer_no)
        {
            this.signal_layer_no = p_layer_no;
        }

        public void keyTyped(java.awt.event.KeyEvent p_evt)
        {
            if (p_evt.getKeyChar() == '\n')
            {
                int curr_layer_no = board_handling.get_routing_board().layer_structure.get_layer_no(this.signal_layer_no);
                double old_value = board_handling.settings.autoroute_settings.get_preferred_direction_trace_costs(curr_layer_no);
                Object input = preferred_direction_trace_cost_arr[this.signal_layer_no].getValue();
                double input_value;
                if (input instanceof Number)
                {
                    input_value = ((Number) input).doubleValue();
                    if (input_value <= 0)
                    {
                        input_value = old_value;
                    }
                } else
                {
                    input_value = old_value;
                }
                board_handling.settings.autoroute_settings.set_preferred_direction_trace_costs(curr_layer_no, input_value);
                preferred_direction_trace_cost_arr[this.signal_layer_no].setValue(input_value);
                preferred_direction_trace_costs_input_completed[this.signal_layer_no] = true;

            } else
            {
                preferred_direction_trace_costs_input_completed[this.signal_layer_no] = false;
            }
        }
        private final int signal_layer_no;
    }

    private class PreferredDirectionTraceCostFocusListener implements java.awt.event.FocusListener
    {

        public PreferredDirectionTraceCostFocusListener(int p_layer_no)
        {
            this.signal_layer_no = p_layer_no;
        }

        public void focusLost(java.awt.event.FocusEvent p_evt)
        {
            if (!preferred_direction_trace_costs_input_completed[this.signal_layer_no])
            {
                start_ripup_cost_input_completed = true;
                refresh();
            }
        }

        public void focusGained(java.awt.event.FocusEvent p_evt)
        {
        }
        private final int signal_layer_no;
    }

    private class AgainstPreferredDirectionTraceCostKeyListener extends java.awt.event.KeyAdapter
    {

        public AgainstPreferredDirectionTraceCostKeyListener(int p_layer_no)
        {
            this.signal_layer_no = p_layer_no;
        }

        public void keyTyped(java.awt.event.KeyEvent p_evt)
        {
            if (p_evt.getKeyChar() == '\n')
            {
                int curr_layer_no = board_handling.get_routing_board().layer_structure.get_layer_no(this.signal_layer_no);
                double old_value = board_handling.settings.autoroute_settings.get_against_preferred_direction_trace_costs(curr_layer_no);
                Object input = against_preferred_direction_trace_cost_arr[this.signal_layer_no].getValue();
                double input_value;
                if (input instanceof Number)
                {
                    input_value = ((Number) input).doubleValue();
                    if (input_value <= 0)
                    {
                        input_value = old_value;
                    }
                } else
                {
                    input_value = old_value;
                }
                board_handling.settings.autoroute_settings.set_against_preferred_direction_trace_costs(curr_layer_no, input_value);
                against_preferred_direction_trace_cost_arr[this.signal_layer_no].setValue(input_value);
                against_preferred_direction_trace_costs_input_completed[this.signal_layer_no] = true;

            } else
            {
                against_preferred_direction_trace_costs_input_completed[this.signal_layer_no] = false;
            }
        }
        private final int signal_layer_no;
    }

    private class AgainstPreferredDirectionTraceCostFocusListener implements java.awt.event.FocusListener
    {

        public AgainstPreferredDirectionTraceCostFocusListener(int p_layer_no)
        {
            this.signal_layer_no = p_layer_no;
        }

        public void focusLost(java.awt.event.FocusEvent p_evt)
        {
            if (!against_preferred_direction_trace_costs_input_completed[this.signal_layer_no])
            {
                start_ripup_cost_input_completed = true;
                refresh();
            }
        }

        public void focusGained(java.awt.event.FocusEvent p_evt)
        {
        }
        private final int signal_layer_no;
    }
}
