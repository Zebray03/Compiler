public class ErrorOutputHelper {
    public static void printSemanticError(ErrorType type, int line, String text) {
        switch (type) {
            case VARIABLE_NOT_DEFINED:
                System.err.println("Error type 1 at Line " + line + ": Undefined variable: " + text + ".");
                break;
            case FUNCTION_NOT_DEFINED:
                System.err.println("Error type 2 at Line " + line + ": Undefined function: " + text + ".");
                break;
            case VARIABLE_REDEFINE:
                System.err.println("Error type 3 at Line " + line + ": Redefined variable: " + text + ".");
                break;
            case FUNCTION_REDEFINE:
                System.err.println("Error type 4 at Line " + line + ": Redefined function: " + text + ".");
                break;
            case ASSIGN_DISMATCH:
                System.err.println("Error type 5 at Line " + line + ": Type mismatched for assignment.");
                break;
            case DISMATCH_OPERAND:
                System.err.println("Error type 6 at Line " + line + ": Type mismatched for operands.");
                break;
            case RETURN_VALUE_DISMATCH:
                System.err.println("Error type 7 at Line " + line + ": Type mismatched for return.");
                break;
            case FUNCTION_PARAM_NOT_APPLICABLE:
                System.err.println("Error type 8 at Line " + line + ": Function is not applicable for arguments.");
                break;
            case SUBSCRIPT_ERROR:
                System.err.println("Error type 9 at Line " + line + ": Not an array: " + text + ".");
                break;
            case FUNCTION_CALL_ERROR:
                System.err.println("Error type 10 at Line " + line + ": Not a function: " + text + ".");
                break;
            case LVAL_ERROR:
                System.err.println("Error type 11 at Line " + line + ": The left-hand side of an assignment must be a variable.");
                break;
            default:
                break;
        }
    }
}