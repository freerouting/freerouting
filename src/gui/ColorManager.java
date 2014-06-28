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
 * ColorManager.java
 *
 * Created on 3. August 2003, 11:16
 */

package gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;

import boardgraphics.GraphicsContext;


/**
 * Window for changing the colors of board objects.
 *
 * @author  Alfons Wirtz
 */
public class ColorManager extends BoardSavableSubWindow
{
    
    /** Creates a new instance of ColorManager */
    public ColorManager(BoardFrame p_board_frame)
    {
        GraphicsContext graphics_context = p_board_frame.board_panel.board_handling.graphics_context;
        java.util.ResourceBundle resources = 
                java.util.ResourceBundle.getBundle("gui.resources.Default", p_board_frame.get_locale());
        this.setTitle(resources.getString("color_manager"));
        final JPanel panel =  new JPanel();
        final int textfield_height = 17;
        final int table_width = 1100;
        final int item_color_table_height = graphics_context.item_color_table.getRowCount() * textfield_height;
        panel.setPreferredSize(new Dimension(10 + table_width, 70 + item_color_table_height));
        
        this.item_color_table = new JTable(graphics_context.item_color_table);
        item_color_table.setPreferredScrollableViewportSize(new Dimension(table_width, item_color_table_height));
        JScrollPane item_scroll_pane = init_color_table(item_color_table, p_board_frame.get_locale());
        panel.add(item_scroll_pane, BorderLayout.NORTH);
        
        this.other_color_table = new JTable(graphics_context.other_color_table);
        this.other_color_table.setPreferredScrollableViewportSize(new Dimension(table_width, textfield_height));
        JScrollPane other_scroll_pane = init_color_table(other_color_table, p_board_frame.get_locale());
        panel.add(other_scroll_pane, BorderLayout.SOUTH);       
        getContentPane().add(panel, BorderLayout.CENTER);
        p_board_frame.set_context_sensitive_help(this, "WindowDisplay_Colors");
        this.pack();
        this.setResizable(false);
    }
    
    /**
     * Reassigns the table model variables because they may have changed in p_graphics_context.
     */
    public void set_table_models(GraphicsContext p_graphics_context)
    {
        this.item_color_table.setModel(p_graphics_context.item_color_table);
        this.other_color_table.setModel(p_graphics_context.other_color_table);
    }
    
    
    
    /**
     * Initializes p_color_table and return the created scroll_pane of the color table.
     */
    private static JScrollPane init_color_table(JTable p_color_table, java.util.Locale p_locale)
    {
        //Create the scroll pane and add the table to it.
        JScrollPane scroll_pane = new JScrollPane(p_color_table);
        //Set up renderer and editor for the Color columns.
        p_color_table.setDefaultRenderer(Color.class, new ColorRenderer(true));
        
        setUpColorEditor(p_color_table, p_locale);
        return scroll_pane;
    }
    
    //Set up the editor for the Color cells.
    private static void setUpColorEditor(JTable p_table, java.util.Locale p_locale)
    {
        //First, set up the button that brings up the dialog.
        final JButton button = new JButton("")
        {
            public void setText(String s)
            {
                //Button never shows text -- only color.
            }
        };
        button.setBackground(Color.white);
        button.setBorderPainted(false);
        button.setMargin(new Insets(0,0,0,0));
        
        //Now create an editor to encapsulate the button, and
        //set it up as the editor for all Color cells.
        final ColorEditor colorEditor = new ColorEditor(button);
        p_table.setDefaultEditor(Color.class, colorEditor);
        
        //Set up the dialog that the button brings up.
        final JColorChooser colorChooser = new JColorChooser();
        ActionListener okListener = new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                colorEditor.currentColor = colorChooser.getColor();
            }
        };
        java.util.ResourceBundle resources = 
                java.util.ResourceBundle.getBundle("gui.resources.Default", p_locale);
        final JDialog dialog = JColorChooser.createDialog(button,
                resources.getString("pick_a_color"), true, colorChooser, okListener, null);
        
        //Here's the code that brings up the dialog.
        button.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                button.setBackground(colorEditor.currentColor);
                colorChooser.setColor(colorEditor.currentColor);
                //Without the following line, the dialog comes up
                //in the middle of the screen.
                //dialog.setLocationRelativeTo(button);
                dialog.setVisible(true);
            }
        });
    }
    
    private final JTable item_color_table;
    private final JTable other_color_table;
    
    private static class ColorRenderer extends JLabel implements TableCellRenderer
    {
        Border unselectedBorder = null;
        Border selectedBorder = null;
        boolean isBordered = true;
        
        public ColorRenderer(boolean p_is_bordered)
        {
            super();
            this.isBordered = p_is_bordered;
            setOpaque(true); //MUST do this for background to show up.
        }
        
        public Component getTableCellRendererComponent(
                JTable p_table, Object p_color,
                boolean p_is_selected, boolean p_has_focus,
                int p_row, int p_column)
        {
            setBackground((Color)p_color);
            if (isBordered)
            {
                if (p_is_selected)
                {
                    if (selectedBorder == null)
                    {
                        selectedBorder =
                                BorderFactory.createMatteBorder(2,5,2,5,
                                p_table.getSelectionBackground());
                    }
                    setBorder(selectedBorder);
                }
                else
                {
                    if (unselectedBorder == null)
                    {
                        unselectedBorder = BorderFactory.createMatteBorder(2,5,2,5,
                                p_table.getBackground());
                    }
                    setBorder(unselectedBorder);
                }
            }
            return this;
        }
    }
    
    /**
     * The editor button that brings up the dialog.
     * We extend DefaultCellEditor for convenience,
     * even though it mean we have to create a dummy
     * check box.  Another approach would be to copy
     * the implementation of TableCellEditor methods
     * from the source code for DefaultCellEditor.
     */
    private static class ColorEditor extends DefaultCellEditor
    {
        Color currentColor = null;
        
        public ColorEditor(JButton b)
        {
            super(new JCheckBox()); //Unfortunately, the constructor
            //expects a check box, combo box,
            //or text field.
            editorComponent = b;
            setClickCountToStart(1); //This is usually 1 or 2.
            
            //Must do this so that editing stops when appropriate.
            b.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    fireEditingStopped();
                }
            });
        }
        
        protected void fireEditingStopped()
        {
            super.fireEditingStopped();
        }
        
        public Object getCellEditorValue()
        {
            return currentColor;
        }
        
        public Component getTableCellEditorComponent(JTable table,
                Object value,
                boolean isSelected,
                int row,
                int column)
        {
            ((JButton)editorComponent).setText(value.toString());
            currentColor = (Color)value;
            return editorComponent;
        }
    }
}
