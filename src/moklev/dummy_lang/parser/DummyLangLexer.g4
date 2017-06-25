lexer grammar DummyLangLexer;

LBRACE:     '{';
RBRACE:     '}';
LPAREN:     '(';
RPAREN:     ')';

PLUS:       '+';
MINUS:      '-';
TIMES:      '*';
DIV:        '/';
MOD:        '%';

OROR:       '||';
ANDAND:     '&&';
NOT:        '!';

EQUALS:     '==';
NOT_EQUALS: '!=';
GREATER:    '>';
GREATER_EQ: '>=';
LESS:       '<';
LESS_EQ:    '<=';

ASSIGN:     '=';

COMMA:      ',';
SEMICOLON:  ';';
COLON:      ':';

INT_LITERAL:    [0-9]+;
TRUE_LITERAL:   'true';
FALSE_LITERAL:  'false';

FUN_KW:         'fun';
I64_KW:         'i64';
BOOL_KW:        'bool';
VAR_KW:         'var';
RETURN_KW:      'return';

IDENT:          [a-zA-Z_][a-zA-Z_0-9]*;

WS:             [ \t\n\r]+ -> skip;
COMMENT:        '/*' .*? '*/' -> skip;
LINE_COMMENT:   '//' ~'\n'* '\n' -> skip;