package designformats.specctra;
@SuppressWarnings("all")
%%

%class SpecctraFileScanner
%implements Scanner
%unicode
%ignorecase 
%function next_token
%type Object
/* %debug */

%{
  StringBuffer string = new StringBuffer();
%}

LineTerminator = \r|\n|\r\n
InputCharacter = [^\r\n]
WhiteSpace     = {LineTerminator} | [ \t\f]

/* comments */
Comment = {TraditionalComment} | {EndOfLineComment}

TraditionalComment   = "/*" [^*] ~"*/" | "/*" "*"+ "/"
EndOfLineComment     = "#" {InputCharacter}* {LineTerminator}

Letter=[A-Za-z]
Digit=[0-9]

/* Character used for quoting string */
QuoteChar1 = \"
QuoteChar2 = '

SpecCharASCII = _|\.|\/|\\|:|#|\$|&|>|<|,|;|=|@|\[|\]||\~|\*|\?|\!|\%|\^

SpecCharANSI1 = €|‚|ƒ|„|…|†|‡|ˆ|‰|Š|‹|Œ|Ž|‘|’|“|”|•|–|—|˜|™|š|›|œ|ž|Ÿ
SpecCharANSI2 = [¡-ÿ]
SpecCharANSI = {SpecCharANSI1}|{SpecCharANSI2}


SpecChar1 = {SpecCharASCII}|{SpecCharANSI}

SpecChar2 = {SpecChar1}|-|\+

SpecChar3 = {SpecChar2}|{QuoteChar1}|{QuoteChar2}

SpecChar4 = {SpecChar1}|\+

SpecChar5 = {SpecChar4}|{QuoteChar1}|{QuoteChar2}


DecIntegerLiteral =  ([+-]? (0 | [1-9][0-9]*))

Mantissa = ([+-]? [0-9]+ ("." [0-9]+)?)

Exponent = ([Ee] {DecIntegerLiteral})

DecFloatLiteral = {Mantissa} {Exponent}?

Identifier = ({Letter}|{SpecChar1})({Letter}|{Digit}|{SpecChar3})* 

NameIdentifier = ({Letter}|{Digit}|{SpecChar2})({Letter}|{Digit}|{SpecChar3})*

IdentifierIgnoringQuotes = ({Letter}|{Digit}|{SpecChar3})*

/* to divide the component name from the pin name with the character "-" */
ComponentIdentifier = ({Letter}|{Digit}|{SpecChar4})({Letter}|{Digit}|{SpecChar5})*

/* States used for qouting strings */
%state STRING1
%state STRING2

/* The state NAME is used if the next token has to be interpreted as string, even if it is a number */
%state NAME

/* The state LAYER_NAME is used if the next token has to be interpreted as a layer name */
%state LAYER_NAME

/* To divide a component name from the pin name with the charracter "-" */
%state COMPONENT_NAME

/* Returns the next character */
%state SPEC_CHAR

/* Reads the next identifier while handling the quote characters as normal characters */
%state IGNORE_QUOTE

%%


<YYINITIAL> {
   /* keywords */
   "absolute"      { return Keyword.ABSOLUTE; }
   "active"        { return Keyword.ACTIVE; }
   "against_preferred_direction_trace_costs" { return Keyword.AGAINST_PREFERRED_DIRECTION_TRACE_COSTS; }
   "against_prefered_direction_trace_costs" { return Keyword.AGAINST_PREFERRED_DIRECTION_TRACE_COSTS; }
   "attach"        { return Keyword.ATTACH; }
   "autoroute"     { return Keyword.AUTOROUTE; }
   "autoroute_settings" { return Keyword.AUTOROUTE_SETTINGS; }
   "back"          { return Keyword.BACK; }
   "boundary"      { return Keyword.BOUNDARY; }
   "circ"          { yybegin(LAYER_NAME); return Keyword.CIRCLE; }
   "circle"        { yybegin(LAYER_NAME); return Keyword.CIRCLE; }
   "circuit"       { return Keyword.CIRCUIT; }
   "class"         { yybegin(NAME); return Keyword.CLASS; }
   "class_class"   { return Keyword.CLASS_CLASS; }
   "classes"       { return Keyword.CLASSES; }
   "clear"         { return Keyword.CLEARANCE; }
   "clearance"     { return Keyword.CLEARANCE; }
   "clearance_class" { yybegin(NAME); return Keyword.CLEARANCE_CLASS; }
   "comp"          { yybegin(NAME); return Keyword.COMPONENT_SCOPE; }
   "component"     { yybegin(NAME); return Keyword.COMPONENT_SCOPE; }
   "constant"      { return Keyword.CONSTANT; }
   "control"       { return Keyword.CONTROL; }
   "fanout"        { return Keyword.FANOUT; }
   "fix"           { return Keyword.FIX; }
   "fortyfive_degree" { return Keyword.FORTYFIVE_DEGREE; }
   "flip_style"    { return Keyword.FLIP_STYLE; }
   "fromto"        { return Keyword.FROMTO; }
   "front"         { return Keyword.FRONT; }
   "generated_by_freeroute" {return Keyword.GENERATED_BY_FREEROUTE; }
   "horizontal"    { return Keyword.HORIZONTAL; }
   "image"         { yybegin(NAME); return Keyword.IMAGE; }
   "host_cad"      { yybegin(NAME); return Keyword.HOST_CAD; }
   "host_version"  { yybegin(NAME); return Keyword.HOST_VERSION; }
   "keepout"       { yybegin(NAME); return Keyword.KEEPOUT; }
   "layer"         { yybegin(NAME); return Keyword.LAYER; }
   "layer_rule"    { yybegin(NAME); return Keyword.LAYER_RULE; }
   "length"        { return Keyword.LENGTH; }
   "library"       { return Keyword.LIBRARY_SCOPE; }
   "lock_type"     { return Keyword.LOCK_TYPE; }
   "logical_part"  { yybegin(NAME); return Keyword.LOGICAL_PART; }
   "logical_part_mapping"  { yybegin(NAME); return Keyword.LOGICAL_PART_MAPPING; }
   "net"           { yybegin(NAME); return Keyword.NET; }
   "network"       { return Keyword.NETWORK_SCOPE; }
   "network_out"   { return Keyword.NETWORK_OUT; }
   "ninety_degree" { return Keyword.NINETY_DEGREE; }
   "none"          { return Keyword.NONE; }
   "normal"        { return Keyword.NORMAL; }
   "off"           { return Keyword.OFF; }
   "on"            { return Keyword.ON; }
   "order"         { return Keyword.ORDER; }
   "outline"       { return Keyword.OUTLINE; }
   "padstack"      { yybegin(NAME); return Keyword.PADSTACK; }
   "parser"        { return Keyword.PARSER_SCOPE; }
   "part_library"  { return Keyword.PART_LIBRARY_SCOPE; }
   "path"          { yybegin(LAYER_NAME); return Keyword.POLYGON_PATH; }
   "pcb"           { return Keyword.PCB_SCOPE; }
   "pin"           { return Keyword.PIN; }
   "pins"          { return Keyword.PINS; }
   "place"         { yybegin(NAME); return Keyword.PLACE; }
   "place_control" { return Keyword.PLACE_CONTROL; } 
   "place_keepout" { yybegin(NAME); return Keyword.PLACE_KEEPOUT; }
   "placement"     { return Keyword.PLACEMENT_SCOPE; }
   "plane"         { yybegin(NAME); return Keyword.PLANE_SCOPE; }
   "plane_via_costs" { return Keyword.PLANE_VIA_COSTS; }
   "poly"          { yybegin(LAYER_NAME); return Keyword.POLYGON; }
   "polygon"       { yybegin(LAYER_NAME); return Keyword.POLYGON; }
   "polyline_path" { yybegin(LAYER_NAME); return Keyword.POLYLINE_PATH; }
   "position"      { return Keyword.POSITION; }
   "postroute"      { return Keyword.POSTROUTE; }
   "power"         { return Keyword.POWER; }
   "preferred_direction" { return Keyword.PREFERRED_DIRECTION; }
   "prefered_direction" { return Keyword.PREFERRED_DIRECTION; }
   "preferred_direction_trace_costs" { return Keyword.PREFERRED_DIRECTION_TRACE_COSTS; }
   "prefered_direction_trace_costs" { return Keyword.PREFERRED_DIRECTION_TRACE_COSTS; }
   "pull_tight"    { return Keyword.PULL_TIGHT; }
   "rect"          { yybegin(LAYER_NAME); return Keyword.RECTANGLE; }
   "rectangle"     { yybegin(LAYER_NAME); return Keyword.RECTANGLE; }
   "resolution"    { return Keyword.RESOLUTION_SCOPE; }
   "rotate"        { return Keyword.ROTATE; }
   "rotate_first"  { return Keyword.ROTATE_FIRST; }
   "routes"        { return Keyword.ROUTES; }
   "rule"          { return Keyword.RULE; }
   "rules"         { return Keyword.RULES; }
   "session"       { return Keyword.SESSION; }
   "shape"         { return Keyword.SHAPE; }
   "shove_fixed"   { return Keyword.SHOVE_FIXED; }
   "side"          { return Keyword.SIDE; }
   "signal"        { return Keyword.SIGNAL; }
   "snap_angle"    { return Keyword.SNAP_ANGLE; }
   "spare"         { return Keyword.SPARE; }
   "start_pass_no" { return Keyword.START_PASS_NO; }
   "start_ripup_costs" { return Keyword.START_RIPUP_COSTS; }
   "string_quote"  { yybegin(IGNORE_QUOTE); return Keyword.STRING_QUOTE; }
   "structure"     { return Keyword.STRUCTURE_SCOPE; }
   "type"          { return Keyword.TYPE; }
   "use_layer"     { yybegin(NAME); return Keyword.USE_LAYER; }
   "use_net"       { yybegin(NAME); return Keyword.USE_NET; }
   "use_via"       { yybegin(NAME); return Keyword.USE_VIA; }
   "vertical"      { return Keyword.VERTICAL; }
   "via"           { yybegin(NAME); return Keyword.VIA; }
   "vias"          { return Keyword.VIAS; }
   "via_at_smd"    { return Keyword.VIA_AT_SMD; }
   "via_costs"     { return Keyword.VIA_COSTS; }
   "via_keepout"   { yybegin(NAME); return Keyword.VIA_KEEPOUT; }
   "via_rule"      { return Keyword.VIA_RULE; }
   "width"         { return Keyword.WIDTH; }
   "window"        { return Keyword.WINDOW; }
   "wire"          { yybegin(NAME); return Keyword.WIRE; }
   "wire_keepout"  { return Keyword.KEEPOUT; }
   "wiring"        { return Keyword.WIRING_SCOPE; }
   "write_resolution" { return Keyword.WRITE_RESOLUTION; }
   "("             { return Keyword.OPEN_BRACKET; }
   ")"             { return Keyword.CLOSED_BRACKET; }

  /* identifiers */ 
  {Identifier}                   { return yytext(); }

  /* Characters for quoting strings */
  {QuoteChar1}                    { string.setLength(0); yybegin(STRING1); }
  {QuoteChar2}                    { string.setLength(0); yybegin(STRING2); }
 
  /* literals */
  {DecIntegerLiteral}            { return new Integer(yytext()); }
  {DecFloatLiteral}              { return new Double(yytext()); }

  /* comments */
  {Comment}                      { /* ignore */ }
 
  /* whitespace */
  {WhiteSpace}                   { /* ignore */ }
}

/* Strings quoted with " */
<STRING1> {
  [^\"\\]+                   { string.append( yytext() ); }
  \\                             { string.append('\\'); }
  \"                             { yybegin(YYINITIAL); return string.toString(); }
}

/* Strings quotet with ' */
<STRING2> {
  [^\'\\]+                   { string.append( yytext() ); }
  \\                             { string.append('\\'); }
  '                              { yybegin(YYINITIAL); return string.toString(); }
}


<NAME> {
   /* keywords */
   "("             { yybegin(YYINITIAL); return Keyword.OPEN_BRACKET;}
   ")"             { yybegin(YYINITIAL); return Keyword.CLOSED_BRACKET;}

  /* identifiers */ 
  {NameIdentifier}               { yybegin(YYINITIAL); return yytext(); }


  /* Characters for quoting strings */
  {QuoteChar1}                    { string.setLength(0); yybegin(STRING1); }
  {QuoteChar2}                    { string.setLength(0); yybegin(STRING2); }

  /* whitespace */
  {WhiteSpace}                   { /* ignore */ }
}

/* Reads the next identifier while handling the quote characters as normal characters */
<IGNORE_QUOTE> {
   /* keywords */
   "("             { yybegin(YYINITIAL); return Keyword.OPEN_BRACKET;}
   ")"             { yybegin(YYINITIAL); return Keyword.CLOSED_BRACKET;}

   /* identifiers */  
   {IdentifierIgnoringQuotes}     { yybegin(YYINITIAL); return yytext(); }
   {WhiteSpace}                   { /* ignore */ }
}
    

<LAYER_NAME> {
   /* keywords */
   "pcb"           { yybegin(YYINITIAL); return Keyword.PCB_SCOPE; }
   "signal"        { yybegin(YYINITIAL); return Keyword.SIGNAL; }
   "("             { yybegin(YYINITIAL); return Keyword.OPEN_BRACKET;}
   ")"             { yybegin(YYINITIAL); return Keyword.CLOSED_BRACKET;}

  /* identifiers */ 
  {NameIdentifier}               { yybegin(YYINITIAL); return yytext(); }
 
  /* Characters for quoting strings */
  {QuoteChar1}                    { string.setLength(0); yybegin(STRING1); }
  {QuoteChar2}                    { string.setLength(0); yybegin(STRING2); }
 
  /* whitespace */
  {WhiteSpace}                   { /* ignore */ }
}

/* to divide a component name from the pin name with the charracter "-" */
<COMPONENT_NAME> {
   /* keywords */
   "("             { yybegin(YYINITIAL); return Keyword.OPEN_BRACKET;}
   ")"             { yybegin(YYINITIAL); return Keyword.CLOSED_BRACKET;}

  /* identifiers */ 
  {ComponentIdentifier}               { yybegin(YYINITIAL); return yytext(); }


  /* Characters for quoting strings */
  {QuoteChar1}                    { string.setLength(0); yybegin(STRING1); }
  {QuoteChar2}                    { string.setLength(0); yybegin(STRING2); }

  /* whitespace */
  {WhiteSpace}                   { /* ignore */ }
}

<SPEC_CHAR> {
   {SpecChar2} {return yytext();}
}

/* error fallback */
.|\n                             { throw new Error("Illegal character <"+
                                                    yytext()+">"); }
