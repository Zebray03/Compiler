lexer grammar SysYLexer;

CONST : 'const' ;

INT : 'int' ;

VOID : 'void' ;

IF : 'if' ;

ELSE : 'else' ;

WHILE : 'while' ;

BREAK : 'break' ;

CONTINUE : 'continue' ;

RETURN : 'return' ;

PLUS : '+' ;

MINUS : '-' ;

MUL : '*' ;

DIV : '/' ;

MOD : '%' ;

ASSIGN : '=' ;

EQ : '==' ;

NEQ : '!=' ;

LT : '<' ;

GT : '>' ;

LE : '<=' ;

GE : '>=' ;

NOT : '!' ;

AND : '&&' ;

OR : '||' ;

TRUE : 'true' ;

FALSE : 'false' ;

L_PAREN : '(' ;

R_PAREN : ')' ;

L_BRACE : '{' ;

R_BRACE : '}' ;

L_BRACKT : '[' ;

R_BRACKT : ']' ;

COMMA : ',' ;

SEMICOLON : ';' ;

IDENT : ('_' | [a-zA-Z]) ('_' | [a-zA-Z0-9])* ;

DECIMAL_INTEGER : '0' | ('+' | '-')? [1-9][0-9]* ;

OCTAL_INTEGER : '0' [0-7]* ;

HEXADECIMAL_INTEGER : ('0x' | '0X') [0-9A-Fa-f]* ;

WS : [ \r\n\t]+ -> skip;

LINE_COMMENT : '//' .*? '\n' -> skip ;

MULTILINE_COMMENT : '/*' .*? '*/' -> skip ;