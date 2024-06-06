import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;


public class FunctionSymbol implements Symbol {
    final String name;
    final LLVMTypeRef type;
    final LLVMValueRef pointer;

    public FunctionSymbol(String functionName, LLVMTypeRef functionType, LLVMValueRef functionPointer) {
        this.name = functionName;
        this.type = functionType;
        this.pointer = functionPointer;
    }

    public String getName() {
        return name;
    }

    public LLVMTypeRef getType() {
        return type;
    }

    @Override
    public LLVMValueRef getPointer() {
        return pointer;
    }
}