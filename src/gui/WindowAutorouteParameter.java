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
 * WindowAutorouteParameter.java
 *
 * Created on 24. Juli 2006, 07:20
 *
 */
package gui;

/**
 * Window handling parameters of the automatic routing.
 *
 * @author Alfons Wirtz
 */
public class WindowAutorouteParameter extends BoardSavableSubWindow
{

    /** Creates a new instance of WindowAutorouteParameter */
    public WindowAutorouteParameter(BoardFrame p_board_frame)
    {
        this.board_handling = p_board_frame.board_panel.board_handling;
        java.util.ResourceBundle resources =
                java.util.ResourceBundle.getBundle("gui.resources.WindowAutorouteParameter", p_board_frame.get_locale());
        this.setTitle(resources.getString("title"));

        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // create main panel

        final javax.swing.JPanel main_panel = new javax.swing.JPanel();
        getContentPane().add(main_panel);
        java.awt.GridBagLayout gridbag = new java.awt.GridBagLayout();
        main_panel.setLayout(gridbag);
        java.awt.GridBagConstraints gridbag_constraints = new java.awt.GridBagConstraints();
        gridbag_constraints.anchor = java.awt.GridBagConstraints.WEST;
        gridbag_constraints.insets = new java.awt.Insets(1, 10, 1, 10);

        gridbag_constraints.gridwidth = 3;
        javax.swing.JLabel layer_label = new javax.swing.JLabel(resources.getString("layer"));
        gridbag.setConstraints(layer_label, gridbag_constraints);
        main_panel.add(layer_label);

        javax.swing.JLabel active_label = new javax.swing.JLabel(resources.getString("active"));
        gridbag.setConstraints(active_label, gridbag_constraints);
        main_panel.add(active_label);

        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        javax.swing.JLabel preferred_direction_label = new javax.swing.JLabel(resources.getString("preferred_direction"));
        gridbag.setConstraints(preferred_direction_label, gridbag_constraints);
        main_panel.add(preferred_direction_label);

        this.horizontal = resources.getString("horizontal");
        this.vertical = resources.getString("vertical");

        board.LayerStructure layer_structure = board_handling.get_routing_board().layer_structure;
        int signal_layer_count = layer_structure.signal_layer_count();
        signal_layer_name_arr = new javax.swing.JLabel[signal_layer_count];
        signal_layer_active_arr = new javax.swing.JCheckBox[signal_layer_count];
        combo_box_arr = new javax.swing.JComboBox[signal_layer_count];
        for (int i = 0; i < signal_layer_count; ++i)
        {
            signal_layer_name_arr[i] = new javax.swing.JLabel();
            board.Layer curr_signal_layer = layer_structure.get_signal_layer(i);
            signal_layer_name_arr[i].setText(curr_signal_layer.name);
            gridbag_constraints.gridwidth = 3;
            gridbag.setConstraints(signal_layer_name_arr[i], gridbag_constraints);
            main_panel.add(signal_layer_name_arr[i]);
            signal_layer_active_arr[i] = new javax.swing.JCheckBox();
            signal_layer_active_arr[i].addActionListener(new LayerActiveListener(i));
            gridbag.setConstraints(signal_layer_active_arr[i], gridbag_constraints);
            main_panel.add(signal_layer_active_arr[i]);
            combo_box_arr[i] = new javax.swing.JComboBox();
            combo_box_arr[i].addItem(this.horizontal);
            combo_box_arr[i].addItem(this.vertical);
            combo_box_arr[i].addActionListener(new PreferredDirectionListener(i));
            gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
            gridbag.setConstraints(combo_box_arr[i], gridbag_constraints);
            main_panel.add(combo_box_arr[i]);
        }

        javax.swing.JLabel separator = new javax.swing.JLabel("----------------------------------------  ");
        gridbag.setConstraints(separator, gridbag_constraints);
        main_panel.add(separator, gridbag_constraints);

        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.RELATIVE;
        javax.swing.JLabel vias_allowed_label = new javax.swing.JLabel(resources.getString("vias_allowed"));
        gridbag.setConstraints(vias_allowed_label, gridbag_constraints);
        main_panel.add(vias_allowed_label);

        this.vias_allowed = new javax.swing.JCheckBox();
        this.vias_allowed.addActionListener(new ViasAllowedListener());
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridbag.setConstraints(vias_allowed, gridbag_constraints);
        main_panel.add(vias_allowed);

        separator = new javax.swing.JLabel("----------------------------------------  ");
        gridbag.setConstraints(separator, gridbag_constraints);
        main_panel.add(separator, gridbag_constraints);

        javax.swing.JLabel passes_label = new javax.swing.JLabel(resources.getString("passes"));

        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.RELATIVE;
        gridbag_constraints.gridheight = 3;
        gridbag.setConstraints(passes_label, gridbag_constraints);
        main_panel.add(passes_label);

        this.fanout_pass_button = new javax.swing.JRadioButton(resources.getString("fanout"));
        this.autoroute_pass_button = new javax.swing.JRadioButton(resources.getString("autoroute"));
        this.postroute_pass_button = new javax.swing.JRadioButton(resources.getString("postroute"));

        fanout_pass_button.addActionListener(new FanoutListener());
        autoroute_pass_button.addActionListener(new AutorouteListener());
        postroute_pass_button.addActionListener(new PostrouteListener());


        fanout_pass_button.setSelected(false);
        autoroute_pass_button.setSelected(true);
        autoroute_pass_button.setSelected(true);


        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridbag_constraints.gridheight = 1;
        gridbag.setConstraints(fanout_pass_button, gridbag_constraints);
        main_panel.add(fanout_pass_button, gridbag_constraints);

        gridbag.setConstraints(autoroute_pass_button, gridbag_constraints);
        main_panel.add(autoroute_pass_button, gridbag_constraints);
        gridbag.setConstraints(postroute_pass_button, gridbag_constraints);
        main_panel.add(postroute_pass_button, gridbag_constraints);

        separator = new javax.swing.JLabel("----------------------------------------  ");
        gridbag.setConstraints(separator, gridbag_constraints);
        main_panel.add(separator, gridbag_constraints);

        detail_window = new WindowAutorouteDetailParameter(p_board_frame);
        javax.swing.JButton detail_button = new javax.swing.JButton(resources.getString("detail_parameter"));
        this.detail_listener = new DetailListener();
        detail_button.addActionListener(detail_listener);
        gridbag.setConstraints(detail_button, gridbag_constraints);

        main_panel.add(detail_button);

        p_board_frame.set_context_sensitive_help(this, "WindowAutorouteParameter");

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

        this.vias_allowed.setSelected(settings.get_vias_allowed());
        this.fanout_pass_button.setSelected(settings.get_with_fanout());
        this.autoroute_pass_button.setSelected(settings.get_with_autoroute());
        this.postroute_pass_button.setSelected(settings.get_with_postroute());

        for (int i = 0; i < signal_layer_active_arr.length; ++i)
        {
            this.signal_layer_active_arr[i].setSelected(settings.get_layer_active(layer_structure.get_layer_no(i)));
        }

        for (int i = 0; i < combo_box_arr.length; ++i)
        {
            if (settings.get_preferred_direction_is_horizontal(layer_structure.get_layer_no(i)))
            {
                this.combo_box_arr[i].setSelectedItem(this.horizontal);
            }
            else
            {
                this.combo_box_arr[i].setSelectedItem(this.vertical);
            }
        }
        this.detail_window.refresh();
    }

    public void dispose()
    {
        detail_window.dispose();
        super.dispose();
    }

    public void parent_iconified()
    {
        detail_window.parent_iconified();
        super.parent_iconified();
    }

    public void parent_deiconified()
    {
        detail_window.parent_deiconified();
        super.parent_deiconified();
    }
    private final interactive.BoardHandling board_handling;
    private final javax.swing.JLabel[] signal_layer_name_arr;
    private final javax.swing.JCheckBox[] signal_layer_active_arr;
    private final javax.swing.JComboBox[] combo_box_arr;
    private final javax.swing.JCheckBox vias_allowed;
    private final javax.swing.JRadioButton fanout_pass_button;
    private final javax.swing.JRadioButton autoroute_pass_button;
    private final javax.swing.JRadioButton postroute_pass_button;
    private final WindowAutorouteDetailParameter detail_window;
    private final DetailListener detail_listener;
    private final String horizontal;
    private final String vertical;

    private class DetailListener implements java.awt.event.ActionListener
    {

        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            if (first_time)
            {
                java.awt.Point location = getLocation();
                detail_window.setLocation((int) location.getX() + 200, (int) location.getY() + 100);
                first_time = false;
            }
            detail_window.setVisible(true);
        }
        private boolean first_time = true;
    }

    private class LayerActiveListener implements java.awt.event.ActionListener
    {

        public LayerActiveListener(int p_layer_no)
        {
            signal_layer_no = p_layer_no;
        }

        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            int curr_layer_no = board_handling.get_routing_board().layer_structure.get_layer_no(this.signal_layer_no);
            board_handling.settings.autoroute_settings.set_layer_active(curr_layer_no, signal_layer_active_arr[this.signal_layer_no].isSelected());
        }
        private final int signal_layer_no;
    }

    private class PreferredDirectionListener implements java.awt.event.ActionListener
    {

        public PreferredDirectionListener(int p_layer_no)
        {
            signal_layer_no = p_layer_no;
        }

        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            int curr_layer_no = board_handling.get_routing_board().layer_structure.get_layer_no(this.signal_layer_no);
            board_handling.settings.autoroute_settings.set_preferred_direction_is_horizontal(curr_layer_no,
                    combo_box_arr[signal_layer_no].getSelectedItem() == horizontal);
        }
        private final int signal_layer_no;
    }

    private class ViasAllowedListener implements java.awt.event.ActionListener
    {

        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_handling.settings.autoroute_settings.set_vias_allowed(vias_allowed.isSelected());
        }
    }

    private class FanoutListener implements java.awt.event.ActionListener
    {

        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            interactive.AutorouteSettings autoroute_settings = board_handling.settings.autoroute_settings;
            autoroute_settings.set_with_fanout(fanout_pass_button.isSelected());
            autoroute_settings.set_pass_no(1);
        }
    }

    private class AutorouteListener implements java.awt.event.ActionListener
    {

        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            interactive.AutorouteSettings autoroute_settings = board_handling.settings.autoroute_settings;
            autoroute_settings.set_with_autoroute(autoroute_pass_button.isSelected());
            autoroute_settings.set_pass_no(1);
        }
    }

    private class PostrouteListener implements java.awt.event.ActionListener
    {

        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            interactive.AutorouteSettings autoroute_settings = board_handling.settings.autoroute_settings;
            autoroute_settings.set_with_postroute(postroute_pass_button.isSelected());
            autoroute_settings.set_pass_no(1);
        }
    }
}
