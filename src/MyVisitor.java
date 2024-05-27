import org.antlr.v4.runtime.Token;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;

import static org.bytedeco.llvm.global.LLVM.*;

public class MyVisitor extends SysYParserBaseVisitor<LLVMValueRef> {
    LLVMModuleRef module;
    LLVMBuilderRef builder;
    LLVMTypeRef i32Type;

    GlobalScope globalScope;
    Scope currentScope;

    @Override
    public LLVMValueRef visitProgram(SysYParser.ProgramContext ctx) {
        module = LLVMModuleCreateWithName("module");
        builder = LLVMCreateBuilder();
        i32Type = LLVMInt32Type();

        globalScope = new GlobalScope(null);
        this.currentScope = globalScope;

        return super.visitProgram(ctx);
    }

    @Override
    // 对于 int 常量，它们的处理与 int 变量一致
    public LLVMValueRef visitConstDef(SysYParser.ConstDefContext ctx) {
        if (currentScope == globalScope) {
            LLVMValueRef globalVar = LLVMAddGlobal(module, i32Type, ctx.IDENT().getText());
            VariableSymbol globalSymbol = new VariableSymbol(ctx.IDENT().toString(), i32Type, globalVar.getPointer(), false);
            currentScope.define(globalSymbol);
            LLVMSetInitializer(globalVar, visit(ctx.constInitVal()));
        } else {
            //申请一块能存放int型的内存
            LLVMValueRef pointer = LLVMBuildAlloca(builder, i32Type, ctx.IDENT().getText());

            VariableSymbol symbol = new VariableSymbol(ctx.IDENT().toString(), i32Type, pointer, false);
            currentScope.define(symbol);

            //将数值存入该内存
            LLVMBuildStore(builder, visit(ctx.constInitVal()), pointer);
        }
        return null;
    }

    @Override
    public LLVMValueRef visitVarDef(SysYParser.VarDefContext ctx) {
        if (currentScope == globalScope) {
            LLVMValueRef globalVar = LLVMAddGlobal(module, i32Type, ctx.IDENT().getText());
            VariableSymbol globalSymbol = new VariableSymbol(ctx.IDENT().toString(), i32Type, globalVar.getPointer(), true);
            currentScope.define(globalSymbol);
            if (ctx.initVal() != null) {
                LLVMSetInitializer(globalVar, visit(ctx.initVal()));
            }
        } else {
            //申请一块能存放int型的内存
            LLVMValueRef pointer = LLVMBuildAlloca(builder, i32Type, ctx.IDENT().getText());

            VariableSymbol symbol = new VariableSymbol(ctx.IDENT().toString(), i32Type, pointer, true);
            currentScope.define(symbol);
            if (ctx.initVal() != null) {
                //将数值存入该内存
                LLVMBuildStore(builder, visit(ctx.initVal()), pointer);
            }
        }
        return null;
    }

    @Override
    public LLVMValueRef visitFuncDef(SysYParser.FuncDefContext ctx) {
        //生成返回值类型
        LLVMTypeRef returnType = i32Type;

        //生成函数参数类型
        PointerPointer<Pointer> argumentTypes;
        int argumentCount;
        if (ctx.funcFParams() != null) {
            argumentCount = ctx.funcFParams().funcFParam().size();
            argumentTypes = new PointerPointer<>(argumentCount);
        } else {
            argumentCount = 0;
            argumentTypes = null;
        }

        //生成函数类型
        LLVMTypeRef ft = LLVMFunctionType(returnType, argumentTypes, argumentCount, 0);

        //生成函数，即向之前创建的module中添加函数
        LLVMValueRef function = LLVMAddFunction(module, ctx.IDENT().toString(), ft);

        LLVMBasicBlockRef block = LLVMAppendBasicBlock(function, ctx.IDENT().toString() + "Entry");

        LLVMPositionBuilderAtEnd(builder, block);
        visit(ctx.block());
        return null;
    }

    @Override
    public LLVMValueRef visitBlock(SysYParser.BlockContext ctx) {
        Scope localScope = new LocalScope(currentScope);
        this.currentScope = localScope;
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
        Symbol symbol = currentScope.resolve(ctx.lVal().IDENT().toString());
        //从内存中将值取出
        LLVMValueRef lVal = LLVMBuildLoad(builder, symbol.getPointer(), symbol.getName());
        return lVal;
    }

    @Override
    public LLVMValueRef visitIntExp(SysYParser.IntExpContext ctx) {
        long result = 0;
        //判断进制
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
        return LLVMConstInt(i32Type, result, 0);
    }

    @Override
    public LLVMValueRef visitUnaryOpExp(SysYParser.UnaryOpExpContext ctx) {
        LLVMValueRef result = visit(ctx.exp());
        if (ctx.unaryOp().MINUS() != null) {
            result = LLVMBuildNSWSub(builder, LLVMConstInt(i32Type, 0, 0), result, "result");
        } else if (ctx.unaryOp().PLUS() != null) {
            result = LLVMBuildNSWAdd(builder, LLVMConstInt(i32Type, 0, 0), result, "result");
        } else if (ctx.unaryOp().NOT() != null) {
            result = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(i32Type, 0, 0), result, "result");
            result = LLVMBuildXor(builder, result, LLVMConstInt(LLVMInt1Type(), 1, 0), "result");
            result = LLVMBuildZExt(builder, result, i32Type, "result");
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
        Symbol symbol = currentScope.resolve(ctx.IDENT().toString());
        return symbol.getPointer();
    }
}
