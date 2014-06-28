package gui;
@SuppressWarnings("all")
%%

%class GUIDefaultsScanner
%unicode
%ignorecase 
%function next_token
%type Object
/* %debug */

LineTerminator = \r|\n|\r\n
InputCharacter = [^\r\n]
WhiteSpace     = {LineTerminator} | [ \t\f]


/* comments */
Comment = {TraditionalComment} | {EndOfLineComment}

TraditionalComment   = "/*" [^*] ~"*/" | "/*" "*"+ "/"
EndOfLineComment     = "#" {InputCharacter}* {LineTerminator}

Letter=[A-Za-z]
Digit=[0-9]

DecIntegerLiteral =  ([+-]? (0 | [1-9][0-9]*))

Mantissa = ([+-]? [0-9]+ ("." [0-9]+)?)

Exponent = ([Ee] {DecIntegerLiteral})

DecFloatLiteral = {Mantissa} {Exponent}?

SpecChar = _


Identifier = ({Letter}|{SpecChar})({Letter}|{Digit}|{SpecChar})* 

%%

/* keywords */
<YYINITIAL> {
   "all_visible"               { return GUIDefaultsFile.Keyword.ALL_VISIBLE; }
   "assign_net_rules"          { return GUIDefaultsFile.Keyword.ASSIGN_NET_RULES; }
   "automatic_layer_dimming"   { return GUIDefaultsFile.Keyword.AUTOMATIC_LAYER_DIMMING; }
   "background"                { return GUIDefaultsFile.Keyword.BACKGROUND; }
   "board_frame"               { return GUIDefaultsFile.Keyword.BOARD_FRAME; }
   "bounds"                    { return GUIDefaultsFile.Keyword.BOUNDS; }
   "clearance_compensation"    { return GUIDefaultsFile.Keyword.CLEARANCE_COMPENSATION; }
   "clearance_matrix"          { return GUIDefaultsFile.Keyword.CLEARANCE_MATRIX; }
   "colors"                    { return GUIDefaultsFile.Keyword.COLORS; }
   "color_manager"             { return GUIDefaultsFile.Keyword.COLOR_MANAGER; }
   "component_back"            { return GUIDefaultsFile.Keyword.COMPONENT_BACK; }
   "component_front"           { return GUIDefaultsFile.Keyword.COMPONENT_FRONT; }
   "component_grid"            { return GUIDefaultsFile.Keyword.COMPONENT_GRID; }
   "component_info"            { return GUIDefaultsFile.Keyword.COMPONENT_INFO; }
   "conduction"                { return GUIDefaultsFile.Keyword.CONDUCTION; }
   "current_layer"             { return GUIDefaultsFile.Keyword.CURRENT_LAYER; }
   "current_only"              { return GUIDefaultsFile.Keyword.CURRENT_ONLY; }
   "deselected_snapshot_attributes" { return GUIDefaultsFile.Keyword.DESELECTED_SNAPSHOT_ATTRIBUTES; }
   "display_miscellanious"     { return GUIDefaultsFile.Keyword.DISPLAY_MISCELLANIOUS; }
   "display_region"            { return GUIDefaultsFile.Keyword.DISPLAY_REGION; }
   "drag_components_enabled"   { return GUIDefaultsFile.Keyword.DRAG_COMPONENTS_ENABLED; }
   "dynamic"                   { return GUIDefaultsFile.Keyword.DYNAMIC; }
   "edit_net_rules"            { return GUIDefaultsFile.Keyword.EDIT_NET_RULES; }
   "edit_vias"                 { return GUIDefaultsFile.Keyword.EDIT_VIAS; }
   "fixed"                     { return GUIDefaultsFile.Keyword.FIXED; }
   "fixed_traces"              { return GUIDefaultsFile.Keyword.FIXED_TRACES; }
   "fixed_vias"                { return GUIDefaultsFile.Keyword.FIXED_VIAS; }
   "fortyfive_degree"          { return GUIDefaultsFile.Keyword.FORTYFIVE_DEGREE; }
   "gui_defaults"              { return GUIDefaultsFile.Keyword.GUI_DEFAULTS; }
   "hilight"                   { return GUIDefaultsFile.Keyword.HILIGHT; }
   "hilight_routing_obstacle"  { return GUIDefaultsFile.Keyword.HILIGHT_ROUTING_OBSTACLE; }
   "ignore_conduction_areas"   { return GUIDefaultsFile.Keyword.IGNORE_CONDUCTION_AREAS; }
   "incompletes"               { return GUIDefaultsFile.Keyword.INCOMPLETES; }
   "incompletes_info"          { return GUIDefaultsFile.Keyword.INCOMPLETES_INFO; }
   "interactive_state"         { return GUIDefaultsFile.Keyword.INTERACTIVE_STATE; }
   "keepout"                   { return GUIDefaultsFile.Keyword.KEEPOUT; }
   "layer_visibility"          { return GUIDefaultsFile.Keyword.LAYER_VISIBILITY; }
   "length_matching"           { return GUIDefaultsFile.Keyword.LENGTH_MATCHING; }
   "manual_rules"              { return GUIDefaultsFile.Keyword.MANUAL_RULES; }
   "manual_rule_settings"      { return GUIDefaultsFile.Keyword.MANUAL_RULE_SETTINGS; }
   "move_parameter"            { return GUIDefaultsFile.Keyword.MOVE_PARAMETER; }
   "net_info"                  { return GUIDefaultsFile.Keyword.NET_INFO; }
   "ninety_degree"             { return GUIDefaultsFile.Keyword.NINETY_DEGREE; }
   "none"                      { return GUIDefaultsFile.Keyword.NONE; }
   "not_visible"               { return GUIDefaultsFile.Keyword.NOT_VISIBLE; }
   "object_colors"             { return GUIDefaultsFile.Keyword.OBJECT_COLORS; }
   "object_visibility"         { return GUIDefaultsFile.Keyword.OBJECT_VISIBILITY; }
   "off"                       { return GUIDefaultsFile.Keyword.OFF; }
   "on"                        { return GUIDefaultsFile.Keyword.ON; }
   "outline"                   { return GUIDefaultsFile.Keyword.OUTLINE; }
   "package_info"              { return GUIDefaultsFile.Keyword.PACKAGE_INFO; }
   "padstack_info"             { return GUIDefaultsFile.Keyword.PADSTACK_INFO; }
   "parameter"                 { return GUIDefaultsFile.Keyword.PARAMETER; }
   "pins"                      { return GUIDefaultsFile.Keyword.PINS; }
   "pull_tight_accuracy"       { return GUIDefaultsFile.Keyword.PULL_TIGHT_ACCURACY; }
   "pull_tight_region"         { return GUIDefaultsFile.Keyword.PULL_TIGHT_REGION; }
   "push_and_shove_enabled"    { return GUIDefaultsFile.Keyword.PUSH_AND_SHOVE_ENABLED; }
   "route_details"             { return GUIDefaultsFile.Keyword.ROUTE_DETAILS; }
   "route_mode"                { return GUIDefaultsFile.Keyword.ROUTE_MODE; }
   "route_parameter"           { return GUIDefaultsFile.Keyword.ROUTE_PARAMETER; }
   "rule_selection"            { return GUIDefaultsFile.Keyword.RULE_SELECTION; }
   "select_parameter"          { return GUIDefaultsFile.Keyword.SELECT_PARAMETER; }
   "selectable_items"          { return GUIDefaultsFile.Keyword.SELECTABLE_ITEMS; }
   "selection_layers"          { return GUIDefaultsFile.Keyword.SELECTION_LAYERS; }
   "snapshots"                 { return GUIDefaultsFile.Keyword.SNAPSHOTS; }
   "shove_enabled"             { return GUIDefaultsFile.Keyword.SHOVE_ENABLED; }
   "stitching"                 { return GUIDefaultsFile.Keyword.STITCHING; }
   "traces"                    { return GUIDefaultsFile.Keyword.TRACES; }
   "unfixed"                   { return GUIDefaultsFile.Keyword.UNFIXED; }
   "via_keepout"               { return GUIDefaultsFile.Keyword.VIA_KEEPOUT; }
   "vias"                      { return GUIDefaultsFile.Keyword.VIAS; }
   "via_rules"                 { return GUIDefaultsFile.Keyword.VIA_RULES; }
   "via_snap_to_smd_center"    { return GUIDefaultsFile.Keyword.VIA_SNAP_TO_SMD_CENTER; }
   "violations"                { return GUIDefaultsFile.Keyword.VIOLATIONS; }
   "violations_info"           { return GUIDefaultsFile.Keyword.VIOLATIONS_INFO; }
   "visible"                   { return GUIDefaultsFile.Keyword.VISIBLE; }
   "windows"                   { return GUIDefaultsFile.Keyword.WINDOWS; }
   "("                         { return GUIDefaultsFile.Keyword.OPEN_BRACKET; }
   ")"                         { return GUIDefaultsFile.Keyword.CLOSED_BRACKET; }
}

<YYINITIAL> {
  /* identifiers */ 
  {Identifier}                   { return yytext(); }
 
  /* literals */
  {DecIntegerLiteral}            { return new Integer(yytext()); }
  {DecFloatLiteral}              { return new Double(yytext()); }

  /* comments */
  {Comment}                      { /* ignore */ }
 
  /* whitespace */
  {WhiteSpace}                   { /* ignore */ }
}

/* error fallback */
.|\n                             { throw new Error("Illegal character <"+
                                                    yytext()+">"); }
