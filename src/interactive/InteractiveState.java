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
 * InteractiveState.java
 *
 * Created on 5. November 2003, 12:55
 */

package interactive;

import geometry.planar.FloatPoint;

import java.awt.Graphics;

/**
 * Common base class of all interaction states with the graphical interface
 *
 * @author  Alfons Wirtz
 *
 */
public class InteractiveState
{
    /** Creates a new instance of InteractiveState */
    protected InteractiveState(InteractiveState p_return_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        this.return_state = p_return_state;
        this.hdlg = p_board_handling;
        this.logfile = p_logfile;
        this.resources =
                java.util.ResourceBundle.getBundle("interactive.resources.InteractiveState", p_board_handling.get_locale());
    }
    
    /**
     * default draw function to be overwritten in derived classes
     */
    public void draw(Graphics p_graphics)
    {
    }
    
    /**
     * Default function to be overwritten in derived classes.
     * Returns the return_state of this state, if the state is left
     * after the method, or else this state.
     */
    public InteractiveState left_button_clicked(FloatPoint p_location)
    {
        return this;
    }
    
    /*
     * Actions to be taken when a mouse button is released.
     * Default function to be overwritten in derived classes.
     * Returns the return_state of this state, if the state is left
     * after the method, or else this state.
     */
    public InteractiveState button_released()
    {
        return this;
    }
    
    /**
     * Actions to be taken, when the location of the mouse pointer changes.
     * Default function to be overwritten in derived classes.
     * Returns the return_state of this state, if the state ends
     * after the method, or else this state.
     */
    public InteractiveState mouse_moved()
    {
        FloatPoint mouse_position =
                hdlg.coordinate_transform.board_to_user(hdlg.get_current_mouse_position());
        hdlg.screen_messages.set_mouse_position(mouse_position);
        return this;
    }
    
    /**
     * Actions to be taken when the mouse moves with a button pressed down.
     * Default function to be overwritten in derived classes.
     * Returns the return_state of this state, if the state is left
     * after the method, or else this state.
     */
    public InteractiveState mouse_dragged(FloatPoint p_point)
    {
        return this;
    }
    
    /**
     * Actions to be taken when the left mouse button is pressed down.
     * Default function to be overwritten in derived classes.
     * Returns the return_state of this state, if the state is left
     * after the method, or else this state.
     */
    public InteractiveState mouse_pressed(FloatPoint p_point)
    {
        return this;
    }
    
    /**
     * Action to be taken, when the mouse wheel was turned..
     */
    public InteractiveState mouse_wheel_moved(int p_rotation)
    {
        java.awt.geom.Point2D screen_mouse_pos =
                hdlg.graphics_context.coordinate_transform.board_to_screen(hdlg.get_current_mouse_position());
        hdlg.get_panel().zoom_with_mouse_wheel(screen_mouse_pos, p_rotation);
        return this;
    }
    
    /**
     * Default actions when a key shortcut is pressed.
     * Overwritten in derived classes for other key shortcut actions.
     */
    public InteractiveState key_typed(char p_key_char)
    {
        InteractiveState result = this;
        java.awt.geom.Point2D screen_mouse_pos =
                hdlg.graphics_context.coordinate_transform.board_to_screen(hdlg.get_current_mouse_position());
        if (p_key_char == 'a')
        {
            hdlg.get_panel().board_frame.zoom_all();
        }
        else if (p_key_char == 'c')
        {
            hdlg.get_panel().center_display(screen_mouse_pos);
        }
        else if (p_key_char == 'f')
        {
            result = ZoomRegionState.get_instance(hdlg.get_current_mouse_position(), this, hdlg, logfile);
        }
        else if (p_key_char =='h')
        {
            hdlg.get_panel().board_frame.select_previous_snapshot();
        }
        if (p_key_char == 'j')
        {
            hdlg.get_panel().board_frame.goto_selected_snapshot();
        }
        else if (p_key_char =='k')
        {
            hdlg.get_panel().board_frame.select_next_snapshot();
        }
        else if (p_key_char == 'o')
        {
            hdlg.get_panel().zoom_out(screen_mouse_pos);
        }
        else if (p_key_char == 'z')
        {
            hdlg.get_panel().zoom_in(screen_mouse_pos);
        }
        else if (p_key_char == ',')
        {
            // toggle the crosshair cursor
            hdlg.get_panel().set_custom_crosshair_cursor(!hdlg.get_panel().is_custom_cross_hair_cursor());
        }
        else if (p_key_char == '\n' || p_key_char == ' ')
        {
            result = this.complete();
        }
        else if (p_key_char == java.awt.event.KeyEvent.VK_ESCAPE)
        {
            result = this.cancel();
        }
        else if (Character.isDigit(p_key_char))
        {
            // change the current layer to the p_key_char-ths signal layer
            board.LayerStructure layer_structure = hdlg.get_routing_board().layer_structure;
            int d = Character.digit(p_key_char, 10);
            d = Math.min(d, layer_structure.signal_layer_count());
            // Board layers start at 0, keyboard input for layers starts at 1.
            d = Math.max(d - 1, 0);
            d = layer_structure.get_no(layer_structure.get_signal_layer(d));
            hdlg.set_current_layer(d);
        }
        return result;
    }
    
    /**
     * Action to be taken, when this state is completed and exited.
     * Default function to be overwritten in derived classes.
     * Returns the return_state of this state.
     */
    public InteractiveState complete()
    {
        if (this.return_state != this &&logfile != null)
        {
            logfile.start_scope(LogfileScope.COMPLETE_SCOPE);
        }
        return this.return_state;
    }
    
    /**
     * Actions to be taken, when this state gets cancelled.
     * Default function to be overwritten in derived classes.
     * Returns the parent state of this state.
     */
    public InteractiveState cancel()
    {
        if (this.return_state != this && logfile != null)
        {
            logfile.start_scope(LogfileScope.CANCEL_SCOPE);
        }
        return this.return_state;
    }
    
    /**
     * Action to be taken, when the current layer is changed.
     * returns false, if the layer could not be changed,
     * Default function to be overwritten in derived classes.
     */
    public boolean change_layer_action(int p_new_layer)
    {
        hdlg.set_layer(p_new_layer);
        return true;
    }
    
    /**
     * Used when reading the next point from a logfile.
     * Default function to be overwritten in derived classes.
     */
    public InteractiveState process_logfile_point(FloatPoint p_point)
    {
        return this;
    }
    
    /**
     * The default message displayed, when this state is active.
     */
    public void display_default_message()
    {
    }
    
    /**
     * Gets the identifier for displaying help for the user about this state.
     */
    public String get_help_id()
    {
        return "MenuState";
    }
    
    
    /**
     * Returns the popup menu from board_panel, which is used in this interactive state.
     * Default function to be overwritten in derived classes.
     */
    public javax.swing.JPopupMenu get_popup_menu()
    {
        return null;
    }
    /**
     * A state using toolbar must overwrite this function.
     */
    public void set_toolbar()
    {
    }
    
    /** board setting access handler for the derived classes */
    protected final BoardHandling hdlg;
    
    /** The intended state after this state is finished */
    protected InteractiveState return_state;
    
    /** if logfile != null, the interactive actions are stored in a logfile */
    protected final Logfile logfile;
    
    /** Contains the files  with the language dependent messages */
    protected final java.util.ResourceBundle resources;
}
