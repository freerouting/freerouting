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
 * VisibilityFrame.java
 *
 * Created on 7. November 2004, 11:29
 */

package gui;

/**
 * Interactive Frame to adjust the visibility of a set of objects
 *
 * @author  Alfons Wirtz
 */
public abstract class WindowVisibility extends BoardSavableSubWindow
{
    
    /** Creates a new instance of VisibilityFrame */
    public WindowVisibility(BoardFrame p_board_frame, String p_title, String p_header_message, String[] p_message_arr)
    {
        this.board_panel = p_board_frame.board_panel;
        this.setTitle(p_title);
        
        // create main panel
        final javax.swing.JPanel main_panel = new javax.swing.JPanel();
        getContentPane().add(main_panel);
        
        java.awt.GridBagLayout gridbag = new java.awt.GridBagLayout();
        main_panel.setLayout(gridbag);
        java.awt.GridBagConstraints gridbag_constraints = new java.awt.GridBagConstraints();
        gridbag_constraints.insets = new java.awt.Insets(5, 10, 5, 10);
        header_message = new javax.swing.JLabel();
        header_message.setText(p_header_message);
        gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridbag_constraints.ipady = 10;
        gridbag.setConstraints(header_message, gridbag_constraints);
        main_panel.add(header_message);
        slider_arr = new javax.swing.JSlider[p_message_arr.length];
        message_arr  = new javax.swing.JLabel [p_message_arr.length];
        gridbag_constraints.ipady = 0;
        for (int i = 0; i < p_message_arr.length; ++i)
        {
            message_arr[i] = new javax.swing.JLabel();
            message_arr[i].setText(p_message_arr[i]);
            gridbag_constraints.gridwidth = java.awt.GridBagConstraints.RELATIVE;
            gridbag.setConstraints(message_arr[i], gridbag_constraints);
            main_panel.add(message_arr[i]);
            slider_arr[i] = new javax.swing.JSlider(0, MAX_SLIDER_VALUE);
            gridbag_constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
            gridbag.setConstraints(slider_arr[i], gridbag_constraints);
            main_panel.add(slider_arr[i]);
            slider_arr[i].addChangeListener(new SliderChangeListener(i));
        }
        javax.swing.JLabel empty_label = new javax.swing.JLabel();
        gridbag.setConstraints(empty_label, gridbag_constraints);
        main_panel.add(empty_label);
        gridbag_constraints.gridwidth = 2;
        java.util.ResourceBundle resources = 
                java.util.ResourceBundle.getBundle("gui.resources.Default", p_board_frame.get_locale());
        javax.swing.JButton min_all_button = new javax.swing.JButton(resources.getString("minimum_all"));
        min_all_button.setToolTipText(resources.getString("minimum_all_tooltip"));
        min_all_button.addActionListener(new MinAllButtonListener());
        gridbag.setConstraints(min_all_button, gridbag_constraints);
        main_panel.add(min_all_button);
        javax.swing.JButton max_all_button = new javax.swing.JButton(resources.getString("maximum_all"));
        max_all_button.setToolTipText(resources.getString("maximum_all_tooltip"));
        max_all_button.addActionListener(new MaxAllButtonListener());
        gridbag.setConstraints(max_all_button, gridbag_constraints);
        main_panel.add(max_all_button);
        this.pack();
        this.setResizable(false);
    }
    
    /**
     * Sets the values of the p_no-ths slider contained in this frame.
     */
    public void set_slider_value( int p_no, double p_value)
    {
        int visibility = (int) Math.round(p_value * MAX_SLIDER_VALUE);
        slider_arr[p_no].setValue(visibility);
    }
    
    protected interactive.BoardHandling get_board_handling()
    {
        return board_panel.board_handling;
    }
    
    protected void set_all_minimum()
    {
        for (int i = 0; i < slider_arr.length; ++i)
        {
            set_slider_value(i, 0);
            set_changed_value(i, 0);
        }
    }
    
    protected void set_all_maximum()
    {
        for (int i = 0; i < slider_arr.length; ++i)
        {
            set_slider_value(i, MAX_SLIDER_VALUE);
            set_changed_value(i, 1);
        }
    }
    
    /**
     * Stores the new value in the board database, when a slider value was changed.
     */
    protected abstract void set_changed_value(int p_index, double p_value);
    
    // private data
    
    private final BoardPanel board_panel;
    
    private final javax.swing.JLabel header_message;
    private final javax.swing.JLabel [] message_arr;
    private final javax.swing.JSlider [] slider_arr;
    
    private static final int MAX_SLIDER_VALUE = 100;
    
    // private classes
    
    private class MinAllButtonListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            set_all_minimum();
            board_panel.repaint();
        }
    }
    
    private class MaxAllButtonListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            set_all_maximum();
            board_panel.repaint();
        }
    }
    
    /**
     * p_slider_no is required to identify the number of the slider in slider_arr.
     */
    private class SliderChangeListener implements javax.swing.event.ChangeListener
    {
        public SliderChangeListener(int p_slider_no)
        {
            slider_no = p_slider_no;
        }
        public void stateChanged(javax.swing.event.ChangeEvent evt)
        {
            int new_visibility = slider_arr[slider_no].getValue();
            set_changed_value(slider_no, ((double) new_visibility) / ((double)MAX_SLIDER_VALUE));
            board_panel.repaint();
        }
        
        public int slider_no;
    }
}
