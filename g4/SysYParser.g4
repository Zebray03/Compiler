parser grammar SysYParser;

options {
    tokenVocab = SysYLexer;
}

program : compUnit ;

compUnit : (funcDef | decl)+ EOF ;

decl : constDecl
     | varDecl ;

constDecl : CONST bType constDef (COMMA constDef)* SEMICOLON;

bType : INT ;

constDef : IDENT (L_BRACKT constExp R_BRACKT)* ASSIGN constInitVal ;

constInitVal : constExp
             | L_BRACE (constInitVal (COMMA constInitVal)* )? R_BRACE ;

varDecl : bType varDef (COMMA varDef)? SEMICOLON ;

varDef : IDENT ( L_BRACKT constExp R_BRACKT)*
       | IDENT ( L_BRACKT constExp R_BRACKT)* ASSIGN initVal ;

initVal : exp                                           # IntInit
        | L_BRACE (initVal (COMMA initVal)* )? R_BRACE  # ArrayInit
        ;

funcDef : funcType IDENT L_PAREN funcFParams? R_PAREN block ;

funcType : VOID | INT ;

funcFParams : funcFParam (COMMA funcFParam)* ;

funcFParam : bType IDENT (L_BRACKT R_BRACKT (L_BRACKT exp R_BRACKT)* )? ;

block : L_BRACE blockItem* R_BRACE ;

blockItem : decl
          | stmt ;

stmt : lVal ASSIGN exp SEMICOLON                    # AssignStmt
     | (exp)? SEMICOLON                             # ExpStmt
     | block                                        # BlockStmt
     | IF L_PAREN cond R_PAREN stmt (ELSE stmt)?    # IfStmt
     | WHILE L_PAREN cond R_PAREN                   # WhileStmt
     | BREAK SEMICOLON                              # BreakStmt
     | CONTINUE SEMICOLON                           # ContinueStmt
     | RETURN exp? SEMICOLON                        # RuturnStmt
     ;

exp : L_PAREN exp R_PAREN                   # CombineExp
    | lVal                                  # LValExp
    | number                                # IntExp
    | IDENT L_PAREN funcRParams? R_PAREN    # FuncCallExp
    | unaryOp exp                           # UnaryOpExp
    | exp (MUL | DIV | MOD) exp             # UpperCalExp
    | exp (PLUS | MINUS) exp                # LowerCalExp
    ;

cond : exp                  # ExpCond
     | cond LT cond         # LtCond
     | cond GT cond         # GtCond
     | cond LE cond         # LeCond
     | cond GE cond         # GeCond
     | cond (EQ | NEQ) cond # EqCond
     | cond (EQ | NEQ) cond # NeqCond
     | cond AND cond        # AndCond
     | cond OR cond         # OrCond
     | TRUE                 # TrueCond
     | FALSE                # FalseCond
     ;

lVal : IDENT (L_BRACKT exp R_BRACKT)* ;

number : DECIMAL_INTEGER | OCTAL_INTEGER | HEXADECIMAL_INTEGER ;

unaryOp : PLUS
        | MINUS
        | NOT ;

funcRParams : param (COMMA param)* ;

param : exp ;

constExp : exp ;
