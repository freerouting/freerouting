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
 * Plane.java
 *
 * Created on 19. Mai 2004, 08:39
 */

package designformats.specctra;



/**
 * Class for reading and writing plane scopes from dsn-files.
 *
 * @author  alfons
 */
public class Plane extends ScopeKeyword
{
    
    /** Creates a new instance of Plane */
    public Plane()
    {
        super("plane");
    }
    
    public boolean read_scope(ReadScopeParameter p_par)
    {
        // read the net name
        String net_name = null;
        boolean skip_window_scopes = p_par.host_cad != null && p_par.host_cad.equalsIgnoreCase("allegro");
        // Cadence Allegro cutouts the pins on power planes, which leads to performance problems
        // when dividing a conduction area into convex pieces.
        Shape.ReadAreaScopeResult conduction_area = null;
        try
        {
            Object next_token = p_par.scanner.next_token();
            if (!(next_token instanceof String))
            {
                System.out.println("Plane.read_scope: String expected");
                return false;
            }
            net_name = (String) next_token;
            conduction_area = Shape.read_area_scope(p_par.scanner, p_par.layer_structure, skip_window_scopes);
        }
        catch (java.io.IOException e)
        {
            System.out.println("Plane.read_scope: IO error scanning file");
            System.out.println(e);
            return false;
        }
        ReadScopeParameter.PlaneInfo plane_info = new ReadScopeParameter.PlaneInfo(conduction_area, net_name);
        p_par.plane_list.add(plane_info);
        return true;
    }
    
    public static void write_scope(WriteScopeParameter p_par, board.ConductionArea p_conduction) throws java.io.IOException
    {
        int net_count = p_conduction.net_count();
        if (net_count <= 0 || net_count > 1)
        {
            System.out.println("Plane.write_scope: unexpected net count");
            return;
        }
        String net_name = p_par.board.rules.nets.get(p_conduction.get_net_no(0)).name;
        geometry.planar.Area curr_area = p_conduction.get_area();
        int layer_no = p_conduction.get_layer();
        board.Layer board_layer = p_par.board.layer_structure.arr[ layer_no];
        Layer plane_layer = new Layer(board_layer.name, layer_no, board_layer.is_signal);
        geometry.planar.Shape boundary_shape;
        geometry.planar.Shape [] holes;
        if (curr_area instanceof geometry.planar.Shape)
        {
            boundary_shape = (geometry.planar.Shape) curr_area;
            holes = new geometry.planar.Shape [0];
        }
        else
        {
            boundary_shape = curr_area.get_border();
            holes = curr_area.get_holes();
        }
        p_par.file.start_scope();
        p_par.file.write("plane ");
        p_par.identifier_type.write(net_name, p_par.file);
        Shape dsn_shape = p_par.coordinate_transform.board_to_dsn(boundary_shape, plane_layer);
        if (dsn_shape != null)
        {
            dsn_shape.write_scope(p_par.file, p_par.identifier_type);
        }
        for (int i = 0; i < holes.length; ++i)
        {
            Shape dsn_hole = p_par.coordinate_transform.board_to_dsn(holes[i], plane_layer);
            dsn_hole.write_hole_scope(p_par.file, p_par.identifier_type);
        }
        p_par.file.end_scope();
    }
}
