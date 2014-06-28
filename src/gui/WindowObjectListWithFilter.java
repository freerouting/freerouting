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
 * ObjectListWindowWithFilter.java
 *
 * Created on 24. Maerz 2005, 10:10
 */

package gui;

/**
 *  Abstract class for windows displaying a list of objects
 *  The object name can be filttered by an alphanumeric input string. * @author Alfons Wirtz
 */
public abstract class WindowObjectListWithFilter extends WindowObjectList
{
    
    /** Creates a new instance of ObjectListWindowWithFilter */
    public WindowObjectListWithFilter(BoardFrame p_board_frame)
    {
        super(p_board_frame);
        java.util.ResourceBundle resources = 
                java.util.ResourceBundle.getBundle("gui.resources.WindowObjectList", p_board_frame.get_locale());
        javax.swing.JPanel input_panel = new javax.swing.JPanel();
        this.south_panel.add(input_panel, java.awt.BorderLayout.SOUTH);
        
        javax.swing.JLabel filter_label = new javax.swing.JLabel(resources.getString("filter"));
        input_panel.add(filter_label, java.awt.BorderLayout.WEST);
        
        this.filter_string = new javax.swing.JTextField(10);
        this.filter_string.setText("");
        input_panel.add(filter_string, java.awt.BorderLayout.EAST);
    }
    
    /**
     * Adds p_object to the list only if its name matches the filter.
     */
    protected void add_to_list(Object p_object)
    {
        String curr_filter_string = this.filter_string.getText().trim();
        boolean object_matches;
        if (curr_filter_string.length() == 0)
        {
            object_matches = true;
        }
        else
        {
            object_matches = p_object.toString().contains(curr_filter_string);
        }
        if (object_matches)
        {
            super.add_to_list(p_object);
        }
    }
    
    /**
     * Returns the filter text string of this window.
     */
    public SnapshotInfo get_snapshot_info()
    {
        int [] selected_indices;
        if (this.list != null)
        {
            selected_indices = this.list.getSelectedIndices();
        }
        else
        {
            selected_indices = new int[0];
        }
        return new SnapshotInfo(filter_string.getText(), selected_indices);
    }
    
    public void set_snapshot_info(SnapshotInfo p_snapshot_info)
    {
        if (!p_snapshot_info.filter.equals(this.filter_string.getText()))
        {
            this.filter_string.setText(p_snapshot_info.filter);
            this.recalculate();
        }
        if (this.list != null && p_snapshot_info.selected_indices.length > 0)
        {
            this.list.setSelectedIndices(p_snapshot_info.selected_indices);
        }
    }
    
    /**
     * Saves also the filter string to disk.
     */
    public void save(java.io.ObjectOutputStream p_object_stream)
    {
        try
        {
            p_object_stream.writeObject(filter_string.getText());
        }
        catch (java.io.IOException e)
        {
            System.out.println("WindowObjectListWithFilter.save: save failed");
        }
        super.save(p_object_stream);
    }
    
    public boolean read(java.io.ObjectInputStream p_object_stream)
    {
        try
        {
            String curr_string = (String) p_object_stream.readObject();
            this.filter_string.setText(curr_string);
        }
        catch (Exception e)
        {
            System.out.println("WindowObjectListWithFilter.read: read failed");
        }
        return super.read(p_object_stream);
    }
    
    private final javax.swing.JTextField filter_string;
    
    /**
     * Information to be stored in a SnapShot.
     */
    public static class SnapshotInfo implements java.io.Serializable
    {
        private SnapshotInfo(String p_filter, int[] p_selected_indices)
        {
            filter = p_filter;
            selected_indices = p_selected_indices;
        }
        private final String filter;
        private final int [] selected_indices;
    }
}
