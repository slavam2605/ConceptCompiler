parser grammar DummyLangParser;

options {
    tokenVocab = DummyLangLexer;
}

file
    :   function*
    ;

function
    :   'fun' IDENT '(' typedIdentList ')' ':' type '{' statement* '}'
    ;
    
typedIdentList
    :   IDENT ':' type (',' IDENT ':' type)*
    |
    ;

statement
    :   'var' IDENT ':' type ';'                        #varDef
    |   expression '=' expression ';'                   #assign
    |   'return' expression ';'                         #return
    |   'if' expression '{'                
            ifTrue+=statement*
        '}' ('else' '{'
            ifFalse+=statement*
        '}')?                                           #ifElse
    |   'for' '(' init=statement ';' cond=expression ';' step=statement ')' '{'
            body+=statement*
        '}'                                             #forLoop
    |   IDENT '(' exprList ')' ';'                      #callStatement
    |   expression ';'                                  #exprStatement
    ;
   
expression
    :   INT_LITERAL                                                             #intConst
    |   IDENT '(' exprList ')'                                                  #call
    |   IDENT                                                                   #variable
    |   '*' expression                                                          #dereference
    |   '&' expression                                                          #addressOf
    |   '(' type ')' expression                                                 #typeCast
    |   '(' expression ')'                                                      #parenExpression
//    |   '-' expression
//    |   '!' expression
//    |   '*' expression
    |   left=expression op=('*'|'/'|'%') right=expression                       #timesDiv
    |   left=expression op=('+'|'-') right=expression                           #plusMinus
    |   left=expression op=('<'|'>'|'=='|'!='|'>='|'<=') right=expression       #compareOp
//    |   left=expression '&&' left=expression
//    |   right=expression '||' right=expression
    ;

exprList
    :   (exprs+=expression (',' exprs+=expression)*)?
    ;

type
    :   type '*'
    |   'i64'
    |   'bool'
    ;