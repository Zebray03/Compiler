import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

public interface Symbol {
    String getName();
    LLVMTypeRef getType();
}
