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
 * SnapShot.java
 *
 * Created on 8. November 2004, 08:01
 */

package interactive;

/**
 * Snapshot of the client situation in an interactive session.
 *
 * @author  Alfons Wirtz
 */
public class SnapShot implements java.io.Serializable
{
    /**
     * Returns a new snapshot or null, if the current interactive state
     * is not suitable to generate a snapshot.
     */
    public static SnapShot get_instance(String p_name, BoardHandling p_board_handling)
    {
        InteractiveState interactive_state = p_board_handling.interactive_state;
        if (!(interactive_state instanceof MenuState))
        {
            return null;
        }
        return new SnapShot(p_name, p_board_handling);
    }
    
    /** Creates a SnapShot of the display region and the interactive settings */
    private SnapShot(String p_name, BoardHandling p_board_handling)
    {
        this.name = p_name;
        this.settings = new Settings(p_board_handling.settings);
        this.interactive_state_no = get_no(p_board_handling.interactive_state);
        this.graphics_context = new boardgraphics.GraphicsContext(p_board_handling.graphics_context);
        this.viewport_position = new java.awt.Point(p_board_handling.get_panel().get_viewport_position());
        this.subwindow_filters = p_board_handling.get_panel().board_frame.get_snapshot_subwindow_selections();
    }
    
    public String toString()
    {
        return this.name;
    }
    
    
    public java.awt.Point copy_viewport_position()
    {
        if (this.viewport_position == null)
        {
            return null;
        }
        return new java.awt.Point(this.viewport_position);
    }
    
    /**
     * Goes to this shnapshot in interactive board etiting.
     */
    public void go_to(interactive.BoardHandling p_board_handling)
    {
        interactive.SnapShot.Attributes snapshot_attributes = this.settings.snapshot_attributes;

        if (snapshot_attributes.object_visibility)
        {
            p_board_handling.graphics_context.color_intensity_table =
                    new boardgraphics.ColorIntensityTable(this.graphics_context.color_intensity_table);
        }
        if (snapshot_attributes.layer_visibility)
        {
            p_board_handling.graphics_context.set_layer_visibility_arr(this.graphics_context.copy_layer_visibility_arr());
        }
 
        if (snapshot_attributes.interactive_state)
        {
            p_board_handling.set_interactive_state(this.get_interactive_state(p_board_handling, p_board_handling.logfile));
        }
        if (snapshot_attributes.selection_layers)
        {
            p_board_handling.settings.select_on_all_visible_layers = this.settings.select_on_all_visible_layers;
        }
        if (snapshot_attributes.selectable_items)
        {
            p_board_handling.settings.item_selection_filter = new board.ItemSelectionFilter(this.settings.item_selection_filter);
        }
        if (snapshot_attributes.current_layer)
        {
            p_board_handling.settings.layer = this.settings.layer;
        }
        if (snapshot_attributes.rule_selection)
        {
            p_board_handling.settings.manual_rule_selection = this.settings.manual_rule_selection;
        }
        if (snapshot_attributes.manual_rule_settings)
        {
            p_board_handling.settings.manual_trace_clearance_class = this.settings.manual_trace_clearance_class;
            p_board_handling.settings.manual_via_rule_index = this.settings.manual_via_rule_index;
            System.arraycopy(this.settings.manual_trace_half_width_arr, 0,
                    p_board_handling.settings.manual_trace_half_width_arr, 0,
                    p_board_handling.settings.manual_trace_half_width_arr.length);
        }
        if (snapshot_attributes.push_and_shove_enabled)
        {
            p_board_handling.settings.push_enabled = this.settings.push_enabled;
        }
        if (snapshot_attributes.drag_components_enabled)
        {
            p_board_handling.settings.drag_components_enabled = this.settings.drag_components_enabled;
        }
        if (snapshot_attributes.pull_tight_region)
        {
            p_board_handling.settings.trace_pull_tight_region_width = this.settings.trace_pull_tight_region_width;
        }
        if (snapshot_attributes.component_grid)
        {
            p_board_handling.settings.horizontal_component_grid = this.settings.horizontal_component_grid;
            p_board_handling.settings.vertical_component_grid = this.settings.vertical_component_grid;
        }  
        if (snapshot_attributes.info_list_selections)
        {
            p_board_handling.get_panel().board_frame.set_snapshot_subwindow_selections(this.subwindow_filters);
        }
    }
    
    /**
     * Returns a new InterativeState from the data of this instance.
     */
    public InteractiveState get_interactive_state(BoardHandling p_board_handling, Logfile p_logfile)
    {
        InteractiveState result;
        if (this.interactive_state_no == 1)
        {
            result = RouteMenuState.get_instance(p_board_handling, p_logfile);
        }
        else if (this.interactive_state_no == 2)
        {
            result = DragMenuState.get_instance(p_board_handling, p_logfile);
        }
        else
        {
            result = SelectMenuState.get_instance(p_board_handling, p_logfile);
        }
        return result;
    }
    
    /**
     * Create a number for writing an interactive state to disk.
     * Only MenuStates are saved. The default is SelectState.
     */
    private static int get_no(InteractiveState p_interactive_state)
    {
        int result;
        if (p_interactive_state instanceof RouteMenuState)
        {
            result = 1;
        }
        else if (p_interactive_state instanceof DragMenuState)
        {
            result = 2;
        }
        else
        {
            result = 0;
        }
        return result;
    }
    
    private final String name;
    public final Settings settings;
    private final int interactive_state_no;
    public final boardgraphics.GraphicsContext graphics_context;
    private final java.awt.Point viewport_position;
    public final gui.BoardFrame.SubwindowSelections subwindow_filters;
    
    /**
     * Defines the data of the snapshot selected for restoring.
     */
    public static class Attributes implements java.io.Serializable
    {
        Attributes()
        {
            object_colors = true;
            object_visibility = true;
            layer_visibility = true;
            display_region = true;
            interactive_state = true;
            selection_layers = true;
            selectable_items = true;
            current_layer = true;
            rule_selection = true;
            manual_rule_settings = true;
            push_and_shove_enabled =  true;
            drag_components_enabled =  true;
            pull_tight_region = true;
            component_grid = true;
            info_list_selections = true;
        }
        
        /** Copy constructor */
        Attributes(Attributes p_attributes)
        {
            object_colors = p_attributes.object_colors;
            object_visibility = p_attributes.object_visibility;
            layer_visibility = p_attributes.layer_visibility;
            display_region = p_attributes.display_region;
            interactive_state = p_attributes.interactive_state;
            selection_layers = p_attributes.selection_layers;
            selectable_items = p_attributes.selectable_items;
            current_layer = p_attributes.current_layer;
            rule_selection = p_attributes.rule_selection;
            manual_rule_settings = p_attributes.manual_rule_settings;
            push_and_shove_enabled = p_attributes.push_and_shove_enabled;
            drag_components_enabled = p_attributes.drag_components_enabled;
            pull_tight_region = p_attributes.pull_tight_region;
            component_grid = p_attributes.component_grid;
            info_list_selections = p_attributes.info_list_selections;
        }
        
        public boolean object_colors;
        public boolean object_visibility;
        public boolean layer_visibility;
        public boolean display_region;
        public boolean interactive_state;
        public boolean selection_layers;
        public boolean selectable_items;
        public boolean current_layer;
        public boolean rule_selection;
        public boolean manual_rule_settings;
        public boolean push_and_shove_enabled;
        public boolean drag_components_enabled;
        public boolean pull_tight_region;
        public boolean component_grid;
        public boolean info_list_selections;
    }
}
