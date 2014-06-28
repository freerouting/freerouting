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
 * LayerComboBox.java
 *
 * Created on 20. Februar 2005, 08:14
 */

package gui;
import board.LayerStructure;

/**
 * A Combo Box with items for individuell board layers plus an additional item for all layers.
 *
 * @author Alfons Wirtz
 */
public class ComboBoxLayer extends javax.swing.JComboBox
{
    
    /** Creates a new instance of LayerComboBox */
    public ComboBoxLayer(LayerStructure p_layer_structure, java.util.Locale p_locale)
    {     
        java.util.ResourceBundle resources = 
                java.util.ResourceBundle.getBundle("gui.resources.Default", p_locale);
        int signal_layer_count = p_layer_structure.signal_layer_count();
        int item_count = signal_layer_count + 1;
        boolean add_inner_layer_item = signal_layer_count > 2;
        if (add_inner_layer_item)
        {
            ++item_count;
        }
        this.layer_arr = new Layer [item_count];
        this.layer_arr[0] = new Layer(resources.getString("all"), ALL_LAYER_INDEX);
        int curr_layer_no = 0;
        if (add_inner_layer_item )
        {
            this.layer_arr[1] = new Layer(resources.getString("inner"), INNER_LAYER_INDEX);
            ++curr_layer_no;
        }
        for (int i = 0; i <  signal_layer_count; ++i)
        {
            ++curr_layer_no;
            board.Layer curr_signal_layer = p_layer_structure.get_signal_layer(i);
            layer_arr[curr_layer_no] = new Layer(curr_signal_layer.name, p_layer_structure.get_no(curr_signal_layer));
        }
        this.setModel(new javax.swing.DefaultComboBoxModel(layer_arr));
        this.setSelectedIndex(0);
    }
    
    public Layer get_selected_layer()
    {
        return (Layer) this.getSelectedItem();
    }
    
    private final Layer [] layer_arr;
    
    /**
     * Layers of the board layer structure plus layer "all".
     * Index is the layer number in the board layer structure or -1 for layer "all".
     */
    public static class Layer
    {
        Layer(String p_name, int p_index)
        {
            name = p_name;
            index = p_index;
        }
        
        public String toString()
        {
            return name;
        }
        
        final String name;
        
        /** The index in the board layer_structure, -1 for the layers with name "all" or "inner" */
        final int index;
    }
    
    /** The layer index, when all layers are selected. */
    public final static int ALL_LAYER_INDEX = -1;
    /** The layer index, when all inner layers ar selected. */
    public final static int INNER_LAYER_INDEX = -2;
}
