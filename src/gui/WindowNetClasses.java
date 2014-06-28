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
 * EditNetRules.java
 *
 * Created on 10. April 2005, 07:49
 */
package gui;

import rules.NetClass;
import rules.ViaRule;
import rules.BoardRules;

import board.ObjectInfoPanel.Printable;

/**
 * Edit window for the table of net rules.
 *
 * @author Alfons Wirtz
 */
public class WindowNetClasses extends BoardSavableSubWindow
{

    /** Creates a new instance of NetClassesWindow */
    public WindowNetClasses(BoardFrame p_board_frame)
    {
        this.resources = java.util.ResourceBundle.getBundle("gui.resources.WindowNetClasses", p_board_frame.get_locale());
        this.setTitle(resources.getString("title"));

        this.board_frame = p_board_frame;

        this.main_panel = new javax.swing.JPanel();
        this.main_panel.setLayout(new java.awt.BorderLayout());

        board.BasicBoard routing_board = p_board_frame.board_panel.board_handling.get_routing_board();

        this.cl_class_combo_box = new javax.swing.JComboBox();
        this.via_rule_combo_box = new javax.swing.JComboBox();
        this.layer_combo_box = new ComboBoxLayer(routing_board.layer_structure, p_board_frame.get_locale());
        add_combobox_items();

        add_table();

        javax.swing.JPanel net_class_button_panel = new javax.swing.JPanel();
        net_class_button_panel.setLayout(new java.awt.FlowLayout());
        this.main_panel.add(net_class_button_panel, java.awt.BorderLayout.SOUTH);
        final javax.swing.JButton add_class_button = new javax.swing.JButton(resources.getString("add"));
        add_class_button.setToolTipText(resources.getString("add_tooltip"));
        add_class_button.addActionListener(new AddNetClassListener());
        net_class_button_panel.add(add_class_button);
        final javax.swing.JButton remove_class_button = new javax.swing.JButton(resources.getString("remove"));
        remove_class_button.setToolTipText(resources.getString("remove_tooltip"));
        remove_class_button.addActionListener(new RemoveNetClassListener());
        net_class_button_panel.add(remove_class_button);

        final javax.swing.JButton assign_button = new javax.swing.JButton(resources.getString("assign"));
        assign_button.setToolTipText(resources.getString("assign_tooltip"));
        assign_button.addActionListener(new AssignClassesListener());
        net_class_button_panel.add(assign_button);

        final javax.swing.JButton select_button = new javax.swing.JButton(resources.getString("select"));
        select_button.setToolTipText(resources.getString("select_tooltip"));
        select_button.addActionListener(new SelectClassesListener());
        net_class_button_panel.add(select_button);

        final javax.swing.JButton contained_nets_button = new javax.swing.JButton(resources.getString("show_nets"));
        net_class_button_panel.add(contained_nets_button);
        contained_nets_button.setToolTipText(resources.getString("show_nets_tooltip"));
        contained_nets_button.addActionListener(new ContainedNetsListener());

        final javax.swing.JButton filter_incompletes_button = new javax.swing.JButton(resources.getString("filter_incompletes"));
        net_class_button_panel.add(filter_incompletes_button);
        filter_incompletes_button.setToolTipText(resources.getString("filter_incompletes_tooltip"));
        filter_incompletes_button.addActionListener(new FilterIncompletesListener());

        p_board_frame.set_context_sensitive_help(this, "WindowNetClasses");

        this.add(main_panel);
        this.pack();
        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    public void refresh()
    {
        this.cl_class_combo_box.removeAllItems();
        this.via_rule_combo_box.removeAllItems();
        add_combobox_items();
        this.table_model.set_values();
        int table_height = TEXTFIELD_HEIGHT * this.table_model.getRowCount();
        int table_width = TEXTFIELD_WIDTH * this.table_model.getColumnCount();
        this.table.setPreferredScrollableViewportSize(new java.awt.Dimension(table_width, table_height));
        // reinsert the scroll to display the correct table size if the table size has changed.
        this.main_panel.remove(this.center_panel);
        this.main_panel.add(center_panel, java.awt.BorderLayout.CENTER);
        this.pack();

        // Dispose all subwindows because they may be no longer uptodate.
        java.util.Iterator<javax.swing.JFrame> it = this.subwindows.iterator();
        while (it.hasNext())
        {
            javax.swing.JFrame curr_subwindow = it.next();
            if (curr_subwindow != null)
            {

                curr_subwindow.dispose();
            }
            it.remove();
        }
    }

    public void dispose()
    {
        for (javax.swing.JFrame curr_subwindow : this.subwindows)
        {
            if (curr_subwindow != null)
            {
                curr_subwindow.dispose();
            }
        }
        super.dispose();
    }

    private void add_table()
    {
        this.table_model = new NetClassTableModel();
        this.table = new NetClassTable(this.table_model);
        javax.swing.JScrollPane scroll_pane = new javax.swing.JScrollPane(this.table);
        int table_height = TEXTFIELD_HEIGHT * this.table_model.getRowCount();
        int table_width = TEXTFIELD_WIDTH * this.table_model.getColumnCount();
        this.table.setPreferredScrollableViewportSize(new java.awt.Dimension(table_width, table_height));
        this.center_panel = new javax.swing.JPanel();
        this.center_panel.setLayout(new java.awt.BorderLayout());

        this.center_panel.add(scroll_pane, java.awt.BorderLayout.CENTER);

        // add message for german localisation bug
        if (board_frame.get_locale().getLanguage().equalsIgnoreCase("de"))
        {
            javax.swing.JLabel bug_label =
                    new javax.swing.JLabel("Wegen eines Java-System-Bugs muss das Dezimalkomma in dieser Tabelle zur Zeit als Punkt eingegeben werden!");
            this.center_panel.add(bug_label, java.awt.BorderLayout.SOUTH);
        }
        this.main_panel.add(center_panel, java.awt.BorderLayout.CENTER);

        this.table.getColumnModel().getColumn(ColumnName.CLEARANCE_CLASS.ordinal()).setCellEditor(new javax.swing.DefaultCellEditor(cl_class_combo_box));

        this.table.getColumnModel().getColumn(ColumnName.VIA_RULE.ordinal()).setCellEditor(new javax.swing.DefaultCellEditor(via_rule_combo_box));
        this.table.getColumnModel().getColumn(ColumnName.ON_LAYER.ordinal()).setCellEditor(new javax.swing.DefaultCellEditor(layer_combo_box));
    }

    private void add_combobox_items()
    {
        board.RoutingBoard routing_board = board_frame.board_panel.board_handling.get_routing_board();
        for (int i = 0; i < routing_board.rules.clearance_matrix.get_class_count(); ++i)
        {
            cl_class_combo_box.addItem(routing_board.rules.clearance_matrix.get_name(i));
        }
        for (ViaRule curr_rule : routing_board.rules.via_rules)
        {
            via_rule_combo_box.addItem(curr_rule.name);
        }
    }

    /**
     * Adjusts the displayed window with the net class table after the size of the table has been changed.
     */
    private void adjust_table()
    {
        this.table_model = new NetClassTableModel();
        this.table = new NetClassTable(this.table_model);
        this.main_panel.remove(this.center_panel);
        this.add_table();
        this.pack();
        this.board_frame.refresh_windows();
    }
    private final BoardFrame board_frame;
    private final javax.swing.JPanel main_panel;
    private javax.swing.JPanel center_panel;
    private NetClassTable table;
    private NetClassTableModel table_model;
    private javax.swing.JComboBox cl_class_combo_box;
    private javax.swing.JComboBox via_rule_combo_box;
    private final ComboBoxLayer layer_combo_box;
    private final java.util.ResourceBundle resources;
    /** The subwindows created inside this window */
    private final java.util.Collection<javax.swing.JFrame> subwindows = new java.util.LinkedList<javax.swing.JFrame>();
    private static final int TEXTFIELD_HEIGHT = 16;
    private static final int TEXTFIELD_WIDTH = 100;
    private static final int WINDOW_OFFSET = 30;

    private class AddNetClassListener implements java.awt.event.ActionListener
    {

        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_frame.board_panel.board_handling.get_routing_board().rules.append_net_class(board_frame.get_locale());
            adjust_table();
        }
    }

    private class RemoveNetClassListener implements java.awt.event.ActionListener
    {

        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            if (table_model.getRowCount() <= 1)
            {
                board_frame.screen_messages.set_status_message(resources.getString("message_1"));
                return;
            }
            int selected_row = table.getSelectedRow();
            if (selected_row < 0)
            {
                return;
            }
            Object net_class_name = table_model.getValueAt(selected_row, ColumnName.NAME.ordinal());
            if (!(net_class_name instanceof String))
            {
                return;
            }
            BoardRules board_rules = board_frame.board_panel.board_handling.get_routing_board().rules;
            NetClass net_rule = board_rules.net_classes.get((String) net_class_name);
            // Check, if net_rule is used in a net of the net list
            for (int i = 1; i < board_rules.nets.max_net_no(); ++i)
            {
                rules.Net curr_net = board_rules.nets.get(i);
                if (curr_net.get_class() == net_rule)
                {
                    String message = resources.getString("message_2") + " " + curr_net.name;
                    board_frame.screen_messages.set_status_message(message);
                    return;
                }
            }
            if (board_rules.net_classes.remove(net_rule))
            {
                adjust_table();
                String message = resources.getString("net_class") + " " + net_rule.get_name() + " " +
                        resources.getString("removed");
                board_frame.screen_messages.set_status_message(message);
            }
        }
    }

    private class AssignClassesListener implements java.awt.event.ActionListener
    {

        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            board_frame.assign_net_classes_window.setVisible(true);
        }
    }

    private class SelectClassesListener implements java.awt.event.ActionListener
    {

        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            int[] selected_rows = table.getSelectedRows();
            if (selected_rows.length <= 0)
            {
                return;
            }
            board.RoutingBoard routing_board = board_frame.board_panel.board_handling.get_routing_board();
            NetClass[] selected_class_arr = new NetClass[selected_rows.length];
            for (int i = 0; i < selected_class_arr.length; ++i)
            {
                selected_class_arr[i] = routing_board.rules.net_classes.get((String) table.getValueAt(selected_rows[i], ColumnName.NAME.ordinal()));
            }
            rules.Nets nets = routing_board.rules.nets;
            java.util.Set<board.Item> selected_items = new java.util.TreeSet<board.Item>();
            java.util.Collection<board.Item> board_items = routing_board.get_items();
            for (board.Item curr_item : board_items)
            {
                boolean item_matches = false;
                for (int i = 0; i < curr_item.net_count(); ++i)
                {
                    rules.NetClass curr_net_class = nets.get(curr_item.get_net_no(i)).get_class();
                    if (curr_net_class == null)
                    {
                        continue;
                    }
                    for (int j = 0; j < selected_class_arr.length; ++j)
                    {
                        if (curr_net_class == selected_class_arr[i])
                        {
                            item_matches = true;
                            break;
                        }
                    }
                    if (item_matches)
                    {
                        break;
                    }
                }
                if (item_matches)
                {
                    selected_items.add(curr_item);
                }
            }
            board_frame.board_panel.board_handling.select_items(selected_items);
            board_frame.board_panel.board_handling.zoom_selection();
        }
    }

    private class FilterIncompletesListener implements java.awt.event.ActionListener
    {

        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            int[] selected_rows = table.getSelectedRows();
            if (selected_rows.length <= 0)
            {
                return;
            }
            interactive.BoardHandling board_handling = board_frame.board_panel.board_handling;
            rules.BoardRules board_rules = board_handling.get_routing_board().rules;
            NetClass[] selected_class_arr = new NetClass[selected_rows.length];
            for (int i = 0; i < selected_class_arr.length; ++i)
            {
                selected_class_arr[i] = board_rules.net_classes.get((String) table.getValueAt(selected_rows[i], ColumnName.NAME.ordinal()));
            }
            int max_net_no = board_rules.nets.max_net_no();
            for (int i = 1; i <= max_net_no; ++i)
            {
                board_handling.set_incompletes_filter(i, true);
                NetClass curr_net_class = board_rules.nets.get(i).get_class();
                for (int j = 0; j < selected_class_arr.length; ++j)
                {
                    if (curr_net_class == selected_class_arr[j])
                    {
                        board_handling.set_incompletes_filter(i, false);
                        break;
                    }
                }

            }
            board_frame.board_panel.repaint();
        }
    }

    private class ContainedNetsListener implements java.awt.event.ActionListener
    {

        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            int[] selected_rows = table.getSelectedRows();
            if (selected_rows.length <= 0)
            {
                return;
            }
            interactive.BoardHandling board_handling = board_frame.board_panel.board_handling;
            rules.BoardRules board_rules = board_handling.get_routing_board().rules;
            NetClass[] selected_class_arr = new NetClass[selected_rows.length];
            for (int i = 0; i < selected_class_arr.length; ++i)
            {
                selected_class_arr[i] = board_rules.net_classes.get((String) table.getValueAt(selected_rows[i], ColumnName.NAME.ordinal()));
            }
            java.util.Collection<Printable> contained_nets = new java.util.LinkedList<Printable>();
            int max_net_no = board_rules.nets.max_net_no();
            for (int i = 1; i <= max_net_no; ++i)
            {
                rules.Net curr_net = board_rules.nets.get(i);
                NetClass curr_net_class = curr_net.get_class();
                for (int j = 0; j < selected_class_arr.length; ++j)
                {
                    if (curr_net_class == selected_class_arr[j])
                    {
                        contained_nets.add(curr_net);
                        break;
                    }
                }
            }
            board.CoordinateTransform coordinate_transform = board_frame.board_panel.board_handling.coordinate_transform;
            WindowObjectInfo new_window =
                    WindowObjectInfo.display(resources.getString("contained_nets"), contained_nets, board_frame, coordinate_transform);
            java.awt.Point loc = getLocation();
            java.awt.Point new_window_location =
                    new java.awt.Point((int) (loc.getX() + WINDOW_OFFSET), (int) (loc.getY() + WINDOW_OFFSET));
            new_window.setLocation(new_window_location);
            subwindows.add(new_window);
        }
    }

    private class NetClassTable extends javax.swing.JTable
    {

        public NetClassTable(NetClassTableModel p_table_model)
        {
            super(p_table_model);
            column_tool_tips = new String[9];
            column_tool_tips[0] = null;
            column_tool_tips[1] = resources.getString("column_tool_tip_1");
            column_tool_tips[2] = resources.getString("column_tool_tip_2");
            column_tool_tips[3] = resources.getString("column_tool_tip_3");
            column_tool_tips[4] = resources.getString("column_tool_tip_4");
            column_tool_tips[5] = resources.getString("column_tool_tip_5");
            column_tool_tips[6] = resources.getString("column_tool_tip_6");
            column_tool_tips[7] = resources.getString("column_tool_tip_7");
            column_tool_tips[8] = resources.getString("column_tool_tip_8");
        }
        //Implement table header tool tips.
        protected javax.swing.table.JTableHeader createDefaultTableHeader()
        {
            return new javax.swing.table.JTableHeader(columnModel)
            {

                public String getToolTipText(java.awt.event.MouseEvent e)
                {
                    java.awt.Point p = e.getPoint();
                    int index = columnModel.getColumnIndexAtX(p.x);
                    int realIndex = columnModel.getColumn(index).getModelIndex();
                    return column_tool_tips[realIndex];
                }
            };
        }
        private final String[] column_tool_tips;
    }

    /**
     * Table model of the net rule table.
     */
    private class NetClassTableModel extends javax.swing.table.AbstractTableModel
    {

        public NetClassTableModel()
        {
            column_names = new String[ColumnName.values().length];

            for (int i = 0; i < column_names.length; ++i)
            {
                column_names[i] = resources.getString(ColumnName.values()[i].toString());
            }
            set_values();
        }

        /** Calculates the the valus in this table */
        public void set_values()
        {
            rules.BoardRules board_rules = board_frame.board_panel.board_handling.get_routing_board().rules;
            this.data = new Object[board_rules.net_classes.count()][];
            for (int i = 0; i < data.length; ++i)
            {
                this.data[i] = new Object[ColumnName.values().length];
            }
            for (int i = 0; i < data.length; ++i)
            {
                NetClass curr_net_class = board_rules.net_classes.get(i);
                this.data[i][ColumnName.NAME.ordinal()] = curr_net_class.get_name();
                if (curr_net_class.get_via_rule() != null)
                {
                    this.data[i][ColumnName.VIA_RULE.ordinal()] = curr_net_class.get_via_rule().name;
                }
                this.data[i][ColumnName.SHOVE_FIXED.ordinal()] = curr_net_class.is_shove_fixed() || !curr_net_class.get_pull_tight();
                this.data[i][ColumnName.CYCLES_WITH_AREAS.ordinal()] = curr_net_class.get_ignore_cycles_with_areas();
                double min_trace_length =
                        board_frame.board_panel.board_handling.coordinate_transform.board_to_user(curr_net_class.get_minimum_trace_length());
                if (min_trace_length <= 0)
                {
                    min_trace_length = 0;
                }
                this.data[i][ColumnName.MIN_TRACE_LENGTH.ordinal()] = (float) min_trace_length;
                double max_trace_length =
                        board_frame.board_panel.board_handling.coordinate_transform.board_to_user(curr_net_class.get_maximum_trace_length());
                if (max_trace_length <= 0)
                {
                    max_trace_length = -1;
                }
                this.data[i][ColumnName.MAX_TRACE_LENGTH.ordinal()] = (float) max_trace_length;
                this.data[i][ColumnName.CLEARANCE_CLASS.ordinal()] =
                        board_rules.clearance_matrix.get_name(curr_net_class.get_trace_clearance_class());
                ComboBoxLayer.Layer combo_layer = layer_combo_box.get_selected_layer();
                set_trace_width_field(i, combo_layer);
                this.data[i][ColumnName.ON_LAYER.ordinal()] = combo_layer.name;
            }
        }

        void set_trace_width_field(int p_rule_no, ComboBoxLayer.Layer p_layer)
        {
            Float trace_width;
            interactive.BoardHandling board_handling = board_frame.board_panel.board_handling;
            rules.BoardRules board_rules = board_handling.get_routing_board().rules;
            NetClass curr_net_class = board_rules.net_classes.get(p_rule_no);
            if (p_layer.index == ComboBoxLayer.ALL_LAYER_INDEX)
            {
                // all layers
                if (curr_net_class.trace_width_is_layer_dependent())
                {
                    trace_width = (float) -1;

                }
                else
                {
                    trace_width = (float) board_handling.coordinate_transform.board_to_user(2 * curr_net_class.get_trace_half_width(0));
                }

            }
            else if (p_layer.index == ComboBoxLayer.INNER_LAYER_INDEX)
            {
                // all inner layers

                if (curr_net_class.trace_width_is_inner_layer_dependent())
                {
                    trace_width = (float) -1;
                }
                else
                {
                    int first_inner_signal_layer_no = 1;
                    board.LayerStructure layer_structure = board_handling.get_routing_board().layer_structure;
                    while (!layer_structure.arr[first_inner_signal_layer_no].is_signal)
                    {
                        ++first_inner_signal_layer_no;
                    }
                    if (first_inner_signal_layer_no < layer_structure.arr.length - 1)
                    {

                        trace_width = (float) board_handling.coordinate_transform.board_to_user(2 * curr_net_class.get_trace_half_width(first_inner_signal_layer_no));
                    }
                    else
                    {
                        trace_width = (float) 0;
                    }
                }
            }
            else
            {
                trace_width = (float) board_handling.coordinate_transform.board_to_user(2 * curr_net_class.get_trace_half_width(p_layer.index));
            }
            this.data[p_rule_no][ColumnName.TRACE_WIDTH.ordinal()] = trace_width;
            fireTableCellUpdated(p_rule_no, ColumnName.TRACE_WIDTH.ordinal());
        }

        public String getColumnName(int p_col)
        {
            return column_names[p_col];
        }

        public int getRowCount()
        {
            return data.length;
        }

        public int getColumnCount()
        {
            return column_names.length;
        }

        public Object getValueAt(int p_row, int p_col)
        {
            return data[p_row][p_col];
        }

        public void setValueAt(Object p_value, int p_row, int p_col)
        {
            board.RoutingBoard routing_board = board_frame.board_panel.board_handling.get_routing_board();
            BoardRules board_rules = routing_board.rules;
            Object net_class_name = getValueAt(p_row, ColumnName.NAME.ordinal());
            if (!(net_class_name instanceof String))
            {
                System.out.println("EditNetRuLesVindow.setValueAt: String expected");
                return;
            }
            NetClass net_rule = board_rules.net_classes.get((String) net_class_name);
            if (net_rule == null)
            {
                System.out.println("EditNetRuLesVindow.setValueAt: net_rule not found");
                return;
            }

            if (p_col == ColumnName.NAME.ordinal())
            {
                if (!(p_value instanceof String))
                {
                    return;
                }
                String new_name = (String) p_value;
                if (board_rules.net_classes.get(new_name) != null)
                {
                    return; // name exists already
                }
                net_rule.set_name(new_name);
                board_frame.via_window.refresh();
            }
            else if (p_col == ColumnName.VIA_RULE.ordinal())
            {
                if (!(p_value instanceof String))
                {
                    return;
                }
                String new_name = (String) p_value;
                ViaRule new_via_rule = board_rules.get_via_rule(new_name);
                if (new_via_rule == null)
                {
                    System.out.println("EditNetRuLesVindow.setValueAt: via_rule not found");
                    return;
                }
                net_rule.set_via_rule(new_via_rule);
            }
            else if (p_col == ColumnName.SHOVE_FIXED.ordinal())
            {
                if (!(p_value instanceof Boolean))
                {
                    return;
                }
                boolean value = (Boolean) p_value;
                net_rule.set_shove_fixed(value);
                net_rule.set_pull_tight(!value);
            }
            else if (p_col == ColumnName.CYCLES_WITH_AREAS.ordinal())
            {
                if (!(p_value instanceof Boolean))
                {
                    return;
                }
                boolean value = (Boolean) p_value;
                net_rule.set_ignore_cycles_with_areas(value);
            }
            else if (p_col == ColumnName.MIN_TRACE_LENGTH.ordinal())
            {

                Float curr_value = 0f;
                if (p_value instanceof Float)
                {
                    curr_value = (Float) p_value;
                }
                else if (p_value instanceof String)
                {
                    // Workaround because of a localisation Bug in Java
                    // The numbers are always displayed in the English Format.

                    try
                    {
                        curr_value = Float.parseFloat((String) p_value);
                    } catch (Exception e)
                    {
                        curr_value = 0f;
                    }
                    p_value = curr_value.toString();
                }
                if (curr_value <= 0)
                {
                    curr_value = (float) 0;
                    p_value = curr_value;
                }
                double min_trace_length = Math.round(board_frame.board_panel.board_handling.coordinate_transform.user_to_board(curr_value));
                net_rule.set_minimum_trace_length(min_trace_length);
                board_frame.board_panel.board_handling.recalculate_length_violations();
            }
            else if (p_col == ColumnName.MAX_TRACE_LENGTH.ordinal())
            {
                Float curr_value = 0f;
                if (p_value instanceof Float)
                {
                    curr_value = (Float) p_value;
                }
                else if (p_value instanceof String)
                {
                    // Workaround because of a localisation Bug in Java
                    // The numbers are always displayed in the English Format.

                    try
                    {
                        curr_value = Float.parseFloat((String) p_value);
                    } catch (Exception e)
                    {
                        curr_value = 0f;
                    }
                    p_value = curr_value.toString();
                }
                if (curr_value <= 0)
                {
                    curr_value = (float) 0;
                    p_value = curr_value - 1;
                }

                double max_trace_length = Math.round(board_frame.board_panel.board_handling.coordinate_transform.user_to_board(curr_value));
                net_rule.set_maximum_trace_length(max_trace_length);
                board_frame.board_panel.board_handling.recalculate_length_violations();
            }
            else if (p_col == ColumnName.CLEARANCE_CLASS.ordinal())
            {
                if (!(p_value instanceof String))
                {
                    return;
                }
                String new_name = (String) p_value;
                int new_cl_class_index = board_rules.clearance_matrix.get_no(new_name);
                {
                    if (new_cl_class_index < 0)
                    {
                        System.out.println("EditNetRuLesVindow.setValueAt: clearance class not found");
                        return;
                    }
                }
                net_rule.set_trace_clearance_class(new_cl_class_index);
            }
            else if (p_col == ColumnName.TRACE_WIDTH.ordinal())
            {
                Float curr_value = 0f;
                if (p_value instanceof Float)
                {
                    curr_value = (Float) p_value;
                }
                else if (p_value instanceof String)
                {
                    // Workaround because of a localisation Bug in Java
                    // The numbers are always displayed in the English Format.

                    try
                    {
                        curr_value = Float.parseFloat((String) p_value);
                    } catch (Exception e)
                    {
                        curr_value = 0f;
                    }
                }
                if (curr_value < 0)
                {
                    return;
                }
                int curr_half_width;
                boolean is_active;
                if (curr_value == 0)
                {
                    curr_half_width = 0;
                    is_active = false;
                }
                else
                {
                    curr_half_width = (int) Math.round(board_frame.board_panel.board_handling.coordinate_transform.user_to_board(0.5 * curr_value));
                    if (curr_half_width <= 0)
                    {
                        return;
                    }
                    is_active = true;

                }
                if (p_value instanceof String)
                {
                    p_value = curr_value.toString();
                }
                int layer_index = layer_combo_box.get_selected_layer().index;
                NetClass curr_net_class = board_rules.net_classes.get(p_row);

                if (layer_index == ComboBoxLayer.ALL_LAYER_INDEX)
                {
                    curr_net_class.set_trace_half_width(curr_half_width);
                    curr_net_class.set_all_layers_active(is_active);
                }
                else if (layer_index == ComboBoxLayer.INNER_LAYER_INDEX)
                {
                    curr_net_class.set_trace_half_width_on_inner(curr_half_width);
                    curr_net_class.set_all_inner_layers_active(is_active);
                }
                else
                {
                    curr_net_class.set_trace_half_width(layer_index, curr_half_width);
                    curr_net_class.set_active_routing_layer(layer_index, is_active);
                }
            }
            else if (p_col == ColumnName.ON_LAYER.ordinal())
            {
                if (!(p_value instanceof ComboBoxLayer.Layer))
                {
                    return;
                }
                set_trace_width_field(p_row, (ComboBoxLayer.Layer) p_value);
            }
            this.data[p_row][p_col] = p_value;
            fireTableCellUpdated(p_row, p_col);
        }

        public boolean isCellEditable(int p_row, int p_col)
        {
            // the name of the default class is not editable
            return p_row > 0 || p_col > 0;
        }

        public Class<?> getColumnClass(int p_col)
        {
            Object curr_entry = getValueAt(0, p_col);
            Class<?> curr_class = curr_entry.getClass();
            // changed because of a localisation bug in Java
            if (curr_entry instanceof Float)
            {
                curr_class = String.class;
            }
            return curr_class;
        }
        private Object[][] data = null;
        private String[] column_names = null;
    }

    private enum ColumnName
    {

        NAME, VIA_RULE, CLEARANCE_CLASS, TRACE_WIDTH, ON_LAYER, SHOVE_FIXED, CYCLES_WITH_AREAS, MIN_TRACE_LENGTH, MAX_TRACE_LENGTH
    }
}
