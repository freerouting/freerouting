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
 */

package gui;

import interactive.ScreenMessages;

import java.io.File;

import datastructures.FileFilter;
import datastructures.IdNoGenerator;

import board.TestLevel;
import board.BoardObservers;

import designformats.specctra.DsnFile;

/**
 *
 * Graphical frame of for interactive editing of a routing board.
 *
 * @author  Alfons Wirtz
 */

public class BoardFrame extends javax.swing.JFrame
{
    public enum Option
    {
        FROM_START_MENU, SINGLE_FRAME, SESSION_FILE, WEBSTART, EXTENDED_TOOL_BAR
    }
    
    /**
     * Creates a new board frame with the input design file imbedded into a host cad software.
     */
    public static BoardFrame get_embedded_instance(String p_design_file_path_name,
            BoardObservers p_observers, IdNoGenerator p_id_no_generator, java.util.Locale p_locale)
    {
        final gui.DesignFile design_file = gui.DesignFile.get_instance(p_design_file_path_name, false);
        if (design_file == null)
        {
            WindowMessage.show("designfile not found");
            return null;
        }
        gui.BoardFrame board_frame = new gui.BoardFrame(design_file, gui.BoardFrame.Option.SINGLE_FRAME,
                TestLevel.RELEASE_VERSION, p_observers, p_id_no_generator, p_locale, false);
        
        
        if (board_frame == null)
        {
            WindowMessage.show("board_frame is null");
            return null;
        }
        java.io.InputStream input_stream = design_file.get_input_stream();
        boolean read_ok =  board_frame.read(input_stream, true, null);
        if (!read_ok)
        {
            String error_message = "Unable to read design file with pathname " + p_design_file_path_name;
            board_frame.setVisible(true); // to be able to display the status message
            board_frame.screen_messages.set_status_message(error_message);
        }
        return board_frame;
    }
    
    /**
     * Creates new form BoardFrame.
     * If p_option = FROM_START_MENU this frame is created from a start menu frame.
     * If p_option = SINGLE_FRAME, this frame is created directly a single frame.
     * If p_option = Option.IN_SAND_BOX, no security sensitive actions like for example choosing
     *  If p_option = Option.WEBSTART, the application has  been started with Java Webstart.
     * files are allowed, so that the frame can be used in an applet.
     * Currently Option.EXTENDED_TOOL_BAR is used only if a new board is
     * created by the application from scratch.
     * If p_test_level > RELEASE_VERSION, functionality not yet ready for release is included.
     * Also the warning output depends on p_test_level.
     */
    public BoardFrame(DesignFile p_design, Option p_option, TestLevel p_test_level,
            java.util.Locale p_locale, boolean p_confirm_cancel)
    {
        this(p_design, p_option, p_test_level,
                new board.BoardObserverAdaptor(), new board.ItemIdNoGenerator(),
                p_locale, p_confirm_cancel);
    }
    
    /**
     * Creates new form BoardFrame.
     * The parameters p_item_observers and p_item_id_no_generator are used for syncronizing purposes,
     * if the frame is embedded into a host system,
     */
    BoardFrame(DesignFile p_design, Option p_option, TestLevel p_test_level, BoardObservers p_observers,
            datastructures.IdNoGenerator p_item_id_no_generator, java.util.Locale p_locale, boolean p_confirm_cancel)
    {
        this.design_file = p_design;
        this.is_web_start = (p_option == Option.WEBSTART);
        this.test_level = p_test_level;
        
        this.confirm_cancel = p_confirm_cancel;
        this.board_observers = p_observers;
        this.item_id_no_generator = p_item_id_no_generator;
        this.locale = p_locale;
        this.resources = java.util.ResourceBundle.getBundle("gui.resources.BoardFrame", p_locale);
        BoardMenuBar curr_menubar;
        boolean session_file_option = (p_option == Option.SESSION_FILE);
        boolean curr_help_system_used =  true;
        try
        {
            curr_menubar = BoardMenuBar.get_instance(this, curr_help_system_used, session_file_option);
        }
        catch (java.lang.NoClassDefFoundError e)
        {
            // the system-file jh.jar may be missing
            curr_help_system_used = false;
            curr_menubar = BoardMenuBar.get_instance(this, false, session_file_option);
            System.out.println("Online-Help deactivated because system file jh.jar is missing");
        }
        this.menubar = curr_menubar;
        this.help_system_used = curr_help_system_used;
        setJMenuBar(this.menubar);
        
        this.toolbar_panel = new BoardToolbar(this);
        this.add(this.toolbar_panel, java.awt.BorderLayout.NORTH);
        
        this.message_panel = new BoardPanelStatus(this.locale);
        this.add(this.message_panel, java.awt.BorderLayout.SOUTH);
        
        this.select_toolbar = new BoardToolbarSelectedItem(this, p_option == Option.EXTENDED_TOOL_BAR);
        
        this.screen_messages =
                new ScreenMessages(this.message_panel.status_message, this.message_panel.add_message,
                this.message_panel.current_layer, this.message_panel.mouse_position, this.locale);
        
        this.scroll_pane = new javax.swing.JScrollPane();
        this.scroll_pane.setPreferredSize(new java.awt.Dimension(1150, 800));
        this.scroll_pane.setVerifyInputWhenFocusTarget(false);
        this.add(scroll_pane, java.awt.BorderLayout.CENTER);
        
        this.board_panel = new BoardPanel(screen_messages, this, this.is_web_start, p_locale);
        this.scroll_pane.setViewportView(board_panel);
        
        this.setTitle(resources.getString("title"));
        this.addWindowListener(new WindowStateListener());
        
        this.pack();
    }
    
    /**
     * Reads interactive actions from a logfile.
     */
    void read_logfile(java.io.InputStream p_input_stream)
    {
        board_panel.board_handling.read_logfile(p_input_stream);
    }
    
    
    /**
     * Reads an existing board design from file.
     * If p_is_import, the design is read from a scpecctra dsn file.
     * Returns false, if the file is invalid.
     */
    boolean read(java.io.InputStream p_input_stream, boolean p_is_import, javax.swing.JTextField p_message_field)
    {
        java.awt.Point viewport_position = null;
        if (p_is_import)
        {
            DsnFile.ReadResult read_result = board_panel.board_handling.import_design(p_input_stream, this.board_observers,
                    this.item_id_no_generator, this.test_level);
            if (read_result !=  DsnFile.ReadResult.OK)
            {
                if (p_message_field != null)
                {
                    if (read_result == DsnFile.ReadResult.OUTLINE_MISSING)
                    {
                        p_message_field.setText(resources.getString("error_7"));
                    }
                    else
                    {
                         p_message_field.setText(resources.getString("error_6"));
                    }
                }
                return false;
            }
            viewport_position = new java.awt.Point(0,0);
            initialize_windows();
        }
        else
        {
            java.io.ObjectInputStream object_stream = null;
            try
            {
                object_stream = new java.io.ObjectInputStream(p_input_stream);
            }
            catch (java.io.IOException e)
            {
                return false;
            }
            boolean read_ok = board_panel.board_handling.read_design(object_stream, this.test_level);
            if (!read_ok)
            {
                return false;
            }
            java.awt.Point frame_location;
            java.awt.Rectangle frame_bounds;
            try
            {
                viewport_position = (java.awt.Point) object_stream.readObject();
                frame_location = (java.awt.Point) object_stream.readObject();
                frame_bounds = (java.awt.Rectangle) object_stream.readObject();
            }
            catch (Exception e)
            {
                return false;
            }
            this.setLocation(frame_location);
            this.setBounds(frame_bounds);
            
            allocate_permanent_subwindows();
            
            for (int i = 0; i < this.permanent_subwindows.length; ++i)
            {
                this.permanent_subwindows[i].read(object_stream);
            }
        }
        try
        {
            p_input_stream.close();
        }
        catch (java.io.IOException e)
        {
            return false;
        }
        
        java.awt.Dimension panel_size = board_panel.board_handling.graphics_context.get_panel_size();
        board_panel.setSize(panel_size);
        board_panel.setPreferredSize(panel_size);
        if (viewport_position != null)
        {
            board_panel.set_viewport_position(viewport_position);
        }
        board_panel.create_popup_menus();
        board_panel.init_colors();
        board_panel.board_handling.create_ratsnest();
        this.hilight_selected_button();
        this.toolbar_panel.unit_factor_field.setValue(board_panel.board_handling.coordinate_transform.user_unit_factor);
        this.toolbar_panel.unit_combo_box.setSelectedItem(board_panel.board_handling.coordinate_transform.user_unit);
        this.setVisible(true);
        if (p_is_import)
        {
            // Read the default gui settings, if gui default file exists.
            java.io.InputStream input_stream = null;
            boolean defaults_file_found;
            if (this.is_web_start)
            {
                input_stream = WebStart.get_file_input_stream(BoardFrame.GUI_DEFAULTS_FILE_NAME);
                defaults_file_found = (input_stream != null);
            }
            else
            {
                File defaults_file = new File(this.design_file.get_parent(), GUI_DEFAULTS_FILE_NAME);
                defaults_file_found = true;
                try
                {
                    input_stream = new java.io.FileInputStream(defaults_file);
                }
                catch (java.io.FileNotFoundException e)
                {
                    defaults_file_found = false;
                }
            }
            if (defaults_file_found)
            {
                boolean read_ok = gui.GUIDefaultsFile.read(this, board_panel.board_handling, input_stream);
                if (!read_ok)
                {
                    screen_messages.set_status_message(resources.getString("error_1"));
                }
                try
                {
                    input_stream.close();
                }
                catch (java.io.IOException e)
                {
                    return false;
                }
            }
            this.zoom_all();
        }
        return true;
    }
    
    
    /**
     * Saves the interactive settings and the design file to disk.
     * Returns false, if the save failed.
     */
    boolean save()
    {
        if (this.design_file == null)
        {
            return false;
        }
        java.io.OutputStream output_stream = null;
        java.io.ObjectOutputStream object_stream = null;
        try
        {
            output_stream = new java.io.FileOutputStream(this.design_file.get_output_file());
            object_stream = new java.io.ObjectOutputStream(output_stream);
        }
        catch (java.io.IOException e)
        {
            screen_messages.set_status_message(resources.getString("error_2"));
            return false;
        }
        catch (java.security.AccessControlException e)
        {
            screen_messages.set_status_message(resources.getString("error_3"));
            return false;
        }
        boolean save_ok = board_panel.board_handling.save_design_file(object_stream);
        if (!save_ok)
        {
            return false;
        }
        try
        {
            object_stream.writeObject(board_panel.get_viewport_position());
            object_stream.writeObject(this.getLocation());
            object_stream.writeObject(this.getBounds());
        }
        catch (java.io.IOException e)
        {
            screen_messages.set_status_message(resources.getString("error_4"));
            return false;
        }
        for (int i = 0; i < this.permanent_subwindows.length; ++i)
        {
            this.permanent_subwindows[i].save(object_stream);
        }
        try
        {
            object_stream.flush();
            output_stream.close();
        }
        catch (java.io.IOException e)
        {
            screen_messages.set_status_message(resources.getString("error_5"));
            return false;
        }
        return true;
    }
    
    /**
     * Sets contexts sensitive help for the input component, if the help system is used.
     */
    public void set_context_sensitive_help(java.awt.Component p_component, String p_help_id)
    {
        if (this.help_system_used)
        {
            java.awt.Component curr_component;
            if (p_component instanceof javax.swing.JFrame)
            {
                curr_component = ((javax.swing.JFrame) p_component).getRootPane();
            }
            else
            {
                curr_component = p_component;
            }
            String help_id = "html_files." + p_help_id;
            javax.help.CSH.setHelpIDString(curr_component, help_id);
            if (!this.is_web_start)
            {
                help_broker.enableHelpKey(curr_component, help_id, help_set);
            }
        }
    }
    
    /** Sets the toolbar to the buttons of the selected item state. */
    public void set_select_toolbar()
    {
        getContentPane().remove(toolbar_panel);
        getContentPane().add(select_toolbar, java.awt.BorderLayout.NORTH);
        repaint();
    }
    
    /** Sets the toolbar buttons to the select. route and drag menu buttons of the main menu. */
    public void set_menu_toolbar()
    {
        getContentPane().remove(select_toolbar);
        getContentPane().add(toolbar_panel, java.awt.BorderLayout.NORTH);
        repaint();
    }
    
    /**
     * Calculates the absolute location of the board frame in his outmost parent frame.
     */
    java.awt.Point absolute_panel_location()
    {
        int x = this.scroll_pane.getX();
        int y = this.scroll_pane.getY();
        java.awt.Container curr_parent = this.scroll_pane.getParent();
        while (curr_parent != null)
        {
            x += curr_parent.getX();
            y += curr_parent.getY();
            curr_parent = curr_parent.getParent();
        }
        return new java.awt.Point(x, y);
    }
    
    /** Sets the displayed region to the whole board. */
    public void zoom_all()
    {
        board_panel.board_handling.adjust_design_bounds();
        java.awt.Rectangle display_rect = board_panel.get_viewport_bounds();
        java.awt.Rectangle design_bounds = board_panel.board_handling.graphics_context.get_design_bounds();
        double width_factor = display_rect.getWidth() /design_bounds.getWidth();
        double height_factor = display_rect.getHeight() /design_bounds.getHeight();
        double zoom_factor =  Math.min(width_factor, height_factor);
        java.awt.geom.Point2D zoom_center = board_panel.board_handling.graphics_context.get_design_center();
        board_panel.zoom(zoom_factor, zoom_center);
        java.awt.geom.Point2D new_vieport_center = board_panel.board_handling.graphics_context.get_design_center();
        board_panel.set_viewport_center(new_vieport_center);
        
    }
    
    /**
     * Actions to be taken when this frame vanishes.
     */
    public void dispose()
    {
        for (int i = 0; i < this.permanent_subwindows.length; ++i)
        {
            if (this.permanent_subwindows[i] != null)
            {
                this.permanent_subwindows[i].dispose();
                this.permanent_subwindows[i] = null;
            }
        }
        for (BoardTemporarySubWindow curr_subwindow : this.temporary_subwindows)
        {
            if (curr_subwindow != null)
            {
                curr_subwindow.board_frame_disposed();
            }
        }
        if (board_panel.board_handling != null)
        {
            board_panel.board_handling.dispose();
            board_panel.board_handling = null;
        }
        super.dispose();
    }
    
    private void allocate_permanent_subwindows()
    {
        this.color_manager = new ColorManager(this);
        this.permanent_subwindows[0] = this.color_manager;
        this.object_visibility_window = WindowObjectVisibility.get_instance(this);
        this.permanent_subwindows[1] = this.object_visibility_window;
        this.layer_visibility_window = WindowLayerVisibility.get_instance(this);
        this.permanent_subwindows[2] = this.layer_visibility_window;
        this.display_misc_window = new WindowDisplayMisc(this);
        this.permanent_subwindows[3] = this.display_misc_window;
        this.snapshot_window = new WindowSnapshot(this);
        this.permanent_subwindows[4] = this.snapshot_window;
        this.route_parameter_window = new WindowRouteParameter(this);
        this.permanent_subwindows[5] = this.route_parameter_window;
        this.select_parameter_window = new WindowSelectParameter(this);
        this.permanent_subwindows[6] = this.select_parameter_window;
        this.clearance_matrix_window = new WindowClearanceMatrix(this);
        this.permanent_subwindows[7] = this.clearance_matrix_window;
        this.padstacks_window = new WindowPadstacks(this);
        this.permanent_subwindows[8] = this.padstacks_window;
        this.packages_window = new WindowPackages(this);
        this.permanent_subwindows[9] = this.packages_window;
        this.components_window = new WindowComponents(this);
        this.permanent_subwindows[10] = this.components_window;
        this.incompletes_window = new WindowIncompletes(this);
        this.permanent_subwindows[11] = this.incompletes_window;
        this.clearance_violations_window = new WindowClearanceViolations(this);
        this.permanent_subwindows[12] = this.clearance_violations_window;
        this.net_info_window = new WindowNets(this);
        this.permanent_subwindows[13] = this.net_info_window;
        this.via_window = new WindowVia(this);
        this.permanent_subwindows[14] = this.via_window;
        this.edit_vias_window = new WindowEditVias(this);
        this.permanent_subwindows[15] = this.edit_vias_window;
        this.edit_net_rules_window = new WindowNetClasses(this);
        this.permanent_subwindows[16] = this.edit_net_rules_window;
        this.assign_net_classes_window = new WindowAssignNetClass(this);
        this.permanent_subwindows[17] = this.assign_net_classes_window;
        this.length_violations_window = new WindowLengthViolations(this);
        this.permanent_subwindows[18] = this.length_violations_window;
        this.about_window = new WindowAbout(this.locale);
        this.permanent_subwindows[19] = this.about_window;
        this.move_parameter_window = new WindowMoveParameter(this);
        this.permanent_subwindows[20] = this.move_parameter_window;
        this.unconnected_route_window = new WindowUnconnectedRoute(this);
        this.permanent_subwindows[21] = this.unconnected_route_window;
        this.route_stubs_window = new WindowRouteStubs(this);
        this.permanent_subwindows[22] = this.route_stubs_window;
        this.autoroute_parameter_window = new WindowAutorouteParameter(this);
        this.permanent_subwindows[23] = this.autoroute_parameter_window;
    }
    
    /**
     * Creates the additional frames of the board frame.
     */
    private void initialize_windows()
    {
        allocate_permanent_subwindows();
        
        this.setLocation(120, 0);
        
        this.select_parameter_window.setLocation(0, 0);
        this.select_parameter_window.setVisible(true);
        
        this.route_parameter_window.setLocation(0, 100);
        this.autoroute_parameter_window.setLocation(0, 200);
        this.move_parameter_window.setLocation(0, 50);
        this.clearance_matrix_window.setLocation(0, 150);
        this.via_window.setLocation(50, 150);
        this.edit_vias_window.setLocation(100, 150);
        this.edit_net_rules_window.setLocation(100, 200);
        this.assign_net_classes_window.setLocation(100, 250);
        this.padstacks_window.setLocation(100, 30);
        this.packages_window.setLocation(200, 30);
        this.components_window.setLocation(300, 30);
        this.incompletes_window.setLocation(400, 30);
        this.clearance_violations_window.setLocation(500, 30);
        this.length_violations_window.setLocation(550, 30);
        this.net_info_window.setLocation(350, 30);
        this.unconnected_route_window.setLocation(650, 30);
        this.route_stubs_window.setLocation(600, 30);
        this.snapshot_window.setLocation(0, 250);
        this.layer_visibility_window.setLocation(0, 450);
        this.object_visibility_window.setLocation(0, 550);
        this.display_misc_window.setLocation(0, 350);
        this.color_manager.setLocation(0, 600);
        this.about_window.setLocation(200, 200);
    }
    
    /**
     * Returns the currently used locale for the language dependent output.
     */
    public java.util.Locale get_locale()
    {
        return this.locale;
    }
    
    /**
     * Sets the background of the board panel
     */
    public void set_board_background(java.awt.Color p_color)
    {
        this.board_panel.setBackground(p_color);
    }
    
    /**
     * Refreshs all displayed coordinates after the user unit has changed.
     */
    public void refresh_windows()
    {
        for (int i = 0; i < this.permanent_subwindows.length; ++i)
        {
            if (permanent_subwindows[i] != null)
            {
                permanent_subwindows[i].refresh();
            }
        }
    }
    
    /**
     * Sets the selected button in the menu button button group
     */
    public void hilight_selected_button()
    {
        this.toolbar_panel.hilight_selected_button();
    }
    
    /**
     * Restore the selected snapshot in the snapshot window.
     */
    public void goto_selected_snapshot()
    {
        if (this.snapshot_window != null)
        {
            this.snapshot_window.goto_selected();
        }
    }
    
    /**
     * Selects  the snapshot, which is previous to the current selected snapshot.
     * Thecurent selected snapshot will be no more selected.
     */
    public void select_previous_snapshot()
    {
        if (this.snapshot_window != null)
        {
            this.snapshot_window.select_previous_item();
        }
    }
    
    /**
     * Selects  the snapshot, which is next to the current selected snapshot.
     * Thecurent selected snapshot will be no more selected.
     */
    public void select_next_snapshot()
    {
        if (this.snapshot_window != null)
        {
            this.snapshot_window.select_next_item();
        }
    }
    
    /**
     * Used for storing the subwindowfilters in a snapshot.
     */
    public SubwindowSelections get_snapshot_subwindow_selections()
    {
        SubwindowSelections result = new SubwindowSelections();
        result.incompletes_selection = this.incompletes_window.get_snapshot_info();
        result.packages_selection = this.packages_window.get_snapshot_info();
        result.nets_selection = this.net_info_window.get_snapshot_info();
        result.components_selection = this.components_window.get_snapshot_info();
        result.padstacks_selection = this.padstacks_window.get_snapshot_info();
        return result;
    }
    
    /**
     * Used for restoring the subwindowfilters from a snapshot.
     */
    public void set_snapshot_subwindow_selections(SubwindowSelections p_filters)
    {
        this.incompletes_window.set_snapshot_info(p_filters.incompletes_selection);
        this.packages_window.set_snapshot_info(p_filters.packages_selection);
        this.net_info_window.set_snapshot_info(p_filters.nets_selection);
        this.components_window.set_snapshot_info(p_filters.components_selection);
        this.padstacks_window.set_snapshot_info(p_filters.padstacks_selection);
    }
    
    /**
     * Repaints this board frame and all the subwindows of the board.
     */
    public void repaint_all()
    {
        this.repaint();
        for (int i = 0; i < permanent_subwindows.length; ++i)
        {
            permanent_subwindows[i].repaint();
        }
    }
    
    /** The scroll pane for the panel of the routing board. */
    final javax.swing.JScrollPane scroll_pane;
    
    /** The menubar of this frame */
    final BoardMenuBar menubar;
    
    /** The panel with the graphical representation of the board. */
    final BoardPanel board_panel;
    
    /** The panel with the toolbars */
    private final BoardToolbar toolbar_panel;
    
    /** The toolbar used in the selected item state. */
    private final javax.swing.JToolBar select_toolbar;
    
    /** The panel with the message line */
    private final BoardPanelStatus message_panel;
    
    final ScreenMessages screen_messages;
    
    private final TestLevel test_level;
    
    /** true, if the frame is created by an application running under Java Web Start */
    final boolean is_web_start;
    
    private final boolean help_system_used;
    static javax.help.HelpSet help_set = null;
    static javax.help.HelpBroker help_broker = null;
    
    private final boolean confirm_cancel;
    
    private final java.util.ResourceBundle resources;
    private java.util.Locale locale;
    
    private final BoardObservers  board_observers;
    private final datastructures.IdNoGenerator item_id_no_generator;
    
    WindowAbout about_window = null;
    WindowRouteParameter route_parameter_window = null;
    WindowAutorouteParameter autoroute_parameter_window = null;
    WindowSelectParameter select_parameter_window = null;
    WindowMoveParameter move_parameter_window = null;
    WindowClearanceMatrix clearance_matrix_window = null;
    WindowVia via_window = null;
    WindowEditVias edit_vias_window = null;
    WindowNetClasses edit_net_rules_window = null;
    WindowAssignNetClass assign_net_classes_window = null;
    WindowPadstacks padstacks_window = null;
    WindowPackages packages_window = null;
    WindowIncompletes incompletes_window = null;
    WindowNets net_info_window = null;
    WindowClearanceViolations clearance_violations_window = null;
    WindowLengthViolations length_violations_window = null;
    WindowUnconnectedRoute unconnected_route_window = null;
    WindowRouteStubs route_stubs_window  = null;
    WindowComponents components_window = null;
    WindowLayerVisibility layer_visibility_window = null;
    WindowObjectVisibility object_visibility_window = null;
    WindowDisplayMisc display_misc_window = null;
    WindowSnapshot snapshot_window = null;
    ColorManager color_manager = null;
    
    /** The windows above stored in an array */
    static final int SUBWINDOW_COUNT = 24;
    BoardSavableSubWindow[] permanent_subwindows = new BoardSavableSubWindow[SUBWINDOW_COUNT];
    
    java.util.Collection<BoardTemporarySubWindow> temporary_subwindows = new java.util.LinkedList<BoardTemporarySubWindow>();
    
    
    DesignFile design_file = null;
    
    static final String [] log_file_extensions = { "log" };
    
    static final String GUI_DEFAULTS_FILE_NAME = "gui_defaults.par";
    static final String GUI_DEFAULTS_FILE_BACKUP_NAME = "gui_defaults.par.bak";
    
    static final FileFilter logfile_filter = new FileFilter(log_file_extensions);
    
    private class WindowStateListener extends java.awt.event.WindowAdapter
    {
        public void windowClosing(java.awt.event.WindowEvent evt)
        {
            setDefaultCloseOperation(DISPOSE_ON_CLOSE );
            if (confirm_cancel)
            {
                int option = javax.swing.JOptionPane.showConfirmDialog(null, resources.getString("confirm_cancel"),
                        null, javax.swing.JOptionPane.YES_NO_OPTION);
                if (option == javax.swing.JOptionPane.NO_OPTION)
                {
                    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
                }
            }
        }
        
        public void windowIconified(java.awt.event.WindowEvent evt)
        {
            for (int i = 0; i < permanent_subwindows.length; ++i)
            {
                permanent_subwindows[i].parent_iconified();
            }
            for (BoardSubWindow curr_subwindow : temporary_subwindows)
            {
                if (curr_subwindow != null)
                {
                    curr_subwindow.parent_iconified();
                }
            }
        }
        
        public void windowDeiconified(java.awt.event.WindowEvent evt)
        {
            for (int i = 0; i < permanent_subwindows.length; ++i)
            {
                if (permanent_subwindows[i] != null)
                {
                    permanent_subwindows[i].parent_deiconified();
                }
            }
            for (BoardSubWindow curr_subwindow : temporary_subwindows)
            {
                if (curr_subwindow != null)
                {
                    curr_subwindow.parent_deiconified();
                }
            }
        }
    }
    
    /**
     * Used for storing the subwindow filters in a snapshot.
     */
    public static class SubwindowSelections implements java.io.Serializable
    {
        private WindowObjectListWithFilter.SnapshotInfo incompletes_selection;
        private WindowObjectListWithFilter.SnapshotInfo packages_selection;
        private WindowObjectListWithFilter.SnapshotInfo nets_selection;
        private WindowObjectListWithFilter.SnapshotInfo components_selection;
        private WindowObjectListWithFilter.SnapshotInfo padstacks_selection;
    }
}

