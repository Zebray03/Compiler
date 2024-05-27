import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

public class VariableSymbol implements Symbol {
    final String name;
    final LLVMTypeRef type;
    final LLVMValueRef pointer;
    final boolean isVariadic;

    public VariableSymbol(String name, LLVMTypeRef type, LLVMValueRef pointer, boolean whetherVariadic) {
        this.name = name;
        this.type = type;
        this.pointer = pointer;
        this.isVariadic = whetherVariadic;
    }

    public String getName() {
        return name;
    }

    public LLVMTypeRef getType() {
        return type;
    }

    public LLVMValueRef getPointer() {
        return pointer;
    }

    public boolean getIsVariadic() {
        return isVariadic;
    }
}