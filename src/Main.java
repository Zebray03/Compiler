import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.bytedeco.javacpp.BytePointer;

import static org.bytedeco.llvm.global.LLVM.*;

import java.io.IOException;


public class Main {
    public static void main(String[] args) throws IOException {
        String source = args[0];
        String target = args[1];

        CharStream input = CharStreams.fromFileName(source);
        SysYLexer sysYLexer = new SysYLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(sysYLexer);
        SysYParser sysYParser = new SysYParser(tokens);
        sysYParser.removeErrorListeners();
        IRGenerateVisitor visitor = new IRGenerateVisitor();

        ParseTree tree = sysYParser.program();


        //初始化LLVM
        LLVMInitializeCore(LLVMGetGlobalPassRegistry());
        LLVMLinkInMCJIT();
        LLVMInitializeNativeAsmPrinter();
        LLVMInitializeNativeAsmParser();
        LLVMInitializeNativeTarget();

        visitor.visit(tree);

        //LLVMDumpModule(visitor.module);
        BytePointer error = new BytePointer();
        LLVMPrintModuleToFile(visitor.module, target, error);
    }
}