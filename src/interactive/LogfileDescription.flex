package interactive;
@SuppressWarnings("all")
%%

%class LogfileScanner
%unicode
%function next_token
%type Object

LineTerminator = \r|\n|\r\n
InputCharacter = [^\r\n]
WhiteSpace     = {LineTerminator} | [ \t\f]


/* comments */
Comment = {TraditionalComment} | {EndOfLineComment}

TraditionalComment   = "/*" [^*] ~"*/" | "/*" "*"+ "/"
EndOfLineComment     = "#" {InputCharacter}* {LineTerminator}

Letter=[A-Za-z]
Digit=[0-9]

SpecChar = _|\.|\/|#|\$

DecIntegerLiteral =  (-? (0 | [1-9][0-9]*))

DecFloatLiteral = ([+-]? [0-9]+ ("." [0-9]+)?)

Identifier = ({Letter}|{SpecChar})({Letter}|{Digit}|{SpecChar})* 

%%

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
