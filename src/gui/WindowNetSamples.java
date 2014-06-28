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
 * WindowNetSampleDesigns.java
 *
 * Created on 11. November 2006, 07:49
 *
 */

package gui;

import java.util.zip.ZipInputStream;

import java.net.URL;
import java.net.URLConnection;

/**
 * Window with a list for selecting samples in the net.
 *
 * @author Alfons Wirtz
 */
public abstract class WindowNetSamples extends BoardSubWindow
{
    
    /** Creates a new instance of WindowNetSampleDesigns */
    public WindowNetSamples(java.util.Locale p_locale, String p_title, String p_button_name, int p_row_count)
    {
        this.locale = p_locale;
        this.resources = java.util.ResourceBundle.getBundle("gui.resources.WindowNetSamples", p_locale);
        this.setTitle(resources.getString(p_title));
        
        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE );
        
        // create main panel
        final javax.swing.JPanel main_panel = new javax.swing.JPanel();
        this.add(main_panel);
        main_panel.setLayout(new java.awt.BorderLayout());
        javax.swing.border.Border panel_border = javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10);
        main_panel.setBorder(panel_border);
        
        
        // create open button
        javax.swing.JButton open_button = new javax.swing.JButton(resources.getString(p_button_name));
        open_button.addActionListener(new OpenListener());
        main_panel.add(open_button, java.awt.BorderLayout.SOUTH);
        
        // create list with the sample designs
        this.list = new javax.swing.JList(this.list_model);
        fill_list();
        this.list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        this.list.setSelectedIndex(0);
        this.list.setVisibleRowCount(p_row_count);
        this.list.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseClicked(java.awt.event.MouseEvent evt)
            {
                if (evt.getClickCount() > 1)
                {
                    button_pushed();
                }
            }
        });
        
        javax.swing.JScrollPane list_scroll_pane = new javax.swing.JScrollPane(this.list);
        list_scroll_pane.setPreferredSize(new java.awt.Dimension(200, 20 * p_row_count));
        main_panel.add(list_scroll_pane, java.awt.BorderLayout.CENTER);
        this.pack();
    }
    
    
    /**
     * Fill the list with the examples.
     */
    protected abstract void fill_list();
    
    /**
     * Action to be perfomed. when the button is pushed after selecting an item in the list.
     */
    protected abstract void button_pushed();
    
    /**
     * Opens a zipped archive from an URL in the net.
     * Returns a zipped input stream, who is positioned at the start of p_file_name,
     * or null, if an error occured,
     */
    protected static ZipInputStream open_zipped_file(String p_archive_name, String p_file_name)
    {
        String archive_path_name = MainApplication.WEB_FILE_BASE_NAME + p_archive_name + ".zip";
        URL archive_url = null;
        try
        {
            archive_url = new URL(archive_path_name);
        }
        catch(java.net.MalformedURLException e)
        {
            return null;
        }
        java.io.InputStream input_stream = null;
        ZipInputStream zip_input_stream = null;
        URLConnection net_connection = null;
        try
        {
            net_connection = archive_url.openConnection();
        }
        catch (Exception e)
        {
            return null;
        }
        try
        {
            input_stream = net_connection.getInputStream();
        }
        catch (java.io.IOException e)
        {
            return null;
        }
        catch (java.security.AccessControlException e)
        {
            return null;
        }
        try
        {
            zip_input_stream = new ZipInputStream(input_stream);
        }
        catch (Exception e)
        {
            WindowMessage.show("unable to get zip input stream");
            return null;
        }
        String compare_name = p_archive_name + "/" + p_file_name;
        java.util.zip.ZipEntry curr_entry = null;
        for (;;)
        {
            try
            {
                curr_entry = zip_input_stream.getNextEntry();
            }
            catch (Exception E)
            {
                return null;
            }
            if (curr_entry == null)
            {
                return null;
            }
            String design_name = curr_entry.getName();
            if (design_name.equals(compare_name))
            {
                break;
            }
        }
        return zip_input_stream;
    }
    
    
    /**
     * Opens a sample design on the website.
     */
    protected static BoardFrame open_design(String p_archive_name, String p_design_name, java.util.Locale p_locale)
    {
        ZipInputStream zip_input_stream = open_zipped_file(p_archive_name, p_design_name);
        if (zip_input_stream == null)
        {
            return null;
        }
        DesignFile design_file = DesignFile.get_instance("sharc_routed.dsn", true);
        BoardFrame new_frame =
                new BoardFrame(design_file, BoardFrame.Option.WEBSTART, board.TestLevel.RELEASE_VERSION,
                p_locale, false);
        boolean read_ok = new_frame.read(zip_input_stream, true, null);
        if (!read_ok)
        {
            return null;
        }
        new_frame.setVisible(true);
        return new_frame;
    }
    
    protected final java.util.ResourceBundle resources;
    protected final java.util.Locale locale;
    
    protected javax.swing.DefaultListModel list_model = new javax.swing.DefaultListModel();
    protected final javax.swing.JList list;
    
    private class OpenListener implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent p_evt)
        {
            button_pushed();
        }
    }
}
