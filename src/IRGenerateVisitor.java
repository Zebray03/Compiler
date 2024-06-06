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

    @Override
    public LLVMValueRef visitProgram(SysYParser.ProgramContext ctx) {
        module = LLVMModuleCreateWithName("module");
        builder = LLVMCreateBuilder();
        globalScope = new GlobalScope(null);
        this.currentScope = globalScope;
        blockStack = new Stack<>();
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
        LLVMTypeRef returnType = LLVMInt32Type();

        PointerPointer<Pointer> argumentTypes;
        int argumentCount;
        if (ctx.funcFParams() != null) {
            argumentCount = ctx.funcFParams().funcFParam().size();
            argumentTypes = new PointerPointer<>(argumentCount);
        } else {
            argumentCount = 0;
            argumentTypes = null;
        }

        // Generate the function type
        LLVMTypeRef functionType = LLVMFunctionType(returnType, argumentTypes, argumentCount, 0);

        // Generate the function
        LLVMValueRef function = LLVMAddFunction(module, ctx.IDENT().toString(), functionType);

        currentFunction = function;

        FunctionSymbol symbol = new FunctionSymbol(ctx.IDENT().toString(), functionType, function);

        currentScope.define(symbol);
        currentScope = new LocalScope(currentScope);

        LLVMBasicBlockRef block = LLVMAppendBasicBlock(function, ctx.IDENT().toString() + "Entry");
        blockStack.push(block);
        LLVMPositionBuilderAtEnd(builder, block);

        if (argumentCount != 0) {
            visit(ctx.funcFParams());
        }

        visit(ctx.block());

        currentScope = currentScope.getEnclosingScope();
        blockStack.pop();
        return null;
    }

    @Override
    public LLVMValueRef visitFuncFParams(SysYParser.FuncFParamsContext ctx) {
        for (int i = 0; i < ctx.funcFParam().size(); i++) {
            LLVMValueRef pointer = LLVMBuildAlloca(builder, LLVMInt32Type(), ctx.funcFParam(i).IDENT().getText());
            VariableSymbol symbol = new VariableSymbol(ctx.funcFParam(i).IDENT().toString(), LLVMInt32Type(), pointer, false);
            currentScope.define(symbol);
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
        LLVMValueRef returnVal = visit(ctx.exp());
        LLVMBuildRet(builder, returnVal);
        return null;
    }

    @Override
    public LLVMValueRef visitCombineExp(SysYParser.CombineExpContext ctx) {
        return visit(ctx.exp());
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
        FunctionSymbol symbol = (FunctionSymbol) currentScope.resolve(ctx.IDENT().toString());
        LLVMValueRef function = symbol.getPointer();
        PointerPointer<Pointer> pointer;
        LLVMValueRef returnValue = null;

        if (ctx.funcRParams() != null) {
            pointer = new PointerPointer<>();
            for (int i = 0; i < ctx.funcRParams().param().size(); i++) {
                LLVMValueRef argu = visit(ctx.funcRParams().param().get(i));
                System.out.println();
                pointer.put(argu);
                returnValue = LLVMBuildCall2(builder, symbol.getType(), function, pointer, ctx.funcRParams().param().size(), "returnValue");
            }
        } else {
            pointer = null;
            returnValue = LLVMBuildCall2(builder, symbol.getType(), function, pointer, 0, "returnValue");
        }
        return returnValue;
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
        LLVMBasicBlockRef ifTrueBlock = LLVMAppendBasicBlock(currentFunction, "true");
        LLVMBasicBlockRef ifFalseBlock = LLVMAppendBasicBlock(currentFunction, "false");
        LLVMBasicBlockRef nextBlock = LLVMAppendBasicBlock(currentFunction, "entry");
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

        // conditionBlock
        LLVMPositionBuilderAtEnd(builder, conditionBlock);
        blockStack.push(conditionBlock);
        LLVMValueRef condition = visit(ctx.cond());
        blockStack.pop();
        LLVMBuildCondBr(builder, condition, bodyBlock, nextBlock);

        // bodyBlock
        LLVMPositionBuilderAtEnd(builder, bodyBlock);
        blockStack.push(bodyBlock);
        visit(ctx.stmt());
        blockStack.pop();
        condition = visit(ctx.cond());
        LLVMBuildCondBr(builder, condition, bodyBlock, nextBlock);

        // nextBlock
        LLVMPositionBuilderAtEnd(builder, nextBlock);
        // nextBlock as the alternative of origin block
        blockStack.pop();
        blockStack.push(nextBlock);
        return null;
    }

    @Override
    public LLVMValueRef visitBreakStmt(SysYParser.BreakStmtContext ctx) {

        return super.visitBreakStmt(ctx);
    }

    @Override
    public LLVMValueRef visitContinueStmt(SysYParser.ContinueStmtContext ctx) {

        return super.visitContinueStmt(ctx);
    }

    // Condition Expression
    @Override
    public LLVMValueRef visitLtCond(SysYParser.LtCondContext ctx) {
        LLVMValueRef lhs = visit(ctx.cond(0));
        LLVMValueRef rhs = visit(ctx.cond(1));
        LLVMValueRef tmp = LLVMBuildICmp(builder, LLVMIntSLT, lhs, rhs, "tmp_");
        tmp = LLVMBuildZExt(builder, tmp, LLVMInt32Type(), "tmp_");
        tmp = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(LLVMInt32Type(), 0, 0), tmp, "tmp_");
        return tmp;
    }

    @Override
    public LLVMValueRef visitGtCond(SysYParser.GtCondContext ctx) {
        LLVMValueRef lhs = visit(ctx.cond(0));
        LLVMValueRef rhs = visit(ctx.cond(1));
        LLVMValueRef tmp = LLVMBuildICmp(builder, LLVMIntSGT, lhs, rhs, "tmp_");
        tmp = LLVMBuildZExt(builder, tmp, LLVMInt32Type(), "tmp_");
        tmp = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(LLVMInt32Type(), 0, 0), tmp, "tmp_");
        return tmp;
    }

    @Override
    public LLVMValueRef visitLeCond(SysYParser.LeCondContext ctx) {
        LLVMValueRef lhs = visit(ctx.cond(0));
        LLVMValueRef rhs = visit(ctx.cond(1));
        LLVMValueRef tmp = LLVMBuildICmp(builder, LLVMIntSLE, lhs, rhs, "tmp_");
        tmp = LLVMBuildZExt(builder, tmp, LLVMInt32Type(), "tmp_");
        tmp = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(LLVMInt32Type(), 0, 0), tmp, "tmp_");
        return tmp;
    }

    @Override
    public LLVMValueRef visitGeCond(SysYParser.GeCondContext ctx) {
        LLVMValueRef lhs = visit(ctx.cond(0));
        LLVMValueRef rhs = visit(ctx.cond(1));
        LLVMValueRef tmp = LLVMBuildICmp(builder, LLVMIntSGE, lhs, rhs, "tmp_");
        tmp = LLVMBuildZExt(builder, tmp, LLVMInt32Type(), "tmp_");
        tmp = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(LLVMInt32Type(), 0, 0), tmp, "tmp_");
        return tmp;
    }

    @Override
    public LLVMValueRef visitEqCond(SysYParser.EqCondContext ctx) {
        LLVMValueRef lhs = visit(ctx.cond(0));
        LLVMValueRef rhs = visit(ctx.cond(1));
        LLVMValueRef tmp = LLVMBuildICmp(builder, LLVMIntEQ, lhs, rhs, "tmp_");
        tmp = LLVMBuildZExt(builder, tmp, LLVMInt32Type(), "tmp_");
        tmp = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(LLVMInt32Type(), 0, 0), tmp, "tmp_");
        return tmp;
    }

    @Override
    public LLVMValueRef visitNeqCond(SysYParser.NeqCondContext ctx) {
        LLVMValueRef lhs = visit(ctx.cond(0));
        LLVMValueRef rhs = visit(ctx.cond(1));
        LLVMValueRef tmp = LLVMBuildICmp(builder, LLVMIntNE, lhs, rhs, "tmp_");
        tmp = LLVMBuildZExt(builder, tmp, LLVMInt32Type(), "tmp_");
        tmp = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(LLVMInt32Type(), 0, 0), tmp, "tmp_");
        return tmp;
    }


}
