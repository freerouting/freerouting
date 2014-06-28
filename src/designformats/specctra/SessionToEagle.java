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
 * SessionToEagle.java
 *
 * Created on 8. Dezember 2004, 07:42
 */

package designformats.specctra;


/**
 * Transformes a Specctra session file into an Eagle script file.
 *
 * @author Alfons Wirtz
 */
public class SessionToEagle extends javax.swing.JFrame
{
    
    public static boolean get_instance(java.io.InputStream p_session, java.io.OutputStream p_output_stream,
            board.BasicBoard p_board)
    {
        if (p_output_stream == null)
        {
            return false;
        }
        
        // create a scanner for reading the session_file.
        
        Scanner scanner = new SpecctraFileScanner(p_session);
        
        // create a file_writer for the eagle script file.
        java.io.OutputStreamWriter file_writer = new java.io.OutputStreamWriter(p_output_stream);
        
        boolean result = true;
        
        double board_scale_factor = p_board.communication.coordinate_transform.board_to_dsn(1);
        SessionToEagle new_instance = new SessionToEagle(scanner, file_writer, p_board,
                p_board.communication.unit,  p_board.communication.resolution, board_scale_factor);
        
        try
        {
            result = new_instance.process_session_scope();
        }
        catch (java.io.IOException e)
        {
            System.out.println("unable to process session scope");
            result = false;
        }
        
        // close files
        try
        {
            p_session.close();
            file_writer.close();
        }
        catch (java.io.IOException e)
        {
            System.out.println("unable to close files");
        }
        return result;
    }
    
    SessionToEagle(Scanner p_scanner, java.io.OutputStreamWriter p_out_file, board.BasicBoard p_board,
            board.Unit p_unit, double p_session_file_scale_dominator, double p_board_scale_factor)
    {
        scanner = p_scanner;
        out_file = p_out_file;
        board = p_board;
        this.specctra_layer_structure = new LayerStructure(p_board.layer_structure);
        unit = p_unit;
        session_file_scale_denominator = p_session_file_scale_dominator;
        board_scale_factor = p_board_scale_factor;
    }
    
    /**
     * Processes the outmost scope of the session file.
     * Returns false, if an error occured.
     */
    private boolean process_session_scope() throws java.io.IOException
    {
        
        // read the first line of the session file
        Object next_token = null;
        for (int i = 0; i < 3; ++i)
        {
            next_token = this.scanner.next_token();
            boolean keyword_ok = true;
            if (i == 0)
            {
                keyword_ok = (next_token == Keyword.OPEN_BRACKET);
            }
            else if (i == 1)
            {
                keyword_ok = (next_token == Keyword.SESSION);
                this.scanner.yybegin(SpecctraFileScanner.NAME); // to overread the name of the pcb for i = 2
            }
            if (!keyword_ok)
            {
                System.out.println("SessionToEagle.process_session_scope specctra session file format expected");
                return false;
            }
        }
        
        // Write the header of the eagle script file.
        
        this.out_file.write("GRID ");
        this.out_file.write(this.unit.toString());
        this.out_file.write("\n");
        this.out_file.write("SET WIRE_BEND 2\n");
        this.out_file.write("SET OPTIMIZING OFF\n");
        
        // Activate all layers in Eagle.
        
        for (int i = 0; i < this.board.layer_structure.arr.length; ++i)
        {
            this.out_file.write("LAYER " + this.get_eagle_layer_string(i) + ";\n");
        }
        
        this.out_file.write("LAYER 17;\n");
        this.out_file.write("LAYER 18;\n");
        this.out_file.write("LAYER 19;\n");
        this.out_file.write("LAYER 20;\n");
        this.out_file.write("LAYER 23;\n");
        this.out_file.write("LAYER 24;\n");
        
        // Generate Code to remove the complete route.
        // Write a bounding rectangle with GROUP (Min_X-1 Min_Y-1) (Max_X+1 Max_Y+1);
        
        geometry.planar.IntBox board_bounding_box = this.board.get_bounding_box();
        
        Float min_x = (float) this.board_scale_factor * (board_bounding_box.ll.x - 1);
        Float min_y = (float) this.board_scale_factor * (board_bounding_box.ll.y - 1);
        Float max_x = (float) this.board_scale_factor * (board_bounding_box.ur.x + 1);
        Float max_y = (float) this.board_scale_factor * (board_bounding_box.ur.y + 1);
        
        this.out_file.write("GROUP (");
        this.out_file.write(min_x.toString());
        this.out_file.write(" ");
        this.out_file.write(min_y.toString());
        this.out_file.write(") (");
        this.out_file.write(max_x.toString());
        this.out_file.write(" ");
        this.out_file.write(max_y.toString());
        this.out_file.write(");\n");
        this.out_file.write("RIPUP;\n");
        
        // read the direct subscopes of the session scope
        for (;;)
        {
            Object prev_token = next_token;
            next_token = this.scanner.next_token();
            if (next_token == null)
            {
                // end of file
                return true;
            }
            if (next_token == Keyword.CLOSED_BRACKET)
            {
                // end of scope
                break;
            }
            
            if (prev_token == Keyword.OPEN_BRACKET)
            {
                if (next_token == Keyword.ROUTES)
                {
                    if (!process_routes_scope())
                    {
                        return false;
                    }
                }
                else if (next_token == Keyword.PLACEMENT_SCOPE)
                {
                    if (!process_placement_scope())
                    {
                        return false;
                    }
                }
                else
                {
                    // overread all scopes except the routes scope for the time being
                    ScopeKeyword.skip_scope(this.scanner);
                }
            }
        }
        // Wird nur einmal am Ende benoetigt!
        this.out_file.write("RATSNEST\n");
        return true;
    }
    
    private boolean process_placement_scope() throws java.io.IOException
    {
        // read the component scopes
        Object next_token = null;
        for (;;)
        {
            Object prev_token = next_token;
            next_token = this.scanner.next_token();
            if (next_token == null)
            {
                // unexpected end of file
                return false;
            }
            if (next_token == Keyword.CLOSED_BRACKET)
            {
                // end of scope
                break;
            }
            
            if (prev_token == Keyword.OPEN_BRACKET)
            {
                
                if (next_token == Keyword.COMPONENT_SCOPE)
                {
                    if (!process_component_placement())
                    {
                        return false;
                    }
                }
                else
                {
                    // skip unknown scope
                    ScopeKeyword.skip_scope(this.scanner);
                }
                
            }
        }
        process_swapped_pins();
        return true;
    }
    
    private boolean process_component_placement() throws java.io.IOException
    {
        ComponentPlacement component_placement = Component.read_scope(this.scanner);
        if (component_placement == null)
        {
            return false;
        }
        for (ComponentPlacement.ComponentLocation curr_location : component_placement.locations)
        {
            this.out_file.write("ROTATE =");
            Integer rotation = (int) Math.round(curr_location.rotation);
            String rotation_string;
            if (curr_location.is_front)
            {
                rotation_string = "R" + rotation.toString();
            }
            else
            {
                rotation_string = "MR" + rotation.toString();
            }
            this.out_file.write(rotation_string);
            this.out_file.write(" '");
            this.out_file.write(curr_location.name);
            this.out_file.write("';\n");
            this.out_file.write("move '");
            this.out_file.write(curr_location.name);
            this.out_file.write("' (");
            Double x_coor = curr_location.coor[0] / this.session_file_scale_denominator;
            this.out_file.write(x_coor.toString());
            this.out_file.write(" ");
            Double y_coor = curr_location.coor[1] / this.session_file_scale_denominator;
            this.out_file.write(y_coor.toString());
            this.out_file.write(");\n");
        }
        return true;
    }
    
    private boolean process_routes_scope() throws java.io.IOException
    {
        // read the direct subscopes of the routes scope
        boolean result = true;
        Object next_token = null;
        for (;;)
        {
            Object prev_token = next_token;
            next_token = this.scanner.next_token();
            if (next_token == null)
            {
                // unexpected end of file
                return false;
            }
            if (next_token == Keyword.CLOSED_BRACKET)
            {
                // end of scope
                break;
            }
            
            if (prev_token == Keyword.OPEN_BRACKET)
            {
                
                if (next_token == Keyword.NETWORK_OUT)
                {
                    result = process_network_scope();
                }
                else
                {
                    // skip unknown scope
                    ScopeKeyword.skip_scope(this.scanner);
                }
                
            }
        }
        return result;
    }
    
    private boolean process_network_scope() throws java.io.IOException
    {
        boolean result = true;
        Object next_token = null;
        // read the net scopes
        for (;;)
        {
            Object prev_token = next_token;
            next_token = this.scanner.next_token();
            if (next_token == null)
            {
                // unexpected end of file
                return false;
            }
            if (next_token == Keyword.CLOSED_BRACKET)
            {
                // end of scope
                break;
            }
            
            if (prev_token == Keyword.OPEN_BRACKET)
            {
                
                if (next_token == Keyword.NET)
                {
                    result = process_net_scope();
                }
                else
                {
                    // skip unknown scope
                    ScopeKeyword.skip_scope(this.scanner);
                }
                
            }
        }
        return result;
    }
    
    private boolean process_net_scope() throws java.io.IOException
    {
        // read the net name
        Object next_token = this.scanner.next_token();
        if (!(next_token instanceof String))
        {
            System.out.println("SessionToEagle.processnet_scope: String expected");
            return false;
        }
        String net_name = (String) next_token;
        
        // Hier alle nicht gefixten Traces und Vias des Netz mit Namen net_name
        // in der Eagle Datenhaltung loeschen.
        
        // read the wires and vias of this net
        for (;;)
        {
            Object prev_token = next_token;
            next_token = this.scanner.next_token();
            if (next_token == null)
            {
                // end of file
                return true;
            }
            if (next_token == Keyword.CLOSED_BRACKET)
            {
                // end of scope
                break;
            }
            
            if (prev_token == Keyword.OPEN_BRACKET)
            {
                if (next_token == Keyword.WIRE)
                {
                    if (!process_wire_scope(net_name))
                    {
                        return false;
                    }
                }
                else if (next_token == Keyword.VIA)
                {
                    if (!process_via_scope(net_name))
                    {
                        return false;
                    }
                }
                else
                {
                    ScopeKeyword.skip_scope(this.scanner);
                }
            }
        }
        return true;
    }
    
    private boolean process_wire_scope(String p_net_name) throws java.io.IOException
    {
        PolygonPath wire_path = null;
        Object next_token = null;
        for (;;)
        {
            Object prev_token = next_token;
            next_token = this.scanner.next_token();
            if (next_token == null)
            {
                System.out.println("SessionToEagle.process_wire_scope: unexpected end of file");
                return false;
            }
            if (next_token == Keyword.CLOSED_BRACKET)
            {
                // end of scope
                break;
            }
            if (prev_token == Keyword.OPEN_BRACKET)
            {
                if (next_token == Keyword.POLYGON_PATH)
                {
                    wire_path = Shape.read_polygon_path_scope(this.scanner, this.specctra_layer_structure);
                }
                else
                {
                    ScopeKeyword.skip_scope(this.scanner);
                }
            }
        }
        if (wire_path == null)
        {
            // conduction areas are skipped
            return true;
        }
        
        this.out_file.write("CHANGE LAYER ");
        
        this.out_file.write(wire_path.layer.name);
        this.out_file.write(";\n");
        
        //WIRE ['signal_name'] [width] [ROUND | FLAT]  [curve | @radius]
        
        this.out_file.write("WIRE '");
        
        this.out_file.write(p_net_name);
        this.out_file.write("' ");
        Double wire_width = wire_path.width / this.session_file_scale_denominator;
        this.out_file.write(wire_width.toString());
        this.out_file.write(" (");
        for (int i = 0; i < wire_path.coordinate_arr.length; ++i)
        {
            Double wire_coor = wire_path.coordinate_arr[i] / this.session_file_scale_denominator;
            this.out_file.write(wire_coor.toString());
            if (i % 2 == 0)
            {
                this.out_file.write(" ");
            }
            else
            {
                if (i == wire_path.coordinate_arr.length - 1)
                {
                    this.out_file.write(")");
                }
                else
                {
                    this.out_file.write(") (");
                }
            }
        }
        this.out_file.write(";\n");
        
        return true;
    }
    
    private boolean process_via_scope(String p_net_name) throws java.io.IOException
    {
        // read the padstack name
        Object next_token = this.scanner.next_token();
        if (!(next_token instanceof String))
        {
            System.out.println("SessionToEagle.process_via_scope: padstack name expected");
            return false;
        }
        String padstack_name = (String) next_token;
        // read the location
        double []location = new double [2];
        for (int i = 0; i < 2; ++i)
        {
            next_token = this.scanner.next_token();
            if (next_token instanceof Double)
            {
                location[i] = ((Double) next_token).doubleValue();
            }
            else if (next_token instanceof Integer)
            {
                location[i] = ((Integer) next_token).intValue();
            }
            else
            {
                System.out.println("SessionToEagle.process_via_scope: number expected");
                return false;
            }
        }
        next_token = this.scanner.next_token();
        while (next_token == Keyword.OPEN_BRACKET)
        {
            // skip unknown scopes
            ScopeKeyword.skip_scope(this.scanner);
            next_token = this.scanner.next_token();
        }
        if (next_token != Keyword.CLOSED_BRACKET)
        {
            System.out.println("SessionToEagle.process_via_scope: closing bracket expected");
            return false;
        }
        
        if (padstack_name == null)
        {
            System.out.println("SessionToEagle.process_via_scope: padstack_name missing");
            return false;
        }
        
        library.Padstack via_padstack = this.board.library.padstacks.get(padstack_name);
        
        if (via_padstack == null)
        {
            System.out.println("SessionToEagle.process_via_scope: via padstack not found");
            return false;
        }
        
        geometry.planar.ConvexShape via_shape = via_padstack.get_shape(via_padstack.from_layer());
        
        Double via_diameter = via_shape.max_width() * this.board_scale_factor;
        
        // The Padstack name is of the form Name$drill_diameter$from_layer-to_layer
        
        String [] name_parts = via_padstack.name.split("\\$", 3);
        
        // example CHANGE DRILL 0.2
        
        this.out_file.write("CHANGE DRILL ");
        if (name_parts.length > 1)
        {
            this.out_file.write(name_parts[1]);
        }
        else
        {
            // create a default drill, because it is needed in Eagle
            this.out_file.write("0.1");
        }
        this.out_file.write(";\n");
        
        
        //VIA ['signal_name'] [diameter] [shape] [layers] [flags]
        // Via Net2 0.6 round 1-4 (20.0, 222.0);
        this.out_file.write("VIA '");
        
        this.out_file.write(p_net_name);
        this.out_file.write("' ");
        
        //Durchmesser aus Padstack
        this.out_file.write(via_diameter.toString());
        
        //Shape lesen und einsetzen Square / Round / Octagon
        if (via_shape instanceof geometry.planar.Circle)
        {
            this.out_file.write(" round ");
        }
        else if (via_shape instanceof geometry.planar.IntOctagon)
        {
            this.out_file.write(" octagon ");
        }
        else
        {
            this.out_file.write(" square ");
        }
        this.out_file.write(get_eagle_layer_string(via_padstack.from_layer()));
        this.out_file.write("-");
        this.out_file.write(get_eagle_layer_string(via_padstack.to_layer()));
        this.out_file.write(" (");
        Double x_coor = location[0] / this.session_file_scale_denominator;
        this.out_file.write(x_coor.toString());
        this.out_file.write(" ");
        Double y_coor = location[1] / this.session_file_scale_denominator;
        this.out_file.write(y_coor.toString());
        this.out_file.write(");\n");
        
        return true;
    }
    
    private String get_eagle_layer_string(int p_layer_no)
    {
        if (p_layer_no < 0 || p_layer_no >= specctra_layer_structure.arr.length)
        {
            return "0";
        }
        String [] name_pieces = this.specctra_layer_structure.arr[p_layer_no].name.split("#", 2);
        return name_pieces[0];
    }
    
    private boolean process_swapped_pins() throws java.io.IOException
    {
        for (int i = 1; i <= this.board.components.count(); ++i)
        {
            if (!process_swapped_pins(i))
            {
                return false;
            }
        }
        return true;
    }
    
    private boolean process_swapped_pins(int p_component_no) throws java.io.IOException
    {
        java.util.Collection<board.Pin> component_pins = this.board.get_component_pins(p_component_no);
        boolean component_has_swapped_pins = false;
        for (board.Pin curr_pin : component_pins)
        {
            if (curr_pin.get_changed_to() != curr_pin)
            {
                component_has_swapped_pins =  true;
                break;
            }
        }
        if (!component_has_swapped_pins)
        {
            return true;
        }
        PinInfo[] pin_info_arr = new PinInfo[component_pins.size()];
        int i = 0;
        for (board.Pin curr_pin : component_pins)
        {
            pin_info_arr[i] = new PinInfo(curr_pin);
            ++i;
        }
        for (i = 0; i < pin_info_arr.length; ++i)
        {
            PinInfo curr_pin_info = pin_info_arr[i];
            if (curr_pin_info.curr_changed_to != curr_pin_info.pin.get_changed_to())
            {
                PinInfo other_pin_info = null;
                for (int j = i + 1; j < pin_info_arr.length; ++j)
                {
                    if (pin_info_arr[j].pin.get_changed_to() == curr_pin_info.pin)
                    {
                        other_pin_info = pin_info_arr[j];
                    }
                }
                if (other_pin_info == null)
                {
                    System.out.println("SessuinToEagle.process_swapped_pins: other_pin_info not found");
                    return false;
                }
                write_pin_swap(curr_pin_info.pin, other_pin_info.pin);
                curr_pin_info.curr_changed_to = other_pin_info.pin;
                other_pin_info.curr_changed_to = curr_pin_info.pin;
            }
        }
        return true;
    }
    
    private void write_pin_swap( board.Pin p_pin_1, board.Pin p_pin_2) throws java.io.IOException
    {
        int layer_no = Math.max(p_pin_1.first_layer(), p_pin_2.first_layer());
        String layer_name = board.layer_structure.arr[layer_no].name;
        
        this.out_file.write("CHANGE LAYER ");
        this.out_file.write(layer_name);
        this.out_file.write(";\n");
        
        double [] location_1 =
                this.board.communication.coordinate_transform.board_to_dsn(p_pin_1.get_center().to_float());
        double [] location_2 =
                this.board.communication.coordinate_transform.board_to_dsn(p_pin_2.get_center().to_float());
        
        this.out_file.write("PINSWAP ");
        this.out_file.write(" (");
        Double curr_coor = location_1[0];
        this.out_file.write(curr_coor.toString());
        this.out_file.write(" ");
        curr_coor = location_1[1];
        this.out_file.write(curr_coor.toString());
        this.out_file.write(") (");
        curr_coor = location_2[0];
        this.out_file.write(curr_coor.toString());
        this.out_file.write(" ");
        curr_coor = location_2[1];
        this.out_file.write(curr_coor.toString());
        this.out_file.write(");\n");
    }
    
    
    
    /** The function for scanning the session file */
    private final Scanner scanner;
    
    /** The generated Eagle script file. */
    private final java.io.OutputStreamWriter out_file;
    
    /** Some information is read from the board, because it is not contained in the speccctra session file. */
    private final board.BasicBoard board;
    
    /** The layer structure in specctra format */
    private final LayerStructure specctra_layer_structure;
    
    private final board.Unit unit;
    
    /** The scale factor for transforming coordinates from the session file to Eagle */
    private final double session_file_scale_denominator;
    
    /** The scale factor for transforming coordinates from the board to Eagle */
    private final double board_scale_factor;
    
    private static class PinInfo
    {
        PinInfo(board.Pin p_pin)
        {
            pin = p_pin;
            curr_changed_to = p_pin;
        }
        final board.Pin pin;
        board.Pin curr_changed_to;
    }
}

