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
 * Keyword.java
 *
 * Created on 8. Mai 2004, 10:23
 */
package designformats.specctra;

/**
 * Enumeration class for keywords of the specctra dsn file format
 *
 * @author  alfons
 */
public class Keyword
{

    /**
     * The only instances of the internal classes:
     *
     * ScopeKeywords  with an inividual read_scope method are defined in an extra class,
     */
    public static final Keyword ABSOLUTE = new Keyword("absolute");
    public static final Keyword ACTIVE = new Keyword("active");
    public static final Keyword AGAINST_PREFERRED_DIRECTION_TRACE_COSTS = new Keyword("against_preferred_direction_trace_costs");
    public static final Keyword ATTACH = new Keyword("attach");
    public static final Keyword AUTOROUTE = new Keyword("autoroute");
    public static final Keyword AUTOROUTE_SETTINGS = new Keyword("autoroute_settings");
    public static final Keyword BACK = new Keyword("back");
    public static final Keyword BOUNDARY = new Keyword("boundary");
    public static final Keyword CIRCUIT = new Keyword("circuit");
    public static final Keyword CIRCLE = new Keyword("circle");
    public static final Keyword CLASS = new Keyword("class");
    public static final Keyword CLASS_CLASS = new Keyword("class_class");
    public static final Keyword CLASSES = new Keyword("classes");
    public static final ScopeKeyword COMPONENT_SCOPE = new Component();
    public static final Keyword CONSTANT = new Keyword("constant");
    public static final Keyword CONTROL = new Keyword("control");
    public static final Keyword CLEARANCE = new Keyword("clearance");
    public static final Keyword CLEARANCE_CLASS = new Keyword("clearance_class");
    public static final Keyword CLOSED_BRACKET = new Keyword(")");
    public static final Keyword FANOUT = new Keyword("fanout");
    public static final Keyword FLIP_STYLE = new Keyword("flip_style");
    public static final Keyword FIX = new Keyword("fix");
    public static final Keyword FORTYFIVE_DEGREE = new Keyword("fortyfive_degree");
    public static final Keyword FROMTO = new Keyword("fromto");
    public static final Keyword FRONT = new Keyword("front");
    public static final Keyword GENERATED_BY_FREEROUTE = new Keyword("generated_by_freeroute");
    public static final Keyword HORIZONTAL = new Keyword("horizontal");
    public static final Keyword HOST_CAD = new Keyword("host_cad");
    public static final Keyword HOST_VERSION = new Keyword("host_version");
    public static final Keyword IMAGE = new Keyword("image");
    public static final Keyword KEEPOUT = new Keyword("keepout");
    public static final Keyword LAYER = new Keyword("layer");
    public static final Keyword LAYER_RULE = new Keyword("layer_rule");
    public static final Keyword LENGTH = new Keyword("length");
    public static final ScopeKeyword LIBRARY_SCOPE = new Library();
    public static final Keyword LOCK_TYPE = new Keyword("lock_type");
    public static final Keyword LOGICAL_PART = new Keyword("logical_part");
    public static final Keyword LOGICAL_PART_MAPPING = new Keyword("logical_part_mapping");
    public static final Keyword NET = new Keyword("net");
    public static final Keyword NETWORK_OUT = new Keyword("network_out");
    public static final ScopeKeyword NETWORK_SCOPE = new Network();
    public static final Keyword NINETY_DEGREE = new Keyword("ninety_degree");
    public static final Keyword NONE = new Keyword("none");
    public static final Keyword NORMAL = new Keyword("normal");
    public static final Keyword OFF = new Keyword("off");
    public static final Keyword ON = new Keyword("on");
    public static final Keyword OPEN_BRACKET = new Keyword("(");
    public static final Keyword ORDER = new Keyword("order");
    public static final Keyword OUTLINE = new Keyword("outline");
    public static final Keyword PADSTACK = new Keyword("padstack");
    public static final ScopeKeyword PART_LIBRARY_SCOPE = new PartLibrary();
    public static final ScopeKeyword PARSER_SCOPE = new Parser();
    public static final ScopeKeyword PCB_SCOPE = new ScopeKeyword("pcb");
    public static final Keyword PIN = new Keyword("pin");
    public static final Keyword PINS = new Keyword("pins");
    public static final Keyword PLACE = new Keyword("place");
    public static final ScopeKeyword PLACE_CONTROL = new PlaceControl();
    public static final Keyword PLACE_KEEPOUT = new Keyword("place_keepout");
    public static final ScopeKeyword PLACEMENT_SCOPE = new Placement();
    public static final ScopeKeyword PLANE_SCOPE = new Plane();
    public static final Keyword PLANE_VIA_COSTS = new Keyword("plane_via_costs");
    public static final Keyword PREFERRED_DIRECTION = new Keyword("preferred_direction");
    public static final Keyword PREFERRED_DIRECTION_TRACE_COSTS = new Keyword("preferred_direction_trace_costs");
    public static final Keyword SNAP_ANGLE = new Keyword("snap_angle");
    public static final Keyword POLYGON = new Keyword("polygon");
    public static final Keyword POLYGON_PATH = new Keyword("polygon_path");
    public static final Keyword POLYLINE_PATH = new Keyword("polyline_path");
    public static final Keyword POSITION = new Keyword("position");
    public static final Keyword POSTROUTE = new Keyword("postroute");
    public static final Keyword POWER = new Keyword("power");
    public static final Keyword PULL_TIGHT = new Keyword("pull_tight");
    public static final Keyword RECTANGLE = new Keyword("rectangle");
    public static final Keyword RESOLUTION_SCOPE = new Resolution();
    public static final Keyword ROTATE = new Keyword("rotate");
    public static final Keyword ROTATE_FIRST = new Keyword("rotate_first");
    public static final Keyword ROUTES = new Keyword("routes");
    public static final Keyword RULE = new Keyword("rule");
    public static final Keyword RULES = new Keyword("rules");
    public static final Keyword SESSION = new Keyword("session");
    public static final Keyword SHAPE = new Keyword("shape");
    public static final Keyword SHOVE_FIXED = new Keyword("shove_fixed");
    public static final Keyword SIDE = new Keyword("side");
    public static final Keyword SIGNAL = new Keyword("signal");
    public static final Keyword SPARE = new Keyword("spare");
    public static final Keyword START_PASS_NO = new Keyword("start_pass_no");
    public static final Keyword START_RIPUP_COSTS = new Keyword("start_ripup_costs");
    public static final Keyword STRING_QUOTE = new Keyword("string_quote");
    public static final ScopeKeyword STRUCTURE_SCOPE = new Structure();
    public static final Keyword TYPE = new Keyword("type");
    public static final Keyword USE_LAYER = new Keyword("use_layer");
    public static final Keyword USE_NET = new Keyword("use_net");
    public static final Keyword USE_VIA = new Keyword("use_via");
    public static final Keyword VERTICAL = new Keyword("vertical");
    public static final Keyword VIA = new Keyword("via");
    public static final Keyword VIAS = new Keyword("vias");
    public static final Keyword VIA_AT_SMD = new Keyword("via_at_smd");
    public static final Keyword VIA_COSTS = new Keyword("via_costs");
    public static final Keyword VIA_KEEPOUT = new Keyword("via_keepout");
    public static final Keyword VIA_RULE = new Keyword("via_rule");
    public static final Keyword WIDTH = new Keyword("width");
    public static final Keyword WINDOW = new Keyword("window");
    public static final Keyword WIRE = new Keyword("wire");
    public static final ScopeKeyword WIRING_SCOPE = new Wiring();
    public static final Keyword WRITE_RESOLUTION = new Keyword("write_resolution");

    /**
     * Returns the name string of this Keyword.
     * The name is used for debugging purposes.
     */
    public String get_name()
    {
        return name;
    }
    private final String name;

    /** prevents creating more instances */
    protected Keyword(String p_name)
    {
        name = p_name;
    }
}
