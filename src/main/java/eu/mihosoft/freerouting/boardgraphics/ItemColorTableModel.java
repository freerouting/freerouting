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
 * ColorTableModel.java
 *
 * Created on 4. August 2003, 08:26
 */

package boardgraphics;

import java.awt.Color;

/**
 * Stores the layer dependent colors used for drawing for the items on the board.
 *
 * @author Alfons Wirtz
 */
public class ItemColorTableModel extends ColorTableModel implements java.io.Serializable
{
    
    public ItemColorTableModel(board.LayerStructure p_layer_structure, java.util.Locale p_locale)
    {
        super(p_layer_structure.arr.length, p_locale);
  
        int row_count = p_layer_structure.arr.length;
        final int item_type_count = ColumnNames.values().length - 1;
        int signal_layer_no = 0;
        for( int layer = 0; layer < row_count; ++layer)
        {
            boolean is_signal_layer = p_layer_structure.arr[layer].is_signal;
            data[layer] = new Object [item_type_count + 1];
            Object[] curr_row = data[layer];
            curr_row[0] = p_layer_structure.arr[layer].name;
            if (layer == 0)
            {
                curr_row[ColumnNames.PINS.ordinal()] = new Color(150, 50, 0);
                curr_row[ColumnNames.TRACES.ordinal()] = Color.red;
                curr_row[ColumnNames.CONDUCTION_AREAS.ordinal()] = new Color(0, 150, 0);
                curr_row[ColumnNames.KEEPOUTS.ordinal()] = new Color(0, 110, 110);
                curr_row[ColumnNames.PLACE_KEEPOUTS.ordinal()] = new Color(150, 50, 0);
            }
            else if (layer == row_count - 1)
            {
                curr_row[ColumnNames.PINS.ordinal()] = new Color(160, 80, 0);
                curr_row[ColumnNames.TRACES.ordinal()] = Color.blue;
                curr_row[ColumnNames.CONDUCTION_AREAS.ordinal()] = new Color(100, 100, 0);
                curr_row[ColumnNames.KEEPOUTS.ordinal()] = new Color(0, 100, 160);
                curr_row[ColumnNames.PLACE_KEEPOUTS.ordinal()] = new Color(160, 80, 0);
            }
            else // inner layer
            {
                if (is_signal_layer)
                {
                    // currenntly 6 different default colors for traces on the inner layers
                    final int different_inner_colors = 6;
                    int remainder = signal_layer_no % different_inner_colors;
                    if (remainder % different_inner_colors == 1)
                    {
                        curr_row[ColumnNames.TRACES.ordinal()] = Color.GREEN;
                    }
                    else if (remainder % different_inner_colors == 2)
                    {
                        curr_row[ColumnNames.TRACES.ordinal()] = Color.YELLOW;
                    }
                    else if (remainder % different_inner_colors == 3)
                    {
                        curr_row[ColumnNames.TRACES.ordinal()] = new Color(200, 100, 255);
                    }
                    else if (remainder % different_inner_colors == 4)
                    {
                        curr_row[ColumnNames.TRACES.ordinal()] = new Color(255, 150, 150);
                    }
                    else if (remainder % different_inner_colors == 5)
                    {
                        curr_row[ColumnNames.TRACES.ordinal()] = new Color(100, 150, 0);
                    }
                    else
                    {
                        curr_row[ColumnNames.TRACES.ordinal()] = new Color(0, 200, 255);
                    }
                }
                else // power layer
                {
                    curr_row [ColumnNames.TRACES.ordinal()] = Color.BLACK;
                }
                curr_row[ColumnNames.PINS.ordinal()] = new Color(255, 150, 0);
                curr_row[ColumnNames.CONDUCTION_AREAS.ordinal()] = new Color(0, 200, 60);
                curr_row[ColumnNames.KEEPOUTS.ordinal()] = new Color(0, 200, 200);
                curr_row[ColumnNames.PLACE_KEEPOUTS.ordinal()] = new Color(150, 50, 0);
            }
            curr_row[ColumnNames.VIAS.ordinal()] = new Color(200, 200, 0);
            curr_row[ColumnNames.FIXED_VIAS.ordinal()] = curr_row[ColumnNames.VIAS.ordinal()];
            curr_row[ColumnNames.FIXED_TRACES.ordinal()] = curr_row[ColumnNames.TRACES.ordinal()];
            curr_row[ColumnNames.VIA_KEEPOUTS.ordinal()] = new Color(100, 100, 100);
            if (is_signal_layer)
            {
                ++signal_layer_no;
            }
        }
    }
    
    public ItemColorTableModel(java.io.ObjectInputStream p_stream)
            throws java.io.IOException, java.lang.ClassNotFoundException
    {
        super(p_stream);
    }
    
    /**
     * Copy construcror.
     */
    public ItemColorTableModel(ItemColorTableModel p_item_color_model)
    {
        super(p_item_color_model.data.length, p_item_color_model.locale);
        for (int i = 0; i < this.data.length; ++i)
        {
            this.data[i] = new Object[p_item_color_model.data[i].length];
            System.arraycopy(p_item_color_model.data[i], 0, this.data[i], 0, this.data[i].length);
        }
    }
    
    public int getColumnCount()
    {
        return ColumnNames.values().length;
    }
    
    public int getRowCount()
    {
        return data.length;
    }
    
    public String getColumnName(int p_col)
    {
        java.util.ResourceBundle resources = 
                java.util.ResourceBundle.getBundle("boardgraphics.resources.ColorTableModel", this.locale);
        return resources.getString(ColumnNames.values()[p_col].toString());
    }
    
    public void setValueAt(Object p_value, int p_row, int p_col)
    {
        super.setValueAt(p_value, p_row, p_col);
        this.item_colors_precalculated = false;
    }
    
    /**
     * Don't need to implement this method unless your table's
     * editable.
     */
    public boolean isCellEditable(int p_row, int p_col)
    {
        //Note that the data/cell address is constant,
        //no matter where the cell appears onscreen.
        return p_col >= 1;
    }
    
    Color[] get_trace_colors(boolean p_fixed)
    {
        if (!item_colors_precalculated)
        {
            precalulate_item_colors();
        }
        Color[] result;
        if (p_fixed)
        {
            result = precalculated_item_colors[ColumnNames.FIXED_TRACES.ordinal() - 1];
        }
        else
        {
            result = precalculated_item_colors[ColumnNames.TRACES.ordinal() - 1];
        }
        return result;
    }
    
    Color[] get_via_colors(boolean p_fixed)
    {
        if (!item_colors_precalculated)
        {
            precalulate_item_colors();
        }
        Color[] result;
        if (p_fixed)
        {
            result = precalculated_item_colors[ColumnNames.FIXED_VIAS.ordinal() - 1];
        }
        else
        {
            result = precalculated_item_colors[ColumnNames.VIAS.ordinal() - 1];
        }
        return result;
    }
    
    Color[] get_pin_colors()
    {
        if (!item_colors_precalculated)
        {
            precalulate_item_colors();
        }
        return precalculated_item_colors[ColumnNames.PINS.ordinal() - 1];
    }
    
    Color[] get_conduction_colors()
    {
        if (!item_colors_precalculated)
        {
            precalulate_item_colors();
        }
        return precalculated_item_colors[ColumnNames.CONDUCTION_AREAS.ordinal() - 1];
    }
    
    Color[] get_obstacle_colors()
    {
        if (!item_colors_precalculated)
        {
            precalulate_item_colors();
        }
        return precalculated_item_colors[ColumnNames.KEEPOUTS.ordinal() - 1];
    }
    
    Color[] get_via_obstacle_colors()
    {
        if (!item_colors_precalculated)
        {
            precalulate_item_colors();
        }
        return precalculated_item_colors[ColumnNames.VIA_KEEPOUTS.ordinal() - 1];
    }
    
    Color[] get_place_obstacle_colors()
    {
        if (!item_colors_precalculated)
        {
            precalulate_item_colors();
        }
        return precalculated_item_colors[ColumnNames.PLACE_KEEPOUTS.ordinal() - 1];
    }
    
    
    public void set_trace_colors(Color[] p_color_arr, boolean p_fixed)
    {
        if (p_fixed)
        {
            set_colors(ColumnNames.FIXED_TRACES.ordinal(), p_color_arr);
        }
        else
        {
            set_colors(ColumnNames.TRACES.ordinal(), p_color_arr);
        }
    }
    
    public void set_via_colors(Color[] p_color_arr, boolean p_fixed)
    {
        if (p_fixed)
        {
            set_colors(ColumnNames.FIXED_VIAS.ordinal(), p_color_arr);
        }
        else
        {
            set_colors(ColumnNames.VIAS.ordinal(), p_color_arr);
        }
    }
    
    public void set_pin_colors(Color[] p_color_arr)
    {
        set_colors(ColumnNames.PINS.ordinal(), p_color_arr);
    }
    
    public void set_conduction_colors(Color[] p_color_arr)
    {
        set_colors(ColumnNames.CONDUCTION_AREAS.ordinal(), p_color_arr);
    }
    
    public void set_keepout_colors(Color[] p_color_arr)
    {
        set_colors(ColumnNames.KEEPOUTS.ordinal(), p_color_arr);
    }
    
    public void set_via_keepout_colors(Color[] p_color_arr)
    {
        set_colors(ColumnNames.VIA_KEEPOUTS.ordinal(), p_color_arr);
    }
    
    public void set_place_keepout_colors(Color[] p_color_arr)
    {
        set_colors(ColumnNames.PLACE_KEEPOUTS.ordinal(), p_color_arr);
    }
    
    
    
    private void set_colors(int p_item_type, Color[] p_color_arr)
    {
        for (int layer = 0; layer < this.data.length - 1; ++layer)
        {
            int color_index = layer % p_color_arr.length;
            this.data[layer][p_item_type] = p_color_arr[color_index];
        }
        data[this.data.length - 1][p_item_type] = p_color_arr[p_color_arr.length - 1];
        this.item_colors_precalculated = false;
    }
    
    
    private void precalulate_item_colors()
    {
        precalculated_item_colors = new Color[ColumnNames.values().length - 1][];
        for (int i = 0; i <  precalculated_item_colors.length; ++i)
        {
            precalculated_item_colors[i] = new Color[data.length];
            Color[] curr_row = precalculated_item_colors[i];
            for (int j = 0; j < data.length; ++j)
            {
                curr_row[j] = (Color) getValueAt(j, i + 1);
            }
        }
        this.item_colors_precalculated = true;
    }
    
    private transient boolean item_colors_precalculated = false;
    
    private transient Color[][] precalculated_item_colors = null;
    
    private enum ColumnNames
    {
        LAYER, TRACES, FIXED_TRACES, VIAS, FIXED_VIAS, PINS, CONDUCTION_AREAS, KEEPOUTS, VIA_KEEPOUTS, PLACE_KEEPOUTS
    }
}
