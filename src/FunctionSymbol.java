import org.bytedeco.llvm.LLVM.LLVMTypeRef;


public class FunctionSymbol implements Symbol {
    final String name;
    final LLVMTypeRef type;

    public FunctionSymbol(String name, LLVMTypeRef functionType) {
        this.name = name;
        this.type = functionType;
    }

    public String getName() {
        return name;
    }

    public LLVMTypeRef getType() {
        return type;
    }
}