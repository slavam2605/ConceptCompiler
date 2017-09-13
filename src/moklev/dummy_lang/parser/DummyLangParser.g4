parser grammar DummyLangParser;

options {
    tokenVocab = DummyLangLexer;
}

file
    :   (function | typeDefinition)* EOF
    ;

function
    :   'fun' IDENT '(' typedIdentList ')' ':' type '{' statement* '}'
    ;
    
typedIdentList
    :   IDENT ':' type (',' IDENT ':' type)*
    |
    ;

statement
    :   'var' IDENT ':' type ('=' expression)? ';'      #varDef
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
    |   left=expression '.' right=IDENT                                         #structField
    |   left=expression op=('*'|'/'|'%') right=expression                       #timesDiv
    |   left=expression op=('+'|'-') right=expression                           #plusMinus
    |   left=expression op=('<'|'>'|'=='|'!='|'>='|'<=') right=expression       #compareOp
    |   left=expression '&&' right=expression                                    #logicalAnd
//    |   left=expression '||' right=expression
    ;

exprList
    :   (exprs+=expression (',' exprs+=expression)*)?
    ;

type
    :   type '*'                #pointerType
    |   ('i64' | 'bool')        #primitiveType
    |   IDENT                   #definedType
    ;
    
typeDefinition
    :   'struct' name=IDENT '{'
            (fieldNames+=IDENT ':' fieldTypes+=type ';')*
        '}'                         #structDefinition
    ;