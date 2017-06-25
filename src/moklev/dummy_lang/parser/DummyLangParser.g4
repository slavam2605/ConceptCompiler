parser grammar DummyLangParser;

options {
    tokenVocab = DummyLangLexer;
}

function
    :   'fun' IDENT '(' typedIdentList ')' ':' type '{' statement* '}'
    ;
    
typedIdentList
    :   IDENT ':' type (',' IDENT ':' type)*
    |
    ;

statement
    :   'var' IDENT ':' type ';'            #varDef
    |   IDENT '=' expression ';'            #assign
    |   'return' expression ';'             #return
    ;
   
expression
    :   INT_LITERAL                                                             #intConst
//    |   IDENT '(' exprList ')'
    |   IDENT                                                                   #variable
//    |   '(' expression ')'
//    |   '-' expression
//    |   '!' expression
//    |   '*' expression
//    |   left=expression op=('*'|'/') right=expression
    |   left=expression op=('+'|'-') right=expression                           #plusMinus
//    |   left=expression op=('<'|'>'|'=='|'!='|'>='|'<=') right=expression
//    |   left=expression '&&' left=expression
//    |   right=expression '||' right=expression
    ;

type
    :   'i64'
    |   'bool'
    ;