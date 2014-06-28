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
 * OtherColorTableModel.java
 *
 * Created on 5. August 2003, 07:39
 */

package boardgraphics;

import java.awt.Color;

/**
 * Stores the colors used for the background and highlighting.
 *
 * @author Alfons Wirtz
 */
public class OtherColorTableModel extends ColorTableModel implements java.io.Serializable
{
    
    public OtherColorTableModel(java.util.Locale p_locale)
    {
        super(1, p_locale);
        data[0] = new Color [ColumnNames.values().length];
        Object[] curr_row = data[0];
        curr_row[ColumnNames.BACKGROUND.ordinal()] = new Color(70, 70, 70);
        curr_row[ColumnNames.HIGHLIGHT.ordinal()] = Color.white;
        curr_row[ColumnNames.INCOMPLETES.ordinal()] = Color.white;
        curr_row[ColumnNames.OUTLINE.ordinal()] = new Color(100, 150, 255);
        curr_row[ColumnNames.VIOLATIONS.ordinal()] = Color.magenta;
        curr_row[ColumnNames.COMPONENT_FRONT.ordinal()] = Color.blue;
        curr_row[ColumnNames.COMPONENT_BACK.ordinal()] = Color.red;
        curr_row[ColumnNames.LENGTH_MATCHING_AREA.ordinal()] = Color.green;
    }
    
    public OtherColorTableModel(java.io.ObjectInputStream p_stream)
            throws java.io.IOException, java.lang.ClassNotFoundException
    {
        super(p_stream);
    }
    
    /**
     * Copy construcror.
     */
    public OtherColorTableModel(OtherColorTableModel p_item_color_model)
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
    
    public String getColumnName(int p_col)
    {
        java.util.ResourceBundle resources = 
                java.util.ResourceBundle.getBundle("boardgraphics.resources.ColorTableModel", this.locale);
        return resources.getString(ColumnNames.values()[p_col].toString());
    }
    
    public boolean isCellEditable(int p_row, int p_col)
    {
        return true;
    }
    
    public Color get_background_color()
    {
        return (Color)(data[0][ColumnNames.BACKGROUND.ordinal()]);
    }
    
    public Color get_hilight_color()
    {
        return (Color) (data[0][ColumnNames.HIGHLIGHT.ordinal()]);
    }
    
    public Color get_incomplete_color()
    {
        return (Color) (data[0][ColumnNames.INCOMPLETES.ordinal()]);
    }
    
    public Color get_outline_color()
    {
        return (Color) (data[0][ColumnNames.OUTLINE.ordinal()]);
    }
    
    public Color get_violations_color()
    {
        return (Color) (data[0][ColumnNames.VIOLATIONS.ordinal()]);
    }
    
    public Color get_component_color(boolean p_front)
    {
        Color result;
        if (p_front)
        {
            result = (Color) (data[0][ColumnNames.COMPONENT_FRONT.ordinal()]);
        }
        else
        {
            result = (Color) (data[0][ColumnNames.COMPONENT_BACK.ordinal()]);
        }
        return result;
    }
    
    public Color get_length_matching_area_color()
    {
        return (Color) (data[0][ColumnNames.LENGTH_MATCHING_AREA.ordinal()]);
    }
    
    public void set_background_color(Color p_color)
    {
        data[0][ColumnNames.BACKGROUND.ordinal()] = p_color;
    }
    
    public void set_hilight_color(Color p_color)
    {
        data[0][ColumnNames.HIGHLIGHT.ordinal()] = p_color;
    }
    
    public void set_incomplete_color(Color p_color)
    {
        data[0][ColumnNames.INCOMPLETES.ordinal()] = p_color;
    }
    
    public void set_violations_color(Color p_color)
    {
        data[0][ColumnNames.VIOLATIONS.ordinal()] = p_color;
    }
    
    public void set_outline_color(Color p_color)
    {
        data[0][ColumnNames.OUTLINE.ordinal()] = p_color;
    }
    
    public void set_component_color(Color p_color, boolean p_front)
    {
        if (p_front)
        {
            data[0][ColumnNames.COMPONENT_FRONT.ordinal()] = p_color;
        }
        else
        {
            data[0][ColumnNames.COMPONENT_BACK.ordinal()] = p_color;
        }
    }
    
    public void set_length_matching_area_color(Color p_color)
    {
        data[0][ColumnNames.LENGTH_MATCHING_AREA.ordinal()] = p_color;
    }
    
    private enum ColumnNames
    {
        BACKGROUND, HIGHLIGHT, INCOMPLETES, VIOLATIONS, OUTLINE, COMPONENT_FRONT, COMPONENT_BACK, LENGTH_MATCHING_AREA
    }
}
