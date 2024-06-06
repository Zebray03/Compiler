import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;

public class Loop {
    private final LLVMBasicBlockRef conditionBlock;
    private final LLVMBasicBlockRef bodyBlock;
    private final LLVMBasicBlockRef nextBlock;

    public Loop(LLVMBasicBlockRef condition, LLVMBasicBlockRef body, LLVMBasicBlockRef next) {
        conditionBlock = condition;
        bodyBlock = body;
        nextBlock = next;
    }

    public LLVMBasicBlockRef getConditionBlock() {
        return conditionBlock;
    }

    public LLVMBasicBlockRef getBodyBlock() {
        return bodyBlock;
    }

    public LLVMBasicBlockRef getNextBlock() {
        return nextBlock;
    }
}
