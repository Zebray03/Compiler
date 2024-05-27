import java.util.ArrayList;
import java.util.Objects;

public class SysYTypeCheckerVisitor extends SysYParserBaseVisitor<Void> {
    private GlobalScope globalScope = null;
    private Scope currentScope = null;
    private boolean isCorrect = true;

    @Override
    public Void visitProgram(SysYParser.ProgramContext ctx) {
        globalScope = new GlobalScope(null);
        this.currentScope = globalScope;
        visitCompUnit(ctx.compUnit());
        return null;
    }

    @Override
    public Void visitVarDecl(SysYParser.VarDeclContext ctx) {
        for (int i = 0; i < ctx.varDef().size(); i++) {
            visit(ctx.varDef(i));
        }
        return null;
    }

    @Override
    public Void visitVarDef(SysYParser.VarDefContext ctx) {
        String varName = ctx.IDENT().getText();
        if (currentScope.getSymbols().get(varName) != null) {
            ErrorOutputHelper.printSemanticError(ErrorType.VARIABLE_REDEFINE, ctx.IDENT().getSymbol().getLine(), ctx.IDENT().getText());
            this.isCorrect = false;
        }

        if (ctx.constExp().isEmpty()) {
            if (ctx.ASSIGN() != null) {
                visitIntInit((SysYParser.IntInitContext) ctx.initVal());
            }
            Symbol symbol = new BaseSymbol(varName, new IntType());
            currentScope.define(symbol);
        } else {
            if (ctx.ASSIGN() != null) {
                visitArrayInit((SysYParser.ArrayInitContext) ctx.initVal());
            }
            int[] num = new int[ctx.constExp().size()];
            for (int i = 0; i < num.length; i++) {
                num[i] = Integer.parseInt(ctx.constExp().get(i).getText());
            }
            Symbol symbol = new BaseSymbol(varName, new ArrayType(new IntType(), num));
            currentScope.define(symbol);
        }
        return null;
    }

    @Override
    public Void visitFuncDef(SysYParser.FuncDefContext ctx) {
        String funcName = ctx.IDENT().getText();
        if (currentScope.resolve(funcName) != null) {
            ErrorOutputHelper.printSemanticError(ErrorType.FUNCTION_REDEFINE, ctx.IDENT().getSymbol().getLine(), ctx.IDENT().getText());
            this.isCorrect = false;
            return null;
        }
        Type returnType;
        String typeStr = ctx.funcType().getText();
        if (typeStr.equals("int"))
            returnType = new IntType();
        else if (typeStr.equals("void")) {
            returnType = new VoidType();
        } else {
            returnType = null;
        }
        ArrayList<Type> list = null;
        if (ctx.funcFParams() != null) {
            visit(ctx.funcFParams());
            list = new ArrayList<>();
            for (int i = 0; i < ctx.funcFParams().getChildCount(); i++) {
                String paramName = ctx.funcFParams().funcFParam(i).getText();
                if (currentScope.resolve(paramName) != null) {
                    return super.visitFuncDef(ctx);
                }
                list.add(new IntType());
            }
            FunctionType type = new FunctionType(returnType, list);
            FunctionSymbol scope = new FunctionSymbol(funcName, type, currentScope);
            currentScope.define(scope);
            this.currentScope = scope;
        } else {
            FunctionType type = new FunctionType(returnType, null);
            FunctionSymbol scope = new FunctionSymbol(funcName, type, currentScope);
            currentScope.define(scope);
            this.currentScope = scope;
        }
        visit(ctx.block());
        return null;
    }

    @Override
    public Void visitFuncFParams(SysYParser.FuncFParamsContext ctx) {
        ArrayList<Type> paramsTypeList = new ArrayList<>();
        for (int i = 0; i < ctx.getChildCount(); i++) {
            visit(ctx.funcFParam(i));
        }
        return null;
    }

    @Override
    public Void visitFuncFParam(SysYParser.FuncFParamContext ctx) {

        return null;
    }

    @Override
    public Void visitFuncRParams(SysYParser.FuncRParamsContext ctx) {
        for (int i = 0; i < ctx.getChildCount(); i++) {
            visit(ctx.param(i));
        }
        return null;
    }

    @Override
    public Void visitParam(SysYParser.ParamContext ctx) {
        return super.visitParam(ctx);
    }

    @Override
    public Void visitFuncCall(SysYParser.FuncCallContext ctx) {
        if (currentScope.resolve(ctx.IDENT().getText()) == null) {
            ErrorOutputHelper.printSemanticError(ErrorType.FUNCTION_NOT_DEFINED, ctx.IDENT().getSymbol().getLine(), ctx.IDENT().getText());
            this.isCorrect = false;
        } else {
            Symbol symbol = currentScope.resolve(ctx.IDENT().getText());
            if (symbol.getClass() != FunctionSymbol.class) {
                ErrorOutputHelper.printSemanticError(ErrorType.FUNCTION_CALL_ERROR, ctx.IDENT().getSymbol().getLine(), ctx.IDENT().getText());
                this.isCorrect = false;
            } else {
                ArrayList<Type> typeList = ((FunctionType) currentScope.resolve(ctx.IDENT().getText()).getType()).getParamsType();
                if (typeList == null) {
                    typeList = new ArrayList<>();
                }
                if (typeList.size() != ctx.getChildCount()) {
                    ErrorOutputHelper.printSemanticError(ErrorType.FUNCTION_PARAM_NOT_APPLICABLE, ctx.IDENT().getSymbol().getLine(), ctx.IDENT().getText());
                    this.isCorrect = false;
                }
            }

        }
        if (ctx.funcRParams() != null) {
            visit(ctx.funcRParams());
        }
        return null;
    }

    @Override
    public Void visitLVal(SysYParser.LValContext ctx) {
        if (currentScope.resolve(ctx.getText()) == null) {
            ErrorOutputHelper.printSemanticError(ErrorType.VARIABLE_NOT_DEFINED, ctx.IDENT().getSymbol().getLine(), ctx.IDENT().getText());
            this.isCorrect = false;
        }
//        else {
//            if (ctx.L_BRACKT().size() == 0 && currentScope.resolve(ctx.getText()).getType().getClass() != ArrayType.class) {
//                OutputHelper.printSemanticError(ErrorType.SUBSCRIPT_ERROR, ctx.IDENT().getSymbol().getLine(), ctx.IDENT().getText());
//            }
//        }
        for (int i = 0; i < ctx.exp().size(); i++) {
            visit(ctx.exp(i));
        }
        return null;
    }

    @Override
    public Void visitAssignStmt(SysYParser.AssignStmtContext ctx) {
        String leftName = ctx.lVal().getText();
        visitLVal(ctx.lVal());
        if (currentScope.resolve(leftName) != null) {
            Symbol leftSymbol = currentScope.resolve(leftName);
            Symbol rightSymbol = currentScope.getSymbols().get(ctx.exp().getText());
            if (leftSymbol.getType().getClass() == FunctionType.class) {
                ErrorOutputHelper.printSemanticError(ErrorType.LVAL_ERROR, ctx.ASSIGN().getSymbol().getLine(), ctx.ASSIGN().getText());
                this.isCorrect = false;
            } else {
                if (leftSymbol.getType().getClass() == ArrayType.class && rightSymbol.getType().getClass() == ArrayType.class) {
                    ArrayType leftType = (ArrayType) leftSymbol.getType();
                    ArrayType rightType = (ArrayType) rightSymbol.getType();
                    int[] left_dim = leftType.getDimension();
                    int[] right_dim = rightType.getDimension();
                    if (left_dim.length != right_dim.length) {
                        ErrorOutputHelper.printSemanticError(ErrorType.ASSIGN_DISMATCH, ctx.ASSIGN().getSymbol().getLine(), ctx.ASSIGN().getText());
                        this.isCorrect = false;
                    } else {
                        for (int i = 0; i < left_dim.length; i++) {
                            if (left_dim[i] != right_dim[i]) {
                                ErrorOutputHelper.printSemanticError(ErrorType.ASSIGN_DISMATCH, ctx.ASSIGN().getSymbol().getLine(), ctx.ASSIGN().getText());
                                this.isCorrect = false;
                                break;
                            }
                        }
                    }
                } else if (leftSymbol.getType().getClass() != (rightSymbol == null ? IntType.class : rightSymbol.getType().getClass())) {
                    ErrorOutputHelper.printSemanticError(ErrorType.ASSIGN_DISMATCH, ctx.ASSIGN().getSymbol().getLine(), ctx.ASSIGN().getText());
                    this.isCorrect = false;
                }
            }
        }
        if (ctx.exp().getClass() == SysYParser.FuncCallContext.class) {
            visitFuncCall((SysYParser.FuncCallContext) ctx.exp());
        } else if (ctx.exp().getClass() == SysYParser.LValExpContext.class) {
            visitLValExp((SysYParser.LValExpContext) ctx.exp());
        }
        return null;
    }

    @Override
    public Void visitRuturnStmt(SysYParser.RuturnStmtContext ctx) {
        String returnName = ctx.exp().getText();
        if (currentScope.resolve(returnName) != null) {
            Symbol returnSymbol = currentScope.resolve(returnName);
            Type returnType = returnSymbol.getType();
            String needString = ctx.parent.parent.parent.getChild(0).getText();
            Type needType;
            if (needString.equals("int")) {
                needType = new IntType();
            } else {
                needType = new VoidType();
            }
            if (returnType.getClass() != needType.getClass()) {
                ErrorOutputHelper.printSemanticError(ErrorType.RETURN_VALUE_DISMATCH, ctx.RETURN().getSymbol().getLine(), null);
                this.isCorrect = false;
            }
        }
        visit(ctx.exp());
        return null;
    }

    @Override
    public Void visitLowerLevelCalculate(SysYParser.LowerLevelCalculateContext ctx) {
        visit(ctx.exp(0));
        visit(ctx.exp(1));
        if (ctx.exp(0).getClass() != SysYParser.IntContext.class && ctx.exp(1).getClass() != SysYParser.IntContext.class) {
            String leftName = ctx.exp(0).getText();
            String rightName = ctx.exp(1).getText();
            if (currentScope.resolve(leftName) != null && currentScope.resolve(rightName) != null) {
                Symbol leftSymbol = currentScope.getSymbols().get(leftName);
                Symbol rightSymbol = currentScope.getSymbols().get(rightName);
                if (leftSymbol.getType().getClass() == IntType.class && rightSymbol.getType().getClass() == IntType.class) {
                    ArrayType leftType = (ArrayType) leftSymbol.getType();
                    ArrayType rightType = (ArrayType) rightSymbol.getType();
                    int[] left_dim = leftType.getDimension();
                    int[] right_dim = rightType.getDimension();
                    if (left_dim.length != right_dim.length) {
                        if (Objects.equals(ctx.getChild(1).getText(), "+")) {
                            ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.PLUS().getSymbol().getLine(), ctx.PLUS().getText());
                            this.isCorrect = false;
                        } else {
                            ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.MINUS().getSymbol().getLine(), ctx.MINUS().getText());
                            this.isCorrect = false;
                        }
                    } else {
                        for (int i = 0; i < left_dim.length; i++) {
                            if (left_dim[i] != right_dim[i]) {
                                if (Objects.equals(ctx.getChild(1).getText(), "+")) {
                                    ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.PLUS().getSymbol().getLine(), ctx.PLUS().getText());
                                    this.isCorrect = false;
                                } else {
                                    ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.MINUS().getSymbol().getLine(), ctx.MINUS().getText());
                                    this.isCorrect = false;
                                }
                                break;
                            }
                        }
                    }
                } else {
                    if (Objects.equals(ctx.getChild(1).getText(), "+")) {
                        ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.PLUS().getSymbol().getLine(), ctx.PLUS().getText());
                        this.isCorrect = false;
                    } else {
                        ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.MINUS().getSymbol().getLine(), ctx.MINUS().getText());
                        this.isCorrect = false;
                    }
                }
            }
        } else if (ctx.exp(0).getClass() == SysYParser.IntContext.class) {
            String rightName = ctx.exp(1).getText();
            if (currentScope.resolve(rightName) != null) {
                Symbol rightSymbol = currentScope.getSymbols().get(rightName);
                if (rightSymbol.getType().getClass() != IntType.class) {
                    if (Objects.equals(ctx.getChild(1).getText(), "+")) {
                        ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.PLUS().getSymbol().getLine(), ctx.PLUS().getText());
                        this.isCorrect = false;
                    } else {
                        ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.MINUS().getSymbol().getLine(), ctx.MINUS().getText());
                        this.isCorrect = false;
                    }
                }
            }
        } else {
            String leftName = ctx.exp(0).getText();
            if (currentScope.resolve(leftName) != null) {
                Symbol leftSymbol = currentScope.getSymbols().get(leftName);
                if (leftSymbol.getType().getClass() != IntType.class) {
                    if (Objects.equals(ctx.getChild(1).getText(), "+")) {
                        ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.PLUS().getSymbol().getLine(), ctx.PLUS().getText());
                        this.isCorrect = false;
                    } else {
                        ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.MINUS().getSymbol().getLine(), ctx.MINUS().getText());
                        this.isCorrect = false;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Void visitUpperLevelCalculate(SysYParser.UpperLevelCalculateContext ctx) {
        visit(ctx.exp(0));
        visit(ctx.exp(1));
        if (ctx.exp(0).getClass() != SysYParser.IntContext.class && ctx.exp(1).getClass() != SysYParser.IntContext.class) {
            String leftName = ctx.exp(0).getText();
            String rightName = ctx.exp(1).getText();
            if (currentScope.resolve(leftName) != null && currentScope.resolve(rightName) != null) {
                Symbol leftSymbol = currentScope.getSymbols().get(leftName);
                Symbol rightSymbol = currentScope.getSymbols().get(rightName);
                if (leftSymbol.getType().getClass() == IntType.class && rightSymbol.getType().getClass() == IntType.class) {
                    ArrayType leftType = (ArrayType) leftSymbol.getType();
                    ArrayType rightType = (ArrayType) rightSymbol.getType();
                    int[] left_dim = leftType.getDimension();
                    int[] right_dim = rightType.getDimension();
                    if (left_dim.length != right_dim.length) {
                        if (Objects.equals(ctx.getChild(1).getText(), "*")) {
                            ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.MUL().getSymbol().getLine(), ctx.MUL().getText());
                            this.isCorrect = false;
                        } else if (Objects.equals(ctx.getChild(1).getText(), "/")) {
                            ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.DIV().getSymbol().getLine(), ctx.DIV().getText());
                            this.isCorrect = false;
                        } else {
                            ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.MOD().getSymbol().getLine(), ctx.MOD().getText());
                            this.isCorrect = false;
                        }
                    } else {
                        for (int i = 0; i < left_dim.length; i++) {
                            if (left_dim[i] != right_dim[i]) {
                                if (Objects.equals(ctx.getChild(1).getText(), "*")) {
                                    ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.MUL().getSymbol().getLine(), ctx.MUL().getText());
                                    this.isCorrect = false;
                                } else if (Objects.equals(ctx.getChild(1).getText(), "/")) {
                                    ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.DIV().getSymbol().getLine(), ctx.DIV().getText());
                                    this.isCorrect = false;
                                } else {
                                    ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.MOD().getSymbol().getLine(), ctx.MOD().getText());
                                    this.isCorrect = false;
                                }
                                break;
                            }
                        }
                    }
                } else {
                    if (Objects.equals(ctx.getChild(1).getText(), "*")) {
                        ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.MUL().getSymbol().getLine(), ctx.MUL().getText());
                        this.isCorrect = false;
                    } else if (Objects.equals(ctx.getChild(1).getText(), "/")) {
                        ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.DIV().getSymbol().getLine(), ctx.DIV().getText());
                        this.isCorrect = false;
                    } else {
                        ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.MOD().getSymbol().getLine(), ctx.MOD().getText());
                        this.isCorrect = false;
                    }
                }
            }
        } else if (ctx.exp(0).getClass() == SysYParser.IntContext.class) {
            String rightName = ctx.exp(1).getText();
            if (currentScope.resolve(rightName) != null) {
                Symbol rightSymbol = currentScope.getSymbols().get(rightName);
                if (rightSymbol.getType().getClass() != IntType.class) {
                    if (Objects.equals(ctx.getChild(1).getText(), "*")) {
                        ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.MUL().getSymbol().getLine(), ctx.MUL().getText());
                        this.isCorrect = false;
                    } else if (Objects.equals(ctx.getChild(1).getText(), "/")) {
                        ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.DIV().getSymbol().getLine(), ctx.DIV().getText());
                        this.isCorrect = false;
                    } else {
                        ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.MOD().getSymbol().getLine(), ctx.MOD().getText());
                        this.isCorrect = false;
                    }
                }
            }
        } else {
            String leftName = ctx.exp(0).getText();
            if (currentScope.resolve(leftName) != null) {
                Symbol leftSymbol = currentScope.getSymbols().get(leftName);
                if (leftSymbol.getType().getClass() != IntType.class) {
                    if (Objects.equals(ctx.getChild(1).getText(), "*")) {
                        ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.MUL().getSymbol().getLine(), ctx.MUL().getText());
                        this.isCorrect = false;
                    } else if (Objects.equals(ctx.getChild(1).getText(), "/")) {
                        ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.DIV().getSymbol().getLine(), ctx.DIV().getText());
                        this.isCorrect = false;
                    } else {
                        ErrorOutputHelper.printSemanticError(ErrorType.DISMATCH_OPERAND, ctx.MOD().getSymbol().getLine(), ctx.MOD().getText());
                        this.isCorrect = false;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Void visitBlock(SysYParser.BlockContext ctx) {
        Scope localScope = new LocalScope(currentScope);
        this.currentScope = localScope;
        ctx.blockItem().forEach(this::visit);
        currentScope = currentScope.getEnclosingScope();
        return null;
    }

    public boolean getIsCorrect() {
        return this.isCorrect;
    }
}