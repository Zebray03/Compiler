import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;

import java.util.Stack;

import static org.bytedeco.llvm.global.LLVM.*;

public class IRGenerateVisitor extends SysYParserBaseVisitor<LLVMValueRef> {
    LLVMModuleRef module;
    LLVMBuilderRef builder;
    GlobalScope globalScope;
    Scope currentScope;
    LLVMValueRef currentFunction;
    Stack<LLVMBasicBlockRef> blockStack;
    Stack<Loop> loopStack;

    @Override
    public LLVMValueRef visitProgram(SysYParser.ProgramContext ctx) {
        module = LLVMModuleCreateWithName("module");
        builder = LLVMCreateBuilder();
        globalScope = new GlobalScope(null);
        this.currentScope = globalScope;
        blockStack = new Stack<>();
        loopStack = new Stack<>();
        return super.visitProgram(ctx);
    }

    @Override
    // 对于 int 常量，它们的处理与 int 变量一致
    public LLVMValueRef visitConstDef(SysYParser.ConstDefContext ctx) {
        if (currentScope == globalScope) {
            LLVMValueRef globalVar = LLVMAddGlobal(module, LLVMInt32Type(), ctx.IDENT().getText());
            VariableSymbol globalSymbol = new VariableSymbol(ctx.IDENT().toString(), LLVMInt32Type(), globalVar.getPointer(), false);
            currentScope.define(globalSymbol);
            LLVMSetInitializer(globalVar, visit(ctx.constInitVal()));
        } else {
            LLVMValueRef pointer = LLVMBuildAlloca(builder, LLVMInt32Type(), ctx.IDENT().getText());

            VariableSymbol symbol = new VariableSymbol(ctx.IDENT().toString(), LLVMInt32Type(), pointer, false);
            currentScope.define(symbol);

            LLVMBuildStore(builder, visit(ctx.constInitVal()), pointer);
        }
        return null;
    }

    @Override
    public LLVMValueRef visitVarDef(SysYParser.VarDefContext ctx) {
        if (currentScope == globalScope) {
            LLVMValueRef globalVar = LLVMAddGlobal(module, LLVMInt32Type(), ctx.IDENT().getText());
            VariableSymbol globalSymbol = new VariableSymbol(ctx.IDENT().toString(), LLVMInt32Type(), globalVar.getPointer(), true);
            currentScope.define(globalSymbol);
            if (ctx.initVal() != null) {
                LLVMSetInitializer(globalVar, visit(ctx.initVal()));
            }
        } else {
            LLVMValueRef pointer = LLVMBuildAlloca(builder, LLVMInt32Type(), ctx.IDENT().getText());

            VariableSymbol symbol = new VariableSymbol(ctx.IDENT().toString(), LLVMInt32Type(), pointer, true);
            currentScope.define(symbol);
            if (ctx.initVal() != null) {
                LLVMBuildStore(builder, visit(ctx.initVal()), pointer);
            }
        }
        return null;
    }

    @Override
    public LLVMValueRef visitFuncDef(SysYParser.FuncDefContext ctx) {
        LLVMTypeRef returnType;
        if (ctx.funcType().INT() != null) {
            returnType = LLVMInt32Type();
        } else {
            returnType = LLVMVoidType();
        }

        PointerPointer<Pointer> argumentTypes;
        int argumentCount;
        if (ctx.funcFParams() != null) {
            argumentCount = ctx.funcFParams().funcFParam().size();
            argumentTypes = new PointerPointer<>(argumentCount);
            for (int i = 0; i < argumentCount; i++) {
                // Renew the argument type list
                argumentTypes.put(i, LLVMInt32Type());
            }
        } else {
            argumentCount = 0;
            argumentTypes = null;
        }

        // Generate the function type
        LLVMTypeRef functionType = LLVMFunctionType(returnType, argumentTypes, argumentCount, 0);

        // Generate the function
        LLVMValueRef function = LLVMAddFunction(module, ctx.IDENT().toString(), functionType);
        currentFunction = function;

        // Generate the function symbol and local scope
        FunctionSymbol symbol = new FunctionSymbol(ctx.IDENT().toString(), functionType, function);
        currentScope.define(symbol);
        currentScope = new LocalScope(currentScope);

        // Generate the function block
        LLVMBasicBlockRef block = LLVMAppendBasicBlock(function, ctx.IDENT().toString() + "Entry");
        blockStack.push(block);
        LLVMPositionBuilderAtEnd(builder, block);

        if (argumentCount != 0) {
            visit(ctx.funcFParams());
        }

        visit(ctx.block());

        // Exit the function definition
        currentScope = currentScope.getEnclosingScope();
        blockStack.pop();
        return null;
    }

    @Override
    public LLVMValueRef visitFuncFParams(SysYParser.FuncFParamsContext ctx) {
        for (int i = 0; i < ctx.funcFParam().size(); i++) {
            LLVMValueRef pointer = LLVMBuildAlloca(builder, LLVMInt32Type(), ctx.funcFParam(i).IDENT().getText());

            // Define the symbol for the format argument
            VariableSymbol symbol = new VariableSymbol(ctx.funcFParam(i).IDENT().getText(), LLVMInt32Type(), pointer, false);
            currentScope.define(symbol);

            // Store the argument value
            LLVMBuildStore(builder, LLVMGetParam(currentFunction, i), pointer);
        }
        return null;
    }

    @Override
    public LLVMValueRef visitBlock(SysYParser.BlockContext ctx) {
        this.currentScope = new LocalScope(currentScope);
        ctx.blockItem().forEach(this::visit);
        currentScope = currentScope.getEnclosingScope();
        return null;
    }

    @Override
    public LLVMValueRef visitAssignStmt(SysYParser.AssignStmtContext ctx) {
        LLVMValueRef lhs = visit(ctx.lVal());
        LLVMValueRef rhs = visit(ctx.exp());
        LLVMBuildStore(builder, rhs, lhs);
        return null;
    }

    @Override
    public LLVMValueRef visitRuturnStmt(SysYParser.RuturnStmtContext ctx) {
        LLVMValueRef returnVal;
        if (ctx.exp() != null) {
            returnVal = visit(ctx.exp());
            LLVMBuildRet(builder, returnVal);
        } else if(ctx.cond() != null) {
            returnVal = visit(ctx.cond());
            LLVMBuildRet(builder, returnVal);
        }
        return null;
    }

    @Override
    public LLVMValueRef visitLValExp(SysYParser.LValExpContext ctx) {
        VariableSymbol symbol = (VariableSymbol) currentScope.resolve(ctx.lVal().IDENT().toString());
        // Load the value
        LLVMValueRef lVal = LLVMBuildLoad(builder, symbol.getPointer(), symbol.getName());
        return lVal;
    }

    @Override
    public LLVMValueRef visitIntExp(SysYParser.IntExpContext ctx) {
        long result = 0;
        // OCTAL_INTEGER or HEXADECIMAL_INTEGER or DECIMAL_INTEGER
        if (ctx.number().OCTAL_INTEGER() != null) {
            String str = ctx.number().OCTAL_INTEGER().toString();
            for (int i = 0; i < str.length(); i++) {
                result *= 8;
                result += (str.charAt(i) - '0');
            }
        } else if (ctx.number().HEXADECIMAL_INTEGER() != null) {
            String str = ctx.number().HEXADECIMAL_INTEGER().toString();
            for (int i = 2; i < str.length(); i++) {
                result *= 16;
                if (str.charAt(i) >= '0' && str.charAt(i) <= '9') {
                    result += (str.charAt(i) - '0');
                } else if (str.charAt(i) >= 'A' && str.charAt(i) <= 'F') {
                    result += (str.charAt(i) - 'A' + 10);
                } else if (str.charAt(i) >= 'a' && str.charAt(i) <= 'f') {
                    result += (str.charAt(i) - 'a' + 10);
                }
            }
        } else if (ctx.number().DECIMAL_INTEGER() != null) {
            result = Long.parseLong(ctx.number().DECIMAL_INTEGER().toString());
        }
        return LLVMConstInt(LLVMInt32Type(), result, 0);
    }

    @Override
    public LLVMValueRef visitFuncCallExp(SysYParser.FuncCallExpContext ctx) {
        // Get the function
        FunctionSymbol symbol = (FunctionSymbol) currentScope.resolve(ctx.IDENT().toString());
        LLVMValueRef function = symbol.getPointer();

        // Get the argument and result
        int argumentCount = 0;
        PointerPointer<Pointer> pointer;
        if (ctx.funcRParams() != null) {
            argumentCount = ctx.funcRParams().param().size();
            pointer = new PointerPointer<>(argumentCount);
            for (int i = 0; i < ctx.funcRParams().param().size(); i++) {
                LLVMValueRef argument = visit(ctx.funcRParams().param().get(i));
                pointer.put(i, argument.getPointer());
            }
        } else {
            pointer = new PointerPointer<>(argumentCount);
        }

        if (symbol.getType() != LLVMVoidType()) {
            // Get the result
            LLVMValueRef returnValue = LLVMBuildCall2(builder, symbol.getType(), function, pointer, argumentCount, "returnValue");
            return returnValue;
        } else {
            return null;
        }
    }

    @Override
    public LLVMValueRef visitUnaryOpExp(SysYParser.UnaryOpExpContext ctx) {
        LLVMValueRef result = visit(ctx.exp());
        if (ctx.unaryOp().MINUS() != null) {
            result = LLVMBuildNSWSub(builder, LLVMConstInt(LLVMInt32Type(), 0, 0), result, "result");
        } else if (ctx.unaryOp().PLUS() != null) {
            result = LLVMBuildNSWAdd(builder, LLVMConstInt(LLVMInt32Type(), 0, 0), result, "result");
        } else if (ctx.unaryOp().NOT() != null) {
            result = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(LLVMInt32Type(), 0, 0), result, "result");
            result = LLVMBuildXor(builder, result, LLVMConstInt(LLVMInt1Type(), 1, 0), "result");
            result = LLVMBuildZExt(builder, result, LLVMInt32Type(), "result");
        }
        return result;
    }

    @Override
    public LLVMValueRef visitLowerCalExp(SysYParser.LowerCalExpContext ctx) {
        LLVMValueRef leftVal = visit(ctx.exp(0));
        LLVMValueRef rightVal = visit(ctx.exp(1));
        if (ctx.PLUS() != null) {
            leftVal = LLVMBuildAdd(builder, leftVal, rightVal, "tmp_");
        } else if (ctx.MINUS() != null) {
            leftVal = LLVMBuildSub(builder, leftVal, rightVal, "tmp_");
        }
        return leftVal;
    }

    @Override
    public LLVMValueRef visitUpperCalExp(SysYParser.UpperCalExpContext ctx) {
        LLVMValueRef leftVal = visit(ctx.exp(0));
        LLVMValueRef rightVal = visit(ctx.exp(1));
        if (ctx.MUL() != null) {
            leftVal = LLVMBuildMul(builder, leftVal, rightVal, "tmp_");
        } else if (ctx.DIV() != null) {
            leftVal = LLVMBuildSDiv(builder, leftVal, rightVal, "tmp_");
        } else if (ctx.MOD() != null) {
            leftVal = LLVMBuildSRem(builder, leftVal, rightVal, "tmp_");
        }
        return leftVal;
    }

    @Override
    public LLVMValueRef visitLVal(SysYParser.LValContext ctx) {
        VariableSymbol symbol = (VariableSymbol) currentScope.resolve(ctx.IDENT().toString());
        return symbol.getPointer();
    }

    // Stream Control Statement
    @Override
    public LLVMValueRef visitIfStmt(SysYParser.IfStmtContext ctx) {
        LLVMValueRef condition = visit(ctx.cond());
        LLVMBasicBlockRef ifTrueBlock = LLVMAppendBasicBlock(currentFunction, "ifConditionIsTrue");
        LLVMBasicBlockRef ifFalseBlock = LLVMAppendBasicBlock(currentFunction, "ifConditionIsFalse");
        LLVMBasicBlockRef nextBlock = LLVMAppendBasicBlock(currentFunction, "entry");

        condition = LLVMBuildZExt(builder, condition, LLVMInt32Type(), "_tmp");
        condition = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(LLVMInt32Type(), 0, 0), condition, "ifCondition");
        LLVMBuildCondBr(builder, condition, ifTrueBlock, ifFalseBlock);

        // ifTrueBlock
        LLVMPositionBuilderAtEnd(builder, ifTrueBlock);
        blockStack.push(ifTrueBlock);
        visit(ctx.stmt(0));
        blockStack.pop();
        LLVMBuildBr(builder, nextBlock);
        LLVMPositionBuilderAtEnd(builder, nextBlock);

        // ifFalseBlock
        LLVMPositionBuilderAtEnd(builder, ifFalseBlock);
        if (ctx.ELSE() != null) {
            blockStack.push(ifFalseBlock);
            visit(ctx.stmt(1));
            blockStack.pop();
        }
        LLVMBuildBr(builder, nextBlock);
        LLVMPositionBuilderAtEnd(builder, nextBlock);

        // nextBlock as the alternative of origin block
        blockStack.pop();
        blockStack.push(nextBlock);
        return null;
    }

    @Override
    public LLVMValueRef visitWhileStmt(SysYParser.WhileStmtContext ctx) {
        LLVMBasicBlockRef conditionBlock = LLVMAppendBasicBlock(currentFunction, "whileCondition");
        LLVMBasicBlockRef bodyBlock = LLVMAppendBasicBlock(currentFunction, "whileBody");
        LLVMBasicBlockRef nextBlock = LLVMAppendBasicBlock(currentFunction, "entry");
        Loop loop = new Loop(conditionBlock, bodyBlock, nextBlock);
        loopStack.push(loop);
        LLVMBuildBr(builder, conditionBlock);

        // conditionBlock
        LLVMPositionBuilderAtEnd(builder, conditionBlock);
        blockStack.push(conditionBlock);
        LLVMValueRef condition = visit(ctx.cond());
        blockStack.pop();

        condition = LLVMBuildZExt(builder, condition, LLVMInt32Type(), "_tmp");
        condition = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(LLVMInt32Type(), 0, 0), condition, "whileCondition");
        LLVMBuildCondBr(builder, condition, bodyBlock, nextBlock);

        // bodyBlock
        LLVMPositionBuilderAtEnd(builder, bodyBlock);
        blockStack.push(bodyBlock);
        visit(ctx.stmt());
        blockStack.pop();
        LLVMBuildBr(builder, conditionBlock);

        // nextBlock
        LLVMPositionBuilderAtEnd(builder, nextBlock);
        // nextBlock as the alternative of origin block
        blockStack.pop();
        blockStack.push(nextBlock);
        loopStack.pop();
        return null;
    }

    @Override
    public LLVMValueRef visitBreakStmt(SysYParser.BreakStmtContext ctx) {
        LLVMBuildBr(builder, loopStack.peek().getNextBlock());
        return null;
    }

    @Override
    public LLVMValueRef visitContinueStmt(SysYParser.ContinueStmtContext ctx) {
        LLVMBuildBr(builder, loopStack.peek().getConditionBlock());
        return null;
    }

    // Condition Expression
    @Override
    public LLVMValueRef visitLtCond(SysYParser.LtCondContext ctx) {
        LLVMValueRef lhs = visit(ctx.cond(0));
        lhs = LLVMBuildZExt(builder, lhs, LLVMInt32Type(), "lhs");
        LLVMValueRef rhs = visit(ctx.cond(1));
        rhs = LLVMBuildZExt(builder, rhs, LLVMInt32Type(), "rhs");
        LLVMValueRef tmp = LLVMBuildICmp(builder, LLVMIntSLT, lhs, rhs, "tmp_");
        tmp = LLVMBuildZExt(builder, tmp, LLVMInt32Type(), "tmp_");
        tmp = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(LLVMInt32Type(), 0, 0), tmp, "tmp_");
        return tmp;
    }

    @Override
    public LLVMValueRef visitGtCond(SysYParser.GtCondContext ctx) {
        LLVMValueRef lhs = visit(ctx.cond(0));
        lhs = LLVMBuildZExt(builder, lhs, LLVMInt32Type(), "lhs");
        LLVMValueRef rhs = visit(ctx.cond(1));
        rhs = LLVMBuildZExt(builder, rhs, LLVMInt32Type(), "rhs");
        LLVMValueRef tmp = LLVMBuildICmp(builder, LLVMIntSGT, lhs, rhs, "tmp_");
        tmp = LLVMBuildZExt(builder, tmp, LLVMInt32Type(), "tmp_");
        tmp = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(LLVMInt32Type(), 0, 0), tmp, "tmp_");
        return tmp;
    }

    @Override
    public LLVMValueRef visitLeCond(SysYParser.LeCondContext ctx) {
        LLVMValueRef lhs = visit(ctx.cond(0));
        lhs = LLVMBuildZExt(builder, lhs, LLVMInt32Type(), "lhs");
        LLVMValueRef rhs = visit(ctx.cond(1));
        rhs = LLVMBuildZExt(builder, rhs, LLVMInt32Type(), "rhs");
        LLVMValueRef tmp = LLVMBuildICmp(builder, LLVMIntSLE, lhs, rhs, "tmp_");
        tmp = LLVMBuildZExt(builder, tmp, LLVMInt32Type(), "tmp_");
        tmp = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(LLVMInt32Type(), 0, 0), tmp, "tmp_");
        return tmp;
    }

    @Override
    public LLVMValueRef visitGeCond(SysYParser.GeCondContext ctx) {
        LLVMValueRef lhs = visit(ctx.cond(0));
        lhs = LLVMBuildZExt(builder, lhs, LLVMInt32Type(), "lhs");
        LLVMValueRef rhs = visit(ctx.cond(1));
        rhs = LLVMBuildZExt(builder, rhs, LLVMInt32Type(), "rhs");
        LLVMValueRef tmp = LLVMBuildICmp(builder, LLVMIntSGE, lhs, rhs, "tmp_");
        tmp = LLVMBuildZExt(builder, tmp, LLVMInt32Type(), "tmp_");
        tmp = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(LLVMInt32Type(), 0, 0), tmp, "tmp_");
        return tmp;
    }

    @Override
    public LLVMValueRef visitEqCond(SysYParser.EqCondContext ctx) {
        LLVMValueRef lhs = visit(ctx.cond(0));
        lhs = LLVMBuildZExt(builder, lhs, LLVMInt32Type(), "lhs");
        LLVMValueRef rhs = visit(ctx.cond(1));
        rhs = LLVMBuildZExt(builder, rhs, LLVMInt32Type(), "rhs");
        LLVMValueRef tmp = LLVMBuildICmp(builder, LLVMIntEQ, lhs, rhs, "tmp_");
        tmp = LLVMBuildZExt(builder, tmp, LLVMInt32Type(), "tmp_");
        tmp = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(LLVMInt32Type(), 0, 0), tmp, "tmp_");
        return tmp;
    }

    @Override
    public LLVMValueRef visitNeqCond(SysYParser.NeqCondContext ctx) {
        LLVMValueRef lhs = visit(ctx.cond(0));
        lhs = LLVMBuildZExt(builder, lhs, LLVMInt32Type(), "lhs");
        LLVMValueRef rhs = visit(ctx.cond(1));
        rhs = LLVMBuildZExt(builder, rhs, LLVMInt32Type(), "rhs");
        LLVMValueRef tmp = LLVMBuildICmp(builder, LLVMIntNE, lhs, rhs, "tmp_");
        tmp = LLVMBuildZExt(builder, tmp, LLVMInt32Type(), "tmp_");
        tmp = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(LLVMInt32Type(), 0, 0), tmp, "tmp_");
        return tmp;
    }

    @Override
    public LLVMValueRef visitAndCond(SysYParser.AndCondContext ctx) {
        LLVMValueRef lhs = visit(ctx.cond(0));
        lhs = LLVMBuildZExt(builder, lhs, LLVMInt32Type(), "lhs");
        LLVMBasicBlockRef lhsIsTrueBlock = LLVMAppendBasicBlock(currentFunction, "lhsIsTrue");
        LLVMBasicBlockRef lhsIsFalseBlock = LLVMAppendBasicBlock(currentFunction, "lhsIsFalse");
        LLVMBasicBlockRef nextBlock = LLVMAppendBasicBlock(currentFunction, "entry");

        lhs = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(LLVMInt32Type(), 0, 0), lhs, "tmp_");
        LLVMBuildCondBr(builder, lhs, lhsIsTrueBlock, lhsIsFalseBlock);

        // lhsIsTrue
        LLVMPositionBuilderAtEnd(builder, lhsIsTrueBlock);
        blockStack.push(lhsIsTrueBlock);
        LLVMValueRef rhs = visit(ctx.cond(1));
        lhs = LLVMBuildZExt(builder, lhs, LLVMInt32Type(), "lhs");
        rhs = LLVMBuildZExt(builder, rhs, LLVMInt32Type(), "rhs");
        LLVMBuildBr(builder, nextBlock);
        blockStack.pop();

        // lhsIsFalse
        // Skip rhs if lhs is false
        LLVMPositionBuilderAtEnd(builder, lhsIsFalseBlock);
        LLVMBuildBr(builder, nextBlock);

        // nextBlock
        LLVMPositionBuilderAtEnd(builder, nextBlock);
        // nextBlock as the alternative of origin block
        blockStack.pop();
        blockStack.push(nextBlock);

        LLVMValueRef andResult = LLVMBuildAnd(builder, lhs, rhs, "tmp_");
        return LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(LLVMInt32Type(), 0, 0), andResult, "tmp_");
    }

    @Override
    public LLVMValueRef visitOrCond(SysYParser.OrCondContext ctx) {
        LLVMValueRef lhs = visit(ctx.cond(0));
        lhs = LLVMBuildZExt(builder, lhs, LLVMInt32Type(), "lhs");
        LLVMBasicBlockRef lhsIsTrueBlock = LLVMAppendBasicBlock(currentFunction, "lhsIsTrue");
        LLVMBasicBlockRef lhsIsFalseBlock = LLVMAppendBasicBlock(currentFunction, "lhsIsFalse");
        LLVMBasicBlockRef nextBlock = LLVMAppendBasicBlock(currentFunction, "entry");

        lhs = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(LLVMInt32Type(), 0, 0), lhs, "tmp_");
        LLVMBuildCondBr(builder, lhs, lhsIsTrueBlock, lhsIsFalseBlock);

        // lhsIsTrue
        // Skip rhs if lhs is true
        LLVMPositionBuilderAtEnd(builder, lhsIsTrueBlock);
        LLVMBuildBr(builder, nextBlock);

        // lhsIsFalse
        LLVMPositionBuilderAtEnd(builder, lhsIsFalseBlock);
        blockStack.push(lhsIsFalseBlock);
        LLVMValueRef rhs = visit(ctx.cond(1));
        lhs = LLVMBuildZExt(builder, lhs, LLVMInt32Type(), "lhs");
        rhs = LLVMBuildZExt(builder, rhs, LLVMInt32Type(), "rhs");
        LLVMBuildBr(builder, nextBlock);
        blockStack.pop();

        // nextBlock
        LLVMPositionBuilderAtEnd(builder, nextBlock);
        // nextBlock as the alternative of origin block
        blockStack.pop();
        blockStack.push(nextBlock);

        LLVMValueRef orResult = LLVMBuildOr(builder, lhs, rhs, "tmp_");
        return LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(LLVMInt32Type(), 0, 0), orResult, "tmp_");
    }

    @Override
    public LLVMValueRef visitTrueCond(SysYParser.TrueCondContext ctx) {
        return LLVMConstInt(LLVMInt1Type(), 1, 0);
    }

    @Override
    public LLVMValueRef visitFalseCond(SysYParser.FalseCondContext ctx) {
        return LLVMConstInt(LLVMInt1Type(), 0, 0);
    }
}
